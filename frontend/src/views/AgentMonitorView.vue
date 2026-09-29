<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import * as api from '../api/monitor'
import { dateTime } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(),
    status = ref(''),
    conversation = ref(''),
    from = ref(''),
    until = ref(''),
    page = ref(1)
const rows = ref<api.MonitorPage | null>(null),
    detail = ref<api.MonitorRun | null>(null),
    tools = ref<api.ToolCall[]>([])
const loading = ref(false),
    error = ref<unknown>(null)
let version = 0,
    controller: AbortController | undefined
function duration(ms: number) {
    return `${(ms / 1000).toFixed(2)} 秒`
}
async function load() {
    controller?.abort()
    controller = new AbortController()
    const current = ++version
    loading.value = true
    error.value = null
    rows.value = null
    detail.value = null
    tools.value = []
    try {
        if (route.params.id) {
            const [run, calls] = await Promise.all([
                api.monitorRun(String(route.params.id), controller.signal),
                api.monitorTools(String(route.params.id), controller.signal),
            ])
            if (current !== version) return
            detail.value = run
            tools.value = calls
        } else {
            const query = new URLSearchParams({ page: String(page.value), size: '20' })
            if (status.value) query.set('status', status.value)
            if (conversation.value.trim()) query.set('conversationId', conversation.value.trim())
            if (from.value) query.set('from', new Date(from.value).toISOString())
            if (until.value) query.set('until', new Date(until.value).toISOString())
            const result = await api.monitorRuns(query, controller.signal)
            if (current === version) rows.value = result
        }
    } catch (cause) {
        if (current === version) error.value = cause
    } finally {
        if (current === version) loading.value = false
    }
}
function search() {
    page.value = 1
    void load()
}
function turn(delta: number) {
    page.value += delta
    void load()
}
watch(
    () => route.fullPath,
    () => {
        page.value = 1
        void load()
    },
    { immediate: true },
)
onBeforeUnmount(() => {
    version++
    controller?.abort()
})
</script>
<template>
    <h1>Agent 执行监控</h1>
    <p class="muted">
        执行成功不等于回答正确。耗时包含排队和模型处理，不含用户后续确认；Token 为模型上报记录，0 也可能表示未上报。
    </p>
    <ErrorNotice :error="error" />
    <button class="button secondary" :disabled="loading" @click="load">{{ loading ? '正在加载…' : '刷新记录' }}</button>
    <template v-if="!route.params.id">
        <form class="surface monitor-filters" @submit.prevent="search">
            <label
                >任务状态<select v-model="status">
                    <option value="">全部</option>
                    <option v-for="(label, key) in api.runStatuses" :key="key" :value="key">{{ label }}</option>
                </select></label
            >
            <label>会话 ID<input v-model="conversation" maxlength="36" placeholder="可选：完整会话 ID" /></label>
            <label>开始时间<input v-model="from" type="datetime-local" /></label>
            <label>结束时间<input v-model="until" type="datetime-local" /></label>
            <button class="button primary" :disabled="loading">查询</button>
        </form>
        <template v-if="rows">
            <p>共 {{ rows.total }} 条任务 · 第 {{ rows.page }} 页</p>
            <div class="surface monitor-table">
                <table>
                    <thead>
                        <tr>
                            <th>开始时间 / 任务</th>
                            <th>状态</th>
                            <th>模型</th>
                            <th>耗时</th>
                            <th>工具 / 失败</th>
                            <th>输入 / 输出 Token</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in rows.items" :key="row.id">
                            <td>
                                <RouterLink :to="`/staff/agent-runs/${row.id}`"
                                    >{{ dateTime(row.createdAt)
                                    }}<small class="task-id">{{ row.id }}</small></RouterLink
                                >
                            </td>
                            <td>{{ api.runStatuses[row.status] }}</td>
                            <td>{{ row.model }}</td>
                            <td>{{ duration(row.durationMs) }}</td>
                            <td>{{ row.toolCalls }} / {{ row.failedToolCalls }}</td>
                            <td>{{ row.inputTokens }} / {{ row.outputTokens }}</td>
                        </tr>
                    </tbody>
                </table>
                <p v-if="!rows.items.length">没有符合条件的任务。</p>
            </div>
            <button class="button secondary" :disabled="loading || page === 1" @click="turn(-1)">上一页</button>
            <button class="button secondary" :disabled="loading || page * rows.size >= rows.total" @click="turn(1)">
                下一页
            </button>
        </template>
    </template>
    <template v-else-if="detail">
        <RouterLink class="back-link" to="/staff/agent-runs">← 返回任务列表</RouterLink>
        <section class="surface">
            <h2>{{ api.runStatuses[detail.status] }}</h2>
            <dl class="order-facts">
                <dt>任务 ID</dt>
                <dd class="preserve-text">{{ detail.id }}</dd>
                <dt>会话 ID</dt>
                <dd class="preserve-text">{{ detail.conversationId }}</dd>
                <dt>模型</dt>
                <dd>{{ detail.model }}</dd>
                <dt>开始</dt>
                <dd>{{ dateTime(detail.createdAt) }}</dd>
                <dt>结束</dt>
                <dd>{{ detail.finishedAt ? dateTime(detail.finishedAt) : '尚无持久化结束时间' }}</dd>
                <dt>耗时</dt>
                <dd>{{ duration(detail.durationMs) }}</dd>
                <dt>输入 / 输出 Token</dt>
                <dd>{{ detail.inputTokens }} / {{ detail.outputTokens }}</dd>
                <dt>工具调用 / 失败</dt>
                <dd>{{ detail.toolCalls }} / {{ detail.failedToolCalls }}</dd>
            </dl>
            <p v-if="detail.errorCode" class="notice">错误分类：{{ detail.errorCode }}</p>
            <p v-if="detail.status === 'EXPIRED'" class="notice">
                执行租约已过期，数据库原状态为 {{ detail.storedStatus }}。监控只读，不触发恢复、重试或模型调用。
            </p>
        </section>
        <section class="surface">
            <h2>关联上下文</h2>
            <template v-if="detail.ownConversation"
                ><RouterLink :to="{ path: '/assistant', query: { conversation: detail.conversationId } }"
                    >打开本人会话 →</RouterLink
                >
                <h3>用户输入</h3>
                <p class="preserve-text">{{ detail.userContent }}</p>
                <h3>最终回答</h3>
                <p class="preserve-text">{{ detail.assistantContent || '暂无已保存的回答' }}</p></template
            >
            <p v-else class="muted">
                此任务属于其他用户，监控不显示原始输入和回答。若用户已提交人工工单，可查看其主动共享的聊天快照。
            </p>
            <p v-for="id in detail.ticketIds" :key="id">
                <RouterLink :to="`/staff/tickets/${id}`">查看关联工单 #{{ id }} →</RouterLink>
            </p>
        </section>
        <section class="surface">
            <h2>工具执行记录</h2>
            <p class="muted">
                参数仅展示白名单字段；自由文本及完整结果未记录。执行中的调用完成后才写入；旧记录缺失的信息显示“未记录”。
            </p>
            <p v-if="!tools.length">暂无已完成的工具调用记录，不能仅据此判断模型是否异常。</p>
            <ol class="timeline">
                <li v-for="tool in tools" :key="tool.id">
                    <span class="timeline-dot"></span>
                    <div>
                        <h3>
                            {{ tool.callIndex === null ? '历史记录' : `第 ${tool.callIndex} 次` }} · {{ tool.toolName }}
                        </h3>
                        <p>{{ tool.status === 'SUCCEEDED' ? '成功' : '失败' }} · {{ duration(tool.durationMs) }}</p>
                        <p v-if="tool.errorCode">错误码：{{ tool.errorCode }}</p>
                        <p>
                            开始：{{ tool.startedAt ? dateTime(tool.startedAt) : '未记录' }} · 完成：{{
                                dateTime(tool.finishedAt)
                            }}
                        </p>
                        <h4>参数摘要</h4>
                        <pre>{{ tool.inputSummary || '未记录' }}</pre>
                        <h4>结果摘要</h4>
                        <pre>{{ tool.resultSummary || '未记录' }}</pre>
                    </div>
                </li>
            </ol>
        </section>
    </template>
</template>
<style scoped>
.monitor-filters {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    align-items: end;
}
.monitor-filters label {
    display: grid;
    gap: 8px;
    flex: 1 1 190px;
}
.monitor-filters input,
.monitor-filters select {
    width: 100%;
    min-width: 0;
}
.monitor-table {
    overflow-x: auto;
}
table {
    width: 100%;
    border-collapse: collapse;
    text-align: left;
}
th,
td {
    padding: 14px 12px;
    border-bottom: 1px solid #e4ebe7;
    white-space: nowrap;
}
.task-id {
    display: block;
    margin-top: 6px;
    color: #718278;
}
pre {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    background: #f4f7f5;
    padding: 12px;
    border-radius: 8px;
}
dd {
    overflow-wrap: anywhere;
}
</style>
