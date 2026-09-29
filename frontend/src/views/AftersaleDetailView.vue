<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
    getAftersale,
    cancelAftersale,
    reviewAftersale,
    registerReturnShipment,
    confirmReturnReceipt,
    submitSimulatedRefund,
    reconcileRefund,
    refundStatuses,
    type RefundMode,
    aftersaleStatuses,
    reasons,
    type Aftersale,
} from '../api/aftersales'
import { dateTime, money } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
import EvidencePanel from '../components/EvidencePanel.vue'
const route = useRoute(),
    data = ref<Aftersale | null>(null),
    error = ref<unknown>(null),
    busy = ref(false),
    loading = ref(false)
const staff = computed(() => !!route.meta.staff),
    base = computed(() => (staff.value ? '/staff/aftersales' : '/aftersales'))
const decision = ref('APPROVED'),
    note = ref(''),
    confirmCancel = ref(false),
    confirmReview = ref(false)
const carrier = ref(''),
    trackingNumber = ref(''),
    receiptNote = ref(''),
    confirmReturn = ref(false)
const refundMode = ref<RefundMode>('SUCCESS'),
    confirmRefund = ref(false)
// 请求异常时保留同一键及原内容，重试不能变成另一笔退款。
const pendingRefund = ref<{ key: string; mode: RefundMode; previousKey: string | null } | null>(null)
async function refundAction(queryOnly = false) {
    if (busy.value || !data.value || !staff.value || (!queryOnly && !confirmRefund.value)) return
    const id = data.value.id,
        current = version
    busy.value = true
    error.value = null
    try {
        let value: Aftersale
        if (queryOnly) {
            const latest = data.value.refunds[0]
            if (!latest) return
            value = await reconcileRefund(id, latest.requestKey)
        } else {
            pendingRefund.value ??= {
                key: crypto.randomUUID(),
                mode: refundMode.value,
                previousKey: data.value.refunds[0]?.requestKey ?? null,
            }
            const attempt = pendingRefund.value
            value = await submitSimulatedRefund(id, attempt.key, attempt.mode, attempt.previousKey)
        }
        if (current === version) {
            data.value = value
            pendingRefund.value = null
            confirmRefund.value = false
        }
    } catch (cause) {
        if (current === version) error.value = cause
    } finally {
        if (current === version) busy.value = false
    }
}
let controller: AbortController | undefined,
    version = 0
async function load() {
    controller?.abort()
    controller = new AbortController()
    const current = ++version
    loading.value = true
    error.value = null
    data.value = null
    try {
        const value = await getAftersale(String(route.params.id), staff.value, controller.signal)
        if (current === version) {
            data.value = value
            // 主动刷新成功后按最新流水重新核对，解除旧失败请求的场景锁定。
            pendingRefund.value = null
            confirmRefund.value = false
        }
    } catch (cause) {
        if (current === version && !(cause instanceof Error && cause.name === 'AbortError')) error.value = cause
    } finally {
        if (current === version) loading.value = false
    }
}
async function act() {
    if (busy.value || !data.value) return
    if (staff.value ? !confirmReview.value : !confirmCancel.value) return
    const id = data.value.id,
        current = version,
        isStaff = staff.value
    busy.value = true
    error.value = null
    try {
        const value = isStaff ? await reviewAftersale(id, decision.value, note.value) : await cancelAftersale(id)
        if (current === version) {
            data.value = value
            confirmCancel.value = false
            confirmReview.value = false
        }
    } catch (cause) {
        if (current === version) error.value = cause
    } finally {
        if (current === version) busy.value = false
    }
}
async function returnAction() {
    if (busy.value || !data.value || !confirmReturn.value) return
    const id = data.value.id,
        current = version,
        isStaff = staff.value
    busy.value = true
    error.value = null
    try {
        const value = isStaff
            ? await confirmReturnReceipt(id, receiptNote.value.trim())
            : await registerReturnShipment(id, carrier.value.trim(), trackingNumber.value.trim())
        if (current === version) {
            data.value = value
            confirmReturn.value = false
        }
    } catch (cause) {
        if (current === version) error.value = cause
    } finally {
        if (current === version) busy.value = false
    }
}
watch(
    () => route.fullPath,
    () => {
        busy.value = false
        confirmCancel.value = false
        confirmReview.value = false
        note.value = ''
        carrier.value = ''
        trackingNumber.value = ''
        receiptNote.value = ''
        confirmReturn.value = false
        confirmRefund.value = false
        pendingRefund.value = null
        refundMode.value = 'SUCCESS'
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
    <RouterLink class="back-link" :to="{ path: base, query: route.query }">← 返回申请列表</RouterLink>
    <div v-if="loading" class="state-panel" role="status">正在加载申请…</div>
    <ErrorNotice :error="error"
        ><button class="button secondary" :disabled="busy" @click="load">刷新详情</button></ErrorNotice
    >
    <template v-if="data"
        ><section class="detail-heading">
            <div>
                <p class="eyebrow">AFTERCARE DETAILS</p>
                <h1>售后申请 #{{ data.id }}</h1>
                <p class="muted">退货退款 · {{ data.orderNumber }}</p>
            </div>
            <span class="status-pill">{{ aftersaleStatuses[data.status] }}</span>
        </section>
        <p v-if="data.status === 'APPROVED'" class="notice">
            申请已审核通过。请先与人工客服确认退回地址和方式，实际寄出后登记物流。本系统不提供退回地址，也不执行资金退款。
        </p>
        <p v-if="data.status === 'RETURN_SHIPPED'" class="notice">
            退回物流已登记，等待客服核对实物并确认收货。此状态不代表快递公司已揽收或签收。
        </p>
        <p v-if="data.status === 'RETURN_RECEIVED'" class="notice">
            客服已确认收货，可以由客服发起模拟退款。演示操作不会产生真实资金变动。
        </p>
        <p v-if="data.status === 'REFUND_PENDING'" class="notice">
            模拟渠道响应超时，结果尚未确认。请由客服查询原退款流水，不能直接再次退款。
        </p>
        <p v-if="data.status === 'COMPLETED'" class="notice">
            售后已完成，模拟退款成功。本项目未接入真实支付，款项不会实际到账。
        </p>
        <section class="surface">
            <h2>{{ data.productName }}</h2>
            <p class="muted">{{ data.specification }} · {{ data.quantity }} 件</p>
            <div class="total-row">
                <span>申请金额</span><strong>{{ money(data.amount) }}</strong>
            </div>
            <dl class="order-facts">
                <dt>申请原因</dt>
                <dd>{{ reasons[data.reason] }}</dd>
                <dt>问题描述</dt>
                <dd class="preserve-text">{{ data.description }}</dd>
                <dt>申请时间</dt>
                <dd>{{ dateTime(data.createdAt) }}</dd>
            </dl>
            <RouterLink v-if="!staff" class="back-link" :to="`/orders/${data.orderId}`">查看原订单 →</RouterLink>
        </section>
        <EvidencePanel :key="`${staff}:${data.id}`" :request-id="data.id" :status="data.status" :staff="staff" />
        <section v-if="data.status === 'PENDING'" class="surface">
            <form v-if="staff" class="aftersale-form" @submit.prevent="act">
                <h2>客服审核</h2>
                <fieldset :disabled="busy">
                    <label for="review-decision">审核决定</label
                    ><select id="review-decision" v-model="decision">
                        <option value="APPROVED">通过申请 · 待退货</option>
                        <option value="REJECTED">拒绝申请</option></select
                    ><label for="review-note">审核意见</label
                    ><textarea id="review-note" v-model="note" required maxlength="1000" rows="3"></textarea
                    ><label class="check-label"
                        ><input
                            v-model="confirmReview"
                            type="checkbox"
                            required
                        />我已核对申请，确认提交以上审核决定</label
                    >
                </fieldset>
                <button class="button primary" :disabled="busy || !confirmReview">
                    {{ busy ? '正在处理…' : '确认审核' }}
                </button>
            </form>
            <div v-else>
                <h2>撤销申请</h2>
                <p class="muted">撤销后释放占用的商品数量；再次申请仍需满足期限规则。</p>
                <label class="check-label"
                    ><input v-model="confirmCancel" type="checkbox" :disabled="busy" />确认撤销此申请</label
                ><button class="button secondary" :disabled="busy || !confirmCancel" @click="act">
                    {{ busy ? '正在撤销…' : '撤销申请' }}
                </button>
            </div>
        </section>
        <section v-if="data.returnShipment" class="surface">
            <h2>退回物流</h2>
            <dl class="order-facts">
                <dt>承运商</dt>
                <dd>{{ data.returnShipment.carrier }}</dd>
                <dt>运单号</dt>
                <dd>{{ data.returnShipment.trackingNumber }}</dd>
                <dt>登记时间</dt>
                <dd>{{ dateTime(data.returnShipment.registeredAt) }}</dd>
            </dl>
            <p class="muted">物流由用户登记，当前不查询快递实时轨迹。登记后不可在线修改，如填错请联系人工客服核实。</p>
        </section>
        <section v-if="data.receipt" class="surface">
            <h2>收货确认</h2>
            <p class="preserve-text">{{ data.receipt.note }}</p>
            <p class="muted">{{ dateTime(data.receipt.receivedAt) }} · 收货不代表退款完成</p>
        </section>
        <section
            v-if="(!staff && data.status === 'APPROVED') || (staff && data.status === 'RETURN_SHIPPED')"
            class="surface"
        >
            <form class="aftersale-form" @submit.prevent="returnAction">
                <h2>{{ staff ? '确认退回商品收货' : '登记退回物流' }}</h2>
                <fieldset :disabled="busy">
                    <template v-if="!staff">
                        <label for="return-carrier">承运商</label>
                        <input
                            id="return-carrier"
                            v-model="carrier"
                            required
                            minlength="2"
                            maxlength="40"
                            placeholder="例如：顺丰速运"
                        />
                        <label for="return-tracking">实际寄件单号</label>
                        <input
                            id="return-tracking"
                            v-model="trackingNumber"
                            required
                            minlength="6"
                            maxlength="64"
                            pattern="[A-Za-z0-9][A-Za-z0-9\-]{5,63}"
                            title="6–64 位字母、数字或连字符"
                            autocomplete="off"
                        />
                    </template>
                    <template v-else>
                        <label for="receipt-note">收货核对记录</label>
                        <textarea
                            id="receipt-note"
                            v-model="receiptNote"
                            required
                            maxlength="1000"
                            rows="3"
                            placeholder="请记录实际核对的商品、数量及配件情况"
                        ></textarea>
                    </template>
                    <label class="check-label"
                        ><input v-model="confirmReturn" type="checkbox" required />{{
                            staff
                                ? '我已核对实物，确认收到退回商品；此操作不代表退款'
                                : '我已与客服确认退回方式并实际寄出，以上物流信息准确'
                        }}</label
                    >
                </fieldset>
                <button class="button primary" :disabled="busy || !confirmReturn">
                    {{ busy ? '正在提交…' : staff ? '确认已收货' : '登记物流' }}
                </button>
            </form>
        </section>
        <section v-if="data.refunds.length" class="surface">
            <h2>模拟退款流水</h2>
            <p class="muted">以下为演示记录，不是实际支付凭证。按最新记录优先展示。</p>
            <ol class="timeline">
                <li v-for="refund in data.refunds" :key="refund.requestKey">
                    <span class="timeline-dot"></span>
                    <div>
                        <p>{{ refundStatuses[refund.status] }} · {{ money(refund.amount) }}</p>
                        <p>{{ refund.operationNumber }}</p>
                        <time>{{ dateTime(refund.updatedAt) }}</time>
                    </div>
                </li>
            </ol>
            <button
                v-if="staff && data.status === 'REFUND_PENDING'"
                class="button primary"
                :disabled="busy"
                @click="refundAction(true)"
            >
                {{ busy ? '正在查询…' : '查询并同步退款结果' }}
            </button>
        </section>
        <section v-if="staff && ['RETURN_RECEIVED', 'REFUND_FAILED'].includes(data.status)" class="surface">
            <form class="aftersale-form" @submit.prevent="refundAction(false)">
                <h2>{{ data.status === 'REFUND_FAILED' ? '重新发起模拟退款' : '发起模拟退款' }}</h2>
                <p class="muted">
                    退款金额
                    {{ money(data.amount) }}，由售后申请确定。失败不会释放商品数量，成功后也不可再次申请同一数量。
                </p>
                <fieldset :disabled="busy">
                    <label for="refund-mode">演示渠道场景</label>
                    <select id="refund-mode" v-model="refundMode" :disabled="!!pendingRefund">
                        <option value="SUCCESS">立即成功</option>
                        <option value="FAILURE">明确失败</option>
                        <option value="TIMEOUT_SUCCESS">响应超时，查询后成功</option>
                        <option value="TIMEOUT_FAILURE">响应超时，查询后失败</option>
                    </select>
                    <p v-if="pendingRefund" class="muted">
                        上次请求尚未获得确定响应，将使用原请求重试。如状态已变化，请刷新核对。
                    </p>
                    <label class="check-label"
                        ><input
                            v-model="confirmRefund"
                            type="checkbox"
                            required
                        />我已核对商品、收货记录及金额，确认执行模拟退款</label
                    >
                </fieldset>
                <button class="button primary" :disabled="busy || !confirmRefund">
                    {{ busy ? '正在处理…' : '确认模拟退款' }}
                </button>
            </form>
        </section>
        <section class="surface">
            <h2>处理记录</h2>
            <ol class="timeline">
                <li v-for="(event, index) in data.events" :key="index">
                    <span class="timeline-dot"></span>
                    <div>
                        <p>{{ event.action === 'SUBMITTED' ? '已提交申请' : aftersaleStatuses[event.action] }}</p>
                        <p class="preserve-text">{{ event.note }}</p>
                        <time>{{ dateTime(event.occurredAt) }}</time>
                    </div>
                </li>
            </ol>
        </section></template
    >
</template>
