<script setup>
import { computed, onMounted, ref } from 'vue'

const events = ref([])
const loading = ref(false)
const error = ref('')
const traceId = ref('')
const selectedSessionId = ref(null)
const selectedTicketCategoryId = ref(null)
const userId = ref(Math.floor(10000 + Math.random() * 90000))
const grabLoading = ref(false)
const grabError = ref('')
const grabTraceId = ref('')
const order = ref(null)
const payLoading = ref(false)
const payError = ref('')
const payTraceId = ref('')
const ticket = ref(null)
const ticketLookupCode = ref('')
const ticketLookupLoading = ref(false)
const ticketLookupError = ref('')
const ticketLookupTraceId = ref('')
const verifierId = ref(7001)
const verifyLoading = ref(false)
const verifyError = ref('')
const verifyTraceId = ref('')
const adminKey = ref('eventrush-admin-key')
const adminLoading = ref('')
const adminError = ref('')
const adminOrdersTraceId = ref('')
const adminTicketByOrderTraceId = ref('')
const adminTicketByCodeTraceId = ref('')
const requestRecords = ref([])
const adminOrders = ref([])
const adminTicketByOrder = ref(null)
const adminTicketByCode = ref(null)
const pressureMode = ref('默认 H2/本地基线')
const pressureUsers = ref(40)
const pressureStock = ref(20)
const pressureSuccess = ref(20)
const pressureFailed = ref(20)
const pressureQps = ref(0)
const pressureAvgMs = ref(0)
const pressureP95Ms = ref(0)
const pressureP99Ms = ref(0)
const pressureSystemErrors = ref(0)
const pressureComparisons = ref([
  {
    mode: '默认 H2/本地基线',
    users: 40,
    stock: 20,
    success: 20,
    failed: 20,
    qps: 0,
    p95Ms: 0,
    p99Ms: 0,
    systemErrors: 0,
  },
  {
    mode: 'Redis Lua 库存',
    users: 40,
    stock: 20,
    success: 20,
    failed: 20,
    qps: 0,
    p95Ms: 0,
    p99Ms: 0,
    systemErrors: 0,
  },
])

const summary = computed(() => {
  const sessions = events.value.flatMap((event) => event.sessions ?? [])
  const categories = sessions.flatMap((session) => session.ticketCategories ?? [])
  const remainingStock = categories.reduce((sum, category) => sum + (category.remainingStock ?? 0), 0)

  return {
    events: events.value.length,
    sessions: sessions.length,
    categories: categories.length,
    remainingStock,
  }
})

const ticketOptions = computed(() =>
  events.value.flatMap((event) =>
    (event.sessions ?? []).flatMap((session) =>
      (session.ticketCategories ?? []).map((category) => ({
        event,
        session,
        category,
        key: `${session.id}-${category.id}`,
      })),
    ),
  ),
)

const selectedTicket = computed(() =>
  ticketOptions.value.find(
    (option) =>
      option.session.id === selectedSessionId.value &&
      option.category.id === selectedTicketCategoryId.value,
  ),
)

const pressureSuccessRate = computed(() => {
  const total = Number(pressureSuccess.value) + Number(pressureFailed.value)
  if (total === 0) {
    return '0.00%'
  }

  return `${((Number(pressureSuccess.value) / total) * 100).toFixed(2)}%`
})

const pressureOversold = computed(
  () => Number(pressureSuccess.value) > Number(pressureStock.value),
)

const pressurePassed = computed(
  () => !pressureOversold.value && Number(pressureSystemErrors.value) === 0,
)

const pressureComparisonRows = computed(() =>
  pressureComparisons.value.map((item) => {
    const oversold = Number(item.success) > Number(item.stock)
    const passed = !oversold && Number(item.systemErrors) === 0

    return {
      ...item,
      oversold,
      passed,
    }
  }),
)

const pressureComparisonConclusion = computed(() => {
  const rows = pressureComparisonRows.value
  const failedRow = rows.find((row) => !row.passed)

  if (failedRow) {
    return `${failedRow.mode} 存在超卖或系统异常，本轮对比不能作为有效优化结论。`
  }

  const [baseline, redisLua] = rows
  if (!baseline || !redisLua) {
    return '请补齐两组压测数据后再判断。'
  }

  if (Number(redisLua.qps) > Number(baseline.qps) && Number(redisLua.p95Ms) <= Number(baseline.p95Ms)) {
    return 'Redis Lua 方案在当前记录中 QPS 更高且 P95 没有变差，可以作为优化候选证据。'
  }

  return '两组方案都未超卖且无系统异常，但性能优势还不明显，需要结合更多轮压测判断。'
})

const hookText = computed(() => {
  if (!order.value) {
    return '本轮尚未创建订单 · 从车票预订开始'
  }

  if (order.value.status !== 'PAID') {
    return `本轮订单 ${order.value.status} · 等待支付出票`
  }

  if (!ticket.value) {
    return '本轮订单 PAID · 等待查询电子票'
  }

  const failureHint = requestRecords.value.some((record) => record.result !== '成功')
    ? ' · 最近失败请求可用 traceId 排查'
    : ''
  return `本轮订单 PAID · 电子票 ${ticket.value.status}${failureHint}`
})
const coldStartDayOne =
  '先选择一个有库存的票档，完成一次抢票和支付，工作台就能生成本轮 orderId、ticketCode 和 traceId。'
const copiedTraceId = ref('')
const activeView = ref('booking')

const productTabs = [
  { key: 'booking', label: '车票预订', detail: '查票档、下单、支付' },
  { key: 'tickets', label: '我的电子票', detail: '查票与订单状态' },
  { key: 'gate', label: '验票入口', detail: '入场核验' },
  { key: 'evidence', label: '工程证据', detail: '排查与压测' },
]

const latestRequestRecord = computed(() => requestRecords.value[0] ?? null)
const latestFailedRequest = computed(() =>
  requestRecords.value.find((record) => record.result !== '成功'),
)

const currentTicketCode = computed(
  () => ticket.value?.ticketCode ?? adminTicketByOrder.value?.ticketCode ?? ticketLookupCode.value,
)

const acceptanceSummary = computed(() => [
  {
    label: 'userId',
    value: userId.value,
    detail: '本轮演示用户',
    tone: 'ready',
  },
  {
    label: 'orderId',
    value: order.value?.id ?? '待抢票',
    detail: order.value ? '订单已生成' : '同步抢票后写入',
    tone: order.value ? 'ready' : 'pending',
  },
  {
    label: 'ticketCode',
    value: currentTicketCode.value || '待出票',
    detail: currentTicketCode.value ? '电子票凭证' : '支付出票后写入',
    tone: currentTicketCode.value ? 'ready' : 'pending',
  },
  {
    label: '订单状态',
    value: order.value?.status ?? '未创建',
    detail: order.value ? '来自订单接口' : '等待抢票',
    tone: order.value?.status === 'PAID' ? 'ready' : 'pending',
  },
  {
    label: '电子票状态',
    value: ticket.value?.status ?? '未出票',
    detail: ticket.value ? '来自查票/验票接口' : '等待支付',
    tone: ticket.value?.status === 'VERIFIED' ? 'ready' : 'pending',
  },
  {
    label: '失败 traceId',
    value: latestFailedRequest.value?.traceId || '暂无失败',
    detail: latestFailedRequest.value?.action ?? '失败请求会在这里出现',
    tone: latestFailedRequest.value ? 'danger' : 'quiet',
  },
])

const pipelineSteps = computed(() => [
  {
    name: '选票档',
    detail: selectedTicket.value
      ? `${selectedTicket.value.category.name} · 余 ${selectedTicket.value.category.remainingStock}`
      : '选择有库存票档',
    done: Boolean(selectedTicket.value),
  },
  {
    name: '抢票',
    detail: order.value ? `orderId ${order.value.id}` : '生成订单',
    done: Boolean(order.value),
  },
  {
    name: '支付',
    detail: ticket.value ? `ticketCode ${ticket.value.ticketCode}` : '支付后出票',
    done: Boolean(ticket.value),
  },
  {
    name: '查票',
    detail: ticketLookupTraceId.value ? '已记录 traceId' : '回看电子票',
    done: Boolean(ticketLookupTraceId.value || ticket.value),
  },
  {
    name: '验票',
    detail: ticket.value?.status === 'VERIFIED' ? '已核验入场' : '验证入场状态',
    done: ticket.value?.status === 'VERIFIED',
  },
])

const pressureHeroItems = computed(() => [
  { label: '是否超卖', value: pressureOversold.value ? '是' : '否', danger: pressureOversold.value },
  { label: 'QPS', value: pressureQps.value || 0 },
  { label: 'P95', value: `${pressureP95Ms.value || 0} ms` },
  { label: 'P99', value: `${pressureP99Ms.value || 0} ms` },
  { label: '系统异常', value: pressureSystemErrors.value || 0, danger: Number(pressureSystemErrors.value) > 0 },
])

function addRequestRecord(record) {
  requestRecords.value = [
    {
      id: `${Date.now()}-${Math.random()}`,
      time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
      ...record,
    },
    ...requestRecords.value,
  ].slice(0, 8)
}

async function requestJson(action, method, path, options = {}, summarize = () => '') {
  let payload = null

  try {
    const response = await fetch(path, {
      ...options,
      method,
    })
    payload = await response.json()
    const nextTraceId = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''
    const success = response.ok && payload.success !== false

    addRequestRecord({
      action,
      method,
      path,
      result: success ? '成功' : '失败',
      code: payload.code ?? (success ? 'OK' : String(response.status)),
      traceId: nextTraceId,
      summary: success ? summarize(payload.data) : payload.message,
    })

    if (!success) {
      throw new Error(payload.message || `${action}失败`)
    }

    return { payload, traceId: nextTraceId }
  } catch (caught) {
    if (!payload) {
      addRequestRecord({
        action,
        method,
        path,
        result: '失败',
        code: 'NETWORK_ERROR',
        traceId: '',
        summary: caught instanceof Error ? caught.message : `${action}失败`,
      })
    }

    throw caught
  }
}

function selectTicket(sessionId, ticketCategoryId) {
  selectedSessionId.value = sessionId
  selectedTicketCategoryId.value = ticketCategoryId
  grabError.value = ''
  payError.value = ''
  ticketLookupError.value = ''
  verifyError.value = ''
  adminError.value = ''
}

function selectFirstTicketIfNeeded() {
  if (selectedTicket.value || ticketOptions.value.length === 0) {
    return
  }

  const firstAvailable = ticketOptions.value.find((option) => option.category.remainingStock > 0)
  const fallback = firstAvailable ?? ticketOptions.value[0]
  selectTicket(fallback.session.id, fallback.category.id)
}

async function loadEvents() {
  loading.value = true
  error.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '加载活动',
      'GET',
      '/api/events',
      {},
      (data) => `活动 ${data?.length ?? 0} 个`,
    )
    traceId.value = nextTraceId

    events.value = payload.data ?? []
    selectFirstTicketIfNeeded()
  } catch (caught) {
    events.value = []
    traceId.value = ''
    error.value = caught instanceof Error ? caught.message : '活动列表加载失败'
  } finally {
    loading.value = false
  }
}

async function grabTicket() {
  if (!selectedTicket.value) {
    grabError.value = '请先选择票档'
    return
  }

  grabLoading.value = true
  grabError.value = ''
  grabTraceId.value = ''
  order.value = null
  ticket.value = null
  ticketLookupCode.value = ''
  payError.value = ''
  payTraceId.value = ''
  ticketLookupError.value = ''
  ticketLookupTraceId.value = ''
  verifyError.value = ''
  verifyTraceId.value = ''
  adminError.value = ''
  adminOrders.value = []
  adminTicketByOrder.value = null
  adminTicketByCode.value = null

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '同步抢票',
      'POST',
      '/api/orders/grab',
      {
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          userId: Number(userId.value),
          sessionId: selectedSessionId.value,
          ticketCategoryId: selectedTicketCategoryId.value,
        }),
      },
      (data) => `orderId=${data.id} ${data.status}`,
    )
    grabTraceId.value = nextTraceId

    order.value = payload.data
    await loadEvents()
  } catch (caught) {
    grabError.value = caught instanceof Error ? caught.message : '抢票失败'
  } finally {
    grabLoading.value = false
  }
}

function adminHeaders() {
  return {
    'X-Admin-Key': adminKey.value.trim(),
  }
}

async function loadAdminOrders() {
  adminLoading.value = 'orders'
  adminError.value = ''
  adminOrdersTraceId.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '按用户查订单',
      'GET',
      `/api/admin/users/${Number(userId.value)}/orders`,
      { headers: adminHeaders() },
      (data) => `订单 ${data?.length ?? 0} 条`,
    )
    adminOrdersTraceId.value = nextTraceId

    adminOrders.value = payload.data ?? []
  } catch (caught) {
    adminError.value = caught instanceof Error ? caught.message : '用户订单查询失败'
  } finally {
    adminLoading.value = ''
  }
}

async function loadAdminTicketByOrder() {
  if (!order.value) {
    adminError.value = '请先完成抢票或支付，拿到 orderId'
    return
  }

  adminLoading.value = 'orderTicket'
  adminError.value = ''
  adminTicketByOrderTraceId.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '按订单查票',
      'GET',
      `/api/admin/orders/${order.value.id}/ticket`,
      { headers: adminHeaders() },
      (data) => `ticketCode=${data.ticketCode}`,
    )
    adminTicketByOrderTraceId.value = nextTraceId

    adminTicketByOrder.value = payload.data
  } catch (caught) {
    adminError.value = caught instanceof Error ? caught.message : '订单电子票查询失败'
  } finally {
    adminLoading.value = ''
  }
}

async function loadAdminTicketByCode() {
  const code = ticketLookupCode.value.trim()

  if (!code) {
    adminError.value = '请先完成支付或输入 ticketCode'
    return
  }

  adminLoading.value = 'codeTicket'
  adminError.value = ''
  adminTicketByCodeTraceId.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '按票码查票',
      'GET',
      `/api/admin/tickets/${encodeURIComponent(code)}`,
      { headers: adminHeaders() },
      (data) => `${data.ticketCode} ${data.status}`,
    )
    adminTicketByCodeTraceId.value = nextTraceId

    adminTicketByCode.value = payload.data
  } catch (caught) {
    adminError.value = caught instanceof Error ? caught.message : '票码查询失败'
  } finally {
    adminLoading.value = ''
  }
}

async function refreshOrder(orderId) {
  const { payload } = await requestJson(
    '刷新订单',
    'GET',
    `/api/orders/${orderId}`,
    {},
    (data) => `orderId=${data.id} ${data.status}`,
  )

  order.value = payload.data
}

async function payOrder() {
  if (!order.value) {
    payError.value = '请先完成抢票'
    return
  }

  payLoading.value = true
  payError.value = ''
  payTraceId.value = ''
  ticket.value = null
  ticketLookupError.value = ''
  verifyError.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '支付出票',
      'POST',
      `/api/orders/${order.value.id}/pay`,
      {},
      (data) => `ticketCode=${data.ticketCode}`,
    )
    payTraceId.value = nextTraceId

    ticket.value = payload.data
    ticketLookupCode.value = ticket.value.ticketCode
    await refreshOrder(order.value.id)
  } catch (caught) {
    payError.value = caught instanceof Error ? caught.message : '支付失败'
  } finally {
    payLoading.value = false
  }
}

async function lookupTicket() {
  const code = ticketLookupCode.value.trim()

  if (!code) {
    ticketLookupError.value = '请先输入 ticketCode'
    return
  }

  ticketLookupLoading.value = true
  ticketLookupError.value = ''
  ticketLookupTraceId.value = ''
  verifyError.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '查询电子票',
      'GET',
      `/api/tickets/${encodeURIComponent(code)}`,
      {},
      (data) => `${data.ticketCode} ${data.status}`,
    )
    ticketLookupTraceId.value = nextTraceId

    ticket.value = payload.data
    ticketLookupCode.value = ticket.value.ticketCode
  } catch (caught) {
    ticketLookupError.value = caught instanceof Error ? caught.message : '电子票查询失败'
  } finally {
    ticketLookupLoading.value = false
  }
}

async function verifyTicket() {
  const code = ticketLookupCode.value.trim()

  if (!code) {
    verifyError.value = '请先输入 ticketCode'
    return
  }

  verifyLoading.value = true
  verifyError.value = ''
  verifyTraceId.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '验票入场',
      'POST',
      '/api/tickets/verify',
      {
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ticketCode: code,
          verifierId: Number(verifierId.value),
        }),
      },
      (data) => `${data.ticketCode} ${data.status}`,
    )
    verifyTraceId.value = nextTraceId

    ticket.value = payload.data
    ticketLookupCode.value = ticket.value.ticketCode
  } catch (caught) {
    verifyError.value = caught instanceof Error ? caught.message : '验票失败'
  } finally {
    verifyLoading.value = false
  }
}

async function copyTraceId(nextTraceId) {
  if (!nextTraceId) {
    copiedTraceId.value = '这条记录没有 traceId'
    window.setTimeout(() => {
      copiedTraceId.value = ''
    }, 1800)
    return
  }

  try {
    await navigator.clipboard.writeText(nextTraceId)
    copiedTraceId.value = 'traceId 已复制'
  } catch {
    copiedTraceId.value = '浏览器不允许自动复制，请手动选择 traceId'
  }

  window.setTimeout(() => {
    copiedTraceId.value = ''
  }, 1800)
}

onMounted(loadEvents)
</script>

<template>
  <main class="shell">
    <section class="topbar">
      <div class="brand">
        <img src="/favicon.svg" alt="" class="brand-mark" />
        <div>
          <p class="eyebrow">EventRush</p>
          <h1>活动票务预订</h1>
        </div>
      </div>
      <button type="button" class="reload-button" :disabled="loading" @click="loadEvents">
        {{ loading ? '加载中' : '重新加载' }}
      </button>
    </section>

    <nav class="product-nav" aria-label="产品导航">
      <button
        v-for="tab in productTabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeView === tab.key }"
        :aria-current="activeView === tab.key ? 'page' : undefined"
        @click="activeView = tab.key"
      >
        <strong>{{ tab.label }}</strong>
        <span>{{ tab.detail }}</span>
      </button>
    </nav>

    <section v-if="activeView === 'booking'" class="customer-hero" aria-label="车票预订首页">
      <div>
        <p class="eyebrow">购票流程</p>
        <h2>选择活动票档，完成下单和出票</h2>
        <p>选择场次和票档，提交订单后完成支付，电子票会自动进入“我的电子票”。</p>
      </div>
      <aside class="booking-side">
        <article class="booking-status">
          <span>当前订单</span>
          <strong>{{ order ? `#${order.id} · ${order.status}` : '尚未下单' }}</strong>
          <p>{{ ticket ? `电子票 ${ticket.ticketCode} · ${ticket.status}` : '支付后生成电子票' }}</p>
        </article>

        <article class="boundary-card">
          <span>后续服务</span>
          <div>
            <strong>退票</strong>
            <strong>通知</strong>
            <strong>主办方入口</strong>
          </div>
          <p>这些入口会进入后续阶段，不挤占当前主流程。</p>
        </article>
      </aside>
    </section>

    <section v-if="activeView === 'evidence'" class="hook-strip" aria-label="本轮状态">
      <p class="hook-text">{{ hookText }}</p>
      <span class="hook-badge">state</span>
    </section>

    <section v-if="activeView === 'evidence'" class="home-hero" aria-label="本轮验收摘要">
      <div class="hero-layout">
        <article class="summary-panel">
          <div class="panel-title-row">
            <div>
              <p class="eyebrow">本轮验收摘要</p>
              <h2>把 orderId、ticketCode、状态和 traceId 放在第一屏</h2>
            </div>
            <span class="result-badge" :class="{ danger: !order || ticket?.status !== 'VERIFIED' }">
              {{ order && ticket?.status === 'VERIFIED' ? '链路完成' : '待补齐' }}
            </span>
          </div>

          <p v-if="!order && requestRecords.length === 0" class="cold-start">
            {{ coldStartDayOne }}
          </p>

          <div class="acceptance-grid">
            <article
              v-for="item in acceptanceSummary"
              :key="item.label"
              class="evidence-card"
              :class="item.tone"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.detail }}</small>
            </article>
          </div>
        </article>

        <aside class="chain-panel">
          <p class="eyebrow">当前可操作链路</p>
          <div class="pipeline-list">
            <article
              v-for="step in pipelineSteps"
              :key="step.name"
              class="pipeline-step"
              :class="{ done: step.done }"
            >
              <span class="step-dot"></span>
              <div>
                <strong>{{ step.name }}</strong>
                <p>{{ step.detail }}</p>
              </div>
            </article>
          </div>
        </aside>
      </div>

      <div class="home-metrics" aria-label="活动和压测概览">
        <article>
          <span>活动</span>
          <strong>{{ summary.events }}</strong>
        </article>
        <article>
          <span>场次</span>
          <strong>{{ summary.sessions }}</strong>
        </article>
        <article>
          <span>票档</span>
          <strong>{{ summary.categories }}</strong>
        </article>
        <article>
          <span>剩余库存</span>
          <strong>{{ summary.remainingStock }}</strong>
        </article>
        <article
          v-for="item in pressureHeroItems"
          :key="item.label"
          :class="{ danger: item.danger }"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>
    </section>

    <section v-if="activeView === 'booking'" class="panel booking-panel">
      <div class="panel-header">
        <div>
          <h2>选择活动和票档</h2>
        </div>
      </div>

      <p v-if="loading" class="hint">正在通过 Vite 代理请求后端活动接口...</p>
      <p v-else-if="error" class="error">{{ error }}。请确认后端服务运行在 18086 端口。</p>
      <div v-else-if="events.length === 0" class="empty">暂无活动数据。</div>
      <div v-else class="event-list">
        <article v-for="event in events" :key="event.id" class="event-row">
          <div class="event-main">
            <p class="event-name">{{ event.name }}</p>
            <p class="event-meta">ID {{ event.id }} · {{ event.location }} · {{ event.status }}</p>
          </div>
          <div class="session-list">
            <div v-for="session in event.sessions" :key="session.id" class="session-row">
              <div>
                <p class="session-title">场次 {{ session.id }}</p>
                <p class="event-meta">{{ session.startTime }} 至 {{ session.endTime }}</p>
              </div>
              <div class="ticket-list">
                <button
                  v-for="category in session.ticketCategories"
                  :key="category.id"
                  type="button"
                  class="ticket-chip"
                  :class="{
                    selected:
                      selectedSessionId === session.id &&
                      selectedTicketCategoryId === category.id,
                  }"
                  @click="selectTicket(session.id, category.id)"
                >
                  {{ category.name }} · 余 {{ category.remainingStock }}
                </button>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section v-if="activeView === 'booking'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>提交订单</h2>
        </div>
      </div>

      <div class="grab-layout">
        <div class="form-grid">
          <label>
            <span>用户 ID</span>
            <input v-model.number="userId" type="number" min="1" />
          </label>
          <label>
            <span>场次 ID</span>
            <input :value="selectedSessionId ?? ''" readonly />
          </label>
          <label>
            <span>票档 ID</span>
            <input :value="selectedTicketCategoryId ?? ''" readonly />
          </label>
          <button
            type="button"
            class="primary-action"
            :disabled="grabLoading || !selectedTicket"
            @click="grabTicket"
          >
            {{ grabLoading ? '抢票中' : '同步抢票' }}
          </button>
        </div>

        <div class="selected-box">
          <p class="box-title">当前选择</p>
          <template v-if="selectedTicket">
            <p>{{ selectedTicket.event.name }}</p>
            <p class="event-meta">
              场次 {{ selectedTicket.session.id }} · {{ selectedTicket.category.name }} · 剩余
              {{ selectedTicket.category.remainingStock }}
            </p>
          </template>
          <p v-else class="event-meta">暂无可选票档。</p>
        </div>
      </div>

      <p v-if="grabError" class="error">{{ grabError }}</p>
      <div v-if="order" class="order-result">
        <p class="box-title">抢票结果</p>
        <div class="result-grid">
          <span>orderId</span>
          <strong>{{ order.id }}</strong>
          <span>订单状态</span>
          <strong>{{ order.status }}</strong>
          <span>支付截止</span>
          <strong>{{ order.expireTime }}</strong>
        </div>
      </div>
    </section>

    <section v-if="activeView === 'booking'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>支付并出票</h2>
        </div>
      </div>

      <div class="pay-layout">
        <div class="selected-box">
          <p class="box-title">当前订单</p>
          <template v-if="order">
            <p>订单 {{ order.id }}</p>
            <p class="event-meta">状态 {{ order.status }} · 用户 {{ order.userId }}</p>
          </template>
          <p v-else class="event-meta">请先完成同步抢票。</p>
        </div>

        <button
          type="button"
          class="primary-action"
          :disabled="payLoading || !order || order.status === 'CANCELED'"
          @click="payOrder"
        >
          {{ payLoading ? '支付中' : '支付并出票' }}
        </button>
      </div>

      <p v-if="payError" class="error">{{ payError }}</p>
      <div v-if="ticket" class="ticket-result">
        <p class="box-title">电子票</p>
        <div class="result-grid">
          <span>ticketCode</span>
          <strong>{{ ticket.ticketCode }}</strong>
          <span>票状态</span>
          <strong>{{ ticket.status }}</strong>
          <span>orderId</span>
          <strong>{{ ticket.orderId }}</strong>
        </div>
      </div>
    </section>

    <section v-if="activeView === 'tickets' || activeView === 'gate'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>{{ activeView === 'gate' ? '入场验票' : '查询电子票' }}</h2>
        </div>
      </div>

      <div class="verify-layout">
        <label class="code-field">
          <span>ticketCode</span>
          <input
            v-model.trim="ticketLookupCode"
            type="text"
            placeholder="支付后会自动带出票码，也可以手动粘贴"
          />
        </label>
        <label v-if="activeView === 'gate'">
          <span>验票员 ID</span>
          <input v-model.number="verifierId" type="number" min="1" />
        </label>
        <button
          type="button"
          class="secondary-action"
          :disabled="ticketLookupLoading || !ticketLookupCode"
          @click="lookupTicket"
        >
          {{ ticketLookupLoading ? '查询中' : '查询电子票' }}
        </button>
        <button
          v-if="activeView === 'gate'"
          type="button"
          class="primary-action"
          :disabled="verifyLoading || !ticketLookupCode"
          @click="verifyTicket"
        >
          {{ verifyLoading ? '验票中' : '验票入场' }}
        </button>
      </div>

      <p v-if="ticketLookupError" class="error">{{ ticketLookupError }}</p>
      <p v-if="verifyError" class="error">{{ verifyError }}</p>
      <div v-if="ticket" class="ticket-result verified-result">
        <p class="box-title">当前电子票</p>
        <div class="result-grid">
          <span>ticketCode</span>
          <strong>{{ ticket.ticketCode }}</strong>
          <span>票状态</span>
          <strong>{{ ticket.status }}</strong>
          <span>orderId</span>
          <strong>{{ ticket.orderId }}</strong>
          <span>验票员</span>
          <strong>{{ ticket.verifierId ?? '未核验' }}</strong>
        </div>
      </div>
    </section>

    <section v-if="activeView === 'evidence'" class="panel action-panel request-panel">
      <div class="panel-header">
        <div>
          <h2>最近请求记录</h2>
        </div>
        <p v-if="copiedTraceId" class="copy-feedback">{{ copiedTraceId }}</p>
      </div>

      <div class="request-overview">
        <article class="latest-request-card">
          <span>最新请求</span>
          <template v-if="latestRequestRecord">
            <strong>{{ latestRequestRecord.action }}</strong>
            <p>
              {{ latestRequestRecord.time }} · {{ latestRequestRecord.result }} ·
              {{ latestRequestRecord.code }}
            </p>
          </template>
          <p v-else>{{ coldStartDayOne }}</p>
        </article>

        <article class="latest-request-card failed">
          <span>最近失败</span>
          <template v-if="latestFailedRequest">
            <strong>{{ latestFailedRequest.action }}</strong>
            <p>{{ latestFailedRequest.summary || latestFailedRequest.code }}</p>
            <button
              type="button"
              class="trace-copy"
              :disabled="!latestFailedRequest.traceId"
              @click="copyTraceId(latestFailedRequest.traceId)"
            >
              复制 traceId
            </button>
          </template>
          <p v-else>暂无失败请求。出现错误时会优先显示 traceId。</p>
        </article>
      </div>

      <div v-if="requestRecords.length === 0" class="empty request-empty">
        {{ coldStartDayOne }}
      </div>
      <div v-else class="request-list">
        <div class="request-head">
          <span>时间</span>
          <span>动作</span>
          <span>接口</span>
          <span>结果</span>
          <span>code</span>
          <span>traceId</span>
          <span>摘要</span>
        </div>
        <div
          v-for="record in requestRecords"
          :key="record.id"
          class="request-row"
          :class="{ failed: record.result !== '成功' }"
        >
          <span class="request-time">{{ record.time }}</span>
          <strong>{{ record.action }}</strong>
          <span class="request-path">
            <b>{{ record.method }}</b>
            {{ record.path }}
          </span>
          <span :class="['request-result', { failed: record.result !== '成功' }]">
            {{ record.result }}
          </span>
          <span class="request-code">{{ record.code }}</span>
          <span class="trace-cell">
            {{ record.traceId || '无 traceId' }}
            <button
              v-if="record.traceId"
              type="button"
              class="trace-copy compact"
              @click="copyTraceId(record.traceId)"
            >
              复制
            </button>
          </span>
          <span class="request-summary">{{ record.summary || '无摘要' }}</span>
        </div>
      </div>
    </section>

    <section v-if="activeView === 'evidence'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>压测结果记录</h2>
        </div>
        <strong class="result-badge" :class="{ danger: !pressurePassed }">
          {{ pressurePassed ? '通过' : '需复查' }}
        </strong>
      </div>

      <div class="pressure-layout">
        <label>
          <span>模式</span>
          <input v-model.trim="pressureMode" type="text" />
        </label>
        <label>
          <span>Users</span>
          <input v-model.number="pressureUsers" type="number" min="0" />
        </label>
        <label>
          <span>票档库存</span>
          <input v-model.number="pressureStock" type="number" min="0" />
        </label>
        <label>
          <span>成功数</span>
          <input v-model.number="pressureSuccess" type="number" min="0" />
        </label>
        <label>
          <span>失败数</span>
          <input v-model.number="pressureFailed" type="number" min="0" />
        </label>
        <label>
          <span>QPS</span>
          <input v-model.number="pressureQps" type="number" min="0" step="0.01" />
        </label>
        <label>
          <span>平均耗时 ms</span>
          <input v-model.number="pressureAvgMs" type="number" min="0" step="0.01" />
        </label>
        <label>
          <span>P95 ms</span>
          <input v-model.number="pressureP95Ms" type="number" min="0" step="0.01" />
        </label>
        <label>
          <span>P99 ms</span>
          <input v-model.number="pressureP99Ms" type="number" min="0" step="0.01" />
        </label>
        <label>
          <span>系统异常数</span>
          <input v-model.number="pressureSystemErrors" type="number" min="0" />
        </label>
      </div>

      <div class="pressure-summary">
        <article>
          <span>是否超卖</span>
          <strong>{{ pressureOversold ? '是' : '否' }}</strong>
        </article>
        <article>
          <span>成功率</span>
          <strong>{{ pressureSuccessRate }}</strong>
        </article>
        <article>
          <span>P95</span>
          <strong>{{ pressureP95Ms }} ms</strong>
        </article>
        <article>
          <span>P99</span>
          <strong>{{ pressureP99Ms }} ms</strong>
        </article>
      </div>

      <div class="selected-box pressure-conclusion">
        <p class="box-title">结论</p>
        <p>
          {{ pressureMode }}：{{ pressureUsers }} 个用户并发下，成功 {{ pressureSuccess }}，
          失败 {{ pressureFailed }}，QPS {{ pressureQps }}，系统异常
          {{ pressureSystemErrors }}。{{
            pressurePassed
              ? '成功数没有超过库存，且没有系统异常，可以作为本地基线证据。'
              : '存在超卖或系统异常，需要回看压测输出和后端日志。'
          }}
        </p>
      </div>
    </section>

    <section v-if="activeView === 'evidence'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>H2 基线与 Redis Lua 对比</h2>
        </div>
      </div>

      <div class="comparison-table">
        <div class="comparison-head">
          <span>模式</span>
          <span>Users</span>
          <span>库存</span>
          <span>success</span>
          <span>failed</span>
          <span>QPS</span>
          <span>P95</span>
          <span>P99</span>
          <span>异常</span>
          <span>结论</span>
        </div>
        <div v-for="(row, index) in pressureComparisons" :key="row.mode" class="comparison-row">
          <input v-model.trim="row.mode" type="text" :aria-label="`模式 ${index + 1}`" />
          <input v-model.number="row.users" type="number" min="0" aria-label="Users" />
          <input v-model.number="row.stock" type="number" min="0" aria-label="库存" />
          <input v-model.number="row.success" type="number" min="0" aria-label="success" />
          <input v-model.number="row.failed" type="number" min="0" aria-label="failed" />
          <input v-model.number="row.qps" type="number" min="0" step="0.01" aria-label="QPS" />
          <input v-model.number="row.p95Ms" type="number" min="0" step="0.01" aria-label="P95" />
          <input v-model.number="row.p99Ms" type="number" min="0" step="0.01" aria-label="P99" />
          <input v-model.number="row.systemErrors" type="number" min="0" aria-label="异常" />
          <strong
            class="mini-badge"
            :class="{ danger: !pressureComparisonRows[index].passed }"
          >
            {{ pressureComparisonRows[index].passed ? '通过' : '需复查' }}
          </strong>
        </div>
      </div>

      <div class="comparison-summary">
        <article v-for="row in pressureComparisonRows" :key="`${row.mode}-summary`">
          <span>{{ row.mode }}</span>
          <strong>{{ row.oversold ? '超卖' : '未超卖' }}</strong>
          <p>QPS {{ row.qps }} · P95 {{ row.p95Ms }} ms · P99 {{ row.p99Ms }} ms</p>
        </article>
      </div>

      <div class="selected-box pressure-conclusion">
        <p class="box-title">对比结论</p>
        <p>{{ pressureComparisonConclusion }}</p>
      </div>
    </section>

    <section v-if="activeView === 'evidence'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>管理端排查</h2>
        </div>
        <div class="trace-list">
          <p v-if="adminOrdersTraceId" class="trace">用户订单 traceId: {{ adminOrdersTraceId }}</p>
          <p v-if="adminTicketByOrderTraceId" class="trace">
            订单查票 traceId: {{ adminTicketByOrderTraceId }}
          </p>
          <p v-if="adminTicketByCodeTraceId" class="trace">
            票码查票 traceId: {{ adminTicketByCodeTraceId }}
          </p>
        </div>
      </div>

      <div class="admin-layout">
        <label class="code-field">
          <span>X-Admin-Key</span>
          <input v-model.trim="adminKey" type="text" />
        </label>
        <button
          type="button"
          class="secondary-action"
          :disabled="adminLoading === 'orders'"
          @click="loadAdminOrders"
        >
          {{ adminLoading === 'orders' ? '查询中' : '按用户查订单' }}
        </button>
        <button
          type="button"
          class="secondary-action"
          :disabled="adminLoading === 'orderTicket' || !order"
          @click="loadAdminTicketByOrder"
        >
          {{ adminLoading === 'orderTicket' ? '查询中' : '按订单查票' }}
        </button>
        <button
          type="button"
          class="secondary-action"
          :disabled="adminLoading === 'codeTicket' || !ticketLookupCode"
          @click="loadAdminTicketByCode"
        >
          {{ adminLoading === 'codeTicket' ? '查询中' : '按票码查票' }}
        </button>
      </div>

      <p v-if="adminError" class="error">{{ adminError }}</p>
      <div class="admin-results">
        <div v-if="adminOrders.length" class="selected-box">
          <p class="box-title">用户 {{ userId }} 的订单</p>
          <div class="admin-list">
            <div v-for="item in adminOrders" :key="item.id" class="admin-row">
              <span>#{{ item.id }}</span>
              <strong>{{ item.status }}</strong>
              <span>票档 {{ item.ticketCategoryId }}</span>
            </div>
          </div>
        </div>

        <div v-if="adminTicketByOrder" class="ticket-result">
          <p class="box-title">订单对应电子票</p>
          <div class="result-grid">
            <span>ticketCode</span>
            <strong>{{ adminTicketByOrder.ticketCode }}</strong>
            <span>票状态</span>
            <strong>{{ adminTicketByOrder.status }}</strong>
            <span>orderId</span>
            <strong>{{ adminTicketByOrder.orderId }}</strong>
          </div>
        </div>

        <div v-if="adminTicketByCode" class="ticket-result verified-result">
          <p class="box-title">票码查询结果</p>
          <div class="result-grid">
            <span>ticketCode</span>
            <strong>{{ adminTicketByCode.ticketCode }}</strong>
            <span>票状态</span>
            <strong>{{ adminTicketByCode.status }}</strong>
            <span>验票员</span>
            <strong>{{ adminTicketByCode.verifierId ?? '未核验' }}</strong>
          </div>
        </div>
      </div>
    </section>

  </main>
</template>
