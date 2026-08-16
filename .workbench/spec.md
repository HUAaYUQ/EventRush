---
name: EventRush 购票平台
domain: system
subject: 活动
purpose: 让购票用户在一个统一产品中完成找活动、选票档、核对购票人、支付和管理个人票务，不接触内部角色工具。
surface: desktop
structure: { primary: event_registry, secondary: ticket_operation }
moment: 用户想找活动、继续未完成交易或查看电子票时
dials: { cadence: 7, input: 4, depth: 6 }

roles:
  - { name: 购票用户, opens_daily: true, does: 浏览活动、选择场次与票档、维护购票人、支付、候补、查看电子票和处理售后 }
  - { name: 活动主办方, opens_daily: true, does: 下一阶段在独立产品管理活动、场次、票价、库存和通知 }
  - { name: 内部角色, opens_daily: false, does: 验票、运营排查和工程验收保留兼容地址，但不属于本阶段正式产品 }

surfaces:
  - { path: /, role: 购票用户, contains: [活动搜索, 售票状态筛选, 活动目录], excludes: [购票人表单, 支付, 退票, 验票, 工程证据] }
  - { path: /events/:eventId, role: 购票用户, contains: [活动内容, 场次, 票档, 库存或候补状态], excludes: [订单列表, 支付表单, 售后] }
  - { path: /checkout, role: 购票用户, contains: [当前结算步骤], excludes: [活动目录, 全部订单, 工程证据] }
  - { path: /account, role: 购票用户, contains: [状态导航, 订单或候补列表, 当前详情, 电子票, 售后], excludes: [活动目录, 验票, 管理排查, 压测] }
  - { path: /my, role: 购票用户, contains: [重定向到 /account], excludes: [独立页面内容] }
  - { path: /gate, role: 内部角色, contains: [兼容访问], excludes: [购票导航] }
  - { path: /ops, role: 内部角色, contains: [兼容访问], excludes: [购票导航] }
  - { path: /lab, role: 内部角色, contains: [兼容访问], excludes: [购票导航] }

hook:
  text: "本轮订单 {order.status} · 电子票 {ticket.status} · 最近失败请求可用 traceId 排查"
  cold_start: "本轮尚未创建订单 · 从车票预订开始"
  shape: state
  fields:
    - { name: order.status, reads: 订单已走到 PAID, writes: system, when: 支付接口返回订单状态后 }
    - { name: ticket.status, reads: 电子票 VERIFIED, writes: system, when: 验票接口返回电子票状态后 }
    - { name: requestRecords.latestFailed, reads: 最近失败请求可排查, writes: system, when: 前端统一请求函数记录失败响应后 }

cold_start:
  day_1: 先选择一个有库存的票档，完成一次抢票和支付，工作台就能生成本轮 orderId、ticketCode 和 traceId。
  day_2: 回看上一轮 orderId 和 ticketCode，再补一次重复验票失败记录，证明错误链路也可追踪。
  day_7: 对比本周压测记录，说明默认方案和 Redis Lua 方案在同参数下的正确性和性能差异。
  re_entry: 距上次验收已经隔了一段时间，直接重新跑一条新链路，不需要补旧数据。

home:
  - 发现活动：搜索活动名称与地点，只使用真实数据支持的售票中和可候补筛选
  - 活动卡片：本地海报、名称、下一场时间、地点、最低价格和可售状态
  - 待支付恢复：只用窄提示引导到我的票务，不在首页铺开订单详情

product_boundary:
  foreground: 正式购票导航只有发现活动和我的票务；主办方将在独立产品中建设。
  evidence: 验票、运营和工程证据本阶段不建设、不进入正式导航，只保留旧地址兼容。
  reference: 借鉴 12306 的任务分区、订单恢复和候补心智，借鉴 Vibe Hub 的搜索与目录关系，但不复制视觉和卡片墙。

channels:
  - name: 购票旅程
    type: today
    weight: primary
    does: 在目录找活动，在详情选场次和票档，在结算处理当前一步，在我的票务恢复交易和管理电子票
    pages:
      - { level: L1, shows: 活动目录和活动详情，不出现购票人、支付和售后, actions: [搜索活动, 筛选状态, 打开活动, 选择场次, 选择票档] }
      - { level: L2, shows: 当前结算步骤和状态化票务中心, actions: [核对购票人, 提交订单或候补, 支付出票, 恢复待支付, 查看电子票, 提交退票] }
  - name: 验票
    type: record
    weight: regular
    does: 输入票码和验票员 ID，核验电子票并展示重复验票错误
    pages:
      - { level: L1, shows: 票码输入、当前票状态、验票结果, actions: [查询电子票, 验票入场] }
  - name: 管理排查
    type: record
    weight: regular
    does: 携带 X-Admin-Key 按用户、订单、票码反查后台数据
    pages:
      - { level: L1, shows: 管理密钥输入和三类查询按钮, actions: [按用户查订单, 按订单查票, 按票码查票] }
      - { level: L2, shows: 用户订单列表或单张电子票详情, actions: [回填 ticketCode] }
  - name: 请求记录
    type: review
    weight: regular
    does: 回看最近 8 次接口调用的动作、路径、结果、code、traceId 和摘要
    pages:
      - { level: L1, shows: 横向表格，按时间倒序，失败请求高亮, actions: [复制 traceId] }
  - name: 压测证据
    type: review
    weight: regular
    does: 录入压测参数和结果，判断是否超卖，并对比默认方案和 Redis Lua 方案
    pages:
      - { level: L1, shows: 压测录入表单 + 是否超卖结论 + 两方案对比表, actions: [编辑数据] }
  - name: 设置
    type: knowledge
    weight: occasional
    does: 保存本地演示参数，例如默认管理端密钥和验票员 ID
    pages:
      - { level: L1, shows: 简单配置表单, actions: [保存本地参数] }

entities:
  - name: Event
    fields: [id, name, location, status]
    written_by: { id: system, name: system, location: system, status: system }
    relations: [Event 1-n Session]
  - name: Session
    fields: [id, event_id, start_time, end_time]
    written_by: { id: system, event_id: system, start_time: system, end_time: system }
    relations: [Session n-1 Event, Session 1-n TicketCategory]
  - name: TicketCategory
    fields: [id, session_id, name, remaining_stock]
    written_by: { id: system, session_id: system, name: system, remaining_stock: system }
    relations: [TicketCategory n-1 Session, TicketCategory 1-n TicketOrder]
  - name: TicketOrder
    fields: [id, user_id, session_id, ticket_category_id, quantity, unit_price_cents, amount_cents, refunded_quantity, refunded_amount_cents, status, refund_time, expire_time]
    written_by: { id: system, user_id: user, session_id: user, ticket_category_id: user, quantity: system, unit_price_cents: system, amount_cents: system, status: system, expire_time: system }
    relations: [TicketOrder 1-n TicketPassenger, TicketOrder 1-n ElectronicTicket]
  - name: TicketWaitlistRequest
    fields: [id, user_id, session_id, ticket_category_id, quantity, status, waiting_ahead, order_id, payment_expire_time]
    written_by: { id: system, user_id: user, session_id: user, ticket_category_id: user, quantity: system, status: system, waiting_ahead: system, order_id: system, payment_expire_time: system }
    relations: [TicketWaitlistRequest n-1 TicketCategory, TicketWaitlistRequest 1-n TicketPassenger, TicketWaitlistRequest 0-1 TicketOrder]
  - name: TicketPassenger
    fields: [id, order_id, sequence, name, document_type, document_last4]
    written_by: { id: system, order_id: system, sequence: system, name: user, document_type: user, document_last4: user }
    relations: [TicketPassenger n-1 TicketOrder, TicketPassenger 1-1 ElectronicTicket]
  - name: ElectronicTicket
    fields: [ticket_code, order_id, passenger_id, status, verifier_id, refunded_time]
    written_by: { ticket_code: system, order_id: system, passenger_id: system, status: system, verifier_id: user }
    relations: [ElectronicTicket n-1 TicketOrder, ElectronicTicket 1-1 TicketPassenger]
  - name: RequestRecord
    fields: [time, action, method, path, result, code, trace_id, summary]
    written_by: { time: system, action: system, method: system, path: system, result: system, code: system, trace_id: system, summary: system }
    relations: [RequestRecord n-1 TicketOrder]
  - name: PressureRun
    fields: [mode, users, stock, success, failed, qps, avg_ms, p95_ms, p99_ms, system_errors]
    written_by: { mode: user, users: user, stock: user, success: user, failed: user, qps: user, avg_ms: user, p95_ms: user, p99_ms: user, system_errors: user }
    relations: [PressureRun n-1 TicketCategory]

depends_on:
  - { field: 活动价格, source: TicketCategory.priceCents, exists_today: true, until_then: 缺失时显示票价待确认，不能显示 ¥0.00 }

mvp: [活动目录, 活动详情, 结算, 我的票务, 候补, 支付, 电子票, 退票]
later: [主办方中心, 座位图, 真实支付网关, 多角色登录]
visual: 冷灰白纸面、炭黑信息层级、朱红主操作；绿色只用于可售与成功。图片只承担活动识别，不做装饰。
seam: { type: transaction, why: 活动详情通过明确票档选择进入结算，不能在目录卡片里直接抢票。 }
excluded:
  - 消费者导航中的验票、运营和工程证据入口。
  - 伪造多个活动、推荐榜单或虚构折扣。
  - 把购票人、支付、电子票和退票同时铺在活动首页。
deferred:
  - 主办方中心：Stage 53 独立建设。
  - 验票和工程证据：产品主线稳定后再评估。
---

## 这些入口是给谁的

当前正式产品只服务购票用户。用户从同一个 EventRush 入口发现活动、完成交易并管理个人票务；主办方将在下一阶段拥有独立工作台。

## 每次怎么用

从 `/` 浏览活动，进入 `/events/:eventId` 选场次和票档，再到 `/checkout` 处理当前结算步骤。待支付、候补、电子票和售后统一在 `/account` 恢复和管理。

## 为什么是这几个模块

- 活动目录解决“找什么”，只展示真实可用的搜索和售票状态筛选。
- 活动详情解决“买哪场、哪档”，不承载购票人和支付。
- 结算解决“完成当前交易步骤”，一次只显示一个阶段。
- 我的票务解决“恢复和管理”，通过状态导航和主从布局处理单条记录。

## 已经想过但没做的

主办方中心、座位图、真实支付和多角色登录暂不进入本阶段。Stage 53 先建设活动主办方工作台，再评估其他能力。

## 给实现方

- 这是响应式 Web 购票产品，不是营销首页，也不是工程控制台。
- 顶部导航只允许出现“发现活动”和“我的票务”。
- 空数据时给出下一步动作，不显示空表、`--` 或假数据。
- 四条正式网址刷新后必须保持当前页面，不得退回首页。
- 页面按“找、选、买、管”拆分，禁止从上到下堆叠完整链路。
- `entities` 是数据来源说明，`written_by` 决定哪些字段来自用户输入，哪些来自系统响应。
- 第一版只做 `mvp`，`later` 内容不进入消费者导航。
- 视觉按 `visual`，卡片圆角不超过 8px，按钮触控高度至少 44px。
