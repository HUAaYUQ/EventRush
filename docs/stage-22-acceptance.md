# 第 22 阶段验收：抢票压测准备

## 本阶段交付

- 新增抢票压测指南：`docs/pressure-test-guide.md`。
- 增强现有压测脚本：`scripts/pressure-grab.ps1`。
- 压测脚本新增输出 `qps`、`successRate`、`p99Ms`。
- 在 `README.md` 和 `docs/learning-map.md` 中补充压测指南入口。

## 为什么要做这一阶段

高并发项目不能只说“我做了抢票”，还要能说明“我怎么验证它”。

压测准备阶段要解决：

- 用什么脚本压测。
- 压测哪个接口。
- 看哪些指标。
- 怎么判断有没有超卖。
- 怎么记录结果。
- 后续如何对比默认方案和 Redis Lua 方案。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 脚本文档变更不影响业务代码。

## 手动验收

启动应用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

新开 PowerShell 窗口执行：

```powershell
.\scripts\pressure-grab.ps1 -BaseUrl "http://localhost:18086" -Users 10 -StartUserId 9000 -SessionId 101 -TicketCategoryId 1001
```

预期输出包含：

- `success=`
- `failed=`
- `elapsedMs=`
- `qps=`
- `successRate=`
- `avgMs=`
- `p95Ms=`
- `p99Ms=`

## 你需要学会的点

- 压测不是只看 QPS，还要看是否超卖、失败是否符合预期、P95/P99 是否稳定。
- 本地 H2 压测只能作为学习基线，不代表生产性能。
- 压测脚本要能沉淀指标，后续才方便做优化前后对比。
- 抢票项目最好能讲清楚“怎么验证没有超卖”。

## 面试表达

可以这样说：

> 我给抢票接口准备了本地压测脚本，脚本会并发请求抢票接口，并统计成功数、失败数、QPS、平均耗时、P95 和 P99。压测重点是验证高并发下不会超卖，失败主要来自库存不足或重复抢票，而不是系统异常。后续可以用同一套指标对比默认方案和 Redis Lua 库存预扣减方案。

## 下一阶段建议

第 23 阶段建议做“压测报告模板”：把压测环境、参数、结果表格、现象分析和优化结论整理成一份可填写的报告文档。
