# EventRush

EventRush 是一个面向高并发活动票务场景的 Java 后端项目，模拟演唱会、校园活动、讲座、赛事等场景下的抢票、支付、出票、验票和后台排查流程。

这个项目的重点不是做一个页面好看的票务系统，而是训练 Java 后端面试里高频出现的核心问题：库存扣减、重复抢票、订单状态流转、支付幂等、延时取消、消息消费幂等、缓存、限流、统一响应和链路追踪。

## 项目能力

当前已经完成的核心链路：

1. 查询活动列表和活动详情。
2. 用户选择场次和票档进行抢票。
3. 系统创建待支付订单并扣减库存。
4. 用户模拟支付，系统生成电子票。
5. 支付接口支持幂等，同一订单不会重复出票。
6. 未支付订单支持超时取消和库存释放。
7. 电子票支持查询和入场核验。
8. 核验接口防止重复核验。
9. 管理端可以按用户、订单、票码查询数据。
10. 管理端接口通过 `X-Admin-Key` 做轻量鉴权。
11. 所有接口使用统一响应格式，并返回 `traceId` 方便排查。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 后端框架 | Java 17, Spring Boot 3 |
| Web 接口 | Spring MVC, Validation |
| 数据访问 | Spring JDBC |
| 默认数据库 | H2 文件数据库 |
| 可选数据库 | MySQL |
| 缓存和限流 | Redis，可通过配置开关启用 |
| 消息队列 | RocketMQ，可通过配置开关启用 |
| 测试 | JUnit 5, Spring Boot Test, MockMvc |
| 构建工具 | Maven |

## 本地启动

项目默认使用 H2 文件数据库，不需要先安装 MySQL、Redis 或 RocketMQ 就能启动基础功能。

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

启动后访问：

```text
http://localhost:18086
```

如果要设置管理端密钥，可以在启动前配置环境变量：

```powershell
$env:EVENTRUSH_ADMIN_KEY="my-local-admin-key"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18086"
```

不设置环境变量时，本地默认管理端密钥是：

```text
eventrush-admin-key
```

## 运行测试

```powershell
mvn test
```

测试会覆盖核心业务链路、统一响应、`traceId`、管理端鉴权、支付幂等、异步抢票结果和订单超时取消等能力。

## 接口文档

中文 API 总文档：

```text
docs/api-reference.md
```

项目学习地图：

```text
docs/learning-map.md
```

项目面试题库：

```text
docs/interview-qa.md
```

项目复盘问答训练：

```text
docs/review-qa-drill.md
```

核心代码阅读路线：

```text
docs/core-code-reading-route.md
```

核心代码讲解笔记：

```text
docs/core-code-lecture-notes.md
```

完整业务链路实操验收：

```text
docs/end-to-end-manual-acceptance.md
```

抢票压测指南：

```text
docs/pressure-test-guide.md
```

抢票压测报告模板：

```text
docs/pressure-test-report-template.md
```

Redis Lua 压测开关说明：

```text
docs/redis-lua-pressure-switch.md
```

项目阶段总览：

```text
docs/stage-overview.md
```

HTTP 示例文件：

```text
requests/stage-17.http
requests/stage-18.http
```

推荐按这个顺序验收一条完整链路：

1. 查询活动列表，拿到 `sessionId` 和 `ticketCategoryId`。
2. 调用抢票接口，拿到 `orderId`。
3. 调用支付接口，拿到 `ticketCode`。
4. 查询订单详情，确认状态为 `PAID`。
5. 查询电子票，确认状态为 `VALID`。
6. 调用验票接口，确认状态变为 `VERIFIED`。
7. 携带 `X-Admin-Key` 调用管理端接口做后台查询。

## 重要目录

| 路径 | 说明 |
| --- | --- |
| `src/main/java/com/eventrush/api` | 控制器、统一响应、异常处理、过滤器 |
| `src/main/java/com/eventrush/domain` | 活动、订单、电子票等领域对象 |
| `src/main/java/com/eventrush/service` | 核心业务逻辑 |
| `src/main/resources` | 应用配置和初始化 SQL |
| `src/test/java` | 自动化测试 |
| `docs` | 中文阶段验收文档、API 文档、部署说明 |
| `requests` | 可手动执行的 HTTP 请求示例 |
| `scripts` | 压测或辅助脚本 |

## 配置开关

默认配置偏向本地学习，外部中间件能力默认关闭：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `eventrush.cache.redis-enabled` | `false` | 是否启用 Redis 活动详情缓存 |
| `eventrush.rate-limit.redis-enabled` | `false` | 是否启用 Redis 限流 |
| `eventrush.stock.redis-enabled` | `false` | 是否启用 Redis 库存扣减 |
| `eventrush.queue.redis-enabled` | `false` | 是否启用 Redis 队列 |
| `eventrush.queue.rocket-enabled` | `false` | 是否启用 RocketMQ 异步抢票 |
| `eventrush.admin.key` | `${EVENTRUSH_ADMIN_KEY:eventrush-admin-key}` | 管理端密钥 |

RocketMQ 相关部署说明：

```text
docs/rocketmq-linux-manual.md
docs/rocketmq-docker.md
docs/rocketmq/broker.conf
```

MySQL 建表脚本：

```text
docs/sql/mysql-schema.sql
```

## 阶段验收文档

项目按阶段推进，每一阶段都有中文验收说明：

```text
docs/stage-1-acceptance.md
docs/stage-2-acceptance.md
...
docs/stage-29-acceptance.md
```

这些文档适合用来复习“每一步为什么做、交付了什么、怎么验收、面试怎么说”。

## 面试表达

可以这样介绍这个项目：

> EventRush 是我做的一个高并发活动票务后端项目，完整实现了活动查询、抢票、支付出票、超时取消、电子票核验和后台查询。抢票链路围绕库存一致性、防重复抢票、订单状态流转和接口幂等设计；异步和超时能力通过 RocketMQ 延时消息和消费幂等保证可靠性；接口层统一了响应结构和 traceId，方便联调和排查。这个项目主要用来证明我对 Java 后端高并发业务场景的理解。

## 学习重点

你需要重点掌握：

- 为什么抢票不能只靠普通数据库更新。
- 如何防止超卖和重复抢票。
- 订单状态为什么要设计成状态机。
- 支付、取消、验票为什么都要考虑幂等。
- 延时消息为什么不能直接取消订单，必须先查当前订单状态。
- 后台接口为什么必须有权限边界。
- API 文档为什么要写通用规则、错误码和完整业务链路。
