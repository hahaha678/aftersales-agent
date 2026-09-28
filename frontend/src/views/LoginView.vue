<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../services/auth'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), busy = ref(false), error = ref<unknown>(null)
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = null
  try {
    await login(username.value, password.value)
    const next = route.query.redirect
    await router.replace(typeof next === 'string' && /^\/orders(?:[/?]|$)/.test(next) ? next : '/orders')
  } catch (cause) { error.value = cause }
  finally { busy.value = false; password.value = '' }
}
</script>
<template>
  <section class="login-layout">
    <div class="login-story"><p class="eyebrow">WELCOME TO AFTERCARE</p><h1>每一笔订单，<br>都有清晰的下一步。</h1><p>登录后查看订单、核对商品与物流，<br>让每一次服务都有迹可循。</p><div class="login-mark" aria-hidden="true">a.</div><span class="tag">订单 · 物流 · 服务</span></div>
    <div class="login-card"><p class="eyebrow">YOUR ACCOUNT</p><h2>登录售后助手</h2><p class="muted">使用你的账号继续。</p>
      <p v-if="route.query.expired" class="notice" role="status">登录状态已失效，请重新登录。</p>
      <form @submit.prevent="submit">
        <label for="username">账号</label><input id="username" v-model="username" required maxlength="64" autocomplete="username" placeholder="请输入账号" :disabled="busy">
        <label for="password">密码</label><input id="password" v-model="password" type="password" required maxlength="128" autocomplete="current-password" placeholder="请输入密码" :disabled="busy">
        <ErrorNotice :error="error" /><button class="button primary full-width" :disabled="busy">{{ busy ? '正在登录…' : '登录并查看订单 →' }}</button>
      </form><p class="fine-print">退出登录后，此设备的会话将失效。</p>
    </div>
  </section>
</template>
