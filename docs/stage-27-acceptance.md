# 第 27 阶段验收：核心代码阅读路线

## 本阶段交付

- 新增核心代码阅读路线：`docs/core-code-reading-route.md`。
- 按接口入口、核心业务、活动库存、领域对象、数据库、Redis Lua、MQ、接口治理、自动化测试整理阅读顺序。
- 每组都说明应该读哪些文件、重点看哪些方法、读完要能回答什么问题。
- 给出最小阅读路线，适合时间紧时快速抓主线。
- 在 `README.md` 和 `docs/learning-map.md` 中补充入口。

## 为什么要做这一阶段

前面已经有阶段总览和复盘问答训练，但如果你想真正接收项目成果，还是要亲自读懂关键代码。

这一阶段要解决的问题是：

- 你不用被整个项目文件树吓住。
- 你知道先读接口入口，再读业务编排。
- 你知道哪些方法真正对应高频面试问题。
- 你能把“文档里的表达”落回“代码里的实现”。

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
docs/core-code-reading-route.md
```

检查以下内容：

- 是否列出了核心 Java 类和 Lua 脚本。
- 是否说明每组文件重点看什么。
- 是否列出关键方法和对应问题。
- 是否有最小阅读路线。
- 是否有阅读验收问题。

## 你需要学会的点

- 读项目代码要沿着业务链路读，不要按文件名随机翻。
- Controller 负责入口，Service 负责编排，Repository 负责落库，Filter/Advice 负责接口治理。
- 测试不是附属品，它告诉你哪些业务风险必须守住。
- 能讲清楚关键方法，比背完整项目结构更重要。

## 面试表达

可以这样说：

> 我给项目整理了一份核心代码阅读路线，按接口入口、业务编排、库存、订单状态、Redis Lua、RocketMQ、接口治理和测试来读。这样我能把文档里的设计说明落回具体代码，比如 `TicketingService.grabTicket` 对应抢票主流程，`TicketOrderRepository.markCanceledIfPending` 对应超时取消的状态保护，`grab-ticket.lua` 对应 Redis 原子扣库存，`AsyncGrabService.process` 对应消息消费幂等。

## 下一阶段建议

第 28 阶段建议做“核心代码讲解笔记”：挑 `TicketingService.grabTicket`、`payOrder`、`cancelExpiredOrder`、`AsyncGrabService.process` 和 `grab-ticket.lua` 做逐段中文讲解。
