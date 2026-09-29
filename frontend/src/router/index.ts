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
        {
            path: '/orders',
            component: () => import('../views/OrdersView.vue'),
            meta: { title: '我的订单', requiresAuth: true },
        },
        {
            path: '/orders/:id',
            component: () => import('../views/OrderDetailView.vue'),
            meta: { title: '订单详情', requiresAuth: true },
        },
        {
            path: '/assistant',
            component: () => import('../views/AssistantView.vue'),
            meta: { title: '智能售后', requiresAuth: true },
        },
        {
            path: '/orders/:id/aftersales/new',
            component: () => import('../views/AftersaleApplyView.vue'),
            meta: { title: '申请售后', requiresAuth: true },
        },
        {
            path: '/aftersales',
            component: () => import('../views/AftersaleListView.vue'),
            meta: { title: '售后记录', requiresAuth: true },
        },
        {
            path: '/aftersales/:id',
            component: () => import('../views/AftersaleDetailView.vue'),
            meta: { title: '售后详情', requiresAuth: true },
        },
        {
            path: '/staff/aftersales',
            component: () => import('../views/AftersaleListView.vue'),
            meta: { title: '客服审核', requiresAuth: true, staff: true },
        },
        {
            path: '/staff/aftersales/:id',
            component: () => import('../views/AftersaleDetailView.vue'),
            meta: { title: '审核详情', requiresAuth: true, staff: true },
        },
        {
            path: '/:pathMatch(.*)*',
            component: PlannedView,
            meta: { title: '页面未找到', description: '这个地址没有对应的页面，请返回服务首页。' },
        },
    ],
    scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async (to) => {
    await restoreSession()
    if (to.meta.requiresAuth && !session.token) return { path: '/login', query: { redirect: to.fullPath } }
    if (to.meta.staff && session.user && session.user.role !== 'STAFF') return '/aftersales'
    if (to.path === '/login' && session.user) return '/orders'
})
router.afterEach((to) => {
    document.title = `${String(to.meta.title)} · 售后助手`
})
window.addEventListener('session-expired', () => {
    const current = router.currentRoute.value
    if (current.meta.requiresAuth)
        void router.replace({ path: '/login', query: { expired: '1', redirect: current.fullPath } })
})
export default router
