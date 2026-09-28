import { createRouter, createWebHistory } from 'vue-router'
import OverviewView from '../views/OverviewView.vue'
import PlannedView from '../views/PlannedView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: OverviewView, meta: { title: '项目总览' } },
    { path: '/assistant', component: PlannedView, meta: { title: '智能售后', description: '在对话中查询订单、了解售后政策，并在确认后提交申请。' } },
    { path: '/orders', component: PlannedView, meta: { title: '我的订单', description: '查看订单商品与物流信息，选择需要售后的商品。' } },
    { path: '/aftersales', component: PlannedView, meta: { title: '售后记录', description: '追踪退货退款和换货进度，查看申请详情与处理时间线。' } },
    { path: '/:pathMatch(.*)*', component: PlannedView, meta: { title: '页面未找到', description: '这个地址暂时没有对应的页面，请返回项目总览。' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title)} · 售后助手`
})

export default router
