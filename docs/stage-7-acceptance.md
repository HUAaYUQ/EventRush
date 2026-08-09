# 第 7 阶段验收：压测准备与并发正确性基线

## 本阶段交付

- 新增并发抢票自动测试：40 个用户同时抢 VIP 票档，VIP 库存只有 10 张，最终成功订单必须等于 10，不能超卖。
- 新增本地轻量压测脚本：`scripts/pressure-grab.ps1`，用于从 HTTP 接口发起多用户并发抢票。
- 本阶段不要求安装 JMeter、RabbitMQ 或 Kafka；先把“怎么判断抢票没有超卖、接口表现如何”学清楚。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- `concurrentGrabDoesNotOversell` 通过，说明并发场景下成功抢票数没有超过库存。

## 手动压测

先启动应用，例如：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

再打开另一个终端执行：

```powershell
.\scripts\pressure-grab.ps1 -BaseUrl http://localhost:18086 -Users 20 -TicketCategoryId 1001
```

如果 Windows 提示“禁止运行脚本”，使用一次性的绕过方式：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\pressure-grab.ps1 -BaseUrl http://localhost:18086 -Users 20 -TicketCategoryId 1001
```

脚本会输出：

- `success`：成功抢到票的请求数。
- `failed`：失败请求数。
- `elapsedMs`：整轮请求耗时。
- `avgMs`：平均响应耗时。
- `p95Ms`：95% 请求能在这个耗时内完成。
- `failed status distribution`：失败 HTTP 状态分布。

## HTTP 示例

也可以参考：

```text
requests/stage-7.http
```

## 你需要学会的点

- 压测不是只看“能不能跑”，而是看成功数、失败数、耗时分布和业务正确性。
- 抢票系统最怕的不是失败，而是“卖出数量超过库存”，所以并发测试的第一指标是不能超卖。
- `avgMs` 代表平均体验，`p95Ms` 更接近高峰时大多数用户的真实感受。
- 现在脚本只是本地基线，不等于正式性能报告；正式压测还需要独立压测机、稳定数据库、监控和更真实的用户模型。

## 面试表达

可以这样说：

> 我没有一开始就上复杂压测平台，而是先补了并发正确性测试，确保核心链路不会超卖。然后用轻量脚本做本地 HTTP 压测基线，观察成功数、失败数、平均耗时和 p95 耗时。这样可以先验证业务安全，再逐步引入 JMeter、监控和消息队列。

## 下一阶段建议

第 8 阶段可以进入“消息队列削峰”的设计与实现。考虑到当前电脑还没有消息队列中间件，可以优先选 Redis Stream 或本地内存队列演示削峰思想，等基础流程跑通后再切到 RabbitMQ 或 Kafka。
