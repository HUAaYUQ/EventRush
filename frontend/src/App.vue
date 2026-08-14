<script setup>
import { computed, onMounted, ref } from 'vue'

const events = ref([])
const loading = ref(false)
const error = ref('')
const traceId = ref('')

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
  } catch (caught) {
    events.value = []
    traceId.value = ''
    error.value = caught instanceof Error ? caught.message : '活动列表加载失败'
  } finally {
    loading.value = false
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
          <h1>前端接口联通页</h1>
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
                <span
                  v-for="category in session.ticketCategories"
                  :key="category.id"
                  class="ticket-chip"
                >
                  {{ category.name }} · 余 {{ category.remainingStock }}
                </span>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="next-panel">
      <h2>下一步</h2>
      <p>在这个基础上继续做抢票、支付、验票、管理查询和最近请求记录。</p>
    </section>
  </main>
</template>
