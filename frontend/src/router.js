import { createRouter, createWebHistory } from 'vue-router'

const SurfaceRoute = { render: () => null }

const routes = [
  { path: '/', name: 'booking', component: SurfaceRoute, meta: { surface: 'customer', view: 'booking' } },
  { path: '/my', name: 'my-tickets', component: SurfaceRoute, meta: { surface: 'customer', view: 'tickets' } },
  { path: '/gate', name: 'gate', component: SurfaceRoute, meta: { surface: 'gate', view: 'gate' } },
  { path: '/ops', name: 'operations', component: SurfaceRoute, meta: { surface: 'ops', view: 'evidence' } },
  { path: '/lab', name: 'engineering-lab', component: SurfaceRoute, meta: { surface: 'lab', view: 'evidence' } },
  { path: '/demo', name: 'demo-entry', component: SurfaceRoute, meta: { surface: 'demo', view: 'demo' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})
