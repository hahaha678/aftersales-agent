<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import * as api from '../api/tickets'
import type { Message } from '../api/agent'
import { session } from '../state/session'
import { dateTime } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(),
    staff = computed(() => !!route.meta.staff),
    base = computed(() => (staff.value ? '/staff/tickets' : '/tickets'))
const rows = ref<api.Ticket[]>([]),
    ticket = ref<api.Ticket | null>(null),
    messages = ref<Message[]>([])
const status = ref(''),
    resolution = ref(''),
    confirm = ref(false),
    more = ref(false),
    older = ref(false)
const loading = ref(false),
    busy = ref(false),
    error = ref<unknown>(null)
let generation = 0,
    controller: AbortController | undefined
async function load(append = false) {
    controller?.abort()
    controller = new AbortController()
    const current = ++generation
    loading.value = true
    error.value = null
    if (!append) {
        rows.value = []
        ticket.value = null
        messages.value = []
        resolution.value = ''
        confirm.value = false
    }
    try {
        if (route.params.id) {
            const [detail, context] = await Promise.all([
                api.getTicket(String(route.params.id), staff.value, controller.signal),
                api.ticketContext(String(route.params.id), staff.value, undefined, controller.signal),
            ])
            if (current !== generation) return
            ticket.value = detail
            messages.value = context
            older.value = context.length === 50
        } else {
            const result = await api.listTickets(
                staff.value,
                status.value,
                append ? rows.value.at(-1)?.id : undefined,
                controller.signal,
            )
            if (current !== generation) return
            rows.value = append ? [...rows.value, ...result] : result
            more.value = result.length === 50
        }
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) loading.value = false
    }
}
async function earlier() {
    if (loading.value || !ticket.value) return
    const current = generation
    loading.value = true
    error.value = null
    try {
        const result = await api.ticketContext(ticket.value.id, staff.value, messages.value[0]?.id, controller?.signal)
        if (current !== generation) return
        messages.value = [...result, ...messages.value]
        older.value = result.length === 50
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) loading.value = false
    }
}
async function act() {
    if (busy.value || loading.value || !ticket.value || !confirm.value) return
    const current = generation,
        row = ticket.value
    busy.value = true
    error.value = null
    try {
        const result =
            row.status === 'OPEN'
                ? await api.claimTicket(row.id)
                : await api.resolveTicket(row.id, resolution.value.trim())
        if (current === generation) {
            ticket.value = result
            confirm.value = false
        }
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) busy.value = false
    }
}
watch(
    () => route.fullPath,
    () => {
        busy.value = false
        status.value = ''
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
    <h1>{{ staff ? '客服工单工作台' : '我的人工工单' }}</h1>
    <p class="muted">异步处理问题；解决工单不会自动修改订单、售后或退款状态。</p>
    <ErrorNotice :error="error" />
    <button class="button secondary" :disabled="busy || loading" @click="load()">刷新</button>
    <p v-if="loading" role="status">正在加载…</p>
    <template v-if="!route.params.id">
        <label for="ticket-status">工单状态</label>
        <select id="ticket-status" v-model="status" :disabled="loading" @change="load()">
            <option value="">全部</option>
            <option v-for="(label, key) in api.ticketStatuses" :key="key" :value="key">{{ label }}</option>
        </select>
        <p v-if="!rows.length && !loading">暂无工单。可从智能售后会话中的“转人工”入口提交。</p>
        <section v-for="row in rows" :key="row.id" class="surface">
            <RouterLink :to="`${base}/${row.id}`"
                ><h2>#{{ row.id }} · {{ api.ticketStatuses[row.status] }}</h2>
                <p class="preserve-text">{{ row.problem }}</p>
                <small>{{ dateTime(row.createdAt) }} · 查看详情 →</small></RouterLink
            >
        </section>
        <button v-if="more" class="button secondary" :disabled="loading" @click="load(true)">加载更多工单</button>
    </template>
    <template v-else-if="ticket">
        <RouterLink class="back-link" :to="base">← 返回工单列表</RouterLink>
        <section class="surface">
            <h2>工单 #{{ ticket.id }} · {{ api.ticketStatuses[ticket.status] }}</h2>
            <p class="preserve-text">{{ ticket.problem }}</p>
            <p class="muted">
                {{ dateTime(ticket.createdAt) }} · {{ ticket.assigneeId ? '客服已领取' : '等待客服领取' }}
            </p>
            <p v-if="ticket.orderId">
                关联订单 ID：{{ ticket.orderId }}
                <RouterLink v-if="!staff" :to="`/orders/${ticket.orderId}`">查看订单 →</RouterLink>
            </p>
            <RouterLink v-if="ticket.aftersaleId" :to="`${staff ? '/staff' : ''}/aftersales/${ticket.aftersaleId}`"
                >查看关联售后 #{{ ticket.aftersaleId }} →</RouterLink
            >
            <p v-if="ticket.resolution" class="preserve-text"><strong>客服处理结果：</strong>{{ ticket.resolution }}</p>
        </section>
        <form
            v-if="
                staff &&
                (ticket.status === 'OPEN' ||
                    (ticket.status === 'IN_PROGRESS' && ticket.assigneeId === session.user?.id))
            "
            class="surface aftersale-form"
            @submit.prevent="act"
        >
            <h2>{{ ticket.status === 'OPEN' ? '领取工单' : '回复并解决' }}</h2>
            <fieldset :disabled="busy || loading">
                <template v-if="ticket.status === 'IN_PROGRESS'"
                    ><label for="resolution">处理结果（用户可见）</label
                    ><textarea id="resolution" v-model="resolution" required maxlength="2000" rows="4"></textarea>
                </template>
                <label class="check-label"
                    ><input v-model="confirm" type="checkbox" required />{{
                        ticket.status === 'OPEN' ? '确认由我跟进此工单' : '已核实问题，确认发送以上结果并解决工单'
                    }}</label
                >
            </fieldset>
            <button class="button primary" :disabled="busy || loading || !confirm">
                {{ busy ? '正在提交…' : ticket.status === 'OPEN' ? '确认领取' : '回复并解决' }}
            </button>
        </form>
        <p v-else-if="staff && ticket.status === 'IN_PROGRESS'" class="notice">该工单由其他客服处理。</p>
        <section class="surface">
            <h2>提交时的聊天记录</h2>
            <p class="muted">仅包含提交时已保存的消息，后续聊天不会自动共享。助手回答仅供参考，请核对实际业务记录。</p>
            <button v-if="older" class="button secondary" :disabled="loading" @click="earlier">加载更早消息</button>
            <p v-if="!messages.length">提交时没有聊天记录。</p>
            <article v-for="message in messages" :key="message.id" class="ticket-message">
                <strong>{{ message.role === 'USER' ? '用户' : '售后助手' }}</strong>
                <p class="preserve-text">{{ message.content }}</p>
            </article>
        </section>
        <section class="surface">
            <h2>处理记录</h2>
            <ol class="timeline">
                <li v-for="(event, index) in ticket.events" :key="index">
                    <span class="timeline-dot"></span>
                    <div>
                        <p>{{ api.ticketStatuses[event.action] }}</p>
                        <p class="preserve-text">{{ event.note }}</p>
                        <time>{{ dateTime(event.occurredAt) }}</time>
                    </div>
                </li>
            </ol>
        </section>
    </template>
</template>
<style scoped>
.ticket-message {
    padding: 18px 0;
    border-bottom: 1px solid #e4ebe7;
    overflow-wrap: anywhere;
}
</style>
