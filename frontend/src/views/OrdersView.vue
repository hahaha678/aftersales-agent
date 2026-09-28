<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { getOrders } from '../api/orders'
import type { OrderPage } from '../api/types'
import { statuses, dateTime, money } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(), router = useRouter()
const data = ref<OrderPage | null>(null), busy = ref(false), error = ref<unknown>(null)
const status = ref(''), number = ref(''), size = ref(20)
const pages = computed(() => data.value ? Math.max(1, Math.ceil(data.value.total / data.value.size)) : 1)
let controller: AbortController | undefined, version = 0
function pageValue() { const n = Number(route.query.page || 1); return Number.isInteger(n) && n > 0 && n <= 2147483647 ? n : 1 }
async function load() {
  controller?.abort(); controller = new AbortController(); const current = ++version
  status.value = typeof route.query.status === 'string' && Object.prototype.hasOwnProperty.call(statuses, route.query.status) ? route.query.status : ''
  number.value = typeof route.query.orderNumber === 'string' ? route.query.orderNumber : ''
  const count = Number(route.query.size || 20); size.value = [10, 20, 50, 100].includes(count) ? count : 20
  busy.value = true; data.value = null; error.value = null
  const params = new URLSearchParams({ page: String(pageValue()), size: String(size.value) })
  if (status.value) params.set('status', status.value)
  if (number.value) params.set('orderNumber', number.value)
  try { const result = await getOrders(params, controller.signal); if (current === version) data.value = result }
  catch (cause) { if (current === version && !(cause instanceof Error && cause.name === 'AbortError')) error.value = cause }
  finally { if (current === version) busy.value = false }
}
function search() {
  const query = { page: '1', size: String(size.value), ...(status.value ? { status: status.value } : {}), ...(number.value ? { orderNumber: number.value } : {}) }
  if (router.resolve({ path: '/orders', query }).fullPath === route.fullPath) void load()
  else void router.push({ path: '/orders', query })
}
function reset() { status.value = ''; number.value = ''; size.value = 20; search() }
function move(page: number) { void router.push({ path: '/orders', query: { ...route.query, page: String(page) } }) }
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => { version++; controller?.abort() })
</script>
<template>
  <section class="page-heading"><p class="eyebrow">YOUR PURCHASES</p><h1>我的订单</h1><p>查看每笔订单的商品、状态与物流进度。</p></section>
  <form class="filter-panel" @submit.prevent="search">
    <div><label for="order-number">订单号</label><input id="order-number" v-model="number" maxlength="64" placeholder="输入完整订单号" :disabled="busy"></div>
    <div><label for="order-status">订单状态</label><select id="order-status" v-model="status" :disabled="busy"><option value="">全部状态</option><option v-for="(label, key) in statuses" :key="key" :value="key">{{ label }}</option></select></div>
    <div><label for="page-size">每页条数</label><select id="page-size" v-model.number="size" :disabled="busy"><option v-for="n in [10,20,50,100]" :key="n" :value="n">{{ n }} 条</option></select></div>
    <button class="button primary" :disabled="busy">查询订单</button><button type="button" class="button secondary" :disabled="busy" @click="reset">重置</button>
  </form>
  <div v-if="busy" class="state-panel" role="status"><span class="spinner"></span>正在加载订单…</div>
  <ErrorNotice v-else-if="error" :error="error"><button class="button secondary" @click="load">重新加载</button></ErrorNotice>
  <template v-else-if="data">
    <div class="section-heading"><h2>订单记录 <span class="count">{{ data.total }}</span></h2><span>按下单时间由近到远排列</span></div>
    <div v-if="!data.items.length" class="state-panel"><span class="empty-symbol" aria-hidden="true">▤</span><h2>{{ data.total ? '这一页没有订单' : '没有找到订单' }}</h2><p>{{ data.total ? '试试返回第一页查看。' : '你可以调整筛选条件，或稍后再来查看。' }}</p><button class="button secondary" @click="data.page > 1 ? move(1) : reset()">{{ data.page > 1 ? '返回第一页' : '清除筛选' }}</button></div>
    <div v-else class="order-list">
      <article v-for="order in data.items" :key="order.id" class="order-card">
        <div class="order-card-head"><span>订单号 <strong>{{ order.orderNumber }}</strong></span><span class="status-pill" :class="order.status">{{ statuses[order.status] || order.status }}</span></div>
        <div class="order-card-body"><div class="parcel-icon" aria-hidden="true">▣</div><div class="order-card-info"><h3>{{ order.totalQuantity }} 件商品</h3><p>下单时间 {{ dateTime(order.createdAt) }}</p></div><div class="order-amount"><small>实付金额</small><strong>{{ money(order.paidAmount) }}</strong></div><RouterLink class="button secondary" :to="{ path: `/orders/${order.id}`, query: route.query }">查看详情 <span aria-hidden="true">→</span></RouterLink></div>
      </article>
    </div>
    <nav class="pagination" aria-label="订单分页"><span>第 {{ data.page }} 页 · 共 {{ pages }} 页</span><div><button class="button secondary" :disabled="data.page <= 1" @click="move(data.page - 1)">上一页</button><button class="button secondary" :disabled="data.page >= pages" @click="move(data.page + 1)">下一页</button></div></nav>
  </template>
</template>
