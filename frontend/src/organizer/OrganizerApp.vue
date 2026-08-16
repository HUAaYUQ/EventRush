<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Bell, CalendarDays, Check, ChevronRight, ClipboardList, ExternalLink, KeyRound, LayoutDashboard, Menu, Plus, Save, Ticket, X } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const key = ref(localStorage.getItem('eventrush-organizer-key') || 'eventrush-organizer-key')
const authorized = ref(true)
const loading = ref(false)
const busy = ref('')
const error = ref('')
const success = ref('')
const events = ref([])
const event = ref(null)
const eventOrders = ref([])
const ordersLoading = ref(false)
const ordersError = ref('')
const tab = ref('overview')
const railOpen = ref(false)
const step = ref(0)
const savedAt = ref('')
const ids = ref({ event: null, session: null, category: null })
const start = new Date(Date.now() + 7 * 86400000)
start.setMinutes(0, 0, 0)
const draft = ref({
  name: '', location: '', description: '', posterUrl: '/images/events/campus-music-night.jpg',
  startTime: localTime(start), endTime: localTime(new Date(start.getTime() + 7200000)),
  categoryName: '标准票', priceYuan: 199, totalStock: 100,
})
const detail = ref({})
const sessionForm = ref({ startTime: draft.value.startTime, endTime: draft.value.endTime, id: null })
const categoryForm = ref({ sessionId: '', name: '标准票', priceYuan: 199, totalStock: 100, id: null })
const notice = ref({ title: '', content: '' })

const screen = computed(() => route.meta.screen || 'organizer-list')
const stats = computed(() => ({
  all: events.value.length,
  published: events.value.filter((item) => item.status === 'PUBLISHED').length,
  drafts: events.value.filter((item) => item.status === 'DRAFT').length,
  tickets: events.value.reduce((sum, item) => sum + ticketCount(item), 0),
}))
const orderStats = computed(() => ({
  total: eventOrders.value.length,
  paid: eventOrders.value.filter((item) => ['PAID', 'PARTIALLY_REFUNDED'].includes(item.status)).length,
  refunding: eventOrders.value.filter((item) => item.refundedQuantity > 0).length,
  issued: eventOrders.value.reduce((sum, item) => sum + Number(item.issuedTicketCount || 0), 0),
}))
const checks = computed(() => [
  { label: '活动信息', ok: !!(draft.value.name.trim() && draft.value.location.trim()), value: draft.value.name ? `${draft.value.name} · ${draft.value.location}` : '请填写名称和地点', step: 0 },
  { label: '场次时间', ok: validTime(draft.value), value: validTime(draft.value) ? `${fmtDate(draft.value.startTime)} 至 ${fmtDate(draft.value.endTime)}` : '结束时间必须晚于开始时间', step: 1 },
  { label: '票档与库存', ok: validTicket(draft.value), value: validTicket(draft.value) ? `${draft.value.categoryName} · ${money(draft.value.priceYuan * 100)} · ${draft.value.totalStock} 张` : '请补全票档、票价和票数', step: 2 },
  { label: '活动封面', ok: !!draft.value.posterUrl, value: draft.value.posterUrl ? '已使用本地活动海报' : '未设置封面', step: 0 },
])
const canPublish = computed(() => checks.value.every((item) => item.ok))

function localTime(date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
function fmtDate(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) : '待设置' }
function money(cents) { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(cents || 0) / 100) }
function poster(value) { return value?.['posterUrl'] || '/images/events/campus-music-night.jpg' }
function imageAttrs(value, alt = '活动封面') { return { src: poster(value), alt } }
function status(statusValue) { return statusValue === 'PUBLISHED' ? '已发布' : '草稿' }
function orderStatus(statusValue) {
  return {
    PENDING_PAYMENT: '待付款',
    PAID: '已付款',
    PARTIALLY_REFUNDED: '部分退款',
    REFUNDED: '已退款',
    CANCELED: '已取消',
  }[statusValue] || statusValue || '未知状态'
}
function orderStatusTone(statusValue) {
  if (statusValue === 'PAID') return 'paid'
  if (statusValue === 'PARTIALLY_REFUNDED') return 'partial'
  if (['REFUNDED', 'CANCELED'].includes(statusValue)) return 'closed'
  return 'pending'
}
function validTime(value) { return value.startTime && value.endTime && new Date(value.endTime) > new Date(value.startTime) }
function validTicket(value) { return value.categoryName?.trim() && Number(value.priceYuan) >= 0 && Number(value.totalStock) >= 1 }
function ticketCount(item) { return (item.sessions || []).flatMap((session) => session.ticketCategories || []).reduce((sum, category) => sum + Number(category.totalStock || 0), 0) }

async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', 'X-Organizer-Key': key.value } })
  const payload = await response.json().catch(() => ({}))
  if (response.status === 401) authorized.value = false
  if (!response.ok || payload.success === false) throw new Error(payload.message || '请求失败，请稍后重试')
  authorized.value = true
  return payload.data
}
async function run(name, task) {
  busy.value = name; error.value = ''; success.value = ''
  try { return await task() } catch (caught) { error.value = caught.message; return null } finally { busy.value = '' }
}
async function load() {
  railOpen.value = false; loading.value = true; error.value = ''
  try {
    if (screen.value === 'organizer-list') events.value = await api('/api/organizer/events')
    if (screen.value === 'organizer-detail') {
      event.value = await api(`/api/organizer/events/${route.params.eventId}`)
      detail.value = { name: event.value.name, location: event.value.location, description: event.value.description, posterUrl: event.value.posterUrl }
      categoryForm.value.sessionId ||= String(event.value.sessions?.[0]?.id || '')
      await loadOrders(event.value.id)
    }
    if (screen.value === 'organizer-create') restore()
  } catch (caught) { error.value = caught.message } finally { loading.value = false }
}
async function loadOrders(eventId) {
  ordersLoading.value = true
  ordersError.value = ''
  try {
    eventOrders.value = await api(`/api/organizer/events/${eventId}/orders`)
  } catch (caught) {
    eventOrders.value = []
    ordersError.value = caught.message
  } finally {
    ordersLoading.value = false
  }
}
async function reconnect() { localStorage.setItem('eventrush-organizer-key', key.value); authorized.value = true; await load() }
function restore() {
  try {
    const value = JSON.parse(localStorage.getItem('eventrush-organizer-draft') || 'null')
    if (value) { draft.value = { ...draft.value, ...value.draft }; ids.value = { ...ids.value, ...value.ids } }
  } catch { localStorage.removeItem('eventrush-organizer-draft') }
}
function saveLocal() {
  localStorage.setItem('eventrush-organizer-draft', JSON.stringify({ draft: draft.value, ids: ids.value }))
  savedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
watch(draft, saveLocal, { deep: true })

async function saveBasic() {
  if (!draft.value.name.trim() || !draft.value.location.trim()) { error.value = '请先填写活动名称和地点'; return }
  const result = await run('basic', () => api(ids.value.event ? `/api/organizer/events/${ids.value.event}` : '/api/organizer/events', {
    method: ids.value.event ? 'PUT' : 'POST', body: JSON.stringify({ name: draft.value.name, location: draft.value.location, description: draft.value.description, posterUrl: draft.value.posterUrl }),
  }))
  if (result) { ids.value.event = result.id; step.value = 1; saveLocal() }
}
async function saveSession() {
  if (!validTime(draft.value)) { error.value = '结束时间必须晚于开始时间'; return }
  const base = `/api/organizer/events/${ids.value.event}/sessions`
  const result = await run('session', () => api(ids.value.session ? `${base}/${ids.value.session}` : base, { method: ids.value.session ? 'PUT' : 'POST', body: JSON.stringify({ startTime: draft.value.startTime, endTime: draft.value.endTime }) }))
  if (result) { ids.value.session = result.id; step.value = 2; saveLocal() }
}
async function saveCategory() {
  if (!validTicket(draft.value)) { error.value = '请填写有效的票档名称、票价和票数'; return }
  const base = `/api/organizer/events/${ids.value.event}/sessions/${ids.value.session}/ticket-categories`
  const result = await run('category', () => api(ids.value.category ? `${base}/${ids.value.category}` : base, { method: ids.value.category ? 'PUT' : 'POST', body: JSON.stringify({ name: draft.value.categoryName, priceCents: Math.round(draft.value.priceYuan * 100), totalStock: Number(draft.value.totalStock) }) }))
  if (result) { ids.value.category = result.id; step.value = 3; saveLocal() }
}
async function publish() {
  if (!canPublish.value || !confirm(`发布后，“${draft.value.name}”会立即出现在购票平台。确认发布吗？`)) return
  const result = await run('publish', () => api(`/api/organizer/events/${ids.value.event}/publish`, { method: 'POST' }))
  if (result) { localStorage.removeItem('eventrush-organizer-draft'); router.push(`/organizer/events/${result.id}`) }
}
async function saveDetail() {
  const result = await run('detail', () => api(`/api/organizer/events/${event.value.id}`, { method: 'PUT', body: JSON.stringify(detail.value) }))
  if (result) { event.value = result; success.value = '活动信息已保存' }
}
async function saveManagedSession() {
  if (!validTime(sessionForm.value)) { error.value = '结束时间必须晚于开始时间'; return }
  const base = `/api/organizer/events/${event.value.id}/sessions`
  const result = await run('managedSession', () => api(sessionForm.value.id ? `${base}/${sessionForm.value.id}` : base, { method: sessionForm.value.id ? 'PUT' : 'POST', body: JSON.stringify(sessionForm.value) }))
  if (result) { sessionForm.value = { startTime: draft.value.startTime, endTime: draft.value.endTime, id: null }; await load(); success.value = '场次已保存' }
}
async function saveManagedCategory() {
  if (!categoryForm.value.sessionId || !validTicket({ categoryName: categoryForm.value.name, priceYuan: categoryForm.value.priceYuan, totalStock: categoryForm.value.totalStock })) { error.value = '请选择场次并填写有效票档'; return }
  const base = `/api/organizer/events/${event.value.id}/sessions/${categoryForm.value.sessionId}/ticket-categories`
  const result = await run('managedCategory', () => api(categoryForm.value.id ? `${base}/${categoryForm.value.id}` : base, { method: categoryForm.value.id ? 'PUT' : 'POST', body: JSON.stringify({ name: categoryForm.value.name, priceCents: Math.round(categoryForm.value.priceYuan * 100), totalStock: Number(categoryForm.value.totalStock) }) }))
  if (result) { categoryForm.value = { sessionId: String(event.value.sessions?.[0]?.id || ''), name: '标准票', priceYuan: 199, totalStock: 100, id: null }; await load(); success.value = '票档已保存' }
}
async function publishNotice() {
  if (!notice.value.title.trim() || !notice.value.content.trim()) { error.value = '请填写通知标题和内容'; return }
  const result = await run('notice', () => api(`/api/organizer/events/${event.value.id}/notices`, { method: 'POST', body: JSON.stringify(notice.value) }))
  if (result) { notice.value = { title: '', content: '' }; await load(); tab.value = 'notices'; success.value = '通知已发布' }
}
function editSession(item) { sessionForm.value = { id: item.id, startTime: localTime(new Date(item.startTime)), endTime: localTime(new Date(item.endTime)) } }
function editCategory(item) { categoryForm.value = { id: item.id, sessionId: String(item.sessionId), name: item.name, priceYuan: item.priceCents / 100, totalStock: item.totalStock } }
watch(() => route.fullPath, load)
onMounted(load)
</script>

<template>
  <div class="organizer-app">
    <aside class="org-rail" :class="{ open: railOpen }">
      <div class="org-brand"><b>ER</b><div><strong>EventRush</strong><small>主办方中心</small></div><button title="关闭导航" @click="railOpen=false"><X /></button></div>
      <nav><RouterLink to="/organizer/events" :class="{ active: screen !== 'organizer-create' }"><LayoutDashboard />活动管理</RouterLink><RouterLink to="/organizer/events/new" :class="{ active: screen === 'organizer-create' }"><Plus />发布新活动</RouterLink></nav>
      <div class="org-identity"><KeyRound /><div><strong>演示主办方</strong><small>编号 9001</small></div></div>
      <RouterLink class="org-buyer" to="/"><ArrowLeft />返回购票平台</RouterLink>
    </aside>
    <div v-if="railOpen" class="org-backdrop" @click="railOpen=false"></div>
    <main class="org-main">
      <header class="org-topbar"><button class="org-menu" title="打开导航" @click="railOpen=true"><Menu /></button><div><span>主办方中心</span><ChevronRight /><strong>{{ screen === 'organizer-create' ? '发布新活动' : event?.name || '活动管理' }}</strong></div><label><KeyRound /><input v-model="key" aria-label="主办方访问密钥" type="password" /><button @click="reconnect">连接</button></label></header>
      <section v-if="!authorized" class="org-auth"><KeyRound /><h1>连接主办方中心</h1><p>输入主办方访问密钥后继续管理活动。</p><input v-model="key" /><button class="org-primary" @click="reconnect">重新连接</button></section>
      <div v-else class="org-page">
        <p v-if="error" class="org-message error">{{ error }}</p><p v-if="success" class="org-message success"><Check />{{ success }}</p>

        <template v-if="screen === 'organizer-list'">
          <header class="org-page-head"><div><h1>活动管理</h1><p>先处理需要推进的活动，再查看已经上线的项目。</p></div><RouterLink class="org-primary" to="/organizer/events/new"><Plus />发布新活动</RouterLink></header>
          <section class="org-stats"><div><span>全部活动</span><strong>{{ stats.all }}</strong></div><div><span>已发布</span><strong>{{ stats.published }}</strong></div><div><span>待完成草稿</span><strong>{{ stats.drafts }}</strong></div><div><span>配置总票数</span><strong>{{ stats.tickets }}</strong></div></section>
          <section class="org-list"><header><div><h2>我的活动</h2><p>{{ loading ? '正在读取活动' : `共 ${events.length} 个活动` }}</p></div></header><article v-for="item in events" :key="item.id"><img v-bind="imageAttrs(item)" /><div class="org-event-copy"><span :data-status="item.status">{{ status(item.status) }}</span><h3>{{ item.name }}</h3><p>{{ item.location }} · {{ item.sessions.length }} 个场次 · {{ ticketCount(item) }} 张票</p></div><div class="org-next"><small>{{ item.status === 'DRAFT' ? '下一步' : '最近更新' }}</small><strong>{{ item.status === 'DRAFT' ? '补全发布信息' : fmtDate(item.updatedTime) }}</strong></div><RouterLink :to="`/organizer/events/${item.id}`">管理活动<ChevronRight /></RouterLink></article><div v-if="!loading && !events.length" class="org-empty"><CalendarDays /><h3>还没有活动</h3><p>创建第一个草稿，配置场次和票档后发布。</p></div></section>
        </template>

        <template v-else-if="screen === 'organizer-create'">
          <header class="org-page-head"><div><RouterLink class="org-back" to="/organizer/events"><ArrowLeft />活动列表</RouterLink><h1>发布新活动</h1><p>每一步只处理一类决定，最后统一检查。</p></div><span v-if="savedAt" class="org-saved"><Save />已保存到本机 {{ savedAt }}</span></header>
          <ol class="org-stepper"><li v-for="(label,index) in ['基本信息','场次','票档与库存','发布检查']" :key="label" :class="{ current:index===step,done:index<step }"><button :disabled="index>step" @click="index<=step&&(step=index)"><span>{{ index<step?'✓':index+1 }}</span>{{ label }}</button></li></ol>
          <div class="org-workflow"><form class="org-form" @submit.prevent>
            <section v-if="step===0"><div class="org-form-head"><b>1</b><div><h2>活动基本信息</h2><p>这些内容会直接展示给购票用户。</p></div></div><div class="org-fields"><label class="wide"><span>活动名称 *</span><input v-model="draft.name" maxlength="100" placeholder="例如：校园音乐之夜" /></label><label><span>活动地点 *</span><input v-model="draft.location" maxlength="160" /></label><label><span>活动封面</span><input v-model="draft.posterUrl" /></label><label class="wide"><span>活动介绍</span><textarea v-model="draft.description" rows="6" maxlength="1000"></textarea><small>{{ draft.description.length }}/1000</small></label></div></section>
            <section v-else-if="step===1"><div class="org-form-head"><b>2</b><div><h2>首个活动场次</h2><p>发布后仍可继续添加场次。</p></div></div><div class="org-fields two"><label><span>开始时间 *</span><input v-model="draft.startTime" type="datetime-local" /></label><label><span>结束时间 *</span><input v-model="draft.endTime" type="datetime-local" /></label></div></section>
            <section v-else-if="step===2"><div class="org-form-head"><b>3</b><div><h2>首个票档</h2><p>票价和票数决定结算金额与库存边界。</p></div></div><div class="org-fields three"><label><span>票档名称 *</span><input v-model="draft.categoryName" /></label><label><span>票价（元）*</span><input v-model.number="draft.priceYuan" type="number" min="0" step="0.01" /></label><label><span>可售票数 *</span><input v-model.number="draft.totalStock" type="number" min="1" /></label></div><div class="org-derived"><span>预计票面总额</span><strong>{{ money(draft.priceYuan*draft.totalStock*100) }}</strong><small>{{ draft.totalStock }} 张 × {{ money(draft.priceYuan*100) }}</small></div></section>
            <section v-else><div class="org-form-head"><b>4</b><div><h2>发布检查</h2><p>确认后活动会立即进入购票平台。</p></div></div><div class="org-checks"><button v-for="item in checks" :key="item.label" @click="step=item.step"><span :class="item.ok?'ok':'no'">{{ item.ok?'✓':'!' }}</span><div><strong>{{ item.label }}</strong><small>{{ item.value }}</small></div><ChevronRight /></button></div></section>
            <footer><button v-if="step>0" class="org-secondary" @click="step--">上一步</button><span v-else>草稿会自动保存在当前浏览器</span><button v-if="step===0" class="org-primary" @click="saveBasic">{{ busy==='basic'?'保存中':'保存并继续' }}</button><button v-else-if="step===1" class="org-primary" @click="saveSession">{{ busy==='session'?'保存中':'保存并继续' }}</button><button v-else-if="step===2" class="org-primary" @click="saveCategory">{{ busy==='category'?'保存中':'保存并检查' }}</button><button v-else class="org-primary" :disabled="!canPublish" @click="publish">{{ busy==='publish'?'发布中':'确认发布' }}</button></footer>
          </form><aside class="org-preview"><header><strong>购票页预览</strong><span>实时更新</span></header><img v-bind="imageAttrs(draft, '活动封面预览')" /><div><span>{{ fmtDate(draft.startTime) }}</span><h3>{{ draft.name||'活动名称待填写' }}</h3><p>{{ draft.location||'活动地点待填写' }}</p><strong>{{ validTicket(draft)?`${money(draft.priceYuan*100)} 起`:'票价待设置' }}</strong></div><small>预览用于核对主要信息，实际展示以购票页为准。</small></aside></div>
        </template>

        <template v-else-if="event">
          <header class="org-page-head"><div><RouterLink class="org-back" to="/organizer/events"><ArrowLeft />活动列表</RouterLink><div class="org-title"><span :data-status="event.status">{{ status(event.status) }}</span><h1>{{ event.name }}</h1></div><p>{{ event.location }} · 活动编号 {{ event.id }}</p></div><RouterLink v-if="event.status==='PUBLISHED'" class="org-secondary" :to="`/events/${event.id}`"><ExternalLink />查看购票页</RouterLink></header>
          <nav class="org-tabs"><button v-for="item in [{k:'overview',l:'概览'},{k:'sessions',l:'场次与票档'},{k:'orders',l:'订单与售后'},{k:'notices',l:'通知'}]" :key="item.k" :class="{active:tab===item.k}" @click="tab=item.k">{{ item.l }}</button></nav>
          <section v-if="tab==='overview'" class="org-detail"><form @submit.prevent="saveDetail"><header><div><h2>活动信息</h2><p>修改后同步到购票目录。</p></div><button class="org-primary"><Save />保存修改</button></header><div class="org-fields"><label class="wide"><span>活动名称 *</span><input v-model="detail.name" /></label><label><span>活动地点 *</span><input v-model="detail.location" /></label><label><span>封面地址</span><input v-model="detail.posterUrl" /></label><label class="wide"><span>活动介绍</span><textarea v-model="detail.description" rows="6"></textarea></label></div></form><aside><img v-bind="imageAttrs(event)" /><dl><div><dt>活动状态</dt><dd>{{ status(event.status) }}</dd></div><div><dt>场次数</dt><dd>{{ event.sessions.length }}</dd></div><div><dt>配置票数</dt><dd>{{ ticketCount(event) }}</dd></div><div><dt>发布时间</dt><dd>{{ fmtDate(event.publishedTime) }}</dd></div></dl></aside></section>
          <section v-else-if="tab==='sessions'" class="org-manage"><div class="org-sessions"><header><h2>场次与票档</h2><p>按场次检查时间、价格与剩余库存。</p></header><article v-for="session in event.sessions" :key="session.id"><header><div><CalendarDays /><strong>{{ fmtDate(session.startTime) }}</strong><span>至 {{ fmtDate(session.endTime) }}</span></div><button @click="editSession(session)">编辑场次</button></header><div class="org-ticket-row head"><span>票档</span><span>票价</span><span>总票数</span><span>剩余</span><span></span></div><div v-for="cat in session.ticketCategories" :key="cat.id" class="org-ticket-row"><strong>{{ cat.name }}</strong><span>{{ money(cat.priceCents) }}</span><span>{{ cat.totalStock }}</span><span>{{ cat.remainingStock }}</span><button @click="editCategory(cat)">编辑</button></div></article></div><aside class="org-tools"><form @submit.prevent="saveManagedSession"><h3><CalendarDays />{{ sessionForm.id?'编辑场次':'添加场次' }}</h3><label><span>开始时间</span><input v-model="sessionForm.startTime" type="datetime-local" /></label><label><span>结束时间</span><input v-model="sessionForm.endTime" type="datetime-local" /></label><button class="org-primary">保存场次</button></form><form @submit.prevent="saveManagedCategory"><h3><Ticket />{{ categoryForm.id?'编辑票档':'添加票档' }}</h3><label><span>所属场次</span><select v-model="categoryForm.sessionId"><option value="" disabled>选择场次</option><option v-for="s in event.sessions" :key="s.id" :value="String(s.id)">{{ fmtDate(s.startTime) }}</option></select></label><label><span>票档名称</span><input v-model="categoryForm.name" /></label><div><label><span>票价（元）</span><input v-model.number="categoryForm.priceYuan" type="number" min="0" /></label><label><span>总票数</span><input v-model.number="categoryForm.totalStock" type="number" min="1" /></label></div><button class="org-primary">保存票档</button></form></aside></section>
          <section v-else-if="tab==='orders'" class="org-orders"><header class="org-orders-head"><div><h2>订单与售后</h2><p>只读查看订单状态、退票进度与出票结果，退款操作仍由购票用户发起。</p></div><button class="org-secondary" :disabled="ordersLoading" @click="loadOrders(event.id)"><ClipboardList />{{ ordersLoading ? '刷新中' : '刷新订单' }}</button></header><div class="org-order-stats"><div><span>订单总数</span><strong>{{ orderStats.total }}</strong></div><div><span>已付款订单</span><strong>{{ orderStats.paid }}</strong></div><div><span>发生退票</span><strong>{{ orderStats.refunding }}</strong></div><div><span>已出票</span><strong>{{ orderStats.issued }}</strong></div></div><p v-if="ordersError" class="org-message error">{{ ordersError }}</p><div v-if="ordersLoading" class="org-order-empty"><ClipboardList /><p>正在读取订单摘要…</p></div><div v-else-if="!eventOrders.length" class="org-order-empty"><ClipboardList /><h3>暂时没有订单</h3><p>用户完成下单后，订单状态会在这里按场次汇总。</p></div><div v-else class="org-order-list"><article v-for="order in eventOrders" :key="order.id" class="org-order-row"><div class="org-order-main"><div class="org-order-id"><strong>#{{ order.id }}</strong><span :class="`org-order-status ${orderStatusTone(order.status)}`">{{ orderStatus(order.status) }}</span></div><p>用户 {{ order.userId }} · {{ order.ticketCategoryName }} · {{ fmtDate(order.sessionStartTime) }}</p></div><dl><div><dt>数量</dt><dd>{{ order.quantity }} 张</dd></div><div><dt>金额</dt><dd>{{ money(order.amountCents) }}</dd></div><div><dt>退票</dt><dd>{{ order.refundedQuantity }} 张</dd></div><div><dt>出票</dt><dd>{{ order.issuedTicketCount }} 张</dd></div></dl><time>{{ fmtDate(order.createdTime) }}</time></article></div></section>
          <section v-else class="org-notices"><div><header><h2>已发布通知</h2><p>只发布确实影响用户行程的信息。</p></header><article v-for="item in event.notices" :key="item.id"><Bell /><div><span>{{ fmtDate(item.publishedTime) }}</span><h3>{{ item.title }}</h3><p>{{ item.content }}</p></div></article><p v-if="!event.notices.length" class="org-empty-inline"><Bell />暂时没有通知。</p></div><form @submit.prevent="publishNotice"><h3><Bell />发布通知</h3><p>写清变化、何时生效以及用户需要做什么。</p><label><span>通知标题 *</span><input v-model="notice.title" /></label><label><span>通知内容 *</span><textarea v-model="notice.content" rows="7"></textarea></label><button class="org-primary" :disabled="event.status!=='PUBLISHED'">{{ event.status==='PUBLISHED'?'发布通知':'活动发布后可用' }}</button></form></section>
        </template>
      </div>
    </main>
  </div>
</template>

<style src="./OrganizerApp.css"></style>
