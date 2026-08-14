---
name: EventRush 票务证据工作台
domain: system
subject: 票务订单
purpose: 让学习者和面试展示者用一页跑通抢票、支付、出票、验票、后台排查和压测证据。
surface: desktop
structure: { primary: pipeline, secondary: monitor }
moment: 每次阶段验收或面试演示前
dials: { cadence: 5, input: 4, depth: 8 }

roles:
  - { name: 学习者/演示者, opens_daily: true, does: 跑通一条订单链路，记录 orderId、ticketCode、traceId 和压测结论 }
  - { name: 验票人员, opens_daily: false, does: 输入票码，判断能否入场 }
  - { name: 管理排查者, opens_daily: false, does: 用 X-Admin-Key 反查用户订单、订单电子票和票码详情 }

hook:
  text: "本轮订单已走到 PAID · 电子票 VERIFIED · 最近失败请求可用 traceId 排查"
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
  - hook
  - 本轮验收摘要：userId、orderId、ticketCode、订单状态、电子票状态
  - 当前可操作链路：活动票档选择、抢票、支付、查票、验票
  - 最近请求记录：成功和失败请求按时间倒序展示
  - 压测证据摘要：是否超卖、QPS、P95、P99、系统异常数

channels:
  - name: 票务链路
    type: today
    weight: primary
    does: 选择票档、抢票、支付、查票、验票，把订单从创建走到入场核验
    pages:
      - { level: L1, shows: 操作流，按活动票档 -> 抢票 -> 支付 -> 电子票 -> 验票排列, actions: [加载活动, 同步抢票, 支付出票, 查询电子票, 验票入场] }
      - { level: L2, shows: 单个订单详情和对应电子票, actions: [刷新订单, 复制 ticketCode] }
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
    fields: [id, user_id, session_id, ticket_category_id, status, expire_time]
    written_by: { id: system, user_id: user, session_id: user, ticket_category_id: user, status: system, expire_time: system }
    relations: [TicketOrder 1-1 ElectronicTicket]
  - name: ElectronicTicket
    fields: [ticket_code, order_id, status, verifier_id]
    written_by: { ticket_code: system, order_id: system, status: system, verifier_id: user }
    relations: [ElectronicTicket n-1 TicketOrder]
  - name: RequestRecord
    fields: [time, action, method, path, result, code, trace_id, summary]
    written_by: { time: system, action: system, method: system, path: system, result: system, code: system, trace_id: system, summary: system }
    relations: [RequestRecord n-1 TicketOrder]
  - name: PressureRun
    fields: [mode, users, stock, success, failed, qps, avg_ms, p95_ms, p99_ms, system_errors]
    written_by: { mode: user, users: user, stock: user, success: user, failed: user, qps: user, avg_ms: user, p95_ms: user, p99_ms: user, system_errors: user }
    relations: [PressureRun n-1 TicketCategory]

depends_on:
  - { field: 压测历史自动采集, source: 后端压测报告接口, exists_today: false, until_then: 前端保留手动录入，不在首屏伪造历史趋势 }

mvp: [票务链路, 验票, 管理排查, 请求记录, 压测证据]
later: [设置, 异步抢票结果, 历史压测报告]
visual: 浅色桌面工作台，信息密度高，状态和证据优先，少量绿色用于成功与当前选择，红色只用于失败和风险。
seam: { type: none, why: 这是学习和面试展示项目，页面内不做商业转化。 }
excluded:
  - 营销首页：当前重点是验收链路和工程证据。
  - 复杂登录权限：当前使用视图区分和 X-Admin-Key 足够。
  - 座位图和真实支付网关：会冲淡高并发票务主线。
deferred:
  - 监控大屏：等后端有指标采集接口后再做。
  - 多角色登录：等管理端权限升级阶段再做。
---

## 这个台子是给谁的

这是给学习者和面试展示者用的桌面 Web 工作台。它围着“票务订单”转，因为一条可验收链路最终要落到 `orderId`、`ticketCode`、订单状态、电子票状态和 `traceId` 上。

## 每次怎么用

每次阶段验收或面试演示前，先选择一个有库存的票档，然后依次完成抢票、支付、查票、验票，再用管理端反查同一条链路。最后回看最近请求记录和压测证据，说明项目不是只会跑接口，而是有业务状态、错误边界和排查线索。

## 为什么是这几个模块

- 票务链路负责把主流程跑通，是默认落地点。
- 验票负责证明电子票不是静态凭证，而是有入场状态变化。
- 管理排查负责从后台视角验证数据真实存在。
- 请求记录负责把统一响应、错误码和 `traceId` 变成可见证据。
- 压测证据负责把“没有超卖”和“性能对比”讲清楚。

## 已经想过但没做的

登录权限、座位图、真实支付、监控大屏都先不做。它们不是没价值，而是会抢走当前阶段最重要的目标：把高并发票务链路、排查能力和证据展示做扎实。

## 给实现方

- 这是电脑浏览器里的 Web 工作台，不是 App，也不是营销首页。
- 首屏按 `home` 顺序排，第一行是 `hook.text`，不要缩成普通卡片标题。
- 空数据时显示 `cold_start.day_1`，不要显示空表、`--` 或假数据。
- 左侧或顶部导航来自 `channels`，`weight: primary` 的“票务链路”是默认落地页。
- 每个模块按 `pages` 建，`shows` 决定屏幕形态。不要把所有模块都做成一样的表格。
- `entities` 是数据来源说明，`written_by` 决定哪些字段来自用户输入，哪些来自系统响应。
- `depends_on` 里的自动压测历史目前没有后端接口，不要在首屏假装已有历史趋势。
- 第一版只做 `mvp` 里的模块，`later` 里的内容等下一阶段再排。
- 视觉按 `visual`：浅色、克制、高密度，状态清楚，失败和风险要比装饰更醒目。
