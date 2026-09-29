<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import * as api from '../api/agent'
import { runSources, type PolicySource } from '../api/knowledge'
import { reasons } from '../api/aftersales'
import { money, dateTime } from '../utils/format'
import { session } from '../state/session'
import ErrorNotice from '../components/ErrorNotice.vue'
import { messageKey, mergeMessages, visibleMessages } from '../utils/chatMessages'
// 状态展示名称集中维护，模板只负责读取。
const draftStatusLabels: Record<string, string> = {
    READY: '待确认',
    CONFIRMED: '已提交',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
}
const route = useRoute(),
    router = useRouter()
const sourceRows = ref<Record<string, PolicySource[]>>({})
async function showSources(id: string) {
    try {
        sourceRows.value[id] = await runSources(id)
    } catch (cause) {
        error.value = cause
    }
}
const list = ref<api.Conversation[]>([]),
    history = ref<api.Message[]>([]),
    cards = ref<api.Draft[]>([])
const selected = ref(''),
    input = ref(''),
    run = ref<api.Run | null>(null),
    answer = ref(''),
    progress = ref('')
const error = ref<unknown>(null),
    notice = ref(''),
    loading = ref(false),
    sending = ref(false),
    older = ref(false),
    cardBusy = ref('')
const enabled = ref(false),
    availability = ref('正在检查助手状态…'),
    checks = ref<Record<string, boolean>>({})
const transcript = ref<HTMLElement | null>(null)
const pending = ref<{ conversation: string; content: string; key: string } | null>(null)
const displayedMessages = computed(() => visibleMessages(history.value, run.value, answer.value))
// 切换会话时递增版本号，忽略旧请求返回，防止消息串到新会话。
let generation = 0,
    reader: AbortController | undefined,
    loader: AbortController | undefined
const isAbort = (cause: unknown) => cause instanceof Error && cause.name === 'AbortError'
const scroll = () =>
    void nextTick(() => {
        transcript.value?.scrollTo({ top: transcript.value.scrollHeight, behavior: 'instant' })
    })
async function refresh() {
    reader?.abort()
    loader?.abort()
    loader = new AbortController()
    const current = ++generation
    selected.value = typeof route.query.conversation === 'string' ? route.query.conversation : ''
    history.value = []
    cards.value = []
    run.value = null
    answer.value = ''
    checks.value = {}
    error.value = null
    loading.value = true
    progress.value = ''
    cardBusy.value = ''
    sending.value = false
    try {
        const [status, items] = await Promise.all([api.agentStatus(loader.signal), api.conversations(loader.signal)])
        if (current !== generation) return
        enabled.value = status.available
        availability.value = status.message
        list.value = items
        if (selected.value) {
            if (!items.some((item) => item.id === selected.value)) throw new Error('会话不存在或不在最近的会话列表中。')
            const [rows, draftRows, active] = await Promise.all([
                api.messages(selected.value, undefined, loader.signal),
                api.drafts(selected.value, loader.signal),
                api.activeRun(selected.value, loader.signal),
            ])
            if (current !== generation) return
            history.value = rows
            older.value = rows.length === 50
            cards.value = draftRows
            scroll()
            if (active) {
                run.value = active
                void connect(active, current)
            }
        }
    } catch (cause) {
        if (current === generation && !isAbort(cause)) error.value = cause
    } finally {
        if (current === generation) loading.value = false
    }
}
async function connect(value: api.Run, current: number) {
    // 进度、草稿和正文分别处理；完成后合并持久化历史，保留原消息身份。
    reader?.abort()
    const abort = new AbortController()
    reader = abort
    answer.value = ''
    run.value = value
    try {
        await api.streamRun(value.id, abort.signal, (event) => {
            if (current !== generation) return
            if (event.name === 'delta') {
                answer.value += (event.data as { text: string }).text
                scroll()
            }
            if (event.name === 'snapshot') answer.value = (event.data as { text: string }).text
            if (event.name === 'status') progress.value = (event.data as { message: string }).message
            if (event.name === 'draft') {
                const card = event.data as api.Draft
                cards.value = [card, ...cards.value.filter((x) => x.id !== card.id)]
            }
            if (event.name === 'done') {
                run.value = event.data as api.Run
                progress.value = ''
                answer.value = run.value.content
                notice.value = run.value.errorMessage || ''
            }
        })
        if (current === generation) {
            const [rows, draftRows] = await Promise.all([
                api.messages(value.conversationId, undefined, abort.signal),
                api.drafts(value.conversationId, abort.signal),
            ])
            if (current === generation) {
                history.value = mergeMessages(history.value, rows)
                cards.value = draftRows
                run.value = null
                answer.value = ''
                scroll()
            }
        }
    } catch (cause) {
        if (current === generation && !isAbort(cause)) {
            error.value = cause
            progress.value = '连接中断，可刷新会话恢复'
        }
    }
}
async function send() {
    if (sending.value || run.value !== null || !enabled.value || (!pending.value && !input.value.trim())) return
    const current = generation
    sending.value = true
    error.value = null
    notice.value = ''
    try {
        if (!pending.value) {
            let id = selected.value
            if (!id) {
                const created = await api.createConversation(input.value.trim().slice(0, 80))
                if (current !== generation) return
                id = created.id
                selected.value = id
                list.value.unshift(created)
                // Keep selection without triggering a reload while the POST is in flight.
                await router.replace({ path: '/assistant', query: { conversation: id } })
            }
            pending.value = { conversation: id, content: input.value.trim(), key: crypto.randomUUID() }
        }
        const attempt = pending.value
        const value = await api.sendMessage(attempt.conversation, attempt.content, attempt.key)
        if (current !== generation) return
        pending.value = null
        input.value = ''
        run.value = value
        const rows = await api.messages(attempt.conversation)
        if (current !== generation) return
        history.value = mergeMessages(history.value, rows)
        void connect(value, current)
        scroll()
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) sending.value = false
    }
}
async function stop() {
    if (!run.value) return
    try {
        await api.stopRun(run.value.id)
    } catch (cause) {
        error.value = cause
    }
}
async function more() {
    if (!history.value[0] || loading.value) return
    const current = generation
    loading.value = true
    const element = transcript.value,
        oldHeight = element?.scrollHeight || 0,
        oldTop = element?.scrollTop || 0
    try {
        const rows = await api.messages(selected.value, history.value[0].id)
        if (current === generation) {
            history.value = mergeMessages(history.value, rows)
            older.value = rows.length === 50
            await nextTick()
            if (element && current === generation) element.scrollTop = oldTop + element.scrollHeight - oldHeight
        }
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) loading.value = false
    }
}
async function act(card: api.Draft, confirm: boolean) {
    if (cardBusy.value || (confirm && !checks.value[card.id])) return
    const current = generation
    cardBusy.value = card.id
    error.value = null
    try {
        const value = confirm ? await api.confirmDraft(card) : await api.cancelDraft(card.id)
        if (current !== generation) return
        const updated = confirm ? (value as api.Confirmation).draft : (value as api.Draft)
        cards.value = cards.value.map((x) => (x.id === updated.id ? updated : x))
        checks.value[card.id] = false
        notice.value = confirm ? (value as api.Confirmation).message : '草稿已取消，可以重新描述你的需求。'
    } catch (cause) {
        if (current === generation) error.value = cause
    } finally {
        if (current === generation) cardBusy.value = ''
    }
}
function select(id: string) {
    if (sending.value) return
    pending.value = null
    input.value = ''
    notice.value = ''
    void router.push({ path: '/assistant', query: id ? { conversation: id } : {} })
}
watch(
    () => route.query.conversation,
    () => {
        if (String(route.query.conversation || '') !== selected.value) void refresh()
    },
)
watch(
    () => session.token,
    () => {
        generation++
        reader?.abort()
        loader?.abort()
        history.value = []
        cards.value = []
        run.value = null
        pending.value = null
    },
)
onBeforeUnmount(() => {
    generation++
    reader?.abort()
    loader?.abort()
})
void refresh()
</script>
<template>
    <section class="page-heading">
        <p class="eyebrow">YOUR AFTERCARE ASSISTANT</p>
        <h1>有问题，一起解决。</h1>
        <p>查订单、看物流、准备售后申请。提交前由你核对确认。</p>
    </section>
    <ErrorNotice :error="error"
        ><button class="button secondary" :disabled="sending" @click="refresh">刷新会话</button></ErrorNotice
    >
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div class="assistant-layout">
        <aside class="surface conversation-sidebar">
            <button class="button primary" :disabled="sending" @click="select('')">＋ 新对话</button>
            <h2>最近的对话</h2>
            <p v-if="!list.length" class="muted">从第一个问题开始。</p>
            <button
                v-for="item in list"
                :key="item.id"
                class="conversation-choice"
                :class="{ selected: item.id === selected }"
                :disabled="sending"
                @click="select(item.id)"
            >
                {{ item.title }}<small>{{ dateTime(item.createdAt) }}</small>
            </button>
        </aside>
        <section class="surface chat-panel">
            <div class="chat-heading">
                <div>
                    <h2>智能售后助手</h2>
                    <span class="muted">{{ enabled ? '订单信息仅对本人可见' : availability }}</span>
                </div>
                <button class="button secondary" :disabled="sending || loading" @click="refresh">刷新会话</button>
            </div>
            <div ref="transcript" class="chat-transcript" aria-label="对话消息" :aria-busy="loading">
                <button v-if="older" class="button secondary" :disabled="loading" @click="more">加载更早的消息</button>
                <div v-if="!history.length && !loading" class="chat-welcome">
                    <span aria-hidden="true">✦</span>
                    <h3>今天有什么可以帮你？</h3>
                    <p>例如：“帮我看看最近的订单”<br />“这件商品还可以申请售后吗？”</p>
                    <small>助手生成的草稿不会自动提交，也不会发起退款。</small>
                </div>
                <article
                    v-for="message in displayedMessages"
                    :key="messageKey(message)"
                    class="chat-message"
                    :class="{ customer: message.role === 'USER' }"
                >
                    <strong>{{ message.role === 'USER' ? '你' : '售后助手' }}</strong>
                    <p>{{ message.content || (message.status === 'RUNNING' ? '正在处理…' : '本次未生成回答') }}</p>
                    <template v-if="message.role === 'ASSISTANT' && message.status !== 'RUNNING'">
                        <button class="text-button" @click="showSources(message.runId)">查看本次检索来源</button>
                        <div v-if="sourceRows[message.runId]">
                            <p v-if="!sourceRows[message.runId]?.length">本次没有检索到政策来源。</p>
                            <details v-for="source in sourceRows[message.runId]" :key="source.sourceId">
                                <summary>{{ source.title }} · v{{ source.version }}</summary>
                                <p>{{ source.excerpt }}</p>
                                <RouterLink :to="'/policies/' + source.policyId">查看政策原文</RouterLink>
                            </details>
                        </div>
                    </template>
                    <small v-if="message.role === 'ASSISTANT' && ['FAILED', 'CANCELLED'].includes(message.status)"
                        >{{ message.status === 'CANCELLED' ? '已停止' : '未完成' }} · 此回复可能不完整</small
                    >
                </article>
                <p v-if="progress" class="muted" role="status">{{ progress }}</p>
            </div>
            <form class="chat-composer" @submit.prevent="send">
                <label for="chat-input">描述你的问题</label
                ><textarea
                    id="chat-input"
                    v-model="input"
                    rows="3"
                    maxlength="2000"
                    placeholder="输入订单、物流或售后问题…"
                    :disabled="sending || !!pending || !!run || !enabled"
                ></textarea>
                <div class="composer-actions">
                    <small>{{
                        pending
                            ? '发送结果未确认，重试会沿用同一请求编号。'
                            : '回答仅供参考，订单和申请结果以系统记录为准。'
                    }}</small
                    ><button v-if="run?.status === 'RUNNING'" type="button" class="button secondary" @click="stop">
                        停止回答</button
                    ><button
                        class="button primary"
                        :disabled="sending || loading || !!run || !enabled || (!pending && !input.trim())"
                    >
                        {{ sending ? '正在发送…' : pending ? '重试本条消息' : '发送消息' }}
                    </button>
                </div>
            </form>
        </section>
    </div>
    <section v-if="cards.length" class="draft-section">
        <div class="section-heading">
            <h2>待核对的售后草稿</h2>
            <span>草稿 15 分钟有效，提交时重新检查资格和金额</span>
        </div>
        <div class="draft-grid">
            <article v-for="card in cards" :key="card.id" class="surface draft-card">
                <span class="status-pill">{{ draftStatusLabels[card.status] }}</span>
                <h3>{{ card.productName }}</h3>
                <p class="muted">{{ card.orderNumber }} · {{ card.quantity }} 件</p>
                <div class="total-row">
                    <span>申请金额</span><strong>{{ money(card.amount) }}</strong>
                </div>
                <p>{{ reasons[card.reason] }} · {{ card.description }}</p>
                <p class="muted">有效期至 {{ dateTime(card.expiresAt) }}</p>
                <template v-if="card.status === 'READY'"
                    ><label class="check-label"
                        ><input
                            v-model="checks[card.id]"
                            type="checkbox"
                            :disabled="!!cardBusy"
                        />我已核对商品、数量、原因和金额，确认提交申请</label
                    >
                    <div class="draft-actions">
                        <button
                            class="button primary"
                            :disabled="!checks[card.id] || !!cardBusy"
                            @click="act(card, true)"
                        >
                            确认提交</button
                        ><button class="button secondary" :disabled="!!cardBusy" @click="act(card, false)">
                            取消草稿
                        </button>
                    </div></template
                >
                <RouterLink v-if="card.aftersaleId" class="back-link" :to="'/aftersales/' + card.aftersaleId"
                    >查看已创建的申请 →</RouterLink
                >
            </article>
        </div>
    </section>
</template>
<style scoped>
.assistant-layout {
    display: grid;
    grid-template-columns: 240px minmax(0, 1fr);
    gap: 24px;
    align-items: start;
}
.conversation-sidebar {
    max-height: 680px;
    overflow: auto;
}
.conversation-sidebar h2 {
    font-size: 16px;
    margin: 24px 0 12px;
}
.conversation-choice {
    display: block;
    width: 100%;
    border: 0;
    background: transparent;
    text-align: left;
    padding: 14px 10px;
    border-radius: 8px;
    cursor: pointer;
    overflow-wrap: anywhere;
    color: inherit;
}
.conversation-choice:hover,
.conversation-choice.selected {
    background: #edf3ed;
}
.conversation-choice small {
    display: block;
    color: #737b75;
    margin-top: 6px;
}
.chat-panel {
    padding: 0;
    overflow: hidden;
    min-width: 0;
}
.chat-heading {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 24px;
    border-bottom: 1px solid #e6e9e3;
}
.chat-heading h2 {
    margin: 0 0 8px;
    font-size: 20px;
}
.chat-transcript {
    height: 420px;
    overflow: auto;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 18px;
}
.chat-welcome {
    margin: auto;
    text-align: center;
    color: #647268;
    line-height: 1.9;
}
.chat-welcome > span {
    font-size: 36px;
    color: #315b45;
}
.chat-message {
    background: #f1f4ef;
    border-radius: 4px 16px 16px 16px;
    padding: 16px 20px;
    max-width: 90%;
    align-self: flex-start;
    overflow-wrap: anywhere;
}
.chat-message.customer {
    align-self: flex-end;
    background: #e4ede5;
    border-radius: 16px 4px 16px 16px;
}
.chat-message strong {
    font-size: 12px;
    color: #52665b;
}
.chat-message p {
    white-space: pre-wrap;
    line-height: 1.8;
    margin: 8px 0 0;
}
.chat-message small {
    display: block;
    margin-top: 8px;
    color: #8e5432;
}
.chat-composer {
    padding: 20px 24px;
    border-top: 1px solid #e6e9e3;
}
.chat-composer label {
    display: block;
    margin-bottom: 10px;
    font-size: 14px;
}
.chat-composer textarea {
    box-sizing: border-box;
    width: 100%;
    resize: vertical;
    border: 1px solid #ced8ce;
    border-radius: 8px;
    padding: 12px;
    font: inherit;
    background: #fff;
}
.composer-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 12px;
}
.composer-actions small {
    flex: 1;
    color: #737b75;
}
.draft-section {
    margin-top: 28px;
}
.draft-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 20px;
}
.draft-card {
    overflow-wrap: anywhere;
}
.draft-actions {
    display: flex;
    gap: 12px;
}
.draft-card .check-label {
    margin: 18px 0;
}
@media (max-width: 800px) {
    .assistant-layout {
        grid-template-columns: 1fr;
    }
    .conversation-sidebar {
        max-height: 200px;
    }
    .chat-heading {
        padding: 16px;
    }
    .chat-transcript {
        padding: 16px;
        height: 380px;
    }
    .chat-composer {
        padding: 16px;
    }
    .composer-actions {
        flex-wrap: wrap;
    }
    .composer-actions small {
        flex-basis: 100%;
    }
}
</style>
