# EventRush 核心代码讲解笔记

这份笔记把第 27 阶段阅读路线里的关键代码展开说明。建议你对照源码阅读，不要只看结论。

本阶段重点：

```text
抢票主流程 -> 支付幂等 -> 超时取消 -> 异步消费幂等 -> Redis Lua -> 接口排查
```

## 1. `TicketingService.grabTicket`

文件：

```text
src/main/java/com/eventrush/service/TicketingService.java
```

这个方法是同步抢票的主编排入口。它没有把所有逻辑塞进一个数据库语句，而是按照业务顺序组织多个步骤。

### 执行顺序

#### 第一步：检查限流

```java
checkGrabRateLimit(userId);
```

当 Redis 限流开关关闭时，这一步直接返回；开启后才会检查用户在时间窗口内是否超过抢票次数。

要理解的点：

- 限流解决的是“请求太多”，不是“库存正确性”。
- 即使限流关闭，后面的库存和重复抢票保护仍然必须执行。

#### 第二步：确认票档存在

```java
eventCatalogService.getTicketCategory(sessionId, ticketCategoryId);
```

这一步先确认场次和票档合法，避免后面围绕不存在的资源扣库存。

#### 第三步：检查重复抢票

```java
if (hasGrabbed(userId, sessionId, ticketCategoryId)) {
    throw new BusinessException("user has already grabbed this ticket");
}
```

业务规则是：同一个用户、同一个场次、同一个票档不能重复抢。

持久化运行时，`hasGrabbed` 会调用 `TicketOrderRepository.existsActiveGrab`；内存测试时则扫描内存订单。已取消订单不算有效抢票记录。

#### 第四步：可选的 Redis 预扣减

```java
if (redisStockEnabled) {
    deductRedisStock(userId, sessionId, ticketCategoryId);
}
```

Redis 开关打开时，先用 Lua 脚本完成 Redis 侧的库存检查、重复用户检查和扣减。

Lua 的返回值会在 `deductRedisStock` 里翻译成业务异常：

| 返回值 | 业务含义 |
| --- | --- |
| `-1` | 库存不足 |
| `-2` | 重复抢票 |
| `-3` | Redis 库存没有初始化 |

#### 第五步：扣减业务库存

```java
eventCatalogService.deductStock(sessionId, ticketCategoryId);
```

这一步扣减应用侧或数据库侧的库存。项目的设计意图是：Redis 负责高并发入口的预扣减，业务数据仍然要落到订单和持久化库存中。

#### 第六步：创建待支付订单

```java
TicketOrder order = createPendingOrder(...);
```

订单状态是 `PENDING_PAYMENT`，表示用户获得了购买资格，但交易还没有完成。

为什么不是直接生成电子票：

- 用户还没有支付。
- 电子票是入场凭证，不应该给未支付用户。
- 待支付订单可以在超时后取消并释放库存。

#### 第七步：发布超时消息

```java
publishOrderTimeout(order);
```

如果 RocketMQ 可用，就发送订单超时消息；如果消息发布失败，定时扫描任务仍然可以兜底处理。

### 这个方法解决了什么问题

`grabTicket` 同时串起了：

- 限流
- 资源校验
- 防重复抢票
- Redis 预扣减
- 业务库存扣减
- 待支付订单创建
- 超时取消触发

### 你要注意的边界

Redis 操作和数据库事务不是同一个事务边界。当前项目适合学习和演示配置开关，真实生产环境还要继续考虑：

- Redis 预扣成功后数据库扣减失败，如何补偿 Redis。
- 数据库订单创建失败后，如何释放 Redis 预扣库存。
- 重试和对账如何保证最终一致。

这不是否定当前实现，而是你在面试中可以主动说出的后续演进点。

## 2. `TicketingService.payOrder`

文件：

```text
src/main/java/com/eventrush/service/TicketingService.java
```

这个方法体现了支付幂等和订单状态机。

### 执行顺序

#### 先查订单

```java
TicketOrder order = getOrder(orderId);
```

订单不存在时直接失败，不继续生成电子票。

#### 已支付时直接返回已有电子票

```java
if (order.status() == OrderStatus.PAID) {
    return getTicketByOrderId(orderId);
}
```

这是支付幂等的关键。

重复支付不是简单返回错误，而是返回第一次支付生成的电子票。这样调用方重试时可以得到稳定结果，不会生成第二张票。

#### 只允许待支付订单支付

```java
if (order.status() != OrderStatus.PENDING_PAYMENT) {
    throw new BusinessException("only pending payment orders can be paid");
}
```

已取消订单不能重新支付，避免订单状态被倒流。

#### 先更新订单，再创建电子票

```java
ticketOrderRepository.markPaid(orderId, payTime);
ElectronicTicket ticket = createElectronicTicket(orderId);
```

订单状态先变成 `PAID`，然后创建状态为 `VALID` 的电子票。

持久化版本里，数据库还会通过电子票的 `order_id` 唯一约束兜底，保证一个订单最多一张电子票。

### 这个方法解决了什么问题

- 防止重复支付生成多张票。
- 防止已取消订单重新支付。
- 把订单交易状态和电子票入场凭证关联起来。

### 你要注意的边界

支付状态更新和电子票创建最好处在同一个数据库事务中。当前服务方法有 `@Transactional`，所以持久化实现可以利用数据库事务回滚；如果未来改成跨服务调用，就要考虑事务消息、状态补偿或支付回调幂等表。

## 3. `TicketingService.cancelExpiredOrder`

文件：

```text
src/main/java/com/eventrush/service/TicketingService.java
```

这个方法是延时消息消费者和定时扫描共同依赖的业务入口。

### 第一步：读取订单当前状态

```java
TicketOrder order = getOrder(orderId);
```

延时消息只表示“可能到期了”，不能代表订单当前一定还没支付。

### 第二步：检查状态和时间

```java
if (order.status() != OrderStatus.PENDING_PAYMENT
        || order.expireTime().isAfter(LocalDateTime.now())) {
    return false;
}
```

只有两个条件同时满足，才允许取消：

- 订单仍然是 `PENDING_PAYMENT`。
- 当前时间已经超过订单过期时间。

已支付订单、已经取消订单、尚未到期订单都会直接返回 `false`。

### 第三步：带状态条件更新数据库

```java
ticketOrderRepository.markCanceledIfPending(order.id(), LocalDateTime.now());
```

Repository 中的 SQL 还会带上：

```sql
WHERE id = ? AND order_status = 'PENDING_PAYMENT'
```

这相当于第二道保护。即使两个消费者同时处理同一订单，也只有一个更新能成功。

### 第四步：释放库存

```java
releaseStock(order);
```

释放应用/数据库库存；如果 Redis 库存开关打开，还会增加 Redis 库存并移除用户抢票记录。

### 这个方法解决了什么问题

- 避免延时消息误取消已支付订单。
- 让重复消息消费具有幂等性。
- 让超时订单最终释放库存。
- 让 RocketMQ 和定时扫描复用同一套业务判断。

### 你要记住的一句话

> 延时消息是提醒，不是事实；订单当前状态才是事实。

## 4. `AsyncGrabService.process`

文件：

```text
src/main/java/com/eventrush/service/AsyncGrabService.java
```

这个方法是异步抢票消费者的核心。

### 第一步：抢占处理权

```java
if (!asyncGrabRequestRepository.markProcessingIfPending(message.requestId())) {
    return;
}
```

只有请求仍然是 `PENDING` 时，才能改成处理中。

如果同一条消息重复到达，第一次消费已经把状态改走，后续消费就会返回，不会重复调用 `ticketingService.grabTicket`。

### 第二步：调用同步业务

```java
TicketOrder order = ticketingService.grabTicket(...);
```

异步消费者并没有重新实现一套抢票规则，而是复用同步抢票业务。

这是一个重要设计：

- 同步和异步入口共用库存、幂等和订单规则。
- 避免两套实现逐渐产生差异。

### 第三步：写入成功或失败结果

成功时：

```java
asyncGrabRequestRepository.markSuccess(message.requestId(), order.id());
```

业务失败时：

```java
asyncGrabRequestRepository.markFailed(message.requestId(), exception.getMessage());
```

系统异常时也会记录失败状态，再把异常继续抛出，让消息基础设施决定后续处理策略。

### `submitGrab` 为什么先落库再入队

`submitGrab` 的顺序是：

1. 生成 `requestId`。
2. 创建 `PENDING` 请求记录。
3. 把消息放入 RocketMQ、Redis 队列或内存队列。
4. 返回 `requestId`。

如果入队失败，会把请求标记为失败，避免调用方拿到一个永远没有结果的请求。

### 这个方法解决了什么问题

- 异步抢票快速返回。
- 消费者重复执行不会重复创建订单。
- 调用方可以通过 `requestId` 查询结果。
- 同步和异步入口复用同一套核心业务。

## 5. `grab-ticket.lua`

文件：

```text
src/main/resources/lua/grab-ticket.lua
```

这个脚本接收两个 Key：

```text
KEYS[1] = 库存 Key
KEYS[2] = 已抢用户 Set Key
ARGV[1] = 用户 ID
```

### 执行顺序

#### 先读取库存

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
```

如果 Key 不存在，就按未初始化处理。

#### 检查库存是否初始化

```lua
if stock < 0 then
    return -3
end
```

#### 检查用户是否重复抢票

```lua
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end
```

#### 检查库存是否足够

```lua
if stock <= 0 then
    return -1
end
```

#### 一次性扣库存并记录用户

```lua
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
```

最后返回扣减后的库存值。

### 为什么它是原子的

脚本在 Redis 内部作为一个整体执行。库存读取、重复判断、扣减和记录用户之间不会被其他客户端命令插入。

### 这个脚本没有解决什么

它只保证 Redis 内部这组操作的原子性，不会自动保证：

- Redis 和数据库事务一致。
- 订单一定创建成功。
- Redis 宕机后数据自动恢复。
- 业务库存和 Redis 库存永远不漂移。

所以项目还需要数据库落库、库存释放、配置开关和压测对比。

## 6. `TraceFilter.doFilterInternal`

文件：

```text
src/main/java/com/eventrush/api/TraceFilter.java
```

这个过滤器在请求进入 Controller 前执行。

### 执行顺序

1. 读取请求头中的 `X-Trace-Id`。
2. 如果没有，就生成新的 traceId。
3. 放入 `TraceContext` 和日志 MDC。
4. 写入响应头。
5. 放行请求。
6. finally 中记录访问日志并清理上下文。

### 为什么要清理 MDC

Web 容器会复用线程。如果不清理，上一条请求的 traceId 可能污染下一条请求的日志。

### 你要记住的一句话

> traceId 不是业务字段，而是把一次请求的响应、日志和排查动作串起来的线索。

## 7. `AdminAuthFilter.doFilterInternal`

文件：

```text
src/main/java/com/eventrush/api/AdminAuthFilter.java
```

这个过滤器只拦截 `/api/admin/` 开头的路径。

### 放行条件

- 请求不是管理端路径。
- 或者请求头 `X-Admin-Key` 等于配置中的管理密钥。

### 拒绝行为

如果密钥不正确，就返回 HTTP `401` 和统一错误响应，不再进入 Controller。

### 这个实现适合什么阶段

它适合当前项目作为轻量管理端保护方案。真实系统继续发展后，可以替换成登录态、JWT、RBAC 或统一网关鉴权。

## 8. 把代码讲成一条链

你可以按下面这段话复述：

> 请求先进入 `TicketingController`，再由 `TicketingService.grabTicket` 编排限流、票档校验、防重复抢票、库存扣减和待支付订单创建。开启 Redis 时，库存和重复用户判断由 `grab-ticket.lua` 原子执行。支付进入 `payOrder` 后，已支付订单直接返回原电子票，保证幂等；超时消息进入 `cancelExpiredOrder` 后先查当前状态，只取消待支付订单并释放库存。异步抢票由 `AsyncGrabService.process` 通过请求状态抢占处理权，避免重复消息重复创建订单。最后 `TraceFilter` 用 traceId 串起响应和日志，`AdminAuthFilter` 保护管理端接口。

## 9. 这次阅读的验收标准

你不需要背每一行代码，但应该做到：

- 能画出同步抢票的 7 个步骤。
- 能解释支付幂等为什么返回已有电子票。
- 能解释延时消息为什么必须查订单状态。
- 能解释 `markProcessingIfPending` 如何防止重复消费。
- 能说出 Lua 的 `-1`、`-2`、`-3` 分别代表什么。
- 能指出 Redis 预扣减和数据库事务之间仍有一致性边界。
- 能说出 traceId 为什么要在 finally 中清理。
