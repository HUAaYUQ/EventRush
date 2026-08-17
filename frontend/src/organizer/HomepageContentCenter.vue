<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRight, GalleryHorizontal, Image } from 'lucide-vue-next'

const props = defineProps({ rows: { type: Array, default: () => [] }, loading: Boolean })

const totals = computed(() => ({
  configured: props.rows.filter((row) => row.banner).length,
  published: props.rows.filter((row) => row.banner?.status === 'PUBLISHED').length,
  pending: props.rows.filter((row) => row.banner?.status === 'DRAFT').length,
}))

function state(row) {
  if (row.event.status !== 'PUBLISHED') return '活动未发布'
  if (row.banner?.status === 'PUBLISHED') return '首页展示中'
  if (row.banner?.status === 'DRAFT') return '内容待发布'
  return '尚未配置'
}
</script>

<template>
  <header class="org-page-head">
    <div><h1>首页内容</h1><p>统一控制购票首页主视觉，只有已发布的活动和内容版本才能对外展示。</p></div>
    <RouterLink class="org-secondary" to="/"><GalleryHorizontal />查看购票首页</RouterLink>
  </header>
  <section class="org-content-stats">
    <div><span>可配置活动</span><strong>{{ rows.length }}</strong></div>
    <div><span>已配置</span><strong>{{ totals.configured }}</strong></div>
    <div><span>首页展示中</span><strong>{{ totals.published }}</strong></div>
    <div><span>待发布内容</span><strong>{{ totals.pending }}</strong></div>
  </section>
  <section class="org-content-list">
    <header><div><h2>主视觉内容清单</h2><p>按活动进入编辑，图片、文案、城市、展示周期和顺序均由后台决定。</p></div></header>
    <p v-if="loading" class="org-content-empty">正在读取首页内容…</p>
    <article v-for="row in rows" v-else :key="row.event.id">
      <div class="org-content-image" :class="{ empty: !row.banner?.imageUrl && !row.event.posterUrl }">
        <img v-if="row.banner?.imageUrl || row.event.posterUrl" :src="row.banner?.imageUrl || row.event.posterUrl" :alt="`${row.event.name} 主视觉`" />
        <Image v-else />
      </div>
      <div class="org-content-copy">
        <span :data-status="row.banner?.status || 'NONE'">{{ state(row) }}</span>
        <h3>{{ row.banner?.title || row.event.name }}</h3>
        <p>{{ row.event.city }} · {{ row.event.location }}</p>
      </div>
      <dl>
        <div><dt>展示顺序</dt><dd>{{ row.banner?.displayOrder ?? '未设置' }}</dd></div>
        <div><dt>展示周期</dt><dd>{{ row.banner ? '已设置' : '未设置' }}</dd></div>
      </dl>
      <RouterLink :to="`/organizer/events/${row.event.id}?tab=homepage`">配置内容<ChevronRight /></RouterLink>
    </article>
    <p v-if="!loading && !rows.length" class="org-content-empty">当前没有可配置的活动，请先创建活动商品。</p>
  </section>
</template>
