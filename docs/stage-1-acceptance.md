# EventRush 第一阶段验收说明

## 本阶段交付了什么

第一阶段是业务 MVP。这里先用内存存储，不急着接 MySQL、Redis、MQ，目的是先把完整票务业务链路跑通。

当前流程：

1. 查询活动列表和活动详情。
2. 用户抢票，系统创建待支付订单。
3. 用户模拟支付，系统生成电子票。
4. 入场工作人员核验电子票。
5. 同一张票重复核验时，系统拒绝。

## 如何验证

运行自动测试：

```powershell
mvn test
```

期望结果：

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

启动应用：

```powershell
mvn "-Dspring-boot.run.arguments=--server.port=18080" spring-boot:run
```

访问地址：

```text
http://localhost:18080
```

说明：当前使用 `18080` 端口，是因为本机的 `8080` 和 `8081` 已经被其他服务占用。

## 手动接口验收流程

查询活动列表：

```http
GET http://localhost:18080/api/events
```

抢一张票：

```http
POST http://localhost:18080/api/orders/grab
Content-Type: application/json

{
  "userId": 1,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

支付订单：

```http
POST http://localhost:18080/api/orders/1/pay
```

核验电子票：

```http
POST http://localhost:18080/api/tickets/verify
Content-Type: application/json

{
  "ticketCode": "替换成支付接口返回的 ticketCode",
  "verifierId": 99
}
```

再用同一个 `ticketCode` 核验一次。期望结果：系统拒绝，并返回 `ticket has already been verified`。

## 你这一阶段需要真正学会什么

- 为什么抢票成功后先生成 `PENDING_PAYMENT` 待支付订单，而不是直接出票。
- 为什么电子票应该在支付成功后生成，而不是抢票时就生成。
- 为什么核验接口必须防重复：同一张票不能进场两次。
- 为什么库存扣减和订单创建属于同一条业务链路。
- 为什么这一阶段先用内存存储：先看清业务状态流转，再引入数据库、Redis 和 MQ。

## 面试表达

我先完成了 EventRush 的完整业务闭环：活动查询、抢票、待支付订单创建、模拟支付、电子票生成和入场核验。这个阶段重点不是高并发优化，而是把领域状态流转讲清楚：抢票时库存减少，订单进入待支付状态；支付成功后生成有效电子票；入场核验后电子票变为已核验，并且系统会阻止重复核验。

## 下一阶段

第二阶段会把当前简单的库存扣减升级为 Redis 预扣库存 + Lua 原子扣减。到这里，项目会开始承载高并发、防超卖、幂等等核心面试问题。
