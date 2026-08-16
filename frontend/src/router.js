import { createRouter, createWebHistory } from 'vue-router'

const SurfaceRoute = { render: () => null }

const routes = [
  { path: '/', name: 'discover', component: SurfaceRoute, meta: { surface: 'customer', view: 'booking', screen: 'discover' } },
  { path: '/events/:eventId', name: 'event-detail', component: SurfaceRoute, meta: { surface: 'customer', view: 'booking', screen: 'event' } },
  { path: '/checkout', name: 'checkout', component: SurfaceRoute, meta: { surface: 'customer', view: 'booking', screen: 'checkout' } },
  { path: '/account', name: 'account', component: SurfaceRoute, meta: { surface: 'customer', view: 'tickets', screen: 'account' } },
  { path: '/my', redirect: '/account' },
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
