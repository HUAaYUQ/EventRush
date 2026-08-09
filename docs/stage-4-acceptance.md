# EventRush 第四阶段验收说明

## 本阶段交付了什么

第四阶段把电子票从内存数据升级为数据库持久化。

当前电子票链路：

1. 用户抢票，生成待支付订单。
2. 用户支付订单。
3. 系统把订单状态更新为 `PAID`。
4. 系统向 `electronic_ticket` 表插入一张 `VALID` 电子票。
5. 入场核验时，系统把电子票状态从 `VALID` 更新为 `VERIFIED`。
6. 同一张票重复核验时，系统拒绝。

## 新增和修改

- `electronic_ticket` 表：保存电子票、票码、状态、生成时间、核验时间、核验人员。
- `ElectronicTicketRepository`：负责电子票创建、查询和核验状态更新。
- 支付接口：支付成功后电子票落库。
- 核验接口：核验状态落库，而不是只改内存对象。

## 如何验证

运行自动测试：

```powershell
mvn test
```

期望结果：

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

启动应用：

```powershell
mvn "-Dspring-boot.run.arguments=--server.port=18084 --eventrush.stock.redis-enabled=false" spring-boot:run
```

访问地址：

```text
http://localhost:18084
```

## 手动接口验收流程

抢一张票：

```http
POST http://localhost:18084/api/orders/grab
Content-Type: application/json

{
  "userId": 500,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

支付订单：

```http
POST http://localhost:18084/api/orders/替换成抢票接口返回的 id/pay
```

查询电子票：

```http
GET http://localhost:18084/api/tickets/替换成支付接口返回的 ticketCode
```

期望结果：电子票状态是 `VALID`。

核验电子票：

```http
POST http://localhost:18084/api/tickets/verify
Content-Type: application/json

{
  "ticketCode": "替换成支付接口返回的 ticketCode",
  "verifierId": 99
}
```

再次查询电子票。期望结果：电子票状态变成 `VERIFIED`，并记录 `verifierId`。

再用同一个 `ticketCode` 核验一次。期望结果：系统返回 `400`，拒绝重复核验。

## 你这一阶段需要真正学会什么

- 为什么电子票也必须落库：它是入场凭证，不能只存在内存里。
- 为什么支付成功后才生成电子票：未支付订单不能拥有有效入场凭证。
- 为什么核验要更新状态：入场动作本身也是业务事实。
- 为什么核验更新要带条件：只有 `VALID` 电子票才能变成 `VERIFIED`，重复核验会失败。
- 订单和电子票的关系：一个已支付订单对应一张电子票，电子票通过 `order_id` 关联订单。

## 面试表达

支付成功后，我会生成一张电子票并写入数据库。电子票表保存订单 ID、票码、票状态、生成时间、核验时间和核验人员。入场核验不是简单返回成功，而是把电子票状态从 `VALID` 条件更新为 `VERIFIED`，如果同一张票再次核验，更新条件不成立，系统会拒绝重复入场。

## 下一阶段

下一阶段开始做订单可靠性：未支付订单超时取消、库存释放，以及支付和取消之间的状态冲突控制。
