<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { listAftersales, aftersaleStatuses, type AftersalePage } from '../api/aftersales'
import { dateTime, money } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(),
    router = useRouter(),
    data = ref<AftersalePage | null>(null),
    error = ref<unknown>(null),
    busy = ref(false),
    status = ref('')
const staff = computed(() => !!route.meta.staff),
    base = computed(() => (staff.value ? '/staff/aftersales' : '/aftersales'))
let controller: AbortController | undefined,
    version = 0
async function load() {
    controller?.abort()
    controller = new AbortController()
    const current = ++version
    data.value = null
    error.value = null
    busy.value = true
    status.value =
        typeof route.query.status === 'string' &&
        Object.prototype.hasOwnProperty.call(aftersaleStatuses, route.query.status)
            ? route.query.status
            : ''
    const number = Number(route.query.page || 1),
        page = Number.isInteger(number) && number > 0 && number <= 2147483647 ? number : 1
    const query = new URLSearchParams({ page: String(page), size: '10' })
    if (status.value) query.set('status', status.value)
    try {
        const result = await listAftersales(query, staff.value, controller.signal)
        if (current === version) data.value = result
    } catch (cause) {
        if (current === version && !(cause instanceof Error && cause.name === 'AbortError')) error.value = cause
    } finally {
        if (current === version) busy.value = false
    }
}
function search() {
    const target = { path: base.value, query: { page: '1', ...(status.value ? { status: status.value } : {}) } }
    if (router.resolve(target).fullPath === route.fullPath) void load()
    else void router.push(target)
}
function move(page: number) {
    void router.push({ path: base.value, query: { ...route.query, page: String(page) } })
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => {
    version++
    controller?.abort()
})
</script>
<template>
    <section class="page-heading">
        <p class="eyebrow">AFTERCARE PROGRESS</p>
        <h1>{{ staff ? '售后审核工作台' : '我的售后记录' }}</h1>
        <p>{{ staff ? '查看申请详情并作出审核决定。' : '查看申请状态与处理记录，待审核申请可以撤销。' }}</p>
    </section>
    <form class="filter-panel" @submit.prevent="search">
        <div>
            <label for="aftersale-status">申请状态</label
            ><select id="aftersale-status" v-model="status" :disabled="busy">
                <option value="">全部状态</option>
                <option v-for="(label, key) in aftersaleStatuses" :key="key" :value="key">{{ label }}</option>
            </select>
        </div>
        <button class="button primary" :disabled="busy">查询申请</button>
    </form>
    <div v-if="busy" class="state-panel" role="status">正在加载申请…</div>
    <ErrorNotice v-else-if="error" :error="error"
        ><button class="button secondary" @click="load">重新加载</button></ErrorNotice
    >
    <template v-else-if="data"
        ><div class="section-heading">
            <h2>
                申请记录 <span class="count">{{ data.total }}</span>
            </h2>
            <span>按申请时间由近到远排列</span>
        </div>
        <div v-if="!data.items.length" class="state-panel">
            <h2>暂无申请记录</h2>
            <button v-if="data.page > 1" class="button secondary" @click="move(1)">返回第一页</button
            ><RouterLink v-if="!staff" class="button secondary" to="/orders">前往订单申请售后</RouterLink>
        </div>
        <div class="order-list">
            <article v-for="item in data.items" :key="item.id" class="order-card">
                <div class="order-card-head">
                    <span>申请 #{{ item.id }} · {{ item.orderNumber }}</span
                    ><span class="status-pill">{{ aftersaleStatuses[item.status] }}</span>
                </div>
                <div class="order-card-body">
                    <div class="order-card-info">
                        <h3>{{ item.productName }} × {{ item.quantity }}</h3>
                        <p>{{ dateTime(item.createdAt) }}</p>
                    </div>
                    <div class="order-amount">
                        <small>申请金额</small><strong>{{ money(item.amount) }}</strong>
                    </div>
                    <RouterLink class="button secondary" :to="{ path: `${base}/${item.id}`, query: route.query }"
                        >查看申请</RouterLink
                    >
                </div>
            </article>
        </div>
        <nav class="pagination" aria-label="售后分页">
            <span>第 {{ data.page }} 页 · 共 {{ Math.max(1, Math.ceil(data.total / data.size)) }} 页</span>
            <div>
                <button class="button secondary" :disabled="data.page <= 1" @click="move(data.page - 1)">上一页</button
                ><button
                    class="button secondary"
                    :disabled="data.page * data.size >= data.total"
                    @click="move(data.page + 1)"
                >
                    下一页
                </button>
            </div>
        </nav></template
    >
</template>
