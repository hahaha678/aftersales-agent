<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getOrder, getShipments } from '../api/orders'
import type { OrderDetail, Shipment } from '../api/types'
import { ApiError } from '../api/http'
import { statuses, dateTime, money } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute()
const order = ref<OrderDetail | null>(null), shipments = ref<Shipment[]>([])
const busy = ref(false), shippingBusy = ref(false), error = ref<unknown>(null), shippingError = ref<unknown>(null)
const shipmentLabels = { IN_TRANSIT: '运输中', DELIVERED: '已签收', EXCEPTION: '物流异常' }
let controller: AbortController | undefined, version = 0
async function loadShipping() {
  if (!order.value || !controller) return
  const current = version, id = order.value.id
  shippingBusy.value = true; shippingError.value = null
  try { const result = await getShipments(id, controller.signal); if (current === version) shipments.value = result }
  catch (cause) { if (current === version && !(cause instanceof Error && cause.name === 'AbortError')) shippingError.value = cause }
  finally { if (current === version) shippingBusy.value = false }
}
async function load() {
  controller?.abort(); controller = new AbortController(); const current = ++version
  busy.value = true; error.value = null; order.value = null; shipments.value = []; shippingError.value = null
  try {
    const result = await getOrder(String(route.params.id), controller.signal)
    if (current === version) { order.value = result; void loadShipping() }
  } catch (cause) { if (current === version && !(cause instanceof Error && cause.name === 'AbortError')) error.value = cause }
  finally { if (current === version) busy.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => { version++; controller?.abort() })
</script>
<template>
  <RouterLink class="back-link" :to="{ path: '/orders', query: route.query }">← 返回我的订单</RouterLink>
  <div v-if="busy" class="state-panel" role="status"><span class="spinner"></span>正在加载订单详情…</div>
  <div v-else-if="error"><section class="page-heading"><h1>{{ error instanceof ApiError && error.status === 404 ? '无法查看这笔订单' : '订单暂时无法加载' }}</h1></section><ErrorNotice :error="error"><button v-if="!(error instanceof ApiError && error.status === 404)" class="button secondary" @click="load">重试</button></ErrorNotice></div>
  <template v-else-if="order">
    <section class="detail-heading"><div><p class="eyebrow">ORDER DETAILS</p><h1>订单详情</h1><p class="muted">{{ order.orderNumber }}</p></div><span class="status-pill" :class="order.status">{{ statuses[order.status] || order.status }}</span></section>
    <div class="detail-layout"><div>
      <section class="surface"><div class="section-heading"><h2>商品清单</h2><span>{{ order.items.length }} 个商品项</span></div>
        <div v-for="item in order.items" :key="item.id" class="product-row"><div class="parcel-icon" aria-hidden="true">▣</div><div class="product-info"><h3>{{ item.productName }}</h3><p>{{ item.specification || '标准规格' }}</p><small>SKU {{ item.skuId }}</small></div><div class="product-price"><strong>{{ money(item.paidAmount) }}</strong><span>共 {{ item.quantity }} 件 · 整行实付</span></div></div>
        <div v-if="!order.items.length" class="notice">暂无商品信息。</div>
        <div class="total-row"><span>订单实付</span><strong>{{ money(order.paidAmount) }}</strong></div>
      </section>
      <section class="surface shipping-panel"><div class="section-heading"><h2>物流进度</h2><span>原订单配送</span></div>
        <p v-if="shippingBusy" class="muted" role="status">正在查询物流…</p>
        <ErrorNotice v-else-if="shippingError" :error="shippingError"><button class="button secondary" @click="loadShipping">重试物流查询</button></ErrorNotice>
        <div v-else-if="!shipments.length" class="shipping-empty"><span aria-hidden="true">◇</span><p>暂无发货记录</p><small>包裹发出后，可在这里查看物流信息。</small></div>
        <article v-for="shipment in shipments" v-else :key="shipment.id"><div class="shipment-heading"><div><strong>{{ shipment.carrier }}</strong><p class="muted">运单号 {{ shipment.trackingNumber }}</p></div><span class="status-pill">{{ shipmentLabels[shipment.status] }}</span></div><ol v-if="shipment.events.length" class="timeline"><li v-for="(event, index) in shipment.events" :key="`${event.occurredAt}-${index}`"><span class="timeline-dot"></span><div><p>{{ event.description }}</p><time>{{ dateTime(event.occurredAt) }}</time></div></li></ol><p v-else class="muted">承运商暂未提供物流轨迹。</p></article>
      </section>
    </div><aside class="order-sidebar"><section class="surface"><h2>订单信息</h2><dl class="order-facts"><dt>下单时间</dt><dd>{{ dateTime(order.createdAt) }}</dd><dt>支付时间</dt><dd>{{ dateTime(order.paidAt) }}</dd><dt>签收时间</dt><dd>{{ dateTime(order.signedAt) }}</dd><dt>结算币种</dt><dd>{{ order.currency }}</dd></dl></section><section class="service-note"><span class="module-icon">↺</span><h2>需要售后帮助？</h2><p>签收后 7 天内可申请退货退款，点击查看商品资格和剩余可申请数量。</p><RouterLink class="button secondary" :to="`/orders/${order.id}/aftersales/new`">查询资格并申请</RouterLink></section></aside></div>
  </template>
</template>
