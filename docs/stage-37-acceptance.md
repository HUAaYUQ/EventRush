# 第 37 阶段验收：电子票查询与验票链路

## 本阶段目标

本阶段在前端工作台中实现用户端主链路的第三步：支付出票后，通过 `ticketCode` 查询电子票，并完成入场验票。

这一阶段要让你明确看到：

- 支付成功后会拿到一张 `VALID` 电子票。
- `ticketCode` 是电子票链路的关键凭证。
- 查询电子票可以确认票码、订单和当前票状态。
- 验票成功后，电子票状态从 `VALID` 变成 `VERIFIED`。
- 同一张票重复验票会被后端拒绝。

## 本阶段交付

更新前端：

| 文件 | 变化 |
| --- | --- |
| `frontend/src/App.vue` | 增加电子票查询、验票、验票员 ID、错误展示和 traceId 展示 |
| `frontend/src/style.css` | 增加电子票查询与验票区域样式 |

更新文档：

| 文件 | 变化 |
| --- | --- |
| `docs/stage-37-acceptance.md` | 新增本阶段中文验收说明 |
| `README.md` | 增加第 37 阶段文档入口 |
| `docs/learning-map.md` | 更新前端工作台学习入口 |
| `docs/stage-overview.md` | 阶段总览增加第 37 阶段 |

## 已完成能力

### 1. 查询电子票

页面会使用输入框中的 `ticketCode` 调用：

```http
GET /api/tickets/{ticketCode}
```

成功后展示：

- `ticketCode`
- 票状态
- 关联 `orderId`
- 验票员 ID
- 查询接口 `traceId`

支付成功后，页面会自动把支付接口返回的 `ticketCode` 带入查询框；也可以手动输入票码查询。

### 2. 验票入场

页面会使用 `ticketCode` 和 `verifierId` 调用：

```http
POST /api/tickets/verify
Content-Type: application/json

{
  "ticketCode": "ER-xxxx",
  "verifierId": 7001
}
```

成功后展示：

- 票状态变为 `VERIFIED`
- 验票员 ID
- 验票接口 `traceId`

### 3. 重复验票错误展示

如果对同一张已经 `VERIFIED` 的电子票再次验票，后端会返回业务错误，前端会直接展示错误信息。

这个能力很关键：验票不是“点了就成功”，而是要保证一张票只能入场一次。

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

1. 等待活动列表加载。
2. 选择一个有库存的票档。
3. 使用一个未抢过该票档的 `userId`。
4. 点击“同步抢票”。
5. 点击“支付并出票”。
6. 确认页面出现 `ticketCode`，票状态为 `VALID`。
7. 点击“查询电子票”。
8. 确认查询结果仍然是同一张票，并且状态为 `VALID`。
9. 输入或保留默认验票员 ID，例如 `7001`。
10. 点击“验票入场”。
11. 确认电子票状态变为 `VERIFIED`。
12. 再次点击“验票入场”，确认页面展示重复验票错误。

## 本轮验收记录

本阶段已完成自动检查：

| 检查项 | 结果 |
| --- | --- |
| 前端构建 | 通过 |
| 后端测试 | 通过，`Tests run: 23, Failures: 0, Errors: 0, Skipped: 0` |
| 代理链路验收 | 通过 |

本轮已经通过前端代理接口完成一次“抢票 -> 支付 -> 查票 -> 验票 -> 重复验票”验证：

| 项目 | 结果 |
| --- | --- |
| userId | `928168` |
| orderId | `8` |
| ticketCode | `ER-0207056D340A4306` |
| 验票前票状态 | `VALID` |
| 验票后票状态 | `VERIFIED` |
| 重复验票 | 已被拒绝 |
| 验票 traceId | `6cc4187f148e4d429a4cbdc929c6624a` |

说明：这些数据来自本地当前环境，后续复跑时 ID 和票码可能不同。只要状态流转符合 `VALID -> VERIFIED`，并且重复验票被拒绝，就是本阶段通过。

## 本阶段边界

本阶段暂不做：

- 管理端按票码查询入口。
- 最近请求记录。
- 验票统计图表。
- 压测结果可视化。

这些能力会在后续阶段继续补齐。

## 你需要学会的点

- `ticketCode` 是电子票链路的核心变量。
- 订单状态和票状态不是一回事。
- 订单支付后是 `PAID`，电子票验票前是 `VALID`。
- 验票后电子票变成 `VERIFIED`，不能再次入场。
- 重复验票必须由后端规则兜底，不能只靠前端禁用按钮。

## 面试表达

可以这样说：

> 支付成功后系统会生成一张电子票，状态是 VALID。入场时工作人员通过 ticketCode 查询电子票，再调用验票接口把票状态更新为 VERIFIED。验票接口会检查票的当前状态，只有 VALID 的票才能核验；如果同一张票重复核验，系统会拒绝，防止重复入场。

## 下一阶段建议

第 38 阶段建议实现管理端排查入口：在前端工作台里通过 `orderId`、`userId`、`ticketCode` 查询后台数据，让用户链路和管理端查询互相印证。
