# EventRush 抢票压测指南

这份文档用于准备抢票链路压测。当前阶段先使用项目已有的 PowerShell 脚本做本地基线压测，不引入 JMeter 或额外依赖。

## 压测目标

压测不是为了追求一个漂亮数字，而是为了证明：

- 并发抢票不会超卖。
- 库存不足时请求会业务失败，而不是系统异常。
- 重复用户不会重复抢到同一票档。
- 平均耗时、P95、P99 和 QPS 有可记录的数据。
- 后续打开 Redis Lua 方案后，可以和当前数据库/本地基线做对比。

## 当前脚本

脚本位置：

```text
scripts/pressure-grab.ps1
```

它会并发请求：

```http
POST /api/orders/grab
```

并输出：

| 指标 | 说明 |
| --- | --- |
| `success` | 抢票成功数量 |
| `failed` | 抢票失败数量 |
| `elapsedMs` | 整轮压测总耗时 |
| `qps` | 每秒处理请求数 |
| `successRate` | 成功率 |
| `avgMs` | 平均响应耗时 |
| `p95Ms` | 95% 请求耗时不超过该值 |
| `p99Ms` | 99% 请求耗时不超过该值 |
| `failed status distribution` | 失败 HTTP 状态分布 |

## 启动应用

先启动服务：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

## 执行压测

新开一个 PowerShell 窗口，在项目根目录执行：

```powershell
.\scripts\pressure-grab.ps1 -BaseUrl "http://localhost:18086" -Users 50 -StartUserId 9000 -SessionId 101 -TicketCategoryId 1001
```

参数说明：

| 参数 | 说明 |
| --- | --- |
| `BaseUrl` | 应用地址 |
| `Users` | 并发用户数量 |
| `StartUserId` | 起始用户 ID，脚本会自动递增 |
| `SessionId` | 场次 ID |
| `TicketCategoryId` | 票档 ID |

## 推荐压测步骤

### 1. 小流量冒烟

```powershell
.\scripts\pressure-grab.ps1 -Users 10 -StartUserId 9000 -TicketCategoryId 1001
```

目标是确认脚本能跑通，服务没有配置问题。

### 2. 超卖验证

选择一个库存较少的票档，例如 `1002`：

```powershell
.\scripts\pressure-grab.ps1 -Users 40 -StartUserId 9100 -TicketCategoryId 1002
```

预期：

- 成功数量不应超过该票档库存。
- 失败请求应该主要是业务失败，比如库存不足。
- 服务不应该出现 500 系统错误。

### 3. 重复用户验证

用相同 `StartUserId` 和相同票档再跑一轮：

```powershell
.\scripts\pressure-grab.ps1 -Users 40 -StartUserId 9100 -TicketCategoryId 1002
```

预期：

- 已抢过的用户不能重复抢同一票档。
- 失败原因应该是重复抢票或库存不足。

## 结果记录模板

| 日期 | 模式 | Users | 票档 | success | failed | qps | avgMs | p95Ms | p99Ms | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-08-13 | 默认 H2/本地基线 | 40 | 1002 |  |  |  |  |  |  |  |
| 2026-08-13 | Redis Lua 库存 | 40 | 1002 |  |  |  |  |  |  |  |

更完整的报告模板见：

```text
docs/pressure-test-report-template.md
```

## 对比维度

后续打开 Redis 库存方案后，重点对比：

- QPS 是否提升。
- P95/P99 是否降低。
- 数据库写压力是否下降。
- 是否仍然不超卖。
- 失败是否主要来自业务限制，而不是系统异常。

## 面试表达

可以这样说：

> 我给抢票接口准备了本地压测脚本，能够并发请求抢票接口并统计成功数、失败数、QPS、平均耗时、P95 和 P99。压测重点不是只看吞吐量，而是验证高并发下不会超卖，库存不足会返回业务失败，后续还可以对比默认方案和 Redis Lua 库存预扣减方案。

## 注意事项

- 本地 H2 压测只能作为学习基线，不代表生产性能。
- 每轮压测会真实创建订单，重复运行时要注意用户 ID 和票档库存。
- 如果要做正式性能报告，建议后续使用 MySQL、Redis、RocketMQ 和更稳定的压测工具。
