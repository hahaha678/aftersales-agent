import { createRouter, createWebHistory } from 'vue-router'
import OverviewView from '../views/OverviewView.vue'
import PlannedView from '../views/PlannedView.vue'
import { session } from '../state/session'
import { restoreSession } from '../services/auth'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: OverviewView, meta: { title: '服务首页' } },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/orders', component: () => import('../views/OrdersView.vue'), meta: { title: '我的订单', requiresAuth: true } },
    { path: '/orders/:id', component: () => import('../views/OrderDetailView.vue'), meta: { title: '订单详情', requiresAuth: true } },
    { path: '/assistant', component: PlannedView, meta: { title: '智能售后', description: '通过对话了解政策、查询订单并获取售后帮助。', planned: true } },
    { path: '/aftersales', component: PlannedView, meta: { title: '售后记录', description: '在这里跟进退货退款、换货申请的处理进度。', planned: true } },
    { path: '/:pathMatch(.*)*', component: PlannedView, meta: { title: '页面未找到', description: '这个地址没有对应的页面，请返回服务首页。' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async to => {
  await restoreSession()
  if (to.meta.requiresAuth && !session.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && session.user) return '/orders'
})
router.afterEach(to => { document.title = `${String(to.meta.title)} · 售后助手` })
window.addEventListener('session-expired', () => {
  const current = router.currentRoute.value
  if (current.meta.requiresAuth) void router.replace({ path: '/login', query: { expired: '1', redirect: current.fullPath } })
})
export default router
