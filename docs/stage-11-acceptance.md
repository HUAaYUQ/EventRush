# 第 11 阶段验收：订单超时取消消息化

## 本阶段交付

- 订单创建成功后，会尝试发送一条 RocketMQ 延时消息，消息内容是 `orderId`。
- 新增订单超时消费者，收到延时消息后按 `orderId` 检查订单是否已经过期。
- 只有仍然是 `PENDING_PAYMENT` 且已经过期的订单才会被取消。
- 如果订单已经支付为 `PAID`，延时消息不会误取消订单。
- 原来的定时扫描保留，作为 RocketMQ 暂时不可用时的兜底。

## 为什么要做这一阶段

第 5 阶段的做法是定时扫描数据库：系统每隔一段时间查一次过期订单。

这种方式能用，但有两个问题：

- 数据库里订单很多时，扫描压力会变大。
- 取消时间不够精确，取决于扫描间隔。

现在改成消息化思路：订单创建时就安排一条“未来要检查这个订单”的消息。时间到了以后消费者只处理这一笔订单，数据库压力更小，链路也更像真实生产系统。

## 当前实现方式

```text
抢票成功 -> 创建待支付订单 -> 发送 RocketMQ 延时消息
                                  |
                                  v
                         延时消息到期后消费
                                  |
                                  v
             检查订单仍是 PENDING_PAYMENT 且已过期 -> 取消订单并释放库存
```

注意：RocketMQ 的延时消息是固定延时等级，不是任意秒数定时器。本项目通过配置 `eventrush.order.timeout-delay-level` 控制延时等级。

默认值：

- `eventrush.order.timeout-topic=eventrush-order-timeout-topic`
- `eventrush.order.timeout-consumer-group=eventrush-order-timeout-consumer`
- `eventrush.order.timeout-delay-level=16`

RocketMQ 常见延时等级里，`16` 对应约 30 分钟。学习和演示时可以把它调小，比如 `2` 通常约 5 秒。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 原有定时扫描取消测试通过。
- 新增“按单个 orderId 取消过期订单”的测试通过。
- 已支付订单不会被超时取消。

## 手动验收：不启用 RocketMQ

如果只是验证基础功能，可以先不启用 RocketMQ：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.order.expire-seconds=2 --eventrush.order.timeout-scan-ms=1000"
```

抢票后等 3 到 5 秒查询订单，预期订单状态变为 `CANCELED`。

## 手动验收：启用 RocketMQ 延时消息

连接你 VMware 里的 RocketMQ：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086 --eventrush.queue.rocket-enabled=true --rocketmq.name-server=192.168.233.128:9876 --eventrush.order.expire-seconds=2 --eventrush.order.timeout-delay-level=2 --eventrush.order.timeout-scan-ms=600000"
```

这里故意把扫描间隔调到很大，是为了更明显地观察“延时消息在取消订单”。

抢票：

```http
POST http://localhost:18086/api/orders/grab
Content-Type: application/json

{
  "userId": 9600,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

查询订单：

```http
GET http://localhost:18086/api/orders/把这里换成返回的订单id
```

预期：

- 刚抢票成功时订单是 `PENDING_PAYMENT`。
- 等几秒后再次查询，订单变成 `CANCELED`。
- 如果在超时前调用支付接口，订单变成 `PAID`，之后不会被延时消息取消。

## 你需要学会的点

- 定时扫描是“批量找过期订单”，延时消息是“给每个订单安排一次未来检查”。
- RocketMQ 延时消息到期后也不能直接取消，必须先查订单当前状态。
- 防误取消的关键条件是：只取消 `PENDING_PAYMENT`，不取消 `PAID`。
- 延时消息可能丢、可能晚、可能重复，所以保留定时扫描作为兜底是更稳的做法。
- 这一阶段复用了原来的取消逻辑，没有另写一套取消流程，这样更不容易出现两套规则不一致。

## 面试表达

可以这样说：

> 我把订单超时取消从单纯定时扫描改成了 RocketMQ 延时消息。订单创建成功后发送一条包含 orderId 的延时消息，消息到期后消费者查询订单当前状态，只对已经过期且仍处于待支付状态的订单执行条件取消，并释放库存。为了避免消息异常导致订单永远不取消，原有定时扫描保留为兜底。

## 下一阶段建议

第 12 阶段建议做“支付链路幂等和电子票防重复生成”：同一个订单重复支付请求只能成功一次，电子票也只能生成一张。
