# EventRush 第五阶段验收说明

## 本阶段交付了什么

第五阶段实现未支付订单超时取消和库存释放。

当前超时取消链路：

1. 用户抢票，系统创建 `PENDING_PAYMENT` 订单。
2. 订单带有 `expire_time`。
3. 定时任务扫描已经过期的待支付订单。
4. 系统只取消状态仍然是 `PENDING_PAYMENT` 的订单。
5. 取消成功后，订单状态变成 `CANCELED`，并记录 `cancel_time`。
6. 系统释放库存。
7. 如果订单已经支付为 `PAID`，不会被超时任务取消。

说明：当前阶段先用 Spring 定时扫描实现超时取消。后续接入 MQ 延迟消息时，可以复用这套“先检查当前订单状态，再条件取消”的核心逻辑。

## 新增和修改

- `OrderTimeoutScheduler`：定时扫描过期待支付订单。
- `TicketOrderRepository.findExpiredPending`：查询过期的待支付订单。
- `TicketOrderRepository.markCanceledIfPending`：只取消待支付订单。
- `TicketingService.cancelExpiredOrders`：取消订单并释放库存。
- `EventCatalogService.releaseStock`：释放活动票种库存。
- `RedisTicketStockService.release`：Redis 模式下释放 Redis 库存和已抢用户标记。

## 如何验证

运行自动测试：

```powershell
mvn test
```

期望结果：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

启动应用，临时把订单超时时间设置成 2 秒：

```powershell
mvn "-Dspring-boot.run.arguments=--server.port=18085 --eventrush.stock.redis-enabled=false --eventrush.order.expire-seconds=2 --eventrush.order.timeout-scan-ms=1000" spring-boot:run
```

访问地址：

```text
http://localhost:18085
```

## 手动接口验收流程

抢一张票：

```http
POST http://localhost:18085/api/orders/grab
Content-Type: application/json

{
  "userId": 700,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

立刻查询订单：

```http
GET http://localhost:18085/api/orders/替换成抢票接口返回的 id
```

期望结果：订单状态是 `PENDING_PAYMENT`。

等待 3 到 5 秒后再次查询订单：

```http
GET http://localhost:18085/api/orders/替换成抢票接口返回的 id
```

期望结果：订单状态变成 `CANCELED`，并且有 `cancelTime`。

## 你这一阶段需要真正学会什么

- 为什么订单不能永远停留在 `PENDING_PAYMENT`。
- 为什么取消订单前必须检查当前状态：只取消待支付订单，不能取消已支付订单。
- 为什么取消成功后要释放库存：否则库存会被未支付订单长期占住。
- 为什么库存释放和订单取消要放在同一条业务逻辑里。
- MQ 延迟消息不是核心本身，核心是消费消息时的状态检查和幂等取消。

## 面试表达

订单创建后会带过期时间。超时处理不是直接把订单改成取消，而是先查询当前订单状态，只对仍然处于待支付的订单执行条件更新，把状态改成 `CANCELED` 并记录取消时间。取消成功后释放对应票种库存。如果订单已经支付，条件更新不会生效，因此不会出现已支付订单被超时任务误取消的问题。

## 下一阶段

下一阶段可以把当前定时扫描替换或扩展为 MQ 延迟消息，同时增加消息消费幂等记录。也可以先做缓存和限流，为后面的压测做准备。
