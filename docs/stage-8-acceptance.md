# 第 8 阶段验收：消息队列削峰入门

## 本阶段交付

- 新增异步抢票接口：请求先进入队列，接口立即返回 `requestId`。
- 新增后台消费者：从队列取出抢票请求，再复用原来的抢票逻辑处理库存、重复抢票和订单创建。
- 新增结果查询接口：通过 `requestId` 查询 `PENDING`、`SUCCESS`、`FAILED`。
- 默认使用内存队列，方便本地直接启动；打开 `eventrush.queue.redis-enabled=true` 后使用 Redis List 作为轻量队列。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- `AsyncGrabServiceTest` 会提交 11 个异步 VIP 抢票请求，VIP 库存只有 10 张，最终 10 个成功、1 个失败，不能超卖。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

提交异步抢票：

```http
POST http://localhost:18086/api/orders/grab-async
Content-Type: application/json

{
  "userId": 9200,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

返回示例：

```json
{
  "requestId": "一串请求号",
  "status": "PENDING",
  "orderId": null,
  "errorMessage": null
}
```

用 `requestId` 查询结果：

```http
GET http://localhost:18086/api/orders/grab-requests/你的requestId
```

成功后会看到：

```json
{
  "requestId": "一串请求号",
  "status": "SUCCESS",
  "orderId": 1,
  "errorMessage": null
}
```

## 使用 Redis 队列

确认 Redis 已启动后，启动应用时加上：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.redis-enabled=true"
```

这时异步抢票消息会写入 Redis 的 `eventrush:queue:grab` 队列，结果会临时保存在 `eventrush:grab-result:{requestId}`，默认保留 10 分钟。

## 你需要学会的点

- 同步抢票是“请求进来立刻抢库存”，高峰时所有压力直接打到核心链路。
- 异步削峰是“请求先进队列”，接口很快返回，后台消费者按自己的速度处理。
- `PENDING` 不代表成功，只代表请求已经被接收；最终结果要查 `SUCCESS` 或 `FAILED`。
- MQ 不是为了让业务一定成功，而是为了把瞬时洪峰变成后端可处理的稳定流量。
- 当前版本是入门版，没有做消费者组、死信队列和消息重试；这些是后续生产化要补的内容。

## 面试表达

可以这样说：

> 我把抢票链路拆成了请求接收和后台消费两段。接口层只负责把请求写入队列并返回 requestId，后台消费者再复用原有抢票逻辑完成扣库存和创建订单。这样可以把瞬时流量削平，同时通过结果查询接口让用户知道最终是否抢票成功。

## 下一阶段建议

第 9 阶段可以继续做“队列生产化”：增加 Redis Stream 消费者组、失败重试、死信队列，或者切换到 RabbitMQ。等这一步完成后，项目就更接近真实高并发抢票系统。
