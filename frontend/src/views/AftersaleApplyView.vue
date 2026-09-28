<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { getEligibility, createAftersale, reasons, type Eligibility, type CreateAftersale } from '../api/aftersales'
import { dateTime, money } from '../utils/format'
import ErrorNotice from '../components/ErrorNotice.vue'
import { ApiError } from '../api/http'
const route=useRoute(),router=useRouter()
const eligibility=ref<Eligibility|null>(null),error=ref<unknown>(null),busy=ref(false),loading=ref(false)
const itemId=ref(''),quantity=ref(1),reason=ref('QUALITY'),description=ref(''),confirmed=ref(false)
const pending=ref<CreateAftersale|null>(null)
const selected=computed(()=>eligibility.value?.items.find(item=>item.orderItemId===itemId.value))
let controller:AbortController|undefined,version=0
async function load(){
 controller?.abort();controller=new AbortController();const current=++version
 loading.value=true;error.value=null;eligibility.value=null
 try {const value=await getEligibility(String(route.params.id),controller.signal);if(current===version){eligibility.value=value;itemId.value=value.items.find(i=>i.eligible)?.orderItemId||''}}
 catch(cause){if(current===version && !(cause instanceof Error && cause.name==='AbortError'))error.value=cause}
 finally{if(current===version)loading.value=false}
}
watch(()=>route.params.id,()=>{busy.value=false;pending.value=null;confirmed.value=false;description.value='';quantity.value=1;void load()},{immediate:true})
watch(itemId,()=>{quantity.value=1;confirmed.value=false})
onBeforeUnmount(()=>{version++;controller?.abort()})
async function submit(){
 if(busy.value)return
 if(!pending.value){
  if(!selected.value?.eligible || !confirmed.value)return
  pending.value={orderId:String(route.params.id),orderItemId:itemId.value,quantity:quantity.value,reason:reason.value,description:description.value,requestKey:crypto.randomUUID()}
 }
 busy.value=true;error.value=null;const current=version
 try {const result=await createAftersale(pending.value);if(current===version)await router.replace(`/aftersales/${result.id}`)}
 catch(cause){if(current!==version)return;error.value=cause;if(cause instanceof ApiError && [400,403,404,409].includes(cause.status))pending.value=null}
 finally{if(current===version)busy.value=false}
}
</script>
<template>
 <RouterLink class="back-link" :to="`/orders/${route.params.id}`">← 返回订单详情</RouterLink>
 <section class="page-heading"><p class="eyebrow">AFTERCARE REQUEST</p><h1>申请退货退款</h1><p>每次申请选择一个商品项，申请金额由服务端按原实付计算。</p></section>
 <div v-if="loading" class="state-panel" role="status">正在检查售后资格…</div>
 <ErrorNotice :error="error"><button v-if="!eligibility" class="button secondary" @click="load">重新检查</button></ErrorNotice>
 <template v-if="eligibility">
  <p class="notice">项目演示规则：签收后 7 天内申请。截止时间：{{dateTime(eligibility.deadline)}}。审核通过后进入待退货阶段，当前不执行实际退款。</p>
  <section class="surface"><h2>商品申请资格</h2><div v-for="item in eligibility.items" :key="item.orderItemId" class="eligibility-row"><strong>{{item.productName}}</strong><span>剩余 {{item.availableQuantity}} 件</span><span :class="item.eligible?'eligible':'muted'">{{item.eligible?'可以申请':item.reason}}</span></div></section>
  <form v-if="eligibility.items.some(i=>i.eligible)" class="surface aftersale-form" @submit.prevent="submit">
   <fieldset :disabled="busy || !!pending">
    <label for="apply-item">申请商品</label><select id="apply-item" v-model="itemId" required><option v-for="item in eligibility.items.filter(i=>i.eligible)" :key="item.orderItemId" :value="item.orderItemId">{{item.productName}}</option></select>
    <label for="apply-quantity">申请数量</label><input id="apply-quantity" v-model.number="quantity" type="number" min="1" :max="selected?.availableQuantity" step="1" required>
    <p class="muted">该商品剩余可申请金额上限 {{money(selected?.remainingAmount||'0.00')}}，本次实际申请金额以提交结果为准。</p>
    <label for="apply-reason">申请原因</label><select id="apply-reason" v-model="reason"><option v-for="(label,key) in reasons" :key="key" :value="key">{{label}}</option></select>
    <label for="apply-description">问题描述</label><textarea id="apply-description" v-model="description" required maxlength="1000" rows="4" placeholder="请描述遇到的问题"></textarea>
    <label class="check-label"><input v-model="confirmed" type="checkbox" required>我已核对商品与数量，确认提交退货退款申请</label>
   </fieldset>
   <p v-if="pending && !busy" class="notice">上次提交结果未确认。重试会复用同一请求，不会重复创建申请。也可先查看售后记录。</p>
   <button class="button primary" :disabled="busy || !confirmed">{{busy?'正在提交…':pending?'重试同一次提交':'确认提交申请'}}</button>
   <RouterLink class="text-button" to="/aftersales">查看售后记录</RouterLink>
  </form>
 </template>
</template>
