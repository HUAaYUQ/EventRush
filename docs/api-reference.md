# EventRush API 文档

本文档汇总当前项目已经完成的核心接口。默认本地服务地址：

```text
http://localhost:18086
```

## 通用规则

### 统一响应格式

所有接口都会返回统一 JSON 结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "trace-demo-001"
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `success` | 请求是否成功 |
| `code` | 业务响应码，成功为 `OK` |
| `message` | 响应说明 |
| `data` | 真实业务数据，失败时通常为 `null` |
| `traceId` | 本次请求链路编号，用于排查日志 |

### 通用请求头

| 请求头 | 是否必填 | 说明 |
| --- | --- | --- |
| `X-Trace-Id` | 否 | 客户端自定义链路编号；不传时后端自动生成 |
| `X-Admin-Key` | 管理端必填 | 访问 `/api/admin/**` 时必须携带；默认值是 `eventrush-admin-key`，可通过环境变量 `EVENTRUSH_ADMIN_KEY` 覆盖 |

### 常见错误

| HTTP 状态 | `code` | 场景 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 请求体字段缺失或格式不合法 |
| `404` | `EVENT_NOT_FOUND`、`ORDER_NOT_FOUND`、`TICKET_NOT_FOUND` | 对应资源不存在，或订单不属于当前用户 |
| `409` | `DUPLICATE_GRAB` | 用户已有同一场次、同一票档的未取消订单 |
| `409` | `TICKET_SOLD_OUT` | 当前票档库存不足 |
| `409` | `ORDER_EXPIRED`、`ORDER_NOT_PAYABLE` | 订单已超时或当前状态不能支付 |
| `409` | `TICKET_ALREADY_VERIFIED` | 电子票已经核验，不能重复入场 |
| `409` | `TICKET_NOT_REFUNDABLE`、`REFUND_WINDOW_CLOSED` | 电子票已验票，或场次已开始，不能在线退票 |
| `429` | `GRAB_RATE_LIMITED` | 抢票请求过于频繁 |
| `503` | `STOCK_SERVICE_UNAVAILABLE` | Redis 库存服务尚未就绪 |
| `401` | `UNAUTHORIZED` | 管理端接口未携带或携带错误 `X-Admin-Key` |

## 核心对象

### 活动 `Event`

| 字段 | 说明 |
| --- | --- |
| `id` | 活动 ID |
| `name` | 活动名称 |
| `location` | 举办地点 |
| `status` | 活动状态 |
| `sessions` | 场次列表 |

### 场次 `EventSession`

| 字段 | 说明 |
| --- | --- |
| `id` | 场次 ID |
| `eventId` | 所属活动 ID |
| `startTime` | 开始时间 |
| `endTime` | 结束时间 |
| `ticketCategories` | 票档列表 |

### 票档 `TicketCategory`

| 字段 | 说明 |
| --- | --- |
| `id` | 票档 ID |
| `sessionId` | 所属场次 ID |
| `name` | 票档名称 |
| `priceCents` | 单价，单位为分 |
| `totalStock` | 总库存 |
| `remainingStock` | 剩余库存 |

### 订单 `TicketOrder`

| 字段 | 说明 |
| --- | --- |
| `id` | 订单 ID |
| `userId` | 用户 ID |
| `eventId` | 活动 ID |
| `sessionId` | 场次 ID |
| `ticketCategoryId` | 票档 ID |
| `unitPriceCents` | 下单时的票档单价快照，单位为分 |
| `amountCents` | 订单应付金额，等于单价乘以购票人数，单位为分 |
| `quantity` | 购票数量，由 `passengers` 数量推导，范围为 1 到 5 |
| `refundedQuantity` | 已成功退票数量；重复请求不会重复累计 |
| `refundedAmountCents` | 累计退款金额，单位为分 |
| `passengers` | 购票人快照列表，每项包含 `id`、顺序、姓名、证件类型和证件后四位 |
| `status` | 订单状态：`PENDING_PAYMENT`、`PAID`、`PARTIALLY_REFUNDED`、`REFUNDED`、`CANCELED` |
| `createdTime` | 创建时间 |
| `payTime` | 支付时间 |
| `cancelTime` | 取消时间 |
| `refundTime` | 最近一次成功退票时间 |
| `expireTime` | 支付截止时间 |

### 电子票 `ElectronicTicket`

| 字段 | 说明 |
| --- | --- |
| `id` | 电子票 ID |
| `orderId` | 关联订单 ID |
| `passengerId` | 关联购票人 ID；一位购票人对应一张电子票 |
| `passengerName` | 购票人姓名快照 |
| `passengerDocumentType` | 证件类型：`ID_CARD`、`PASSPORT`、`OTHER` |
| `passengerDocumentLast4` | 证件号码后四位；接口不接收完整证件号 |
| `ticketCode` | 票码 |
| `status` | 票状态：`VALID`、`VERIFIED`、`REFUNDED` |
| `generatedTime` | 出票时间 |
| `verifiedTime` | 核验时间 |
| `verifierId` | 核验人员 ID |
| `refundedTime` | 该电子票成功退票的时间 |

## 普通用户接口

### 查询活动列表

```http
GET /api/events
```

返回 `Event[]`，用于展示可抢票活动、场次和票档库存。

### 查询活动详情

```http
GET /api/events/{eventId}
```

返回单个 `Event`。如果开启 Redis 缓存，活动详情会先查缓存，未命中再查数据库并写入缓存。

### 提交同步购票订单

```http
POST /api/orders/grab
Content-Type: application/json

{
  "userId": 9800,
  "sessionId": 101,
  "ticketCategoryId": 1001,
  "passengers": [
    {
      "name": "张三",
      "documentType": "ID_CARD",
      "documentLast4": "1234"
    },
    {
      "name": "李四",
      "documentType": "PASSPORT",
      "documentLast4": "8X2P"
    }
  ]
}
```

成功返回 `TicketOrder`，初始状态为 `PENDING_PAYMENT`。`quantity` 由 `passengers` 长度推导，服务端不接受独立数量字段。价格、数量和购票人脱敏信息都会成为订单快照。

每笔订单支持 1 到 5 位购票人。库存按人数扣减，订单金额等于票档单价乘以人数。

如果票档已经存在等待中的候补，普通购票返回 `WAITLIST_QUEUE_ACTIVE`，避免后来请求绕过队首直接占用零散库存。

### 提交候补

```http
POST /api/users/{userId}/waitlists
Content-Type: application/json

{
  "sessionId": 101,
  "ticketCategoryId": 1002,
  "passengers": [
    {
      "name": "张三",
      "documentType": "ID_CARD",
      "documentLast4": "1234"
    }
  ]
}
```

只有当前库存不足以覆盖全部购票人，或该票档已经存在等待队列时才能提交。候补人数为 1 到 5 人，同一用户、场次和票档只能有一个 `WAITING` 候补。

成功返回 `TicketWaitlistRequest`。初始状态为 `WAITING`，`waitingAhead` 表示前方等待笔数。候补按创建时间和 id 先来先得，只在库存能覆盖整组购票人时兑现，不拆单，也不跳过队首满足后来的小单。

### 查询用户候补

```http
GET /api/users/{userId}/waitlists
GET /api/users/{userId}/waitlists/{waitlistId}
```

状态包括：

- `WAITING`：等待库存释放。
- `FULFILLED`：已兑现为 `PENDING_PAYMENT` 订单，响应包含 `orderId` 和 `paymentExpireTime`。
- `CANCELED`：用户已主动取消。
- `EXPIRED`：场次开始后仍未兑现，或候补资格已失效。

查询和取消都校验用户归属，不匹配时统一返回 `WAITLIST_NOT_FOUND`。

### 取消候补

```http
DELETE /api/users/{userId}/waitlists/{waitlistId}
```

只有 `WAITING` 候补可以取消。退款和订单超时取消释放库存后，系统在原事务提交后触发候补兑现；候补异常不会回滚已经完成的退款或取消。

### 异步抢票

```http
POST /api/orders/grab-async
Content-Type: application/json

{
  "userId": 9801,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

成功返回抢票请求结果，通常包含 `requestId` 和当前处理状态。后续由 RocketMQ 消费者异步处理。

异步接口主要用于高并发和 MQ 演示，当前会为压测用户生成明确标记的测试购票人快照；面向用户的正式下单流程使用上面的同步购票接口。

### 查询异步抢票结果

```http
GET /api/orders/grab-requests/{requestId}
```

返回异步抢票处理结果。前端可以轮询这个接口展示“处理中、成功、失败”。

### 支付订单

```http
POST /api/orders/{orderId}/pay
```

成功返回 `ElectronicTicket[]`，每位购票人对应一张独立票。支付接口具备幂等性：同一个已支付订单再次支付，会返回原有票列表，不会重复出票。

### 查询订单详情

```http
GET /api/orders/{orderId}
```

返回 `TicketOrder`。可用于确认订单状态是否为待支付、已支付或已取消。

### 查询用户订单列表

```http
GET /api/users/{userId}/orders
```

返回用户全部订单，按最新订单优先排列。前端“我的电子票”以这个接口作为恢复入口。

### 查询用户订单详情

```http
GET /api/users/{userId}/orders/{orderId}
```

只有订单所属用户可以取得订单；不匹配时统一返回 `ORDER_NOT_FOUND`。

### 支付用户订单

```http
POST /api/users/{userId}/orders/{orderId}/pay
```

支付前校验订单归属和支付截止时间。订单过期时返回 `ORDER_EXPIRED`，同时取消订单并释放库存和再次购买资格。

### 按用户订单取得电子票列表

```http
GET /api/users/{userId}/orders/{orderId}/tickets
```

返回 `ElectronicTicket[]`，用于在刷新页面或重新进入订单中心后找回该订单的全部电子票。

> 当前 `userId` 归属校验用于本地产品演示，还不等同于登录认证。正式身份系统仍是后续阶段。

### 按电子票退票

```http
POST /api/users/{userId}/orders/{orderId}/refunds
Content-Type: application/json

{
  "ticketCodes": ["ER-xxxx"]
}
```

一次可选择 1 到 5 张当前用户、当前订单下的 `VALID` 电子票。当前阶段不收手续费，实际退款金额等于订单单价乘以本次新退票数量。

部分退票后订单变为 `PARTIALLY_REFUNDED`，未退电子票仍可入场，用户暂时不能重复购买同一票档。全部电子票退完后订单变为 `REFUNDED`，购买资格恢复。已核验票和场次开始后的票不能在线退票。

成功返回订单、该订单全部电子票、`newlyRefundedQuantity` 和 `newlyRefundedAmountCents`。重复提交已退票码时两个本次新增字段都为 `0`，不会重复退款或释放库存。

### 查询电子票

```http
GET /api/tickets/{ticketCode}
```

返回 `ElectronicTicket`。用户可查看票码和票状态。

产品前端使用带归属校验的接口：

```http
GET /api/users/{userId}/tickets/{ticketCode}
```

票码关联订单不属于该用户时返回 `ORDER_NOT_FOUND`。

### 核验电子票

```http
POST /api/tickets/verify
Content-Type: application/json

{
  "ticketCode": "ER-xxxx",
  "verifierId": 7001
}
```

成功返回已核验的 `ElectronicTicket`，状态变为 `VERIFIED`。同一张票不能重复核验。

## 管理端接口

管理端接口路径统一以 `/api/admin` 开头，必须携带：

```http
X-Admin-Key: eventrush-admin-key
```

本地默认密钥是 `eventrush-admin-key`。部署或联调时可以通过环境变量 `EVENTRUSH_ADMIN_KEY` 改成自己的密钥。

### 按用户查询订单

```http
GET /api/admin/users/{userId}/orders
X-Admin-Key: eventrush-admin-key
```

返回该用户的订单列表，适合客服排查“用户是否抢到票、订单是否已支付”。

### 按订单查询电子票列表

```http
GET /api/admin/orders/{orderId}/tickets
X-Admin-Key: eventrush-admin-key
```

返回订单对应的全部电子票，适合排查“是否按购票人数完整出票”。

### 按票码查询电子票

```http
GET /api/admin/tickets/{ticketCode}
X-Admin-Key: eventrush-admin-key
```

返回票码对应的电子票，适合排查“票是否有效、是否已核验”。

## 推荐验收流程

1. 查询活动列表，拿到 `sessionId` 和 `ticketCategoryId`。
2. 调用同步抢票接口，拿到 `orderId`。
3. 调用支付接口，拿到 `ticketCode`。
4. 查询订单详情，确认状态为 `PAID`。
5. 查询电子票，确认状态为 `VALID`。
6. 选择其中一张票调用退票接口，确认订单变为 `PARTIALLY_REFUNDED`、该票变为 `REFUNDED`，另一张仍为 `VALID`。
7. 重复提交同一票码，确认新增退款数量和金额都为 `0`。
8. 对剩余 `VALID` 票可继续验票，或全部退完后确认订单为 `REFUNDED` 并可重新购买。
9. 把票档抢至售罄，依次提交两笔候补，确认 `waitingAhead` 按顺序变化。
10. 退票或等待订单超时释放库存，确认队首只有在库存覆盖整组人数时才变为 `FULFILLED`，并拿到待支付 `orderId`。
11. 携带 `X-Admin-Key` 调用管理端接口，确认能按用户、订单、票码查询数据。

## 你需要学会的点

- API 文档不是把路径罗列出来，而是说明“接口解决什么问题、需要什么参数、返回什么数据”。
- 用户端接口负责业务动作，比如抢票、支付、验票。
- 管理端接口负责业务排查，比如按用户、订单、票码查询。
- 统一响应、`traceId`、鉴权请求头属于横切规则，应该集中写在文档开头。
