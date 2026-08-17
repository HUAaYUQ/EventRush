<script setup>
import { reactive } from 'vue'
import { Check, Pencil, Plus, Tags } from 'lucide-vue-next'

const props = defineProps({ categories: { type: Array, default: () => [] }, api: { type: Function, required: true } })
const emit = defineEmits(['reload', 'error', 'success'])
const editor = reactive({ id: null, name: '', iconKey: 'ticket', contentProfile: 'GENERAL', displayOrder: 0, enabled: true })
let saving = false
const profileLabels = { PERFORMANCE: '演出模板', SPORTS: '赛事模板', EXHIBITION: '展览模板', FAMILY: '亲子模板', GENERAL: '通用模板' }

function reset() { Object.assign(editor, { id: null, name: '', iconKey: 'ticket', contentProfile: 'GENERAL', displayOrder: 0, enabled: true }) }
function edit(item) { Object.assign(editor, { id: item.id, name: item.name, iconKey: item.iconKey || 'ticket', contentProfile: item.contentProfile || 'GENERAL', displayOrder: item.displayOrder, enabled: item.enabled }) }
async function save() {
  if (!editor.name.trim() || saving) return
  saving = true
  try {
    await props.api(editor.id ? `/api/organizer/catalog/categories/${editor.id}` : '/api/organizer/catalog/categories', {
      method: editor.id ? 'PUT' : 'POST',
      body: JSON.stringify({ name: editor.name.trim(), iconKey: editor.iconKey.trim(), contentProfile: editor.contentProfile, displayOrder: Number(editor.displayOrder), enabled: editor.enabled }),
    })
    emit('success', editor.id ? '类目修改已保存' : '类目已创建')
    reset()
    emit('reload')
  } catch (error) { emit('error', error.message) } finally { saving = false }
}
</script>

<template>
  <header class="org-page-head"><div><h1>类目管理</h1><p>这里决定购票首页的类目导航，停用后不再对用户展示，但不会删除历史活动。</p></div></header>
  <div class="org-category-layout">
    <section class="org-category-list">
      <header><div><h2>活动类目</h2><p>数字越小越靠前，只有启用类目可以用于公开浏览。</p></div><span>{{ categories.length }} 个类目</span></header>
      <article v-for="item in categories" :key="item.id">
        <div class="org-category-icon"><Tags /></div>
        <div><strong>{{ item.name }}</strong><small>{{ profileLabels[item.contentProfile] || '通用模板' }} · {{ item.iconKey || 'ticket' }}</small></div>
        <span>顺序 {{ item.displayOrder }}</span>
        <b :class="{ disabled: !item.enabled }">{{ item.enabled ? '已启用' : '已停用' }}</b>
        <button type="button" title="编辑类目" @click="edit(item)"><Pencil /></button>
      </article>
      <p v-if="!categories.length" class="org-content-empty">还没有类目，请先在右侧创建。</p>
    </section>
    <form class="org-category-editor" @submit.prevent="save">
      <header><div><h2>{{ editor.id ? '编辑类目' : '创建类目' }}</h2><p>保存后购票端类目导航会读取最新启用配置。</p></div></header>
      <label><span>类目名称 *</span><input v-model="editor.name" maxlength="40" /></label>
      <label><span>图标标识</span><input v-model="editor.iconKey" maxlength="40" /></label>
      <label><span>详情内容模板 *</span><select v-model="editor.contentProfile"><option value="PERFORMANCE">演出模板</option><option value="SPORTS">赛事模板</option><option value="EXHIBITION">展览模板</option><option value="FAMILY">亲子模板</option><option value="GENERAL">通用模板</option></select><small>决定发布活动时出现的须知字段和购票端标题</small></label>
      <label><span>展示顺序</span><input v-model.number="editor.displayOrder" type="number" min="0" max="999" /></label>
      <label class="org-switch"><input v-model="editor.enabled" type="checkbox" /><span><strong>启用类目</strong><small>关闭后不会出现在购票首页导航</small></span></label>
      <footer><button v-if="editor.id" type="button" class="org-secondary" @click="reset">取消编辑</button><button class="org-primary" :disabled="!editor.name.trim()"><Check v-if="editor.id" /><Plus v-else />{{ editor.id ? '保存修改' : '创建类目' }}</button></footer>
    </form>
  </div>
</template>
