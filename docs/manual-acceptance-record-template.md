# EventRush 手动验收结果记录模板

这份模板用于记录每一次本地实操验收结果。它不是接口说明，而是项目演示证据：你什么时候跑的、用了什么输入、拿到了什么输出、状态有没有正确流转、截图或日志在哪里。

建议每次完整验收后复制一份本模板，填写成一次真实记录。

## 基本信息

| 项目 | 记录 |
| --- | --- |
| 验收日期 | 例如：2026-08-14 |
| 验收人 | 例如：你的名字 |
| 项目分支 | `main` |
| Git 提交号 | 例如：`7ce3eec` |
| 启动端口 | 例如：`18086` |
| 服务地址 | `http://localhost:18086` |
| 数据库 | H2 / MySQL |
| Redis 是否开启 | 否 / 是 |
| RocketMQ 是否开启 | 否 / 是 |
| 管理端密钥 | `eventrush-admin-key` 或本次配置值 |

## 启动记录

启动命令：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

启动结果：

```text
填写是否看到 Started EventRushApplication
```

截图或日志位置：

```text
填写截图文件名、日志片段位置，或简单写“浏览器已访问 /api/events 成功”
```

## 活动查询记录

请求：

```text
GET http://localhost:18086/api/events
```

结果：

| 字段 | 记录 |
| --- | --- |
| `success` |  |
| `code` |  |
| 活动 ID |  |
| 活动名称 |  |
| `sessionId` |  |
| `ticketCategoryId` |  |
| 剩余库存 |  |
| `traceId` |  |

验收结论：

```text
通过 / 不通过，原因：
```

## 抢票记录

请求参数：

| 字段 | 记录 |
| --- | --- |
| `userId` |  |
| `sessionId` |  |
| `ticketCategoryId` |  |

返回结果：

| 字段 | 记录 |
| --- | --- |
| `success` |  |
| `code` |  |
| `orderId` |  |
| 订单状态 | 期望：`PENDING_PAYMENT` |
| `expireTime` |  |
| `traceId` |  |

验收结论：

```text
通过 / 不通过，原因：
```

## 支付记录

请求：

```text
POST http://localhost:18086/api/orders/{orderId}/pay
```

返回结果：

| 字段 | 记录 |
| --- | --- |
| `success` |  |
| `code` |  |
| `ticketCode` |  |
| 电子票状态 | 期望：`VALID` |
| `generatedTime` |  |
| `traceId` |  |

验收结论：

```text
通过 / 不通过，原因：
```

## 查询订单记录

请求：

```text
GET http://localhost:18086/api/orders/{orderId}
```

返回结果：

| 字段 | 记录 |
| --- | --- |
| `orderId` |  |
| 订单状态 | 期望：`PAID` |
| `payTime` |  |
| `traceId` |  |

验收结论：

```text
通过 / 不通过，原因：
```

## 查询电子票记录

请求：

```text
GET http://localhost:18086/api/tickets/{ticketCode}
```

返回结果：

| 字段 | 记录 |
| --- | --- |
| `ticketCode` |  |
| 电子票状态 | 期望：`VALID` |
| `orderId` |  |
| `traceId` |  |

验收结论：

```text
通过 / 不通过，原因：
```

## 验票记录

请求参数：

| 字段 | 记录 |
| --- | --- |
| `ticketCode` |  |
| `verifierId` |  |

返回结果：

| 字段 | 记录 |
| --- | --- |
| `success` |  |
| `code` |  |
| 电子票状态 | 期望：`VERIFIED` |
| `verifiedTime` |  |
| `verifierId` |  |
| `traceId` |  |

验收结论：

```text
通过 / 不通过，原因：
```

## 管理端查询记录

### 按用户查询订单

请求：

```text
GET http://localhost:18086/api/admin/users/{userId}/orders
X-Admin-Key: eventrush-admin-key
```

结果：

| 字段 | 记录 |
| --- | --- |
| 是否成功 |  |
| 返回订单数量 |  |
| 是否包含本次 `orderId` |  |
| `traceId` |  |

### 按订单查询电子票

请求：

```text
GET http://localhost:18086/api/admin/orders/{orderId}/ticket
X-Admin-Key: eventrush-admin-key
```

结果：

| 字段 | 记录 |
| --- | --- |
| 是否成功 |  |
| 返回 `ticketCode` |  |
| 是否与支付结果一致 |  |
| `traceId` |  |

### 按票码查询电子票

请求：

```text
GET http://localhost:18086/api/admin/tickets/{ticketCode}
X-Admin-Key: eventrush-admin-key
```

结果：

| 字段 | 记录 |
| --- | --- |
| 是否成功 |  |
| 电子票状态 | 期望：`VERIFIED` |
| `traceId` |  |

管理端验收结论：

```text
通过 / 不通过，原因：
```

## 状态流转总表

| 节点 | 期望状态 | 实际状态 | 是否通过 |
| --- | --- | --- | --- |
| 抢票后订单 | `PENDING_PAYMENT` |  |  |
| 支付后订单 | `PAID` |  |  |
| 支付后电子票 | `VALID` |  |  |
| 验票后电子票 | `VERIFIED` |  |  |

## 异常记录

如果过程中遇到问题，记录在这里：

| 问题 | 现象 | 原因判断 | 处理方式 | 是否解决 |
| --- | --- | --- | --- | --- |
| 例如：重复抢票 | 返回 `user has already grabbed this ticket` | 同一用户重复抢同一票档 | 换新的 `userId` | 是 |

## 最终结论

```text
本次完整业务链路验收：通过 / 不通过

结论说明：

后续待处理：
```

## 面试表达素材

可以根据记录整理成一句话：

> 我做手动验收时，不只看接口有没有返回，还记录了输入参数、订单 ID、票码、traceId 和状态流转。一次完整链路里，抢票后订单是 PENDING_PAYMENT，支付后订单变成 PAID 并生成 VALID 电子票，验票后电子票变成 VERIFIED，管理端也能通过密钥查询到对应订单和票。
