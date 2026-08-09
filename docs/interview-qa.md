# EventRush 项目面试题库

这份题库围绕 EventRush 项目整理，目标不是背标准答案，而是训练你把项目讲成“问题 -> 设计 -> 实现 -> 验证”的结构。

## 1. 你这个项目是做什么的？

### 回答思路

先讲业务场景，再讲技术价值，不要一上来罗列技术栈。

### 参考回答

EventRush 是一个高并发活动票务后端项目，模拟演唱会、校园活动、讲座、赛事等场景下的抢票、支付、出票、验票和后台排查。它的核心价值不是普通 CRUD，而是围绕高并发抢票里的库存一致性、防重复抢票、订单状态流转、支付幂等、延时取消、消息消费幂等、接口统一响应和链路追踪做设计。

### 项目落点

- `README.md`
- `docs/api-reference.md`
- `src/main/java/com/eventrush/service/TicketingService.java`

## 2. EventRush 为什么不是普通 CRUD 项目？

### 回答思路

说明项目里有并发、状态、幂等、消息、缓存、限流和排查能力。

### 参考回答

普通 CRUD 主要是增删改查，而 EventRush 的核心是资源竞争和状态流转。抢票会涉及库存扣减和超卖问题，订单会涉及待支付、已支付、已取消的状态机，支付和验票要考虑重复请求，异步抢票和超时取消要考虑消息重复消费，后台接口还要有鉴权和 traceId 排查能力。

### 项目落点

- `src/test/java/com/eventrush/service/TicketingServiceTest.java`
- `src/test/java/com/eventrush/service/OrderTimeoutServiceTest.java`
- `src/test/java/com/eventrush/api/ApiResponseTest.java`

## 3. 抢票为什么不能直接查库存再扣库存？

### 回答思路

强调高并发下“先查再改”不是原子操作。

### 参考回答

如果多个请求同时查到库存大于 0，然后都继续扣减，就可能出现超卖。抢票接口要保证库存检查和库存扣减在同一个并发控制边界里完成。项目里基础版本通过同步控制和数据库唯一约束保证正确性，进阶版本通过 Redis Lua 把库存检查、用户重复检查、库存扣减和用户记录放在一个脚本里原子执行。

### 项目落点

- `src/main/java/com/eventrush/service/TicketingService.java`
- `src/main/java/com/eventrush/service/RedisTicketStockService.java`
- `src/main/resources/lua/grab-ticket.lua`

## 4. 你怎么防止超卖？

### 回答思路

先讲本地和数据库基础方案，再讲 Redis Lua 优化方案。

### 参考回答

项目里先保证基础正确性：内存版抢票通过同步逻辑控制库存，数据库版创建订单时配合库存扣减和重复订单保护。Redis 方案里使用 Lua 脚本，先读取库存，再判断用户是否已经抢过，再判断库存是否足够，最后执行扣减库存和记录用户。这一组操作在 Redis 单线程执行脚本的过程中是原子的，所以可以防止超卖。

### 项目落点

- `src/main/resources/lua/grab-ticket.lua`
- `src/test/java/com/eventrush/service/TicketingServiceTest.java`

## 5. Redis Lua 在项目里解决了什么问题？

### 回答思路

说明 Lua 不是为了炫技，而是为了把多步 Redis 操作合成原子操作。

### 参考回答

Redis Lua 主要解决两个问题：抢票库存扣减和限流。抢票时，如果分多次 Redis 调用完成查库存、查用户、扣库存、记录用户，中间可能被其他请求插入。Lua 脚本把这些动作合成一次原子执行。限流时，Lua 通过 `INCR` 和 `EXPIRE` 控制用户在固定窗口内的请求次数。

### 项目落点

- `src/main/resources/lua/grab-ticket.lua`
- `src/main/resources/lua/rate-limit.lua`
- `src/main/java/com/eventrush/service/RateLimitService.java`

## 6. 如何防止同一用户重复抢同一个票档？

### 回答思路

讲应用层检查、数据库唯一约束思路、Redis Set 思路。

### 参考回答

项目里同一用户同一票档只能有一张订单。基础实现会在订单创建前检查是否已有订单，持久化层也会防止重复创建。Redis 方案里会用 Set 记录已经抢过该场次票档的用户，Lua 脚本里先判断用户是否存在，存在就直接返回重复抢票错误。

### 项目落点

- `src/main/java/com/eventrush/service/TicketOrderRepository.java`
- `src/main/resources/lua/grab-ticket.lua`
- `src/test/java/com/eventrush/service/TicketingPersistenceTest.java`

## 7. 抢票成功后为什么不是直接出票？

### 回答思路

区分订单和电子票职责。

### 参考回答

抢票成功只代表用户拿到了一个待支付资格，还没有完成交易。如果直接出票，就会出现未支付也拥有入场凭证的问题。所以项目里抢票成功先生成 `PENDING_PAYMENT` 订单，用户支付成功后订单变为 `PAID`，再生成电子票。电子票才是入场凭证。

### 项目落点

- `src/main/java/com/eventrush/domain/TicketOrder.java`
- `src/main/java/com/eventrush/domain/ElectronicTicket.java`
- `src/main/java/com/eventrush/service/TicketingService.java`

## 8. 订单状态机怎么设计？

### 回答思路

讲三个状态和允许的状态流转。

### 参考回答

项目里订单状态有 `PENDING_PAYMENT`、`PAID`、`CANCELED`。抢票成功后进入待支付，支付成功后进入已支付，超时未支付则进入已取消。只有待支付订单能被支付，也只有待支付订单能被超时取消。这样可以避免已支付订单被取消，或者已取消订单又被支付。

### 项目落点

- `src/main/java/com/eventrush/domain/OrderStatus.java`
- `src/main/java/com/eventrush/service/TicketOrderRepository.java`
- `src/test/java/com/eventrush/service/OrderTimeoutServiceTest.java`

## 9. 未支付订单超时取消怎么做？

### 回答思路

讲 RocketMQ 延时消息和定时扫描兜底。

### 参考回答

订单创建后会发布一条超时取消延时消息。消息到期后消费者收到订单 ID，先查询订单当前状态，只有仍然是待支付才取消并释放库存。如果 RocketMQ 临时不可用，项目还有定时扫描作为兜底，定期扫描已经过期的待支付订单进行取消。

### 项目落点

- `src/main/java/com/eventrush/service/OrderTimeoutMessagePublisher.java`
- `src/main/java/com/eventrush/service/RocketOrderTimeoutConsumer.java`
- `src/main/java/com/eventrush/service/OrderTimeoutScheduler.java`
- `docs/stage-11-acceptance.md`

## 10. 延时消息到期后为什么不能直接取消订单？

### 回答思路

强调消息到达时订单状态可能已经变化。

### 参考回答

因为延时消息不是订单状态本身，只是一个提醒。消息到期时用户可能已经支付了，或者订单已经被其他补偿任务取消了。如果消费者拿到消息就直接取消，可能会把已支付订单误取消。所以消费者必须先查订单当前状态，只取消仍处于待支付的订单。

### 项目落点

- `src/main/java/com/eventrush/service/TicketingService.java`
- `src/test/java/com/eventrush/service/OrderTimeoutServiceTest.java`

## 11. 支付接口为什么要做幂等？

### 回答思路

讲网络重试、用户重复点击、前端超时重发。

### 参考回答

支付请求可能因为网络抖动、前端重复点击、调用方超时重试而重复到达。如果不做幂等，同一个订单可能生成多张电子票。项目里支付时先检查订单状态和是否已有电子票，如果订单已经支付过，就返回原来的电子票，保证一个订单只对应一张电子票。

### 项目落点

- `src/main/java/com/eventrush/service/TicketingService.java`
- `src/main/java/com/eventrush/service/ElectronicTicketRepository.java`
- `docs/stage-12-acceptance.md`

## 12. 电子票核验为什么要防重复？

### 回答思路

说明票是一次性入场凭证。

### 参考回答

电子票用于入场核验，一张票只能被使用一次。如果已核验的票还能再次核验，就会出现重复入场。项目里电子票状态有 `VALID` 和 `VERIFIED`，核验前先检查状态，只有 `VALID` 才允许核验，核验后状态变为 `VERIFIED`。

### 项目落点

- `src/main/java/com/eventrush/domain/TicketStatus.java`
- `src/main/java/com/eventrush/service/TicketingService.java`
- `src/test/java/com/eventrush/service/TicketingServiceTest.java`

## 13. RocketMQ 在项目里用在哪里？

### 回答思路

明确两处：异步抢票和订单超时。

### 参考回答

项目里 RocketMQ 用在两个地方。第一是异步抢票，接口先落库抢票请求并返回 `requestId`，消费者异步处理抢票，前端再查询处理结果。第二是订单超时取消，订单创建后发送延时消息，到期后消费者检查订单状态并取消待支付订单。

### 项目落点

- `src/main/java/com/eventrush/service/AsyncGrabService.java`
- `src/main/java/com/eventrush/service/RocketGrabConsumer.java`
- `src/main/java/com/eventrush/service/RocketOrderTimeoutConsumer.java`

## 14. MQ 消息重复消费怎么办？

### 回答思路

讲业务消费必须幂等，不依赖 MQ 只投一次。

### 参考回答

消息队列通常只能保证至少一次投递，所以消费者必须能处理重复消息。项目里异步抢票请求有持久化状态，消费者处理前会根据请求状态判断是否已经处理过；订单超时取消也会先查订单状态，只取消待支付订单。这样即使消息重复到达，也不会重复创建订单或重复取消订单。

### 项目落点

- `src/main/java/com/eventrush/service/AsyncGrabService.java`
- `src/main/java/com/eventrush/service/AsyncGrabRequestRepository.java`
- `src/test/java/com/eventrush/service/AsyncGrabServiceTest.java`

## 15. 为什么要保留定时扫描兜底？

### 回答思路

说明 MQ 不是绝对可靠，业务要有补偿思维。

### 参考回答

如果 RocketMQ 临时不可用，或者消息发送失败，仅靠延时消息可能导致订单一直处于待支付状态，库存无法释放。所以项目保留定时扫描超时订单作为兜底。即使 MQ 链路出问题，扫描任务也能最终取消过期订单并释放库存。

### 项目落点

- `src/main/java/com/eventrush/service/OrderTimeoutScheduler.java`
- `src/main/java/com/eventrush/service/TicketingService.java`

## 16. 统一响应格式有什么价值？

### 回答思路

讲前端处理、联调、错误排查。

### 参考回答

统一响应让前端和调用方不用针对每个接口写不同解析逻辑。项目里所有响应都包含 `success`、`code`、`message`、`data`、`traceId`。成功时 `code=OK`，业务错误和校验错误也有统一结构，排查时可以直接拿 `traceId` 去日志里定位请求。

### 项目落点

- `src/main/java/com/eventrush/api/ApiResponse.java`
- `src/main/java/com/eventrush/api/ApiResponseAdvice.java`
- `src/main/java/com/eventrush/api/ApiExceptionHandler.java`
- `docs/stage-13-acceptance.md`

## 17. traceId 是怎么做的？

### 回答思路

讲请求头、自动生成、响应头、响应体、日志。

### 参考回答

项目里通过 `TraceFilter` 处理 traceId。如果请求带了 `X-Trace-Id`，就沿用客户端传入的值；如果没带，就生成一个新的。traceId 会写入响应头、统一响应体和日志 MDC。这样一次请求出问题时，可以用同一个 traceId 关联接口响应和服务端日志。

### 项目落点

- `src/main/java/com/eventrush/api/TraceFilter.java`
- `src/main/java/com/eventrush/api/TraceContext.java`
- `src/test/java/com/eventrush/api/ApiResponseTest.java`

## 18. 管理端接口为什么要单独鉴权？

### 回答思路

强调后台接口即使只读也可能泄露敏感数据。

### 参考回答

管理端接口能按用户、订单、票码查询数据，虽然当前是只读接口，但仍然涉及用户订单和电子票信息，不能公开访问。项目里先用轻量的 `X-Admin-Key` 保护 `/api/admin/**`，后续可以替换成登录态、JWT 或 RBAC 权限模型。

### 项目落点

- `src/main/java/com/eventrush/api/AdminAuthFilter.java`
- `src/main/java/com/eventrush/api/AdminController.java`
- `docs/stage-16-acceptance.md`

## 19. 为什么管理端密钥要支持环境变量？

### 回答思路

讲敏感配置不要固定写死。

### 参考回答

如果管理端密钥固定写在配置文件里，代码推到 GitHub 后默认密钥就暴露了。项目里把密钥配置成 `${EVENTRUSH_ADMIN_KEY:eventrush-admin-key}`，本地学习时可以用默认值，部署时通过环境变量覆盖真实密钥，不需要改代码。

### 项目落点

- `src/main/resources/application.yml`
- `docs/stage-18-acceptance.md`

## 20. 这个项目目前还有哪些可以继续优化的点？

### 回答思路

不要说“已经很完美”，要能讲下一步。

### 参考回答

目前项目已经覆盖了核心后端场景，但还可以继续优化。比如把 H2 切到 MySQL 做更真实的事务验证，引入登录和 RBAC 替代简单管理密钥，补充 JMeter 压测脚本和压测报告，完善 Redis 和 RocketMQ 的真实联调环境，进一步做库存对账和消息消费记录表。

### 项目落点

- `EventRush_Project_Plan.md`
- `docs/learning-map.md`
- `scripts/pressure-grab.ps1`

## 快速背诵版

如果面试时间很短，可以按下面 6 句话讲：

1. EventRush 是一个高并发活动票务后端项目，不是普通 CRUD。
2. 抢票链路重点解决库存一致性、防超卖和防重复抢票。
3. 支付、验票和消息消费都做了幂等，避免重复请求导致重复出票或重复处理。
4. 订单超时取消通过 RocketMQ 延时消息触发，并用定时扫描兜底。
5. 延时消息到期后不会直接取消订单，而是先查订单当前状态，只取消待支付订单。
6. 接口层统一响应格式和 traceId，后台接口通过管理密钥保护。

## 复习建议

- 先看 `README.md`，知道项目整体怎么讲。
- 再看 `docs/learning-map.md`，按知识点找代码。
- 最后看本题库，练习把代码转成面试表达。
