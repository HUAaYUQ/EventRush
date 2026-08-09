# 第 10 阶段验收：异步抢票结果落库和消费幂等

## 本阶段交付

- 新增 `async_grab_request` 表，用来保存每一次异步抢票请求。
- `POST /api/orders/grab-async` 提交后，会先生成一条 `PENDING` 请求记录，再把消息投递到队列。
- `GET /api/orders/grab-requests/{requestId}` 不再依赖应用内存，而是从数据库查询结果。
- 消费者处理消息前，会用 `requestId` 把请求状态从 `PENDING` 改成内部状态 `PROCESSING`。
- 如果 RocketMQ 重复投递同一条消息，第二次不会再次扣库存、不会再次创建订单。

## 为什么要做这一阶段

消息队列的常见语义不是“绝对只消费一次”，而是更接近“至少投递一次”。这意味着同一条抢票消息有可能被消费者看到两次。

如果没有幂等控制，重复消息可能导致：

- 同一个用户重复下单。
- 库存被重复扣减。
- 查询结果因为应用重启而丢失。

现在我们用 `requestId` 做唯一标识，把请求状态持久化到数据库，并通过状态流转控制重复消费。

## 状态流转

```text
PENDING -> PROCESSING -> SUCCESS
PENDING -> PROCESSING -> FAILED
```

对用户接口来说，`PROCESSING` 仍然显示为 `PENDING`，这样前端只需要理解三种结果：

- `PENDING`：还在排队或处理中。
- `SUCCESS`：抢票成功，返回 `orderId`。
- `FAILED`：抢票失败，返回失败原因。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 原有异步抢票不超卖测试继续通过。
- 新增“重复消息不会重复处理”测试通过。

新增测试会模拟同一个 `requestId` 的消息被消费两次，并确认第二次不会生成新的订单。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

如果要连接你 VMware 里的 RocketMQ：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.rocket-enabled=true --rocketmq.name-server=192.168.233.128:9876"
```

提交异步抢票：

```http
POST http://localhost:18086/api/orders/grab-async
Content-Type: application/json

{
  "userId": 9500,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

查询结果：

```http
GET http://localhost:18086/api/orders/grab-requests/把这里换成返回的requestId
```

预期：

- 刚提交时返回 `PENDING`。
- 等消费者处理后返回 `SUCCESS` 和 `orderId`。
- 如果库存不足，会返回 `FAILED` 和失败原因。

## 你需要学会的点

- MQ 消费者必须考虑重复消息，不能假设消息只来一次。
- 幂等的核心是“同一个业务唯一键，多次执行只有一次生效”。
- 本项目里这个业务唯一键就是 `requestId`。
- 查询异步结果最好落到稳定存储里，不能只放在应用内存，否则应用重启后用户就查不到结果。
- `PENDING` 是对外状态，`PROCESSING` 是内部状态；内部状态可以帮助我们控制并发和重复消费。

## 面试表达

可以这样说：

> 我在异步抢票链路里为每个请求生成 requestId，并把请求状态持久化到数据库。消费者消费消息时，先通过条件更新把状态从 PENDING 改成 PROCESSING，只有更新成功的消费者才能继续执行业务逻辑。这样即使 RocketMQ 重复投递同一条消息，也不会重复扣库存或重复创建订单。最终结果会落库，前端通过 requestId 轮询查询。

## 下一阶段建议

第 11 阶段建议做“订单超时取消的消息化”：用 RocketMQ 延时消息替代当前定时扫描，让订单创建后自动发送一条延时取消消息，到了超时时间再检查订单是否仍未支付。
