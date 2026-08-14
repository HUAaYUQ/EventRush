# 第 28 阶段验收：核心代码讲解笔记

## 本阶段交付

- 新增核心代码讲解笔记：`docs/core-code-lecture-notes.md`。
- 逐步讲解 `TicketingService.grabTicket`、`payOrder`、`cancelExpiredOrder`。
- 逐步讲解 `AsyncGrabService.process` 和 `grab-ticket.lua`。
- 补充 `TraceFilter`、`AdminAuthFilter` 的执行顺序和业务意义。
- 说明 Redis 预扣减与数据库事务之间的一致性边界。
- 在 `README.md` 和 `docs/learning-map.md` 中补充入口。

## 为什么要做这一阶段

第 27 阶段解决“应该读哪些代码”，第 28 阶段解决“读完以后能不能讲清楚”。

这一阶段要让你掌握：

- 抢票主流程每一步为什么存在。
- 支付幂等是如何通过订单状态和电子票查询实现的。
- 超时取消为什么要先查状态，再用条件更新兜底。
- 异步消费者如何通过请求状态防止重复消息。
- Redis Lua 保证了什么，又没有保证什么。

## 自动验收

在项目根目录执行：

```powershell
mvn test
```

预期结果：

- 测试全部通过。
- 文档变更不影响业务代码。

## 手动验收

打开：

```text
docs/core-code-lecture-notes.md
```

至少完成下面 5 个问题的口头回答：

1. `grabTicket` 为什么先检查重复抢票，再扣库存？
2. `payOrder` 为什么已支付时返回已有电子票？
3. `cancelExpiredOrder` 为什么不能拿到消息就直接取消？
4. `AsyncGrabService.process` 如何避免重复消费？
5. `grab-ticket.lua` 的三个错误返回值分别是什么？

## 你需要学会的点

- 代码阅读要同时看执行顺序和业务目的。
- 条件更新 SQL 是并发场景里的第二道状态保护。
- Redis Lua 只保证 Redis 内部原子性，不等于整个订单事务。
- 复用同步抢票业务比异步入口重新实现一套规则更可靠。
- `finally` 清理上下文是避免线程复用造成日志污染的关键。

## 面试表达

可以这样说：

> 我不仅看了项目里的类名，还沿着关键方法读了执行顺序。同步抢票由 `TicketingService.grabTicket` 统一编排，支付通过订单状态和已有电子票保证幂等，超时取消通过当前状态检查和条件更新避免误取消，异步抢票通过 `markProcessingIfPending` 防止重复消息，Redis Lua 则把库存检查、重复用户检查和扣减放在 Redis 内原子执行。同时我也明确了 Redis 与数据库之间仍然需要补偿和对账来保证最终一致。

## 下一阶段建议

第 29 阶段建议做“完整业务链路实操验收”：启动项目，按 HTTP 示例真正完成查询活动、抢票、支付、查单、查票、验票和后台查询。
