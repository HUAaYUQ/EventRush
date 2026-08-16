<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const events = ref([])
const loading = ref(false)
const error = ref('')
const traceId = ref('')
const selectedSessionId = ref(null)
const selectedTicketCategoryId = ref(null)
const userId = ref(getOrCreateDemoUserId())
const passengers = ref(getSavedPassengerProfiles())
let passengerKeySeed = passengers.value.length
const passengerFieldsTouched = ref(false)
const orderReviewReady = ref(false)
const grabLoading = ref(false)
const grabError = ref('')
const grabTraceId = ref('')
const order = ref(null)
const payLoading = ref(false)
const payError = ref('')
const payTraceId = ref('')
const ticket = ref(null)
const issuedTickets = ref([])
const refundSelection = ref([])
const refundLoading = ref(false)
const refundError = ref('')
const refundTraceId = ref('')
const refundResult = ref(null)
const myOrders = ref([])
const myOrdersLoading = ref(false)
const myOrdersError = ref('')
const myWaitlists = ref([])
const myWaitlistsLoading = ref(false)
const myWaitlistsError = ref('')
const waitlistResult = ref(null)
const waitlistTraceId = ref('')
const waitlistActionId = ref(null)
const forcedWaitlistTicketKey = ref('')
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
const now = ref(Date.now())
let clockTimer = null
let expirySyncTimer = null
const adminOrders = ref([])
const adminTicketsByOrder = ref([])
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

const normalizedPassengers = computed(() =>
  passengers.value.map((passenger) => ({
    ...passenger,
    name: passenger.name.trim(),
    documentLast4: passenger.documentLast4.trim().toUpperCase(),
  })),
)
const passengerValidation = computed(() =>
  normalizedPassengers.value.map((passenger) => ({
    nameValid: passenger.name.length >= 2 && passenger.name.length <= 30,
    documentValid: /^[A-Z0-9]{4}$/.test(passenger.documentLast4) && Boolean(passenger.documentType),
  })),
)
const passengerProfilesValid = computed(
  () => passengers.value.length >= 1 && passengers.value.length <= 5
    && passengerValidation.value.every((result) => result.nameValid && result.documentValid),
)
const orderPreviewAmount = computed(
  () => (selectedTicket.value?.category.priceCents ?? 0) * passengers.value.length,
)
const selectedTicketNeedsWaitlist = computed(
  () => Boolean(selectedTicket.value)
    && (selectedTicket.value.category.remainingStock < passengers.value.length
      || forcedWaitlistTicketKey.value === selectedTicket.value.key),
)
const passengerChecks = computed(() => [
  {
    key: 'ticket',
    label: '活动与票档',
    passed: Boolean(selectedTicket.value),
    value: selectedTicket.value
      ? `${selectedTicket.value.event.name} · ${selectedTicket.value.category.name}`
      : '尚未选择票档',
  },
  {
    key: 'passengers',
    label: '购票人清单',
    passed: passengerProfilesValid.value,
    value: passengerProfilesValid.value
      ? `${normalizedPassengers.value.map((passenger) => passenger.name).join('、')} · 共 ${passengers.value.length} 人`
      : '请补全每位购票人的姓名和证件尾号',
  },
  {
    key: 'amount',
    label: '数量与金额',
    passed: Boolean(selectedTicket.value),
    value: selectedTicket.value
      ? `${passengers.value.length} 张 · ${formatMoney(orderPreviewAmount.value)}`
      : '选择票档后计算',
  },
])

const remainingPaymentSeconds = computed(() => {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT') {
    return 0
  }

  return Math.max(0, Math.ceil((new Date(order.value.expireTime).getTime() - now.value) / 1000))
})

const orderCountdown = computed(() => formatCountdown(remainingPaymentSeconds.value))

const currentOrderContext = computed(() => getOrderContext(order.value))
const verifiedTicketCount = computed(
  () => issuedTickets.value.filter((issued) => issued.status === 'VERIFIED').length,
)
const refundedTicketCount = computed(
  () => issuedTickets.value.filter((issued) => issued.status === 'REFUNDED').length,
)
const refundableTickets = computed(
  () => issuedTickets.value.filter((issued) => issued.status === 'VALID'),
)
const selectedRefundTickets = computed(
  () => refundableTickets.value.filter((issued) => refundSelection.value.includes(issued.ticketCode)),
)
const refundPreviewAmount = computed(
  () => (order.value?.unitPriceCents ?? 0) * selectedRefundTickets.value.length,
)
const allIssuedTicketsResolved = computed(
  () => issuedTickets.value.length > 0
    && verifiedTicketCount.value + refundedTicketCount.value === issuedTickets.value.length,
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

  if (order.value.status === 'PENDING_PAYMENT') {
    return '本轮订单待支付 · 等待支付出票'
  }

  if (order.value.status === 'CANCELED') {
    return '本轮订单已取消 · 可重新选择票档'
  }

  if (order.value.status === 'REFUNDED') {
    return `本轮订单已退票 · ${refundedTicketCount.value || order.value.refundedQuantity || 0} 张票码已失效`
  }

  if (issuedTickets.value.length === 0 && !ticket.value) {
    return '本轮订单 PAID · 等待查询电子票'
  }

  const failureHint = requestRecords.value.some((record) => record.result !== '成功')
    ? ' · 最近失败请求可用 traceId 排查'
    : ''
  const refundHint = order.value.status === 'PARTIALLY_REFUNDED'
    ? ` · 已退 ${order.value.refundedQuantity ?? refundedTicketCount.value} 张`
    : ''
  return `本轮订单${orderStatusLabel(order.value.status)} · 已生成 ${issuedTickets.value.length || 1} 张电子票${refundHint}${failureHint}`
})
const coldStartDayOne =
  '先选择一个有库存的票档，填写购票人并核对订单，再完成支付出票。'
const copiedTraceId = ref('')
const surface = computed(() => route.meta.surface ?? 'customer')
const activeView = computed({
  get: () => route.meta.view ?? 'booking',
  set: (view) => {
    const path = {
      booking: '/',
      tickets: '/my',
      gate: '/gate',
      evidence: '/lab',
      demo: '/demo',
    }[view]
    if (path && path !== route.path) {
      router.push(path)
    }
  },
})

const productTabs = computed(() => surface.value === 'customer'
  ? [
      { key: 'booking', path: '/', label: '活动票预订', detail: '选票档、核对订单、支付' },
      { key: 'tickets', path: '/my', label: '我的订单与票', detail: '候补、订单、电子票、退票' },
    ]
  : [])

const surfaceHeader = computed(() => ({
  customer: {
    eyebrow: 'EventRush',
    title: activeView.value === 'tickets' ? '我的订单与电子票' : '活动票务预订',
  },
  gate: { eyebrow: 'EventRush · 验票员', title: '入场验票工作台' },
  ops: { eyebrow: 'EventRush · 平台运营', title: '订单与票务排查' },
  lab: { eyebrow: 'EventRush · 工程实验室', title: '高并发工程证据' },
  demo: { eyebrow: 'EventRush', title: '演示身份入口' },
}[surface.value]))

const demoEntries = [
  {
    path: '/',
    role: '购票用户',
    title: '活动票预订',
    detail: '选活动与票档、维护购票人、支付出票。',
  },
  {
    path: '/my',
    role: '购票用户',
    title: '我的订单与电子票',
    detail: '查看候补、继续支付、查票与退票。',
  },
  {
    path: '/gate',
    role: '验票员',
    title: '入场验票工作台',
    detail: '扫描或输入票码，核验入场资格。',
  },
  {
    path: '/ops',
    role: '平台运营',
    title: '订单与票务排查',
    detail: '根据用户、订单、票码和 traceId 定位问题。',
  },
  {
    path: '/lab',
    role: '研发与面试演示',
    title: '高并发工程证据',
    detail: '记录压测结果，对比 H2 与 Redis Lua 方案。',
  },
]

const customerRefreshLoading = computed(() => activeView.value === 'tickets'
  ? myOrdersLoading.value || myWaitlistsLoading.value
  : loading.value)

async function refreshCustomerSurface() {
  if (activeView.value === 'tickets') {
    await refreshMyTickets()
    return
  }
  await loadEvents()
}

const latestRequestRecord = computed(() => requestRecords.value[0] ?? null)
const latestFailedRequest = computed(() =>
  requestRecords.value.find((record) => record.result !== '成功'),
)

const currentTicketCode = computed(
  () => ticket.value?.ticketCode ?? issuedTickets.value[0]?.ticketCode
    ?? adminTicketsByOrder.value[0]?.ticketCode ?? ticketLookupCode.value,
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
    tone: ['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(order.value?.status) ? 'ready' : 'pending',
  },
  {
    label: '电子票状态',
    value: issuedTickets.value.length
      ? `${verifiedTicketCount.value} 张已入场 · ${refundedTicketCount.value} 张已退票`
      : (ticket.value?.status ?? '未出票'),
    detail: issuedTickets.value.length ? '每位购票人一张独立票码' : '等待支付',
    tone: issuedTickets.value.length ? 'ready' : 'pending',
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
    name: '核对购票人',
    detail: passengerProfilesValid.value
      ? `${passengers.value.length} 位购票人 · 一人一票`
      : '补全每位购票人信息',
    done: passengerProfilesValid.value,
  },
  {
    name: '提交订单',
    detail: order.value ? `orderId ${order.value.id}` : '确认后生成订单',
    done: Boolean(order.value),
  },
  {
    name: '支付',
    detail: issuedTickets.value.length ? `已生成 ${issuedTickets.value.length} 张票` : '支付后逐人出票',
    done: issuedTickets.value.length > 0,
  },
  {
    name: '查票',
    detail: ticketLookupTraceId.value ? '已记录 traceId' : '回看电子票',
    done: Boolean(ticketLookupTraceId.value || ticket.value || issuedTickets.value.length),
  },
  {
    name: '验票 / 退票',
    detail: issuedTickets.value.length
      ? `${verifiedTicketCount.value} 张入场 · ${refundedTicketCount.value} 张退票`
      : (['VERIFIED', 'REFUNDED'].includes(ticket.value?.status) ? '电子票已处理' : '验证入场或退票状态'),
    done: allIssuedTicketsResolved.value || ['VERIFIED', 'REFUNDED'].includes(ticket.value?.status),
  },
])

const pressureHeroItems = computed(() => [
  { label: '是否超卖', value: pressureOversold.value ? '是' : '否', danger: pressureOversold.value },
  { label: 'QPS', value: pressureQps.value || 0 },
  { label: 'P95', value: `${pressureP95Ms.value || 0} ms` },
  { label: 'P99', value: `${pressureP99Ms.value || 0} ms` },
  { label: '系统异常', value: pressureSystemErrors.value || 0, danger: Number(pressureSystemErrors.value) > 0 },
])

class ApiRequestError extends Error {
  constructor(message, code) {
    super(message)
    this.code = code
  }
}

function getOrCreateDemoUserId() {
  const stored = Number(window.localStorage.getItem('eventrush-demo-user-id'))
  if (Number.isInteger(stored) && stored > 0) {
    return stored
  }

  const created = Math.floor(10000 + Math.random() * 90000)
  window.localStorage.setItem('eventrush-demo-user-id', String(created))
  return created
}

function getSavedPassengerProfiles() {
  try {
    const storedProfiles = JSON.parse(window.localStorage.getItem('eventrush-passenger-profiles') ?? '[]')
    if (Array.isArray(storedProfiles) && storedProfiles.length > 0) {
      return storedProfiles.slice(0, 5).map((stored, index) => ({
        key: `saved-${index}`,
        name: typeof stored.name === 'string' ? stored.name : '',
        documentType: ['ID_CARD', 'PASSPORT', 'OTHER'].includes(stored.documentType)
          ? stored.documentType
          : 'ID_CARD',
        documentLast4: typeof stored.documentLast4 === 'string' ? stored.documentLast4 : '',
      }))
    }

    const legacy = JSON.parse(window.localStorage.getItem('eventrush-passenger-profile') ?? '{}')
    return [{
      key: 'saved-0',
      name: typeof legacy.name === 'string' ? legacy.name : '',
      documentType: ['ID_CARD', 'PASSPORT', 'OTHER'].includes(legacy.documentType)
        ? legacy.documentType
        : 'ID_CARD',
      documentLast4: typeof legacy.documentLast4 === 'string' ? legacy.documentLast4 : '',
    }]
  } catch {
    return [{ key: 'saved-0', name: '', documentType: 'ID_CARD', documentLast4: '' }]
  }
}

function savePassengerProfiles() {
  window.localStorage.setItem(
    'eventrush-passenger-profiles',
    JSON.stringify(normalizedPassengers.value.map((passenger) => ({
      name: passenger.name,
      documentType: passenger.documentType,
      documentLast4: passenger.documentLast4,
    }))),
  )
}

function addPassenger() {
  if (passengers.value.length >= 5) {
    return
  }

  const key = `new-${passengerKeySeed += 1}`
  passengers.value.push({ key, name: '', documentType: 'ID_CARD', documentLast4: '' })
  passengerFieldsTouched.value = false
  orderReviewReady.value = false
  focusPassengerField(`passenger-name-${key}`)
}

function removePassenger(index) {
  if (passengers.value.length === 1) {
    return
  }
  passengers.value.splice(index, 1)
  orderReviewReady.value = false
  grabError.value = ''
}

function formatMoney(cents) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(Number(cents ?? 0) / 100)
}

function formatDateTime(value) {
  if (!value) {
    return '待确认'
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatCountdown(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function orderStatusLabel(status) {
  return {
    PENDING_PAYMENT: '待支付',
    PAID: '已出票',
    PARTIALLY_REFUNDED: '部分退票',
    REFUNDED: '已退票',
    CANCELED: '已取消',
  }[status] ?? status
}

function waitlistStatusLabel(status) {
  return {
    WAITING: '排队中',
    FULFILLED: '已兑现',
    CANCELED: '已取消',
    EXPIRED: '已过期',
  }[status] ?? status
}

function ticketStatusLabel(status) {
  return {
    VALID: '待入场',
    VERIFIED: '已入场',
    REFUNDED: '已退票',
  }[status] ?? status
}

function passengerDocumentTypeLabel(type) {
  return {
    ID_CARD: '居民身份证',
    PASSPORT: '护照',
    OTHER: '其他证件',
  }[type] ?? type
}

async function focusPassengerField(fieldId) {
  await nextTick()
  document.getElementById(fieldId)?.focus()
}

function prepareOrderReview() {
  passengerFieldsTouched.value = true
  grabError.value = ''

  if (!selectedTicket.value) {
    grabError.value = '请先选择活动和票档'
    return
  }
  const invalidIndex = passengerValidation.value.findIndex(
    (result) => !result.nameValid || !result.documentValid,
  )
  if (invalidIndex >= 0) {
    const result = passengerValidation.value[invalidIndex]
    const passenger = passengers.value[invalidIndex]
    grabError.value = `请补全第 ${invalidIndex + 1} 位购票人的${result.nameValid ? '证件信息' : '姓名'}`
    focusPassengerField(result.nameValid
      ? `passenger-document-last4-${passenger.key}`
      : `passenger-name-${passenger.key}`)
    return
  }

  passengers.value = normalizedPassengers.value
  savePassengerProfiles()
  orderReviewReady.value = true
}

function editOrderInformation() {
  orderReviewReady.value = false
  grabError.value = ''
  focusPassengerField(`passenger-name-${passengers.value[0].key}`)
}

function getOrderContext(targetOrder) {
  if (!targetOrder) {
    return null
  }

  return ticketOptions.value.find(
    (option) =>
      option.session.id === targetOrder.sessionId &&
      option.category.id === targetOrder.ticketCategoryId,
  ) ?? null
}

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
      throw new ApiRequestError(payload.message || `${action}失败`, payload.code)
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
  orderReviewReady.value = false
  waitlistResult.value = null
  waitlistTraceId.value = ''
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

async function loadMyOrders() {
  myOrdersLoading.value = true
  myOrdersError.value = ''

  try {
    const { payload } = await requestJson(
      '加载我的订单',
      'GET',
      `/api/users/${userId.value}/orders`,
      {},
      (data) => `订单 ${data?.length ?? 0} 条`,
    )
    myOrders.value = payload.data ?? []
  } catch (caught) {
    myOrders.value = []
    myOrdersError.value = caught instanceof Error ? caught.message : '订单列表加载失败'
  } finally {
    myOrdersLoading.value = false
  }
}

async function loadMyWaitlists() {
  myWaitlistsLoading.value = true
  myWaitlistsError.value = ''

  try {
    const { payload } = await requestJson(
      '加载我的候补',
      'GET',
      `/api/users/${userId.value}/waitlists`,
      {},
      (data) => `候补 ${data?.length ?? 0} 条`,
    )
    myWaitlists.value = payload.data ?? []
  } catch (caught) {
    myWaitlists.value = []
    myWaitlistsError.value = caught instanceof Error ? caught.message : '候补列表加载失败'
  } finally {
    myWaitlistsLoading.value = false
  }
}

async function refreshMyTickets() {
  await Promise.all([loadMyOrders(), loadMyWaitlists()])
}

function resetRefundState() {
  refundSelection.value = []
  refundLoading.value = false
  refundError.value = ''
  refundTraceId.value = ''
  refundResult.value = null
}

function continuePayment(targetOrder) {
  resetRefundState()
  order.value = targetOrder
  ticket.value = null
  issuedTickets.value = []
  selectTicket(targetOrder.sessionId, targetOrder.ticketCategoryId)
  activeView.value = 'booking'
}

function buyAgain(targetOrder) {
  resetRefundState()
  order.value = null
  ticket.value = null
  issuedTickets.value = []
  ticketLookupCode.value = ''
  if (targetOrder.passengers?.length) {
    passengers.value = targetOrder.passengers.map((passenger) => ({
      key: `repeat-${passenger.id}`,
      name: passenger.name,
      documentType: passenger.documentType,
      documentLast4: passenger.documentLast4,
    }))
  }
  selectTicket(targetOrder.sessionId, targetOrder.ticketCategoryId)
  activeView.value = 'booking'
}

async function viewOrderTickets(targetOrder) {
  ticketLookupLoading.value = true
  ticketLookupError.value = ''
  resetRefundState()
  order.value = targetOrder

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '打开订单电子票',
      'GET',
      `/api/users/${userId.value}/orders/${targetOrder.id}/tickets`,
      {},
      (data) => `电子票 ${data?.length ?? 0} 张`,
    )
    ticketLookupTraceId.value = nextTraceId
    issuedTickets.value = payload.data ?? []
    ticket.value = issuedTickets.value[0] ?? null
    ticketLookupCode.value = ticket.value?.ticketCode ?? ''
  } catch (caught) {
    ticketLookupError.value = caught instanceof Error ? caught.message : '电子票加载失败'
  } finally {
    ticketLookupLoading.value = false
  }
}

async function grabTicket() {
  if (!orderReviewReady.value || !selectedTicket.value || !passengerProfilesValid.value) {
    prepareOrderReview()
    return
  }

  if (selectedTicketNeedsWaitlist.value) {
    await joinWaitlist()
    return
  }

  grabLoading.value = true
  grabError.value = ''
  grabTraceId.value = ''
  waitlistResult.value = null
  waitlistTraceId.value = ''
  order.value = null
  ticket.value = null
  issuedTickets.value = []
  resetRefundState()
  ticketLookupCode.value = ''
  payError.value = ''
  payTraceId.value = ''
  ticketLookupError.value = ''
  ticketLookupTraceId.value = ''
  verifyError.value = ''
  verifyTraceId.value = ''
  adminError.value = ''
  adminOrders.value = []
  adminTicketsByOrder.value = []
  adminTicketByCode.value = null

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '提交订单',
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
          passengers: normalizedPassengers.value.map((passenger) => ({
            name: passenger.name,
            documentType: passenger.documentType,
            documentLast4: passenger.documentLast4,
          })),
        }),
      },
      (data) => `orderId=${data.id} ${data.status}`,
    )
    grabTraceId.value = nextTraceId

    order.value = payload.data
    orderReviewReady.value = false
    await loadEvents()
    await refreshMyTickets()
  } catch (caught) {
    grabError.value = caught instanceof Error ? caught.message : '抢票失败'
    if (caught instanceof ApiRequestError && caught.code === 'WAITLIST_QUEUE_ACTIVE') {
      forcedWaitlistTicketKey.value = selectedTicket.value?.key ?? ''
      grabError.value = '该票档已有候补队列，已切换为候补核对，请确认后加入队列'
    }
    if (caught instanceof ApiRequestError && caught.code === 'DUPLICATE_GRAB') {
      await loadMyOrders()
      activeView.value = 'tickets'
    }
  } finally {
    grabLoading.value = false
  }
}

async function joinWaitlist() {
  grabLoading.value = true
  grabError.value = ''
  waitlistResult.value = null
  waitlistTraceId.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '提交候补',
      'POST',
      `/api/users/${userId.value}/waitlists`,
      {
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          sessionId: selectedSessionId.value,
          ticketCategoryId: selectedTicketCategoryId.value,
          passengers: normalizedPassengers.value.map((passenger) => ({
            name: passenger.name,
            documentType: passenger.documentType,
            documentLast4: passenger.documentLast4,
          })),
        }),
      },
      (data) => `waitlistId=${data.id} ${data.status}`,
    )
    waitlistResult.value = payload.data
    waitlistTraceId.value = nextTraceId
    forcedWaitlistTicketKey.value = ''
    order.value = null
    orderReviewReady.value = false
    await Promise.all([loadEvents(), loadMyWaitlists()])
  } catch (caught) {
    grabError.value = caught instanceof Error ? caught.message : '候补提交失败'
    if (caught instanceof ApiRequestError && caught.code === 'DUPLICATE_WAITLIST') {
      await loadMyWaitlists()
      activeView.value = 'tickets'
    }
  } finally {
    grabLoading.value = false
  }
}

async function cancelWaitlist(waitlist) {
  if (!window.confirm(`确认取消候补 #${waitlist.id} 吗？取消后将退出当前队列。`)) {
    return
  }
  waitlistActionId.value = waitlist.id
  myWaitlistsError.value = ''
  try {
    await requestJson(
      '取消候补',
      'DELETE',
      `/api/users/${userId.value}/waitlists/${waitlist.id}`,
      {},
      (data) => `waitlistId=${data.id} ${data.status}`,
    )
    await loadMyWaitlists()
  } catch (caught) {
    myWaitlistsError.value = caught instanceof Error ? caught.message : '取消候补失败'
  } finally {
    waitlistActionId.value = null
  }
}

async function continueWaitlistPayment(waitlist) {
  waitlistActionId.value = waitlist.id
  myWaitlistsError.value = ''
  try {
    const { payload } = await requestJson(
      '打开候补兑现订单',
      'GET',
      `/api/users/${userId.value}/orders/${waitlist.orderId}`,
      {},
      (data) => `orderId=${data.id} ${data.status}`,
    )
    continuePayment(payload.data)
  } catch (caught) {
    myWaitlistsError.value = caught instanceof Error ? caught.message : '兑现订单加载失败'
  } finally {
    waitlistActionId.value = null
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
      `/api/admin/orders/${order.value.id}/tickets`,
      { headers: adminHeaders() },
      (data) => `电子票 ${data?.length ?? 0} 张`,
    )
    adminTicketByOrderTraceId.value = nextTraceId

    adminTicketsByOrder.value = payload.data ?? []
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
    `/api/users/${userId.value}/orders/${orderId}`,
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
  issuedTickets.value = []
  resetRefundState()
  ticketLookupError.value = ''
  verifyError.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '支付出票',
      'POST',
      `/api/users/${userId.value}/orders/${order.value.id}/pay`,
      {},
      (data) => `电子票 ${data?.length ?? 0} 张`,
    )
    payTraceId.value = nextTraceId

    issuedTickets.value = payload.data ?? []
    ticket.value = issuedTickets.value[0] ?? null
    ticketLookupCode.value = ticket.value?.ticketCode ?? ''
    await refreshOrder(order.value.id)
    await loadMyOrders()
    activeView.value = 'tickets'
  } catch (caught) {
    payError.value = caught instanceof Error ? caught.message : '支付失败'
    if (caught instanceof ApiRequestError && caught.code === 'ORDER_EXPIRED') {
      await refreshOrder(order.value.id)
      await Promise.all([loadEvents(), refreshMyTickets()])
    }
  } finally {
    payLoading.value = false
  }
}

async function refundTickets() {
  if (!order.value || selectedRefundTickets.value.length === 0) {
    refundError.value = '请先选择至少一张待入场电子票'
    return
  }

  const confirmed = window.confirm(
    `即将退 ${selectedRefundTickets.value.length} 张电子票，票码会立即失效且无法入场。\n`
      + `预计退款 ${formatMoney(refundPreviewAmount.value)}，当前阶段手续费为 ${formatMoney(0)}。\n\n确认继续退票吗？`,
  )
  if (!confirmed) {
    return
  }

  refundLoading.value = true
  refundError.value = ''
  refundTraceId.value = ''
  refundResult.value = null

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '提交退票',
      'POST',
      `/api/users/${userId.value}/orders/${order.value.id}/refunds`,
      {
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ticketCodes: selectedRefundTickets.value.map((issued) => issued.ticketCode),
        }),
      },
      (data) => data.newlyRefundedQuantity > 0
        ? `退票 ${data.newlyRefundedQuantity} 张 · ${formatMoney(data.newlyRefundedAmountCents)}`
        : '所选电子票此前已退票，本次未重复退款',
    )

    refundTraceId.value = nextTraceId
    refundResult.value = payload.data
    order.value = payload.data.order
    issuedTickets.value = payload.data.tickets ?? []
    ticket.value = issuedTickets.value.find((issued) => issued.status === 'VALID')
      ?? issuedTickets.value[0]
      ?? null
    ticketLookupCode.value = ticket.value?.ticketCode ?? ''
    refundSelection.value = []
    await Promise.all([refreshMyTickets(), loadEvents()])
  } catch (caught) {
    refundError.value = caught instanceof Error ? caught.message : '退票失败'
  } finally {
    refundLoading.value = false
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
  refundSelection.value = []
  refundError.value = ''
  refundResult.value = null
  verifyError.value = ''

  try {
    const { payload, traceId: nextTraceId } = await requestJson(
      '查询电子票',
      'GET',
      activeView.value === 'tickets'
        ? `/api/users/${userId.value}/tickets/${encodeURIComponent(code)}`
        : `/api/tickets/${encodeURIComponent(code)}`,
      {},
      (data) => `${data.ticketCode} ${data.status}`,
    )
    ticketLookupTraceId.value = nextTraceId

    ticket.value = payload.data
    issuedTickets.value = [ticket.value]
    ticketLookupCode.value = ticket.value.ticketCode
    if (activeView.value === 'tickets') {
      await refreshOrder(ticket.value.orderId)
    }
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
    issuedTickets.value = issuedTickets.value.map((issued) =>
      issued.ticketCode === ticket.value.ticketCode ? ticket.value : issued,
    )
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

watch(activeView, (nextView) => {
  if (nextView === 'tickets') {
    refreshMyTickets()
  }
})

watch(remainingPaymentSeconds, (seconds, previousSeconds) => {
  if (seconds !== 0 || previousSeconds === 0 || !order.value) {
    return
  }

  window.clearTimeout(expirySyncTimer)
  expirySyncTimer = window.setTimeout(async () => {
    try {
      await refreshOrder(order.value.id)
      await Promise.all([loadEvents(), refreshMyTickets()])
    } catch (caught) {
      payError.value = caught instanceof Error ? caught.message : '订单状态同步失败'
    }
  }, 6000)
})

onMounted(async () => {
  clockTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  await Promise.all([loadEvents(), loadMyOrders(), loadMyWaitlists()])
})

onUnmounted(() => {
  window.clearInterval(clockTimer)
  window.clearTimeout(expirySyncTimer)
})
</script>

<template>
  <main class="shell" :data-surface="surface">
    <section class="topbar">
      <div class="brand">
        <img src="/favicon.svg" alt="" class="brand-mark" />
        <div>
          <p class="eyebrow">{{ surfaceHeader.eyebrow }}</p>
          <h1>{{ surfaceHeader.title }}</h1>
        </div>
      </div>
      <button
        v-if="surface === 'customer'"
        type="button"
        class="reload-button"
        :disabled="customerRefreshLoading"
        @click="refreshCustomerSurface"
      >
        {{ customerRefreshLoading ? '加载中' : '重新加载' }}
      </button>
      <RouterLink v-else-if="surface !== 'demo'" class="entry-link" to="/demo">
        返回身份入口
      </RouterLink>
    </section>

    <nav v-if="productTabs.length" class="product-nav" aria-label="购票用户导航">
      <button
        v-for="tab in productTabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeView === tab.key }"
        :aria-current="activeView === tab.key ? 'page' : undefined"
        @click="router.push(tab.path)"
      >
        <strong>{{ tab.label }}</strong>
        <span>{{ tab.detail }}</span>
      </button>
    </nav>

    <section v-if="surface === 'demo'" class="demo-entry" aria-labelledby="demo-entry-title">
      <div class="demo-entry-heading">
        <p class="eyebrow">按真实职责进入</p>
        <h2 id="demo-entry-title">每类用户只看到自己的任务</h2>
        <p>这些入口共享同一套业务数据，但拥有不同的网址、导航和操作边界。</p>
      </div>
      <div class="demo-entry-grid">
        <RouterLink
          v-for="entry in demoEntries"
          :key="entry.path"
          :to="entry.path"
          class="demo-entry-item"
        >
          <span>{{ entry.role }}</span>
          <strong>{{ entry.title }}</strong>
          <p>{{ entry.detail }}</p>
          <b>进入</b>
        </RouterLink>
      </div>
    </section>

    <section v-if="surface === 'gate'" class="surface-intro">
      <div>
        <p class="eyebrow">当前任务</p>
        <h2>快速判断这张票能否入场</h2>
      </div>
      <p>票码查询与核销集中在同一屏，不展示购票、运营或压测功能。</p>
    </section>

    <section v-if="surface === 'ops'" class="surface-intro">
      <div>
        <p class="eyebrow">当前任务</p>
        <h2>从异常请求追到订单与电子票</h2>
      </div>
      <p>保留 traceId 和业务对象反查，不在运营工作台录入压测数据。</p>
    </section>

    <section v-if="surface === 'lab'" class="surface-intro">
      <div>
        <p class="eyebrow">当前任务</p>
        <h2>用可复核数据解释高并发方案</h2>
      </div>
      <p>压测记录服务于工程复盘和面试展示，不混入购票与运营流程。</p>
    </section>

    <section v-if="activeView === 'booking'" class="customer-hero" aria-label="活动票预订首页">
      <div>
        <p class="eyebrow">购票流程</p>
        <h2>选择票档，核对购票人后提交订单</h2>
        <p>订单会保存票价和购票人脱敏快照，支付出票后可在“我的电子票”找回。</p>
      </div>
      <aside class="booking-side">
        <article class="booking-status">
          <span>当前订单</span>
          <strong>{{ order ? `#${order.id} · ${orderStatusLabel(order.status)}` : '尚未下单' }}</strong>
          <p v-if="order?.status === 'PENDING_PAYMENT'">剩余支付时间 {{ orderCountdown }}</p>
          <p v-else>{{ issuedTickets.length ? `${issuedTickets.length} 张电子票 · 一人一码` : '支付后逐人生成电子票' }}</p>
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

    <section v-if="surface === 'lab'" class="hook-strip" aria-label="本轮状态">
      <p class="hook-text">{{ hookText }}</p>
      <span class="hook-badge">state</span>
    </section>

    <section v-if="surface === 'lab'" class="home-hero" aria-label="本轮验收摘要">
      <div class="hero-layout">
        <article class="summary-panel">
          <div class="panel-title-row">
            <div>
              <p class="eyebrow">本轮验收摘要</p>
              <h2>把 orderId、ticketCode、状态和 traceId 放在第一屏</h2>
            </div>
            <span class="result-badge" :class="{ danger: !order || !allIssuedTicketsVerified }">
              {{ order && allIssuedTicketsVerified ? '链路完成' : '待补齐' }}
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
            <p class="event-meta">{{ event.location }} · 当前可购</p>
          </div>
          <div class="session-list">
            <div v-for="session in event.sessions" :key="session.id" class="session-row">
              <div>
                <p class="session-title">{{ formatDateTime(session.startTime) }} 开场</p>
                <p class="event-meta">预计 {{ formatDateTime(session.endTime) }} 结束</p>
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
                    'sold-out': category.remainingStock === 0,
                  }"
                  @click="selectTicket(session.id, category.id)"
                >
                  <span>{{ category.name }}</span>
                  <strong>{{ formatMoney(category.priceCents) }}</strong>
                  <small>{{ category.remainingStock > 0 ? `余 ${category.remainingStock}` : '可候补' }}</small>
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
          <h2>填写购票人与核对订单</h2>
          <p class="event-meta">每笔订单最多 5 位购票人，支付后每人生成一张独立电子票。</p>
        </div>
      </div>

      <div class="purchase-workflow">
        <div class="passenger-form">
          <div class="workflow-step-heading">
            <span>1</span>
            <div>
              <strong>购票人信息</strong>
              <p>仅保存姓名、证件类型和证件后四位。</p>
            </div>
          </div>

          <div class="identity-field">
            <span>购票身份</span>
            <strong>本机演示用户 #{{ userId }}</strong>
          </div>

          <div class="passenger-list">
            <article v-for="(passenger, index) in passengers" :key="passenger.key" class="passenger-profile-card">
              <div class="passenger-profile-heading">
                <div>
                  <strong>购票人 {{ index + 1 }}</strong>
                  <span>对应第 {{ index + 1 }} 张电子票</span>
                </div>
                <button
                  v-if="!orderReviewReady && passengers.length > 1"
                  type="button"
                  class="text-action danger"
                  @click="removePassenger(index)"
                >
                  移除
                </button>
              </div>

              <div class="passenger-fields">
                <label :for="`passenger-name-${passenger.key}`">
                  <span>姓名 *</span>
                  <input
                    :id="`passenger-name-${passenger.key}`"
                    v-model="passenger.name"
                    type="text"
                    maxlength="30"
                    autocomplete="name"
                    :disabled="orderReviewReady"
                    :aria-invalid="passengerFieldsTouched && !passengerValidation[index].nameValid"
                    @input="orderReviewReady = false"
                  />
                  <small v-if="passengerFieldsTouched && !passengerValidation[index].nameValid" class="field-error">
                    请填写 2 到 30 个字符的姓名。
                  </small>
                </label>

                <label :for="`passenger-document-type-${passenger.key}`">
                  <span>证件类型 *</span>
                  <select
                    :id="`passenger-document-type-${passenger.key}`"
                    v-model="passenger.documentType"
                    :disabled="orderReviewReady"
                    @change="orderReviewReady = false"
                  >
                    <option value="ID_CARD">居民身份证</option>
                    <option value="PASSPORT">护照</option>
                    <option value="OTHER">其他证件</option>
                  </select>
                </label>

                <label :for="`passenger-document-last4-${passenger.key}`">
                  <span>证件后四位 *</span>
                  <input
                    :id="`passenger-document-last4-${passenger.key}`"
                    v-model="passenger.documentLast4"
                    type="text"
                    maxlength="4"
                    autocomplete="off"
                    class="document-last4-input"
                    :disabled="orderReviewReady"
                    :aria-invalid="passengerFieldsTouched && !passengerValidation[index].documentValid"
                    @input="orderReviewReady = false"
                  />
                  <small v-if="passengerFieldsTouched && !passengerValidation[index].documentValid" class="field-error">
                    请输入 4 位字母或数字。
                  </small>
                </label>
              </div>
            </article>
          </div>

          <button
            v-if="!orderReviewReady && passengers.length < 5"
            type="button"
            class="secondary-action add-passenger-action"
            @click="addPassenger"
          >
            添加购票人
          </button>

          <p class="privacy-note">为避免演示项目收集敏感信息，这里不输入和保存完整证件号码。</p>

          <button
            v-if="!orderReviewReady"
            type="button"
            class="primary-action"
            @click="prepareOrderReview"
          >
            {{ selectedTicketNeedsWaitlist ? '核对候补' : '核对订单' }}
          </button>
          <div v-else class="review-ready-message">
            <strong>购票信息已进入核对状态</strong>
            <button type="button" class="secondary-action" @click="editOrderInformation">修改购票人</button>
          </div>
        </div>

        <aside class="order-receipt" :class="{ ready: orderReviewReady }" aria-label="订单核对">
          <div class="workflow-step-heading">
            <span>2</span>
            <div>
              <strong>{{ selectedTicketNeedsWaitlist ? '候补核对' : '订单核对' }}</strong>
              <p>
                {{ orderReviewReady
                  ? (selectedTicketNeedsWaitlist ? '信息完整，可以加入候补队列。' : '信息完整，可以提交订单。')
                  : '完成左侧信息后再提交。' }}
              </p>
            </div>
          </div>

          <dl class="receipt-list">
            <div>
              <dt>活动</dt>
              <dd>{{ selectedTicket?.event.name ?? '待选择' }}</dd>
            </div>
            <div>
              <dt>场次</dt>
              <dd>{{ formatDateTime(selectedTicket?.session.startTime) }}</dd>
            </div>
            <div>
              <dt>票档</dt>
              <dd>{{ selectedTicket?.category.name ?? '待选择' }}</dd>
            </div>
            <div>
              <dt>购票人</dt>
              <dd>{{ passengers.length }} 位</dd>
            </div>
            <div>
              <dt>票数</dt>
              <dd>{{ passengers.length }} 张</dd>
            </div>
            <div>
              <dt>单价</dt>
              <dd>{{ formatMoney(selectedTicket?.category.priceCents) }}</dd>
            </div>
          </dl>

          <div class="receipt-passenger-list">
            <div v-for="(passenger, index) in normalizedPassengers" :key="passenger.key">
              <span>第 {{ index + 1 }} 张</span>
              <strong>{{ passenger.name || '待填写' }}</strong>
              <small>{{ passengerDocumentTypeLabel(passenger.documentType) }}尾号 {{ passenger.documentLast4 || '待填写' }}</small>
            </div>
          </div>

          <div class="purchase-checklist" aria-live="polite">
            <div v-for="item in passengerChecks" :key="item.key" :class="{ passed: item.passed }">
              <span>{{ item.passed ? '已确认' : '待完善' }}</span>
              <p><strong>{{ item.label }}</strong><small>{{ item.value }}</small></p>
            </div>
          </div>

          <div class="receipt-total">
            <span>{{ selectedTicketNeedsWaitlist ? '兑现后应付' : '应付合计' }}</span>
            <strong>{{ formatMoney(orderPreviewAmount) }}</strong>
            <small>票价 {{ formatMoney(selectedTicket?.category.priceCents) }} × {{ passengers.length }} 张</small>
          </div>

          <div v-if="selectedTicketNeedsWaitlist" class="waitlist-notice" role="note">
            <strong>候补成功不等于出票</strong>
            <p>库存释放后按提交顺序整单兑现，不拆分购票人；兑现后仍需在截止时间前完成支付。</p>
          </div>

          <button
            v-if="orderReviewReady"
            type="button"
            class="primary-action receipt-submit"
            :disabled="grabLoading"
            @click="grabTicket"
          >
            {{ grabLoading ? '提交中' : (selectedTicketNeedsWaitlist ? '加入候补' : '确认购票') }}
          </button>
        </aside>
      </div>

      <p v-if="grabError" class="error">{{ grabError }}</p>
      <div v-if="waitlistResult" class="waitlist-success" role="status" aria-live="polite">
        <div>
          <p class="box-title">候补已提交</p>
          <strong>#{{ waitlistResult.id }} · {{ waitlistStatusLabel(waitlistResult.status) }}</strong>
          <p>
            {{ waitlistResult.quantity }} 位购票人整单排队 · 前方
            {{ waitlistResult.waitingAhead }} 笔候补 · 兑现后应付 {{ formatMoney(waitlistResult.unitPriceCents * waitlistResult.quantity) }}
          </p>
          <small>本次候补 traceId：{{ waitlistTraceId || '未返回' }}</small>
        </div>
        <button type="button" class="primary-action" @click="activeView = 'tickets'">查看我的候补</button>
      </div>
      <div v-if="order" class="order-result">
        <p class="box-title">订单已创建</p>
        <div class="result-grid">
          <span>orderId</span>
          <strong>{{ order.id }}</strong>
          <span>订单状态</span>
          <strong>{{ orderStatusLabel(order.status) }}</strong>
          <span>应付金额</span>
          <strong>{{ formatMoney(order.amountCents) }}</strong>
          <span>购票人</span>
          <strong>{{ order.passengers.map((passenger) => passenger.name).join('、') }}</strong>
          <span>剩余时间</span>
          <strong>{{ order.status === 'PENDING_PAYMENT' ? orderCountdown : '已结束' }}</strong>
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
            <p>{{ currentOrderContext?.event.name ?? `订单 #${order.id}` }}</p>
            <p class="event-meta">
              {{ currentOrderContext?.category.name ?? '已选票档' }} · {{ order.quantity ?? 1 }} 张 ·
              {{ formatMoney(order.amountCents) }}
            </p>
            <p class="event-meta">购票人 {{ order.passengers.map((passenger) => passenger.name).join('、') }}</p>
            <p v-if="order.status === 'PENDING_PAYMENT'" class="payment-deadline">
              请在 {{ orderCountdown }} 内完成支付
            </p>
            <p v-else class="event-meta">{{ orderStatusLabel(order.status) }}</p>
          </template>
          <p v-else class="event-meta">请先核对购票人并提交订单。</p>
        </div>

        <button
          type="button"
          class="primary-action"
          :disabled="payLoading || !order || order.status !== 'PENDING_PAYMENT' || remainingPaymentSeconds === 0"
          @click="payOrder"
        >
          {{ payLoading ? '支付中' : order ? `支付 ${formatMoney(order.amountCents)}` : '支付并出票' }}
        </button>
      </div>

      <p v-if="payError" class="error">{{ payError }}</p>
      <div v-if="issuedTickets.length" class="issued-ticket-list">
        <article v-for="issued in issuedTickets" :key="issued.ticketCode" class="ticket-result">
          <p class="box-title">{{ issued.passengerName }}的电子票</p>
          <div class="result-grid">
            <span>ticketCode</span>
            <strong>{{ issued.ticketCode }}</strong>
            <span>票状态</span>
            <strong>{{ ticketStatusLabel(issued.status) }}</strong>
            <span>证件尾号</span>
            <strong>{{ issued.passengerDocumentLast4 }}</strong>
          </div>
        </article>
      </div>
    </section>

    <section v-if="activeView === 'tickets'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>我的订单与电子票</h2>
          <p class="event-meta">本机演示用户 #{{ userId }}，刷新页面后仍可找回。</p>
        </div>
        <button
          type="button"
          class="secondary-action"
          :disabled="myOrdersLoading || myWaitlistsLoading"
          @click="refreshMyTickets"
        >
          {{ myOrdersLoading || myWaitlistsLoading ? '刷新中' : '刷新状态' }}
        </button>
      </div>

      <section class="waitlist-section" aria-labelledby="my-waitlist-title">
        <div class="section-heading-row">
          <div>
            <h3 id="my-waitlist-title">我的候补</h3>
            <p>库存释放后按顺序整单兑现，兑现后会生成一笔限时支付订单。</p>
          </div>
          <span>{{ myWaitlists.length }} 条</span>
        </div>
        <p v-if="myWaitlistsError" class="error">{{ myWaitlistsError }}</p>
        <div v-if="myWaitlistsLoading && myWaitlists.length === 0" class="order-skeleton" aria-label="正在加载候补">
          <span></span><span></span>
        </div>
        <div v-else-if="myWaitlists.length === 0" class="waitlist-empty">
          当前没有候补记录。票档库存不足时，可在车票预订中提交候补。
        </div>
        <div v-else class="waitlist-list">
          <article
            v-for="item in myWaitlists"
            :key="item.id"
            class="waitlist-row"
            :class="item.status.toLowerCase()"
          >
            <div class="waitlist-main">
              <div class="order-heading">
                <strong>{{ getOrderContext(item)?.event.name ?? `候补 #${item.id}` }}</strong>
                <span class="waitlist-status" :class="item.status.toLowerCase()">
                  {{ waitlistStatusLabel(item.status) }}
                </span>
              </div>
              <p>
                {{ formatDateTime(getOrderContext(item)?.session.startTime) }} ·
                {{ getOrderContext(item)?.category.name ?? `票档 ${item.ticketCategoryId}` }} · {{ item.quantity }} 张
              </p>
              <p>购票人 {{ item.passengers.map((passenger) => passenger.name).join('、') }}</p>
              <small v-if="item.status === 'WAITING'">前方 {{ item.waitingAhead }} 笔候补 · 提交于 {{ formatDateTime(item.createdTime) }}</small>
              <small v-else-if="item.status === 'FULFILLED'">订单 #{{ item.orderId }} · 请在 {{ formatDateTime(item.paymentExpireTime) }} 前支付</small>
              <small v-else>{{ formatDateTime(item.updatedTime) }} 更新</small>
            </div>
            <div class="waitlist-amount">
              <span>兑现后应付</span>
              <strong>{{ formatMoney(item.unitPriceCents * item.quantity) }}</strong>
            </div>
            <div class="order-actions">
              <button
                v-if="item.status === 'WAITING'"
                type="button"
                class="secondary-action"
                :disabled="waitlistActionId === item.id"
                @click="cancelWaitlist(item)"
              >
                {{ waitlistActionId === item.id ? '处理中' : '取消候补' }}
              </button>
              <button
                v-else-if="item.status === 'FULFILLED'"
                type="button"
                class="primary-action"
                :disabled="waitlistActionId === item.id"
                @click="continueWaitlistPayment(item)"
              >
                {{ waitlistActionId === item.id ? '打开中' : '查看待支付订单' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <p v-if="myOrdersError" class="error">{{ myOrdersError }}</p>
      <div v-if="myOrdersLoading && myOrders.length === 0" class="order-skeleton" aria-label="正在加载订单">
        <span></span><span></span><span></span>
      </div>
      <div v-else-if="myOrders.length === 0" class="empty order-empty">
        <strong>还没有订单</strong>
        <p>先去选择活动和票档，支付成功后电子票会自动出现在这里。</p>
        <button type="button" class="primary-action" @click="activeView = 'booking'">去购票</button>
      </div>
      <div v-else class="order-list">
        <article v-for="item in myOrders" :key="item.id" class="order-row" :class="item.status.toLowerCase()">
          <div class="order-row-main">
            <div class="order-heading">
              <strong>{{ getOrderContext(item)?.event.name ?? `订单 #${item.id}` }}</strong>
              <span class="order-status" :class="item.status.toLowerCase()">{{ orderStatusLabel(item.status) }}</span>
            </div>
            <p>
              {{ formatDateTime(getOrderContext(item)?.session.startTime) }} ·
              {{ getOrderContext(item)?.category.name ?? `票档 ${item.ticketCategoryId}` }} · {{ item.quantity ?? 1 }} 张
            </p>
            <p>购票人 {{ item.passengers.map((passenger) => passenger.name).join('、') }}</p>
            <small>订单 #{{ item.id }} · 下单于 {{ formatDateTime(item.createdTime) }}</small>
          </div>
          <div class="order-finance">
            <strong class="order-amount">{{ formatMoney(item.amountCents) }}</strong>
            <small v-if="item.refundedAmountCents > 0">
              已退 {{ formatMoney(item.refundedAmountCents) }}
            </small>
          </div>
          <div class="order-actions">
            <button
              v-if="item.status === 'PENDING_PAYMENT'"
              type="button"
              class="primary-action"
              @click="continuePayment(item)"
            >
              继续支付
            </button>
            <template v-else-if="['PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(item.status)">
              <button
                type="button"
                class="secondary-action"
                :disabled="ticketLookupLoading"
                @click="viewOrderTickets(item)"
              >
                查看电子票
              </button>
              <button
                v-if="item.status === 'REFUNDED'"
                type="button"
                class="secondary-action"
                @click="buyAgain(item)"
              >
                重新购买
              </button>
            </template>
            <button v-else type="button" class="secondary-action" @click="buyAgain(item)">
              重新购买
            </button>
          </div>
        </article>
      </div>

      <div v-if="issuedTickets.length && order" class="issued-ticket-list ticket-pass-list">
        <article
          v-for="issued in issuedTickets"
          :key="issued.ticketCode"
          class="ticket-result ticket-pass"
          :class="issued.status.toLowerCase()"
        >
          <div>
            <p class="box-title">{{ currentOrderContext?.event.name ?? '电子票' }}</p>
            <strong>{{ issued.passengerName }} · {{ ticketStatusLabel(issued.status) }}</strong>
            <p>{{ formatDateTime(currentOrderContext?.session.startTime) }} · {{ currentOrderContext?.event.location }}</p>
            <p>{{ passengerDocumentTypeLabel(issued.passengerDocumentType) }}尾号 {{ issued.passengerDocumentLast4 }}</p>
            <label class="refund-ticket-control" :class="{ disabled: issued.status !== 'VALID' }">
              <input
                v-model="refundSelection"
                type="checkbox"
                :value="issued.ticketCode"
                :disabled="issued.status !== 'VALID' || refundLoading"
              />
              <span>
                <strong>{{ issued.status === 'VALID' ? '选择退票' : ticketStatusLabel(issued.status) }}</strong>
                <small v-if="issued.status === 'VALID'">勾选后在下方核对退款金额</small>
                <small v-else-if="issued.status === 'VERIFIED'">已核验入场，不能退票</small>
                <small v-else>票码已失效，不能再次退款</small>
              </span>
            </label>
          </div>
          <div class="ticket-code-block">
            <span>独立电子票码</span>
            <strong>{{ issued.ticketCode }}</strong>
          </div>
        </article>
      </div>

      <div v-if="refundResult" class="refund-success" role="status" aria-live="polite">
        <div>
          <p class="box-title">
            {{ refundResult.newlyRefundedQuantity > 0 ? '退票已受理' : '本次未重复退款' }}
          </p>
          <strong>
            {{ refundResult.newlyRefundedQuantity > 0
              ? `${refundResult.newlyRefundedQuantity} 张电子票已失效`
              : '所选电子票此前已完成退票' }}
          </strong>
          <p>
            订单 {{ orderStatusLabel(refundResult.order.status) }} · 本次退款
            {{ formatMoney(refundResult.newlyRefundedAmountCents) }} · 累计已退
            {{ formatMoney(refundResult.order.refundedAmountCents) }}
          </p>
          <small>退款请求 traceId：{{ refundTraceId || '未返回' }}</small>
        </div>
        <button
          v-if="refundResult.order.status === 'REFUNDED'"
          type="button"
          class="primary-action"
          @click="buyAgain(refundResult.order)"
        >
          重新购买同票档
        </button>
      </div>

      <section
        v-if="issuedTickets.length && order && ['PAID', 'PARTIALLY_REFUNDED'].includes(order.status)"
        class="refund-summary"
        aria-labelledby="refund-summary-title"
      >
        <div class="refund-summary-heading">
          <div>
            <p class="box-title">退票核对</p>
            <h3 id="refund-summary-title">确认失效票码与退款金额</h3>
          </div>
          <button
            type="button"
            class="text-action"
            :disabled="refundableTickets.length === 0 || refundLoading"
            @click="refundSelection = selectedRefundTickets.length === refundableTickets.length
              ? []
              : refundableTickets.map((issued) => issued.ticketCode)"
          >
            {{ selectedRefundTickets.length === refundableTickets.length && refundableTickets.length > 0
              ? '清空选择'
              : '选择全部可退' }}
          </button>
        </div>

        <div class="refund-breakdown">
          <div><span>已选电子票</span><strong>{{ selectedRefundTickets.length }} 张</strong></div>
          <div><span>票面价</span><strong>{{ formatMoney(order.unitPriceCents) }} × {{ selectedRefundTickets.length }}</strong></div>
          <div><span>退票手续费</span><strong>{{ formatMoney(0) }}</strong></div>
          <div class="refund-total"><span>预计退款</span><strong>{{ formatMoney(refundPreviewAmount) }}</strong></div>
        </div>

        <p class="refund-warning">提交后所选票码立即失效，不能再用于验票入场。</p>
        <button
          type="button"
          class="primary-action refund-action"
          :disabled="refundLoading || selectedRefundTickets.length === 0"
          @click="refundTickets"
        >
          {{ refundLoading ? '退票处理中' : `确认退 ${selectedRefundTickets.length} 张` }}
        </button>
      </section>
      <p v-if="refundError" class="error">{{ refundError }}</p>

      <details class="manual-ticket-lookup">
        <summary>使用电子票码查询</summary>
        <div class="compact-lookup">
          <label class="code-field">
            <span>电子票码</span>
            <input v-model.trim="ticketLookupCode" type="text" placeholder="ER-..." />
          </label>
          <button type="button" class="secondary-action" :disabled="ticketLookupLoading || !ticketLookupCode" @click="lookupTicket">
            {{ ticketLookupLoading ? '查询中' : '查询' }}
          </button>
        </div>
      </details>
      <p v-if="ticketLookupError" class="error">{{ ticketLookupError }}</p>
    </section>

    <section v-if="surface === 'gate'" class="panel action-panel">
      <div class="panel-header">
        <div>
          <h2>入场验票</h2>
          <p class="event-meta">输入或扫描电子票码，核验结果会立即返回。</p>
        </div>
      </div>

      <div class="verify-layout">
        <label class="code-field">
          <span>电子票码</span>
          <input
            v-model.trim="ticketLookupCode"
            type="text"
            placeholder="ER-..."
          />
        </label>
        <label>
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
          <span>电子票码</span>
          <strong>{{ ticket.ticketCode }}</strong>
          <span>票状态</span>
          <strong>{{ ticketStatusLabel(ticket.status) }}</strong>
          <span>orderId</span>
          <strong>{{ ticket.orderId }}</strong>
          <span>购票人</span>
          <strong>{{ ticket.passengerName }} · 尾号 {{ ticket.passengerDocumentLast4 }}</strong>
          <span>验票员</span>
          <strong>{{ ticket.verifierId ?? '未核验' }}</strong>
        </div>
      </div>
    </section>

    <section v-if="surface === 'ops'" class="panel action-panel request-panel">
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

    <section v-if="surface === 'lab'" class="panel action-panel">
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

    <section v-if="surface === 'lab'" class="panel action-panel">
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

    <section v-if="surface === 'ops'" class="panel action-panel">
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

        <div v-if="adminTicketsByOrder.length" class="issued-ticket-list admin-ticket-list">
          <article v-for="issued in adminTicketsByOrder" :key="issued.ticketCode" class="ticket-result">
            <p class="box-title">{{ issued.passengerName }}的电子票</p>
            <div class="result-grid">
              <span>ticketCode</span>
              <strong>{{ issued.ticketCode }}</strong>
              <span>票状态</span>
              <strong>{{ issued.status }}</strong>
              <span>证件尾号</span>
              <strong>{{ issued.passengerDocumentLast4 }}</strong>
            </div>
          </article>
        </div>

        <div v-if="adminTicketByCode" class="ticket-result verified-result">
          <p class="box-title">票码查询结果</p>
          <div class="result-grid">
            <span>ticketCode</span>
            <strong>{{ adminTicketByCode.ticketCode }}</strong>
            <span>票状态</span>
            <strong>{{ adminTicketByCode.status }}</strong>
            <span>购票人</span>
            <strong>{{ adminTicketByCode.passengerName }}</strong>
            <span>验票员</span>
            <strong>{{ adminTicketByCode.verifierId ?? '未核验' }}</strong>
          </div>
        </div>
      </div>
    </section>

  </main>
</template>
