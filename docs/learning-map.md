# EventRush 项目学习地图

这份文档不是接口文档，也不是阶段记录。它的作用是告诉你：当你想复习某个后端知识点时，应该看哪些代码、哪些测试、哪些验收文档，以及面试时怎么把它讲出来。

## 推荐复习顺序

1. 先跑通完整业务链路：活动查询 -> 抢票 -> 支付 -> 查票 -> 验票。
2. 再看库存和重复抢票：为什么不能超卖，为什么同一用户不能重复抢。
3. 然后看订单状态机：待支付、已支付、已取消之间怎么流转。
4. 接着看幂等：重复支付、重复验票、重复消息消费如何处理。
5. 再看 RocketMQ：异步抢票和订单超时取消为什么需要消息。
6. 最后看接口治理：统一响应、traceId、后台鉴权和 API 文档。

## 业务主线

### 你要理解的问题

- 用户从看到活动到完成验票，后端发生了什么。
- 订单和电子票分别解决什么问题。
- 为什么支付后才生成电子票。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/api/EventController.java` | 活动列表和详情接口 |
| `src/main/java/com/eventrush/api/TicketingController.java` | 抢票、支付、查单、查票、验票接口 |
| `src/main/java/com/eventrush/service/TicketingService.java` | 完整业务主流程 |
| `src/main/java/com/eventrush/domain/TicketOrder.java` | 订单领域对象 |
| `src/main/java/com/eventrush/domain/ElectronicTicket.java` | 电子票领域对象 |

### 重点测试

| 文件 | 看什么 |
| --- | --- |
| `src/test/java/com/eventrush/service/TicketingServiceTest.java` | 内存版完整业务链路 |
| `src/test/java/com/eventrush/service/TicketingPersistenceTest.java` | 数据库持久化后的业务链路 |

### 面试表达

> 我把票务业务拆成活动、场次、票档、订单和电子票。用户抢票成功后先生成待支付订单，支付成功后再生成电子票，入场时通过票码核验并更新票状态。这样订单负责交易状态，电子票负责入场凭证，职责比较清晰。

## 库存一致性和防超卖

### 你要理解的问题

- 为什么抢票最怕超卖。
- 为什么扣库存和创建订单必须保持一致。
- 为什么同一用户同一票档不能重复抢。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/TicketingService.java` | `grabTicket`、`grabWithDatabaseStock`、`grabWithRedisStock` |
| `src/main/java/com/eventrush/service/EventCatalogService.java` | 本地库存扣减逻辑 |
| `src/main/java/com/eventrush/service/TicketOrderRepository.java` | 数据库订单创建和重复抢票保护 |
| `src/main/java/com/eventrush/service/RedisTicketStockService.java` | Redis 库存预扣减入口 |
| `src/main/resources/lua/grab-ticket.lua` | Redis Lua 原子扣库存和防重复抢票 |

### 重点测试

| 文件 | 看什么 |
| --- | --- |
| `src/test/java/com/eventrush/service/TicketingServiceTest.java` | 并发抢票不超卖、库存不足失败、重复抢票失败 |
| `src/test/java/com/eventrush/service/TicketingPersistenceTest.java` | 持久化场景下重复抢票失败 |

### 面试表达

> 抢票接口不能只依赖普通查询后再更新库存，因为高并发下容易出现并发竞争。项目里先用本地同步和数据库约束保证基础正确性，再提供 Redis Lua 方案，把库存检查、重复用户检查、库存扣减和用户记录放在一个脚本里原子执行，从而防止超卖和重复抢票。

## Redis 缓存、库存和限流

### 你要理解的问题

- Redis 在项目里分别承担了缓存、库存和限流三个角色。
- 哪些能力默认关闭，为什么本地学习阶段可以先不用 Redis。
- Lua 脚本为什么能保证一组操作的原子性。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/EventCacheService.java` | 活动详情缓存 |
| `src/main/java/com/eventrush/service/RedisTicketStockService.java` | Redis 库存扣减和库存释放 |
| `src/main/java/com/eventrush/service/RateLimitService.java` | Redis 限流入口 |
| `src/main/resources/lua/grab-ticket.lua` | 抢票库存 Lua |
| `src/main/resources/lua/rate-limit.lua` | 限流 Lua |
| `src/main/resources/application.yml` | Redis 相关配置开关 |

### 相关文档

| 文件 | 看什么 |
| --- | --- |
| `docs/stage-3-acceptance.md` | 活动详情缓存 |
| `docs/stage-5-acceptance.md` | Redis 库存扣减 |
| `docs/stage-7-acceptance.md` | 限流 |

### 面试表达

> Redis 在项目里不是只当缓存用。活动详情可以走缓存减少查询压力；抢票时可以用 Redis Lua 做库存预扣减和防重复抢票；限流也可以用 Redis 计数实现。几个能力都做成配置开关，本地不依赖 Redis 也能跑，部署或压测时再打开。

## 订单状态机和超时取消

### 你要理解的问题

- 订单为什么不能只有“成功/失败”。
- 为什么超时取消前必须先查订单当前状态。
- 为什么已支付订单不能被延时消息取消。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/domain/OrderStatus.java` | 订单状态枚举 |
| `src/main/java/com/eventrush/domain/TicketOrder.java` | 订单状态变化方法 |
| `src/main/java/com/eventrush/service/TicketingService.java` | 支付、取消、释放库存 |
| `src/main/java/com/eventrush/service/TicketOrderRepository.java` | `markPaid`、`markCanceledIfPending` |
| `src/main/java/com/eventrush/service/OrderTimeoutScheduler.java` | 定时扫描超时订单 |
| `src/main/java/com/eventrush/service/RocketOrderTimeoutConsumer.java` | RocketMQ 超时订单消费者 |

### 重点测试

| 文件 | 看什么 |
| --- | --- |
| `src/test/java/com/eventrush/service/OrderTimeoutServiceTest.java` | 超时取消、已支付订单不取消 |

### 相关文档

| 文件 | 看什么 |
| --- | --- |
| `docs/stage-11-acceptance.md` | RocketMQ 延时消息和兜底扫描 |

### 面试表达

> 订单有待支付、已支付、已取消三种状态。超时取消不能拿到消息就直接取消，而是先查订单当前状态，只取消仍处于待支付的订单。这样即使延时消息晚到，或者用户已经支付，也不会把已支付订单误取消。

## 幂等设计

### 你要理解的问题

- 为什么支付接口可能被重复调用。
- 为什么消息可能被重复消费。
- 为什么票不能重复核验。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/TicketingService.java` | 支付幂等、验票防重复、消息处理 |
| `src/main/java/com/eventrush/service/ElectronicTicketRepository.java` | 电子票创建和票码查询 |
| `src/main/java/com/eventrush/service/AsyncGrabRequestRepository.java` | 异步抢票请求状态持久化 |
| `src/main/java/com/eventrush/service/AsyncGrabService.java` | 异步抢票结果和重复消息处理 |

### 重点测试

| 文件 | 看什么 |
| --- | --- |
| `src/test/java/com/eventrush/service/TicketingServiceTest.java` | 重复支付返回同一张票、重复验票失败 |
| `src/test/java/com/eventrush/service/TicketingPersistenceTest.java` | 持久化场景下支付和验票幂等 |
| `src/test/java/com/eventrush/service/AsyncGrabServiceTest.java` | 异步抢票重复消息不重复处理 |

### 相关文档

| 文件 | 看什么 |
| --- | --- |
| `docs/stage-10-acceptance.md` | 异步抢票结果持久化和消费幂等 |
| `docs/stage-12-acceptance.md` | 支付幂等和单订单单电子票 |

### 面试表达

> 我把幂等拆在不同场景里处理。支付幂等保证同一个订单重复支付返回同一张电子票；验票幂等保证已核验票不能再次核验；消息消费幂等保证同一个异步抢票请求不会被重复处理。这些都是高并发业务里必须考虑的异常重试场景。

## RocketMQ 和异步处理

### 你要理解的问题

- 为什么抢票可以异步化。
- RocketMQ 在项目里负责哪两类消息。
- MQ 失败时为什么还需要兜底扫描。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/service/AsyncGrabService.java` | 异步抢票提交、发送消息、消费消息 |
| `src/main/java/com/eventrush/service/RocketGrabConsumer.java` | 抢票消息消费者 |
| `src/main/java/com/eventrush/service/OrderTimeoutMessagePublisher.java` | 订单超时延时消息发布 |
| `src/main/java/com/eventrush/service/RocketOrderTimeoutConsumer.java` | 订单超时消息消费者 |
| `src/main/resources/application.yml` | RocketMQ 开关、Topic、ConsumerGroup |

### 相关文档

| 文件 | 看什么 |
| --- | --- |
| `docs/rocketmq-linux-manual.md` | Linux 手动部署 RocketMQ |
| `docs/rocketmq-docker.md` | Docker 部署 RocketMQ |
| `docs/stage-9-acceptance.md` | RocketMQ 异步抢票 |
| `docs/stage-11-acceptance.md` | RocketMQ 订单超时取消 |

### 面试表达

> 项目里 RocketMQ 用在两个地方：异步抢票和订单超时取消。异步抢票把请求快速落库并返回 requestId，再由消费者处理；订单创建后发送延时消息，到期后消费者检查订单状态并取消待支付订单。同时保留定时扫描作为兜底，避免 MQ 不可用时超时订单无人处理。

## 接口治理和排查能力

### 你要理解的问题

- 为什么接口要统一响应格式。
- `traceId` 对排查问题有什么帮助。
- 为什么后台接口不能裸奔。

### 重点代码

| 文件 | 看什么 |
| --- | --- |
| `src/main/java/com/eventrush/api/ApiResponse.java` | 统一响应对象 |
| `src/main/java/com/eventrush/api/ApiResponseAdvice.java` | 成功响应统一包装 |
| `src/main/java/com/eventrush/api/ApiExceptionHandler.java` | 错误响应统一包装 |
| `src/main/java/com/eventrush/api/TraceFilter.java` | traceId 生成、响应头、日志 |
| `src/main/java/com/eventrush/api/AdminAuthFilter.java` | 管理端密钥鉴权 |
| `src/main/java/com/eventrush/api/AdminController.java` | 后台查询接口 |

### 重点测试

| 文件 | 看什么 |
| --- | --- |
| `src/test/java/com/eventrush/api/ApiResponseTest.java` | 统一响应、traceId、管理端鉴权 |

### 相关文档

| 文件 | 看什么 |
| --- | --- |
| `docs/api-reference.md` | API 总文档 |
| `docs/stage-13-acceptance.md` | 统一响应 |
| `docs/stage-14-acceptance.md` | traceId 和请求日志 |
| `docs/stage-16-acceptance.md` | 管理端权限保护 |
| `docs/stage-18-acceptance.md` | 管理端密钥环境变量配置 |

### 面试表达

> 我在接口层做了统一响应包装，成功和失败都返回 success、code、message、data、traceId。traceId 会写入响应头和日志，方便根据一次请求定位日志。管理端接口统一走 /api/admin/**，通过 X-Admin-Key 做轻量保护，并支持环境变量覆盖密钥。

## 文档和验收能力

### 你要理解的问题

- 为什么项目不能只有代码。
- 阶段验收文档怎么帮助复盘。
- README 和 API 文档分别解决什么问题。

### 重点文档

| 文件 | 看什么 |
| --- | --- |
| `README.md` | 仓库首页，项目展示入口 |
| `docs/api-reference.md` | API 总文档 |
| `docs/interview-qa.md` | 项目面试题库 |
| `docs/review-qa-drill.md` | 项目复盘短问短答训练 |
| `docs/core-code-reading-route.md` | 核心代码阅读顺序和关键方法 |
| `docs/core-code-lecture-notes.md` | 核心方法逐步讲解和边界说明 |
| `docs/end-to-end-manual-acceptance.md` | 完整业务链路实操验收步骤 |
| `docs/manual-acceptance-record-template.md` | 手动验收结果记录模板 |
| `docs/pressure-test-guide.md` | 抢票压测指南 |
| `docs/pressure-test-report-template.md` | 抢票压测报告模板 |
| `docs/redis-lua-pressure-switch.md` | Redis Lua 压测开关说明 |
| `docs/product-output-blueprint.md` | 产品输出、身份划分、数据证据和面试展示蓝图 |
| `docs/ui-workbench-requirements.md` | 前端工作台页面、接口、状态和验收需求 |
| `docs/frontend-scaffold-plan.md` | 前端技术选型、目录、端口、代理和环境准备 |
| `frontend/src/App.vue` | 前端工作台、活动联通、同步抢票、支付出票、查票、验票、管理端排查入口和压测结果记录区 |
| `frontend/vite.config.js` | 前端端口和 `/api` 代理配置 |
| `docs/stage-overview.md` | 第 1 到第 39 阶段总览和主题归类 |
| `docs/stage-1-acceptance.md` 到 `docs/stage-39-acceptance.md` | 每阶段交付、验收、面试表达 |
| `requests/stage-17.http` | 完整业务链路 HTTP 示例 |
| `requests/stage-18.http` | 管理端密钥配置验收 |

### 面试表达

> 我不是只写代码，也给项目补了 README、API 文档和阶段验收文档。README 负责让别人快速理解项目，API 文档负责联调和验收，阶段文档负责记录每一步为什么做、怎么测、面试怎么讲。

## 最小复习清单

如果时间很紧，优先看这 14 个文件：

1. `README.md`
2. `docs/stage-overview.md`
3. `docs/review-qa-drill.md`
4. `docs/core-code-reading-route.md`
5. `docs/core-code-lecture-notes.md`
6. `docs/end-to-end-manual-acceptance.md`
7. `docs/manual-acceptance-record-template.md`
8. `docs/api-reference.md`
9. `docs/product-output-blueprint.md`
10. `docs/ui-workbench-requirements.md`
11. `docs/frontend-scaffold-plan.md`
12. `frontend/src/App.vue`
13. `frontend/vite.config.js`
14. `src/main/java/com/eventrush/service/TicketingService.java`
15. `src/main/java/com/eventrush/service/AsyncGrabService.java`
16. `src/main/java/com/eventrush/service/TicketOrderRepository.java`
17. `src/main/resources/lua/grab-ticket.lua`
18. `src/main/java/com/eventrush/api/TraceFilter.java`
19. `src/test/java/com/eventrush/service/TicketingServiceTest.java`

看完这 19 个文件，至少能把项目主线、库存、幂等、MQ、接口治理、产品输出、前端展示需求、前端启动边界、前后端联通、同步抢票和支付出票链路讲清楚。

## 自测问题

复习时可以用下面这些问题检查自己：

- EventRush 为什么不是普通 CRUD 项目？
- 抢票为什么会有超卖问题？
- Redis Lua 在项目里解决了什么？
- 同一个用户重复抢同一个票档会发生什么？
- 支付接口为什么要幂等？
- 已支付订单收到超时取消消息会发生什么？
- RocketMQ 消息重复消费时如何避免重复处理？
- `traceId` 出现在响应头、响应体和日志里分别有什么价值？
- 管理端接口为什么必须鉴权？
- README、API 文档、阶段验收文档各自解决什么问题？
- EventRush 的前端为什么应该做成票务操作工作台，而不是营销首页？
- 第一版前端为什么先做压测结果记录，而不是直接做自动监控大屏？
- PowerShell 里 `npm` 被拦截时，为什么可以使用 `npm.cmd`？
- 前端为什么要先跑通 `GET /api/events`，再做抢票和支付？
- 抢票成功后为什么只是 `PENDING_PAYMENT`，还不能算已经出票？
- 支付成功后为什么要刷新订单状态并展示 `ticketCode`？
- 验票为什么要从后端检查 `VALID` 状态，而不能只靠前端按钮控制？
- 管理端为什么要通过 `X-Admin-Key` 查询用户订单和电子票？
- 抢票压测为什么要先看是否超卖，再看 QPS、P95 和 P99？
