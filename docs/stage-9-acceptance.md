# 第 9 阶段验收：RabbitMQ 正式削峰

## 本阶段交付

- 接入 RabbitMQ：打开 `eventrush.queue.rabbit-enabled=true` 后，异步抢票请求会发送到 RabbitMQ。
- 新增 Direct Exchange、业务队列、路由键、死信交换机和死信队列配置。
- 保留原来的异步接口：`POST /api/orders/grab-async` 和 `GET /api/orders/grab-requests/{requestId}` 不变。
- 默认仍关闭 RabbitMQ，方便没有启动中间件时本地测试照常通过。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 默认不开 RabbitMQ，自动测试仍使用内存队列验证异步抢票不会超卖。

## RabbitMQ 环境

本阶段需要 RabbitMQ 服务监听在本机默认端口：

- AMQP 端口：`5672`
- 管理后台端口：`15672`
- 默认用户名/密码通常是：`guest / guest`

你可以用本机安装版，也可以用 Docker。能打开管理后台就说明服务基本可用：

```text
http://localhost:15672
```

## RabbitMQ 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.rabbit-enabled=true"
```

提交异步抢票：

```http
POST http://localhost:18086/api/orders/grab-async
Content-Type: application/json

{
  "userId": 9400,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

返回 `PENDING` 后，等待 1 秒，再查询：

```http
GET http://localhost:18086/api/orders/grab-requests/你的requestId
```

预期看到 `SUCCESS`，并且有 `orderId`。

## 管理后台观察点

登录 RabbitMQ 管理后台后，重点看：

- `Exchanges`：应出现 `eventrush.grab.exchange` 和 `eventrush.grab.dlx`。
- `Queues`：应出现 `eventrush.grab.queue` 和 `eventrush.grab.dlq`。
- 提交请求时，业务队列会短暂出现消息；消费者处理后消息被确认并消失。

## 你需要学会的点

- Exchange 负责接收消息，Queue 负责存放消息，Routing Key 决定消息进入哪个队列。
- RabbitMQ 削峰的关键是：接口快速投递消息，消费者按自己的速度处理。
- 消费成功后消息会被确认；如果消费者处理出现未捕获异常，消息可以进入死信队列，方便排查。
- 当前配置关闭了异常消息的默认重新入队，避免坏消息在业务队列里反复消费。
- 当前结果查询仍存在应用内存里，适合学习链路；生产环境要把 requestId 结果写入 Redis 或数据库。

## 面试表达

可以这样说：

> 我在抢票链路中引入 RabbitMQ 做削峰。接口层收到请求后写入 Direct Exchange，通过 routing key 路由到抢票队列；消费者异步处理扣库存和创建订单。异常消息通过死信交换机进入死信队列，便于后续排查和补偿。

## 下一阶段建议

第 10 阶段可以做 RabbitMQ 的“生产化细节”：消费者幂等、失败重试次数、死信消息查看接口，以及把异步请求结果从内存迁移到 Redis 或数据库。
