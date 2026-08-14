# EventRush 完整业务链路实操验收

这份文档用于第 29 阶段：在本地启动项目后，手动跑通一次完整业务链路。

默认验收环境：

| 项目 | 值 |
| --- | --- |
| 服务地址 | `http://localhost:18086` |
| 数据库 | H2 文件数据库 |
| Redis | 默认关闭 |
| RocketMQ | 默认关闭 |
| 管理端密钥 | `eventrush-admin-key` |

## 1. 启动项目

在项目根目录执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

看到类似下面日志，说明启动成功：

```text
Started EventRushApplication
```

## 2. 查询活动列表

浏览器访问：

```text
http://localhost:18086/api/events
```

预期结果：

- `success` 为 `true`。
- `code` 为 `OK`。
- `data` 里能看到活动、场次和票档。
- 示例票档：
  - `sessionId=101`
  - `ticketCategoryId=1001`
  - `ticketCategoryId=1002`

你在浏览器里看到 JSON 返回，就说明服务已经正常启动。

## 3. 抢票

建议每次换一个新的 `userId`，避免重复抢票失败。

```powershell
$base = "http://localhost:18086"
$userId = 9912
$grabBody = @{
  userId = $userId
  sessionId = 101
  ticketCategoryId = 1001
} | ConvertTo-Json

$grab = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/orders/grab" `
  -ContentType "application/json" `
  -Headers @{ "X-Trace-Id" = "trace-manual-grab" } `
  -Body $grabBody

$grab.data
```

记录返回里的：

```text
orderId = data.id
status = PENDING_PAYMENT
```

## 4. 支付订单

把上一步返回的订单 ID 保存下来：

```powershell
$orderId = $grab.data.id

$pay = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/orders/$orderId/pay" `
  -Headers @{ "X-Trace-Id" = "trace-manual-pay" }

$pay.data
```

记录返回里的：

```text
ticketCode = data.ticketCode
status = VALID
```

## 5. 查询订单详情

```powershell
$order = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/api/orders/$orderId" `
  -Headers @{ "X-Trace-Id" = "trace-manual-order" }

$order.data
```

预期：

```text
status = PAID
```

这说明订单已经从待支付流转到已支付。

## 6. 查询电子票

```powershell
$ticketCode = $pay.data.ticketCode

$ticket = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/api/tickets/$ticketCode" `
  -Headers @{ "X-Trace-Id" = "trace-manual-ticket" }

$ticket.data
```

预期：

```text
status = VALID
```

这说明支付后已经生成了可核验的电子票。

## 7. 核验电子票

```powershell
$verifyBody = @{
  ticketCode = $ticketCode
  verifierId = 7001
} | ConvertTo-Json

$verified = Invoke-RestMethod `
  -Method Post `
  -Uri "$base/api/tickets/verify" `
  -ContentType "application/json" `
  -Headers @{ "X-Trace-Id" = "trace-manual-verify" } `
  -Body $verifyBody

$verified.data
```

预期：

```text
status = VERIFIED
verifierId = 7001
```

这说明电子票已经完成入场核验。

## 8. 管理端按用户查询订单

```powershell
$adminOrders = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/api/admin/users/$userId/orders" `
  -Headers @{
    "X-Trace-Id" = "trace-manual-admin-orders"
    "X-Admin-Key" = "eventrush-admin-key"
  }

$adminOrders.data
```

预期：

- 能看到当前用户的订单。
- 订单状态为 `PAID`。

## 9. 管理端按订单查询电子票

```powershell
$adminTicketByOrder = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/api/admin/orders/$orderId/ticket" `
  -Headers @{
    "X-Trace-Id" = "trace-manual-admin-ticket-by-order"
    "X-Admin-Key" = "eventrush-admin-key"
  }

$adminTicketByOrder.data
```

预期：

- 返回订单对应的电子票。
- `ticketCode` 与支付接口返回的一致。

## 10. 管理端按票码查询电子票

```powershell
$adminTicketByCode = Invoke-RestMethod `
  -Method Get `
  -Uri "$base/api/admin/tickets/$ticketCode" `
  -Headers @{
    "X-Trace-Id" = "trace-manual-admin-ticket-by-code"
    "X-Admin-Key" = "eventrush-admin-key"
  }

$adminTicketByCode.data
```

预期：

- 返回票码对应的电子票。
- 状态为 `VERIFIED`。

## 11. 一次性脚本

如果想快速跑完整链路，可以使用下面脚本。注意每次换一个新的 `$userId`。

```powershell
$base = "http://localhost:18086"
$userId = 9913

$grabBody = @{ userId = $userId; sessionId = 101; ticketCategoryId = 1001 } | ConvertTo-Json
$grab = Invoke-RestMethod -Method Post -Uri "$base/api/orders/grab" -ContentType "application/json" -Headers @{ "X-Trace-Id" = "trace-e2e-grab" } -Body $grabBody
$orderId = $grab.data.id

$pay = Invoke-RestMethod -Method Post -Uri "$base/api/orders/$orderId/pay" -Headers @{ "X-Trace-Id" = "trace-e2e-pay" }
$ticketCode = $pay.data.ticketCode

$order = Invoke-RestMethod -Method Get -Uri "$base/api/orders/$orderId" -Headers @{ "X-Trace-Id" = "trace-e2e-order" }
$ticket = Invoke-RestMethod -Method Get -Uri "$base/api/tickets/$ticketCode" -Headers @{ "X-Trace-Id" = "trace-e2e-ticket" }

$verifyBody = @{ ticketCode = $ticketCode; verifierId = 7001 } | ConvertTo-Json
$verified = Invoke-RestMethod -Method Post -Uri "$base/api/tickets/verify" -ContentType "application/json" -Headers @{ "X-Trace-Id" = "trace-e2e-verify" } -Body $verifyBody

$adminOrders = Invoke-RestMethod -Method Get -Uri "$base/api/admin/users/$userId/orders" -Headers @{ "X-Trace-Id" = "trace-e2e-admin-orders"; "X-Admin-Key" = "eventrush-admin-key" }

[pscustomobject]@{
  userId = $userId
  orderId = $orderId
  ticketCode = $ticketCode
  orderStatus = $order.data.status
  ticketStatusBeforeVerify = $ticket.data.status
  ticketStatusAfterVerify = $verified.data.status
  adminOrderCount = $adminOrders.data.Count
}
```

完整跑通时，应该看到类似结果：

```text
orderStatus = PAID
ticketStatusBeforeVerify = VALID
ticketStatusAfterVerify = VERIFIED
adminOrderCount >= 1
```

## 常见问题

### 端口被占用

如果 `18086` 被占用，可以换端口：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18087"
```

同时把脚本里的 `$base` 改成：

```powershell
$base = "http://localhost:18087"
```

### 重复抢票失败

如果看到：

```text
user has already grabbed this ticket
```

说明同一个 `userId` 已经抢过这个票档。换一个新的 `userId` 即可。

### 管理端 401

如果管理端接口返回 `UNAUTHORIZED`，检查请求头：

```text
X-Admin-Key: eventrush-admin-key
```

## 你需要学会的点

- 浏览器能访问 `/api/events`，只能说明服务启动和查询接口正常。
- 完整验收必须继续验证抢票、支付、查票、验票和管理端查询。
- `orderId` 是订单链路的关键变量，`ticketCode` 是电子票链路的关键变量。
- 订单支付后应该是 `PAID`，电子票核验前是 `VALID`，核验后是 `VERIFIED`。
- 管理端接口必须携带 `X-Admin-Key`。
