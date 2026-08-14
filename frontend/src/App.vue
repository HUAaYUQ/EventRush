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
const adminOrders = ref([])
const adminTicketByOrder = ref(null)
const adminTicketByCode = ref(null)

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
    const response = await fetch('/api/events')
    const payload = await response.json()
    traceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '活动列表加载失败')
    }

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
    const response = await fetch('/api/orders/grab', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        userId: Number(userId.value),
        sessionId: selectedSessionId.value,
        ticketCategoryId: selectedTicketCategoryId.value,
      }),
    })
    const payload = await response.json()
    grabTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '抢票失败')
    }

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
    const response = await fetch(`/api/admin/users/${Number(userId.value)}/orders`, {
      headers: adminHeaders(),
    })
    const payload = await response.json()
    adminOrdersTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '用户订单查询失败')
    }

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
    const response = await fetch(`/api/admin/orders/${order.value.id}/ticket`, {
      headers: adminHeaders(),
    })
    const payload = await response.json()
    adminTicketByOrderTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '订单电子票查询失败')
    }

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
    const response = await fetch(`/api/admin/tickets/${encodeURIComponent(code)}`, {
      headers: adminHeaders(),
    })
    const payload = await response.json()
    adminTicketByCodeTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '票码查询失败')
    }

    adminTicketByCode.value = payload.data
  } catch (caught) {
    adminError.value = caught instanceof Error ? caught.message : '票码查询失败'
  } finally {
    adminLoading.value = ''
  }
}

async function refreshOrder(orderId) {
  const response = await fetch(`/api/orders/${orderId}`)
  const payload = await response.json()

  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || '订单状态刷新失败')
  }

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
    const response = await fetch(`/api/orders/${order.value.id}/pay`, {
      method: 'POST',
    })
    const payload = await response.json()
    payTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '支付失败')
    }

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
    const response = await fetch(`/api/tickets/${encodeURIComponent(code)}`)
    const payload = await response.json()
    ticketLookupTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '电子票查询失败')
    }

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
    const response = await fetch('/api/tickets/verify', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        ticketCode: code,
        verifierId: Number(verifierId.value),
      }),
    })
    const payload = await response.json()
    verifyTraceId.value = payload.traceId ?? response.headers.get('X-Trace-Id') ?? ''

    if (!response.ok || payload.success === false) {
      throw new Error(payload.message || '验票失败')
    }

    ticket.value = payload.data
    ticketLookupCode.value = ticket.value.ticketCode
  } catch (caught) {
    verifyError.value = caught instanceof Error ? caught.message : '验票失败'
  } finally {
    verifyLoading.value = false
  }
}

onMounted(loadEvents)
</script>

<template>
  <main class="shell">
    <section class="topbar">
      <div class="brand">
        <img src="/favicon.svg" alt="" class="brand-mark" />
        <div>
          <p class="eyebrow">EventRush 工作台</p>
          <h1>用户抢票、支付出票与验票</h1>
        </div>
      </div>
      <button type="button" class="reload-button" :disabled="loading" @click="loadEvents">
        {{ loading ? '加载中' : '重新加载' }}
      </button>
    </section>

    <section class="status-grid" aria-label="活动概览">
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
    </section>

    <section class="panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">GET /api/events</p>
          <h2>活动与票档</h2>
        </div>
        <p v-if="traceId" class="trace">traceId: {{ traceId }}</p>
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

    <section class="panel action-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">POST /api/orders/grab</p>
          <h2>同步抢票</h2>
        </div>
        <p v-if="grabTraceId" class="trace">traceId: {{ grabTraceId }}</p>
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

    <section class="panel action-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">POST /api/orders/{orderId}/pay</p>
          <h2>支付出票</h2>
        </div>
        <p v-if="payTraceId" class="trace">traceId: {{ payTraceId }}</p>
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

    <section class="panel action-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">GET /api/tickets/{ticketCode} · POST /api/tickets/verify</p>
          <h2>电子票查询与验票</h2>
        </div>
        <div class="trace-list">
          <p v-if="ticketLookupTraceId" class="trace">查票 traceId: {{ ticketLookupTraceId }}</p>
          <p v-if="verifyTraceId" class="trace">验票 traceId: {{ verifyTraceId }}</p>
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

    <section class="panel action-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">GET /api/admin/**</p>
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

    <section class="next-panel">
      <h2>下一步</h2>
      <p>在这个基础上继续补压测结果记录区，把性能指标和业务正确性放在同一个展示链路里。</p>
    </section>
  </main>
</template>
