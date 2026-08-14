# EventRush 核心代码阅读路线

这份文档是给你亲自读代码用的。目标不是把所有文件都看完，而是沿着业务链路读懂最关键的类：请求怎么进来、库存怎么扣、订单怎么流转、支付怎么幂等、MQ 怎么消费、接口怎么统一响应。

建议你每次只读一组，读完以后用自己的话回答“这段代码解决了什么业务问题”。

## 阅读总原则

读代码时按这个顺序看：

```text
接口入口 -> 业务编排 -> 数据落库 -> 中间件增强 -> 接口治理 -> 自动测试
```

不要一上来就钻进所有细节。先抓住主链路，再看分支能力。

## 第 1 组：接口入口

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/api/EventController.java` | 活动列表和活动详情从哪里进入 |
| `src/main/java/com/eventrush/api/TicketingController.java` | 抢票、异步抢票、查结果、支付、查单、查票、验票接口 |
| `src/main/java/com/eventrush/api/AdminController.java` | 后台按用户、订单、票码查询 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `TicketingController.grabTicket` | 同步抢票请求如何进入业务层 |
| `TicketingController.grabTicketAsync` | 异步抢票为什么返回 `requestId` |
| `TicketingController.payOrder` | 支付请求如何进入业务层 |
| `TicketingController.verifyTicket` | 验票请求如何进入业务层 |

### 读完要能回答

- 用户从接口层能完成哪些动作？
- 同步抢票和异步抢票的接口差异是什么？
- Controller 为什么只做参数接收，不写核心业务？

## 第 2 组：核心业务编排

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/TicketingService.java` | 抢票、支付、出票、验票、超时取消的主流程 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `grabTicket` | 抢票主流程：限流、查票档、防重复、扣库存、建订单、发超时消息 |
| `hasGrabbed` | 为什么同一用户同一票档不能重复抢 |
| `deductRedisStock` | Redis Lua 返回值如何转成业务错误 |
| `payOrder` | 支付幂等如何保证重复支付返回同一张票 |
| `verifyTicket` | 已核验电子票为什么不能重复核验 |
| `cancelExpiredOrders` | 定时扫描如何找出过期待支付订单 |
| `cancelExpiredOrder` | 超时取消为什么必须先检查订单状态 |
| `releaseStock` | 取消订单时为什么要释放库存 |

### 推荐阅读顺序

1. 先读 `grabTicket`，这是抢票主线。
2. 再读 `payOrder` 和 `verifyTicket`，这是支付出票和验票主线。
3. 最后读 `cancelExpiredOrder`，理解为什么已支付订单不能被误取消。

### 读完要能回答

- 抢票成功会生成什么状态的订单？
- 支付为什么要先判断订单状态？
- 重复支付为什么不会重复生成电子票？
- 延时消息到期后为什么不能直接取消订单？

## 第 3 组：活动和库存

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/EventCatalogService.java` | 活动、场次、票档和本地库存扣减 |
| `src/main/java/com/eventrush/domain/Event.java` | 活动数据结构 |
| `src/main/java/com/eventrush/domain/EventSession.java` | 场次数据结构 |
| `src/main/java/com/eventrush/domain/TicketCategory.java` | 票档和库存数据结构 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `EventCatalogService.seedData` | 本地学习数据从哪里来 |
| `EventCatalogService.getTicketCategory` | 如何定位具体票档 |
| `EventCatalogService.deductStock` | 库存不足时如何失败 |
| `EventCatalogService.releaseStock` | 取消订单后库存怎么加回来 |

### 读完要能回答

- 活动、场次、票档三者是什么关系？
- 抢票扣的是哪个层级的库存？
- 订单取消后库存为什么必须释放？

## 第 4 组：订单和电子票领域对象

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/domain/TicketOrder.java` | 订单字段和状态变化 |
| `src/main/java/com/eventrush/domain/OrderStatus.java` | 订单状态枚举 |
| `src/main/java/com/eventrush/domain/ElectronicTicket.java` | 电子票字段和核验变化 |
| `src/main/java/com/eventrush/domain/TicketStatus.java` | 电子票状态枚举 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `TicketOrder.paid` | 订单如何从待支付变成已支付 |
| `TicketOrder.canceled` | 订单如何变成已取消 |
| `ElectronicTicket.verify` | 电子票如何从有效变成已核验 |

### 读完要能回答

- 订单状态和电子票状态为什么要分开？
- 为什么电子票不是抢票成功时生成？
- 为什么状态变化要集中在领域对象里表达？

## 第 5 组：数据库持久化

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/TicketOrderRepository.java` | 订单创建、查询、状态更新 |
| `src/main/java/com/eventrush/service/ElectronicTicketRepository.java` | 电子票创建、查询、核验 |
| `src/main/resources/schema.sql` | 表结构和约束 |
| `src/main/resources/data.sql` | 初始化数据 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `TicketOrderRepository.createPending` | 创建待支付订单时如何处理重复抢票 |
| `TicketOrderRepository.existsActiveGrab` | 如何判断用户是否已经抢过 |
| `TicketOrderRepository.markPaid` | 为什么只允许待支付订单更新为已支付 |
| `TicketOrderRepository.markCanceledIfPending` | 为什么取消时也要带状态条件 |
| `ElectronicTicketRepository.create` | 单订单单电子票如何落库 |
| `ElectronicTicketRepository.markVerified` | 验票状态如何持久化 |

### 读完要能回答

- 为什么状态更新 SQL 要带 `WHERE order_status = ?`？
- 数据库约束在防重复和幂等里起什么作用？
- 应用层判断和数据库层兜底分别解决什么？

## 第 6 组：Redis Lua 库存扣减

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/RedisTicketStockService.java` | Redis 库存预加载、扣减、释放 |
| `src/main/resources/lua/grab-ticket.lua` | 原子抢票脚本 |
| `docs/redis-lua-pressure-switch.md` | Redis Lua 方案怎么开启和压测 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `preloadStock` | 应用启动时为什么要预加载库存 |
| `tryDeduct` | Java 如何调用 Lua 脚本 |
| `release` | 订单取消时 Redis 库存和用户记录如何恢复 |

### Lua 返回值

| 返回值 | 含义 |
| --- | --- |
| `-1` | 库存不足 |
| `-2` | 重复抢票 |
| `-3` | Redis 库存未初始化 |

### 读完要能回答

- Redis Lua 为什么比多次 Redis 调用更适合抢票？
- Redis 预扣减后为什么订单仍然要落库？
- Redis 库存释放时为什么还要移除用户抢票记录？

## 第 7 组：异步抢票和 MQ

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/AsyncGrabService.java` | 异步抢票提交、入队、消费、结果更新 |
| `src/main/java/com/eventrush/service/AsyncGrabRequestRepository.java` | 异步请求状态持久化 |
| `src/main/java/com/eventrush/service/RocketGrabConsumer.java` | RocketMQ 抢票消息消费者 |
| `src/main/java/com/eventrush/service/OrderTimeoutMessagePublisher.java` | 订单超时延时消息发送 |
| `src/main/java/com/eventrush/service/RocketOrderTimeoutConsumer.java` | 订单超时消息消费者 |
| `src/main/java/com/eventrush/service/OrderTimeoutScheduler.java` | 定时扫描兜底 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `AsyncGrabService.submitGrab` | 为什么先创建 `PENDING` 请求再入队 |
| `AsyncGrabService.enqueue` | 内存队列、Redis 队列、RocketMQ 三种路径怎么切换 |
| `AsyncGrabService.process` | 消费者如何保证重复消息不重复处理 |
| `OrderTimeoutMessagePublisher.publish` | 订单创建后如何发送延时消息 |
| `RocketOrderTimeoutConsumer.onMessage` | 消息到期后为什么只调用取消检查 |
| `OrderTimeoutScheduler.cancelExpiredOrders` | MQ 不可用时如何兜底 |

### 读完要能回答

- 异步抢票为什么要有请求状态表？
- MQ 重复消费时为什么不会重复创建订单？
- 延时消息和定时扫描为什么要同时存在？

## 第 8 组：接口治理

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/main/java/com/eventrush/api/ApiResponse.java` | 统一响应结构 |
| `src/main/java/com/eventrush/api/ApiResponseAdvice.java` | 成功响应包装 |
| `src/main/java/com/eventrush/api/ApiExceptionHandler.java` | 异常响应包装 |
| `src/main/java/com/eventrush/api/TraceFilter.java` | traceId 生成、响应头、日志 |
| `src/main/java/com/eventrush/api/AdminAuthFilter.java` | 管理端鉴权 |

### 必看方法

| 方法 | 你要看懂的问题 |
| --- | --- |
| `ApiResponse.success` | 成功响应统一成什么样 |
| `ApiResponse.error` | 错误响应统一成什么样 |
| `TraceFilter.doFilterInternal` | traceId 如何贯穿响应和日志 |
| `AdminAuthFilter.doFilterInternal` | 管理端接口如何拒绝无密钥请求 |

### 读完要能回答

- 为什么统一响应里也要放 `traceId`？
- 为什么后台接口即使只读也要鉴权？
- 统一响应和异常处理对前端联调有什么帮助？

## 第 9 组：自动化测试

### 先读文件

| 文件 | 重点看什么 |
| --- | --- |
| `src/test/java/com/eventrush/service/TicketingServiceTest.java` | 内存版业务链路和并发不超卖 |
| `src/test/java/com/eventrush/service/TicketingPersistenceTest.java` | 数据库持久化场景 |
| `src/test/java/com/eventrush/service/AsyncGrabServiceTest.java` | 异步抢票和消费幂等 |
| `src/test/java/com/eventrush/service/OrderTimeoutServiceTest.java` | 超时取消和已支付不取消 |
| `src/test/java/com/eventrush/api/ApiResponseTest.java` | 统一响应、traceId、管理端鉴权 |

### 必看测试

| 测试方法 | 你要看懂的问题 |
| --- | --- |
| `completesGrabPayAndVerifyFlow` | 主链路是否跑通 |
| `repeatedPaymentReturnsSameTicket` | 支付幂等如何验证 |
| `concurrentGrabDoesNotOversell` | 并发不超卖如何验证 |
| `processesDuplicateMessageOnlyOnce` | 消息重复消费如何验证 |
| `paidExpiredOrderIsNotCanceled` | 已支付订单为什么不能被超时取消 |

### 读完要能回答

- 项目哪些关键风险被测试覆盖了？
- 为什么文档变更后仍然要跑 `mvn test`？
- 如果以后改抢票逻辑，最应该先看哪些测试？

## 最小阅读路线

如果时间很紧，只读这 6 个文件：

1. `src/main/java/com/eventrush/api/TicketingController.java`
2. `src/main/java/com/eventrush/service/TicketingService.java`
3. `src/main/java/com/eventrush/service/TicketOrderRepository.java`
4. `src/main/java/com/eventrush/service/AsyncGrabService.java`
5. `src/main/resources/lua/grab-ticket.lua`
6. `src/test/java/com/eventrush/service/TicketingServiceTest.java`

读完这 6 个文件，你至少能讲清楚：接口入口、抢票主流程、支付幂等、超时取消、异步抢票、Redis Lua 和核心测试。

## 阅读验收问题

读完以后，用下面问题检查自己：

- `grabTicket` 里面为什么先判断重复抢票，再扣库存？
- Redis Lua 扣库存成功后，为什么还要创建数据库订单？
- `payOrder` 遇到已支付订单为什么返回已有电子票？
- `markCanceledIfPending` 为什么要在 SQL 里限制订单状态？
- `AsyncGrabService.process` 为什么先把请求从 `PENDING` 改成处理中？
- `TraceFilter` 为什么要把 traceId 同时放入响应头、日志和上下文？
- 哪个测试能证明并发抢票不会超卖？

## 你真正要读懂到什么程度

读懂不是背代码，而是能做到：

- 指着 `TicketingService.grabTicket` 讲清楚抢票链路。
- 指着 `TicketOrderRepository.markPaid` 讲清楚状态条件更新。
- 指着 `grab-ticket.lua` 讲清楚 Redis Lua 原子扣减。
- 指着 `AsyncGrabService.process` 讲清楚消费幂等。
- 指着测试讲清楚项目如何验证不超卖、幂等和超时取消。
