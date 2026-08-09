# 第 9 阶段验收：RocketMQ 正式削峰

## 本阶段交付

- 接入 RocketMQ：打开 `eventrush.queue.rocket-enabled=true` 后，异步抢票请求会发送到 RocketMQ Topic。
- 新增 RocketMQ 生产者和消费者：接口层发送抢票消息，消费者异步处理扣库存和创建订单。
- 保留原来的异步接口：`POST /api/orders/grab-async` 和 `GET /api/orders/grab-requests/{requestId}` 不变。
- 默认仍关闭 RocketMQ，方便没有启动中间件时本地测试照常通过。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 默认不开 RocketMQ，自动测试仍使用内存队列验证异步抢票不会超卖。

## RocketMQ 环境

本阶段需要 RocketMQ 单机环境：

- NameServer：`127.0.0.1:9876`
- Broker：连接 NameServer，并对应用暴露 Broker 端口。
- Topic：应用发送到 `eventrush-grab-topic`，如果 Broker 开启自动创建 Topic，可以由首次发送自动创建。

Docker 单机部署建议先参考 `docs/rocketmq-docker.md`。

## RocketMQ 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.rocket-enabled=true"
```

如果 RocketMQ 跑在 VMware 虚拟机里，需要把 NameServer 地址换成虚拟机 IP，例如：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.rocket-enabled=true --rocketmq.name-server=192.168.233.128:9876"
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

返回 `PENDING` 后，等待几秒，再查询：

```http
GET http://localhost:18086/api/orders/grab-requests/你的requestId
```

预期看到 `SUCCESS`，并且有 `orderId`。如果第一次查询仍是 `PENDING`，说明消息还在异步消费中，过几秒再查。

## 当前真实联调记录

- RocketMQ NameServer：`192.168.233.128:9876`
- Broker：`192.168.233.128:10911`
- Windows 到 `9876` 和 `10911` 端口连通。
- EventRush 启动后，Producer 和 Consumer 都连接到 RocketMQ。
- 异步抢票请求先返回 `PENDING`，稍后查询变为 `SUCCESS`，并生成订单 `orderId=1`。

## 管理后台观察点

如果后续安装 RocketMQ Dashboard，重点看：

- Topic：应出现 `eventrush-grab-topic`。
- Consumer Group：应出现 `eventrush-grab-consumer`。
- Producer Group：应出现 `eventrush-grab-producer`。
- 提交请求时，Topic 中会产生抢票消息，消费者处理后结果变为 `SUCCESS` 或 `FAILED`。

## 你需要学会的点

- NameServer 负责服务发现，Broker 负责真正存储和投递消息，Topic 是消息分类。
- RocketMQ 削峰的关键是：接口快速投递消息，消费者按自己的速度处理。
- 当前阶段使用普通消息做抢票削峰；后续订单超时取消更适合用 RocketMQ 延时消息。
- 当前结果查询仍存在应用内存里，适合学习链路；生产环境要把 requestId 结果写入 Redis 或数据库。

## 面试表达

可以这样说：

> 我在抢票链路中引入 RocketMQ 做削峰。接口层收到请求后把抢票消息发送到 Topic，消费者组异步消费消息，再复用原有抢票逻辑完成扣库存和创建订单。这样可以把瞬时请求洪峰转成后端可控的消息消费流。

## 下一阶段建议

第 10 阶段可以做 RocketMQ 的“生产化细节”：消费者幂等、失败重试、消息消费日志，以及用延时消息替代当前的定时扫描取消超时订单。
