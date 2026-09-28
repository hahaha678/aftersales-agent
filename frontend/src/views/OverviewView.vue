<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getBackendHealth } from '../api/system'

const state = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const message = ref('点击检测，确认前端与后端的连接。')
const checkedAt = ref('')

async function checkConnection() {
  if (state.value === 'loading') return
  state.value = 'loading'
  message.value = '正在连接后端服务…'
  try {
    const health = await getBackendHealth()
    if (health.status !== 'UP') throw new Error(`后端状态：${health.status}`)
    state.value = 'success'
    message.value = '连接成功，后端健康状态为 UP。'
  } catch (error) {
    state.value = 'error'
    message.value = error instanceof Error ? error.message : '检测失败，请稍后重试。'
  } finally {
    checkedAt.value = new Date().toLocaleTimeString('zh-CN')
  }
}
</script>

<template>
  <section class="page-heading"><p class="eyebrow">WORKSPACE / OVERVIEW</p><h1>让售后，走完最后一公里。</h1><p>连接订单、政策与处理流程，搭建你的智能售后工作台。</p></section>
  <section class="hero-panel">
    <div><span class="tag">开发起点</span><h2>从一个问题，<br />到一个完整的解决方案。</h2><p>先建立可靠的业务流程，再让 Agent 理解问题、查询信息并协助申请。</p><RouterLink to="/assistant" class="button light">查看智能售后入口 <span aria-hidden="true">↗</span></RouterLink></div>
    <ol class="journey"><li><span>01</span><div>理解问题<small>确认订单与售后诉求</small></div></li><li><span>02</span><div>核对方案<small>查询规则，等待用户确认</small></div></li><li><span>03</span><div>跟进处理<small>提交申请，追踪服务进度</small></div></li></ol>
  </section>
  <section class="section-heading"><h2>服务入口</h2><span>业务模块将在后续阶段接入</span></section>
  <div class="module-grid">
    <RouterLink to="/assistant" class="module-card"><span class="module-icon">✦</span><span class="badge">待开发</span><h3>智能售后</h3><p>多轮咨询、政策问答与申请确认。</p><span class="card-link">查看模块 →</span></RouterLink>
    <RouterLink to="/orders" class="module-card"><span class="module-icon">▤</span><span class="badge">待开发</span><h3>我的订单</h3><p>订单详情、商品信息与物流状态。</p><span class="card-link">查看模块 →</span></RouterLink>
    <RouterLink to="/aftersales" class="module-card"><span class="module-icon">↺</span><span class="badge">待开发</span><h3>售后记录</h3><p>申请进度、处理记录与结果查询。</p><span class="card-link">查看模块 →</span></RouterLink>
  </div>
  <section class="connection-panel">
    <div><p class="eyebrow">CONNECTION</p><h2>前后端连通检测</h2><p>通过开发代理请求 Spring Boot 健康接口。</p><p class="connection-result" :class="state" role="status" aria-live="polite">{{ message }}<small v-if="checkedAt">上次检测 {{ checkedAt }}</small></p><small>健康检查不代表数据库、模型或售后功能已经接入。</small></div>
    <button class="button primary" :disabled="state === 'loading'" @click="checkConnection">{{ state === 'loading' ? '检测中…' : '检测连接' }}</button>
  </section>
</template>
