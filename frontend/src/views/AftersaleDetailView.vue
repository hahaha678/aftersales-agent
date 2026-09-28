<script setup lang="ts">
import { ref,computed,watch,onBeforeUnmount } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { getAftersale,cancelAftersale,reviewAftersale,aftersaleStatuses,reasons,type Aftersale } from '../api/aftersales'
import { dateTime,money } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
const route=useRoute(),data=ref<Aftersale|null>(null),error=ref<unknown>(null),busy=ref(false),loading=ref(false)
const staff=computed(()=>!!route.meta.staff),base=computed(()=>staff.value?'/staff/aftersales':'/aftersales')
const decision=ref('APPROVED'),note=ref(''),confirmCancel=ref(false),confirmReview=ref(false)
let controller:AbortController|undefined,version=0
async function load(){
 controller?.abort();controller=new AbortController();const current=++version;loading.value=true;error.value=null;data.value=null
 try{const value=await getAftersale(String(route.params.id),staff.value,controller.signal);if(current===version)data.value=value}
 catch(cause){if(current===version&&!(cause instanceof Error&&cause.name==='AbortError'))error.value=cause}
 finally{if(current===version)loading.value=false}
}
async function act(){
 if(busy.value||!data.value)return
 if(staff.value?!confirmReview.value:!confirmCancel.value)return
 const id=data.value.id,current=version,isStaff=staff.value;busy.value=true;error.value=null
 try{const value=isStaff?await reviewAftersale(id,decision.value,note.value):await cancelAftersale(id);if(current===version){data.value=value;confirmCancel.value=false;confirmReview.value=false}}
 catch(cause){if(current===version)error.value=cause}
 finally{if(current===version)busy.value=false}
}
watch(()=>route.fullPath,()=>{busy.value=false;confirmCancel.value=false;confirmReview.value=false;note.value='';void load()},{immediate:true})
onBeforeUnmount(()=>{version++;controller?.abort()})
</script>
<template>
 <RouterLink class="back-link" :to="{path:base,query:route.query}">← 返回申请列表</RouterLink>
 <div v-if="loading" class="state-panel" role="status">正在加载申请…</div>
 <ErrorNotice :error="error"><button class="button secondary" :disabled="busy" @click="load">刷新详情</button></ErrorNotice>
 <template v-if="data"><section class="detail-heading"><div><p class="eyebrow">AFTERCARE DETAILS</p><h1>售后申请 #{{data.id}}</h1><p class="muted">退货退款 · {{data.orderNumber}}</p></div><span class="status-pill">{{aftersaleStatuses[data.status]}}</span></section>
 <p v-if="data.status==='APPROVED'" class="notice">申请已审核通过，当前为待退货阶段。退回物流和实际退款功能尚未接入，没有发生资金退款。</p>
 <section class="surface"><h2>{{data.productName}}</h2><p class="muted">{{data.specification}} · {{data.quantity}} 件</p><div class="total-row"><span>申请金额</span><strong>{{money(data.amount)}}</strong></div><dl class="order-facts"><dt>申请原因</dt><dd>{{reasons[data.reason]}}</dd><dt>问题描述</dt><dd class="preserve-text">{{data.description}}</dd><dt>申请时间</dt><dd>{{dateTime(data.createdAt)}}</dd></dl><RouterLink v-if="!staff" class="back-link" :to="`/orders/${data.orderId}`">查看原订单 →</RouterLink></section>
 <section v-if="data.status==='PENDING'" class="surface">
  <form v-if="staff" class="aftersale-form" @submit.prevent="act"><h2>客服审核</h2><fieldset :disabled="busy"><label for="review-decision">审核决定</label><select id="review-decision" v-model="decision"><option value="APPROVED">通过申请 · 待退货</option><option value="REJECTED">拒绝申请</option></select><label for="review-note">审核意见</label><textarea id="review-note" v-model="note" required maxlength="1000" rows="3"></textarea><label class="check-label"><input v-model="confirmReview" type="checkbox" required>我已核对申请，确认提交以上审核决定</label></fieldset><button class="button primary" :disabled="busy || !confirmReview">{{busy?'正在处理…':'确认审核'}}</button></form>
  <div v-else><h2>撤销申请</h2><p class="muted">撤销后释放占用的商品数量；再次申请仍需满足期限规则。</p><label class="check-label"><input v-model="confirmCancel" type="checkbox" :disabled="busy">确认撤销此申请</label><button class="button secondary" :disabled="busy || !confirmCancel" @click="act">{{busy?'正在撤销…':'撤销申请'}}</button></div>
 </section>
 <section class="surface"><h2>处理记录</h2><ol class="timeline"><li v-for="(event,index) in data.events" :key="index"><span class="timeline-dot"></span><div><p>{{event.action==='SUBMITTED'?'已提交申请':aftersaleStatuses[event.action]}}</p><p class="preserve-text">{{event.note}}</p><time>{{dateTime(event.occurredAt)}}</time></div></li></ol></section></template>
</template>
