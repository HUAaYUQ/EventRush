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
| `X-Admin-Key` | 管理端必填 | 访问 `/api/admin/**` 时必须携带，默认值是 `eventrush-admin-key` |

### 常见错误

| HTTP 状态 | `code` | 场景 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 请求体字段缺失或格式不合法 |
| `400` | `BUSINESS_ERROR` | 库存不足、订单不存在、重复抢票、重复验票等业务错误 |
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
| `status` | 订单状态：`PENDING_PAYMENT`、`PAID`、`CANCELED` |
| `createdTime` | 创建时间 |
| `payTime` | 支付时间 |
| `cancelTime` | 取消时间 |
| `expireTime` | 支付截止时间 |

### 电子票 `ElectronicTicket`

| 字段 | 说明 |
| --- | --- |
| `id` | 电子票 ID |
| `orderId` | 关联订单 ID |
| `ticketCode` | 票码 |
| `status` | 票状态：`VALID`、`VERIFIED` |
| `generatedTime` | 出票时间 |
| `verifiedTime` | 核验时间 |
| `verifierId` | 核验人员 ID |

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

### 同步抢票

```http
POST /api/orders/grab
Content-Type: application/json

{
  "userId": 9800,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

成功返回 `TicketOrder`，初始状态为 `PENDING_PAYMENT`。

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

### 查询异步抢票结果

```http
GET /api/orders/grab-requests/{requestId}
```

返回异步抢票处理结果。前端可以轮询这个接口展示“处理中、成功、失败”。

### 支付订单

```http
POST /api/orders/{orderId}/pay
```

成功返回 `ElectronicTicket`。支付接口具备幂等性：同一个已支付订单再次支付，会返回同一张电子票，不会重复出票。

### 查询订单详情

```http
GET /api/orders/{orderId}
```

返回 `TicketOrder`。可用于确认订单状态是否为待支付、已支付或已取消。

### 查询电子票

```http
GET /api/tickets/{ticketCode}
```

返回 `ElectronicTicket`。用户可查看票码和票状态。

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

### 按用户查询订单

```http
GET /api/admin/users/{userId}/orders
X-Admin-Key: eventrush-admin-key
```

返回该用户的订单列表，适合客服排查“用户是否抢到票、订单是否已支付”。

### 按订单查询电子票

```http
GET /api/admin/orders/{orderId}/ticket
X-Admin-Key: eventrush-admin-key
```

返回订单对应的电子票，适合排查“支付后是否已出票”。

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
6. 调用验票接口，确认状态变为 `VERIFIED`。
7. 携带 `X-Admin-Key` 调用管理端接口，确认能按用户、订单、票码查询数据。

## 你需要学会的点

- API 文档不是把路径罗列出来，而是说明“接口解决什么问题、需要什么参数、返回什么数据”。
- 用户端接口负责业务动作，比如抢票、支付、验票。
- 管理端接口负责业务排查，比如按用户、订单、票码查询。
- 统一响应、`traceId`、鉴权请求头属于横切规则，应该集中写在文档开头。
