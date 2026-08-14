# 第 38 阶段验收：管理端排查入口

## 本阶段目标

本阶段在前端工作台中加入管理端排查入口，让用户链路和后台查询结果能互相印证。

这一阶段要让你明确看到：

- 管理端接口必须携带 `X-Admin-Key`。
- 可以按 `userId` 查询该用户订单。
- 可以按 `orderId` 查询订单对应的电子票。
- 可以按 `ticketCode` 查询电子票详情。
- 管理端查询结果能和用户侧的抢票、支付、验票结果对上。

## 本阶段交付

更新前端：

| 文件 | 变化 |
| --- | --- |
| `frontend/src/App.vue` | 增加管理端密钥输入、按用户查订单、按订单查票、按票码查票 |
| `frontend/src/style.css` | 增加管理端排查区域和结果列表样式 |

更新文档：

| 文件 | 变化 |
| --- | --- |
| `docs/stage-38-acceptance.md` | 新增本阶段中文验收说明 |
| `README.md` | 增加第 38 阶段文档入口 |
| `docs/learning-map.md` | 更新前端工作台学习入口 |
| `docs/stage-overview.md` | 阶段总览增加第 38 阶段 |

## 已完成能力

### 1. 管理端密钥

页面提供 `X-Admin-Key` 输入框，默认值为本地默认密钥：

```text
eventrush-admin-key
```

访问 `/api/admin/**` 时，前端会把该值放入请求头。

### 2. 按用户查订单

页面会基于当前 `userId` 调用：

```http
GET /api/admin/users/{userId}/orders
X-Admin-Key: eventrush-admin-key
```

成功后展示该用户的订单列表，包括：

- `orderId`
- 订单状态
- 票档 ID
- 查询 `traceId`

### 3. 按订单查票

页面会基于当前 `orderId` 调用：

```http
GET /api/admin/orders/{orderId}/ticket
X-Admin-Key: eventrush-admin-key
```

成功后展示订单对应电子票。

### 4. 按票码查票

页面会基于当前 `ticketCode` 调用：

```http
GET /api/admin/tickets/{ticketCode}
X-Admin-Key: eventrush-admin-key
```

成功后展示电子票状态和验票员信息。

## 验收方式

### 前端构建

在 `frontend` 目录执行：

```powershell
npm.cmd run build
```

预期结果：

- 构建成功。

### 后端测试

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。

### 页面手动验收

启动后端：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

启动前端：

```powershell
cd frontend
npm.cmd run dev
```

访问：

```text
http://localhost:5173
```

操作步骤：

1. 选择一个有库存的票档。
2. 使用一个未抢过该票档的 `userId`。
3. 点击“同步抢票”。
4. 点击“支付并出票”。
5. 点击“查询电子票”。
6. 点击“验票入场”。
7. 在管理端排查区确认 `X-Admin-Key` 为正确密钥。
8. 点击“按用户查订单”，确认能看到当前用户订单。
9. 点击“按订单查票”，确认返回的 `ticketCode` 和支付结果一致。
10. 点击“按票码查票”，确认票状态和验票结果一致。
11. 把 `X-Admin-Key` 改成错误值，再查询一次，确认后端返回鉴权错误。

## 本轮验收记录

本阶段已完成自动检查：

| 检查项 | 结果 |
| --- | --- |
| 前端构建 | 通过 |
| 后端测试 | 通过，`Tests run: 23, Failures: 0, Errors: 0, Skipped: 0` |
| 代理链路验收 | 通过 |

本轮已经通过前端代理接口完成一次“抢票 -> 支付 -> 验票 -> 管理端排查”验证：

| 项目 | 结果 |
| --- | --- |
| userId | `1077073` |
| orderId | `9` |
| ticketCode | `ER-5019B2A810C84F8F` |
| 用户侧验票状态 | `VERIFIED` |
| 管理端用户订单数 | `1` |
| 管理端按订单查票 | `ticketCode` 一致 |
| 管理端按票码查票 | 状态为 `VERIFIED` |
| 错误管理密钥 | 已被拒绝 |
| 管理端查订单 traceId | `c0102fba40b84b87a912af491abeee2a` |

说明：这些数据来自本地当前环境，后续复跑时 ID 和票码可能不同。验收时重点看管理端数据是否和用户侧状态一致。

## 本阶段边界

本阶段暂不做：

- 登录注册。
- 角色权限系统。
- 管理端分页筛选。
- 管理端修改订单或票状态。

当前只做只读排查入口，足够支撑项目展示和面试讲解。

## 你需要学会的点

- 管理端接口即使只读，也不能裸奔。
- `X-Admin-Key` 是当前阶段的轻量权限边界。
- 后台排查不是为了“多一个页面”，而是为了验证业务数据可追踪。
- 用户侧看到的 `orderId`、`ticketCode`，应该能被管理端查到。
- 面试展示时，要用管理端查询证明链路结果真实存在。

## 面试表达

可以这样说：

> 我给项目加了管理端排查入口，支持按用户查订单、按订单查电子票、按票码查电子票。管理端接口统一走 /api/admin/**，必须携带 X-Admin-Key。这样演示时不只是用户侧页面显示成功，还能用后台查询证明订单和电子票数据已经落库，并且状态流转一致。

## 下一阶段建议

第 39 阶段建议实现压测结果记录区：先把压测报告里的 QPS、P95、P99、成功数、失败数和是否超卖做成前端展示数据，为后续可视化大屏打基础。
