# 第 35 阶段验收：用户同步抢票基础链路

## 本阶段目标

本阶段在前端工作台中实现第一条真实业务动作：用户选择票档并发起同步抢票。

这一阶段只做同步抢票，不做支付、出票、验票和管理查询。原因是：先确认“前端选择数据 -> 调用后端抢票接口 -> 展示订单结果”这条链路稳定，再继续往后扩展。

## 本阶段交付

更新前端：

| 文件 | 变化 |
| --- | --- |
| `frontend/src/App.vue` | 增加票档选择、用户 ID 输入、同步抢票、订单结果展示 |
| `frontend/src/style.css` | 增加抢票表单、选中票档和订单结果样式 |

## 已完成能力

### 1. 票档选择

活动列表中的票档现在可以点击选择。

页面会展示：

- 当前 `sessionId`。
- 当前 `ticketCategoryId`。
- 当前活动名称。
- 当前票档名称。
- 当前剩余库存。

### 2. 用户 ID 输入

页面提供 `userId` 输入框。

默认会生成一个随机用户 ID，减少手动验收时因为重复用户导致抢票失败的概率。

### 3. 同步抢票

点击“同步抢票”后，前端调用：

```http
POST /api/orders/grab
```

请求体：

```json
{
  "userId": 10001,
  "sessionId": 101,
  "ticketCategoryId": 1001
}
```

成功后展示：

- `orderId`
- 订单状态
- 支付截止时间
- `traceId`

失败时展示后端返回的错误信息。

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
2. 点击一个票档。
3. 确认同步抢票区显示 `sessionId` 和 `ticketCategoryId`。
4. 确认 `userId` 有值，必要时手动改成一个没用过的新 ID。
5. 点击“同步抢票”。
6. 页面显示 `orderId`、订单状态和支付截止时间。
7. 订单状态应为 `PENDING_PAYMENT`。
8. 如果重复使用同一个用户抢同一个票档，应看到业务错误提示。

## 本轮代理验证结果

本轮已经通过前端代理接口完成一次同步抢票验证：

| 项目 | 结果 |
| --- | --- |
| 前端代理地址 | `http://localhost:5173/api/orders/grab` |
| userId | `889150` |
| orderId | `6` |
| 订单状态 | `PENDING_PAYMENT` |
| traceId | `f5c72024a6264295a2c2ea9f32f998dd` |

说明：这些数据来自本地当前环境，后续复跑时 ID 可能不同。只要能创建 `PENDING_PAYMENT` 订单，就是本阶段通过。

## 本阶段边界

本阶段暂不做：

- 支付订单。
- 展示电子票 `ticketCode`。
- 验票。
- 管理端查询。
- 最近请求记录。

这些会在后续阶段继续补齐。

## 你需要学会的点

- 前端真正开始接业务时，要先确认输入、选择、请求、结果四件事。
- 票档选择不是只为了 UI，而是为了给后端抢票接口提供 `sessionId` 和 `ticketCategoryId`。
- 抢票成功只代表创建了待支付订单，还没有出票。
- `PENDING_PAYMENT` 是订单状态机的第一步，后面支付后才会变成 `PAID`。
- 重复用户抢同一票档失败是业务规则生效，不是前端坏了。

## 下一阶段建议

第 36 阶段建议实现支付出票链路：基于 `orderId` 调用支付接口，展示 `ticketCode` 和电子票状态。
