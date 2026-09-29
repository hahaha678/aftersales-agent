<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { createTicket, type TicketInput } from '../api/tickets'
import { conversations } from '../api/agent'
import { getOrders } from '../api/orders'
import { listAftersales, type Aftersale } from '../api/aftersales'
import type { OrderSummary } from '../api/types'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(),
    router = useRouter()
const problem = ref(''),
    orderId = ref(''),
    aftersaleId = ref(''),
    confirmed = ref(false)
const orders = ref<OrderSummary[]>([]),
    sales = ref<Aftersale[]>([]),
    title = ref('')
const loading = ref(false),
    busy = ref(false),
    error = ref<unknown>(null)
const pending = ref<TicketInput | null>(null)
const conversationId = computed(() => (typeof route.query.conversation === 'string' ? route.query.conversation : ''))
const options = computed(() => sales.value.filter((s) => !orderId.value || s.orderId === orderId.value))
let generation = 0,
    controller: AbortController | undefined
watch(orderId, () => {
    aftersaleId.value = ''
})
async function load() {
    controller?.abort()
    controller = new AbortController()
    const current = ++generation
    loading.value = true
    error.value = null
    title.value = ''
    pending.value = null
    confirmed.value = false
    try {
        const [chats, orderPage, salePage] = await Promise.all([
            conversations(controller.signal),
            getOrders(new URLSearchParams({ size: '100' }), controller.signal),
            listAftersales(new URLSearchParams({ size: '100' }), false, controller.signal),
        ])
        if (current !== generation) return
        const chat = chats.find((c) => c.id === conversationId.value)
        if (!chat) throw new Error('请从智能售后的当前会话进入转人工页面。')
        title.value = chat.title
        orders.value = orderPage.items
        sales.value = salePage.items
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) loading.value = false
    }
}
async function submit() {
    if (busy.value || loading.value || !confirmed.value || !title.value) return
    const current = generation
    pending.value ??= {
        conversationId: conversationId.value,
        requestKey: crypto.randomUUID(),
        problem: problem.value.trim(),
        orderId: orderId.value || null,
        aftersaleId: aftersaleId.value || null,
    }
    busy.value = true
    error.value = null
    try {
        const ticket = await createTicket(pending.value)
        if (current === generation) await router.push(`/tickets/${ticket.id}`)
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) busy.value = false
    }
}
watch(
    conversationId,
    () => {
        problem.value = ''
        orderId.value = ''
        aftersaleId.value = ''
        busy.value = false
        void load()
    },
    { immediate: true },
)
onBeforeUnmount(() => {
    generation++
    controller?.abort()
})
</script>
<template>
    <RouterLink class="back-link" :to="{ path: '/assistant', query: { conversation: conversationId } }"
        >← 返回会话</RouterLink
    >
    <h1>转人工处理</h1>
    <ErrorNotice :error="error"
        ><button class="button secondary" :disabled="busy" @click="load">重新加载并核对</button></ErrorNotice
    >
    <p v-if="loading" role="status">正在读取会话和可关联记录…</p>
    <form v-if="title" class="surface aftersale-form" @submit.prevent="submit">
        <h2>{{ title }}</h2>
        <p class="muted">
            这是异步工单，提交后请到“人工工单”查看处理结果。同一会话已有未关闭工单时返回原工单，不覆盖原问题。
        </p>
        <fieldset :disabled="busy || !!pending">
            <label for="ticket-problem">希望客服解决的问题</label>
            <textarea
                id="ticket-problem"
                v-model="problem"
                required
                maxlength="2000"
                rows="5"
                placeholder="例如：我需要确认这笔退货应寄到哪个地址"
            ></textarea>
            <label for="ticket-order">关联订单（可选，最近100条）</label>
            <select id="ticket-order" v-model="orderId">
                <option value="">不关联订单</option>
                <option v-for="order in orders" :key="order.id" :value="order.id">{{ order.orderNumber }}</option>
            </select>
            <label for="ticket-sale">关联售后申请（可选，最近100条）</label>
            <select id="ticket-sale" v-model="aftersaleId">
                <option value="">不关联售后申请</option>
                <option v-for="sale in options" :key="sale.id" :value="sale.id">
                    #{{ sale.id }} · {{ sale.orderNumber }} · {{ sale.productName }}
                </option>
            </select>
            <label class="check-label"
                ><input
                    v-model="confirmed"
                    type="checkbox"
                    required
                />我确认提交问题，并向客服共享此会话提交时已有的聊天记录。请勿填写密码、验证码等信息。</label
            >
        </fieldset>
        <p v-if="pending" class="muted">请求内容已保留，重试会复用原请求；修改前请重新加载并核对。</p>
        <button class="button primary" :disabled="busy || loading || !confirmed || !problem.trim()">
            {{ busy ? '正在提交…' : '提交人工工单' }}
        </button>
    </form>
</template>
