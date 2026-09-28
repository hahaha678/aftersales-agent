<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <section class="page-heading"><p class="eyebrow">WORKSPACE / MODULE</p><h1>{{ route.meta.title }}</h1><p>{{ route.meta.description }}</p></section>
  <section class="empty-panel"><span class="empty-symbol" aria-hidden="true">◇</span><span class="tag">开发预览</span><h2>入口已就绪，业务即将接入。</h2><p>当前页面是路由占位，尚未连接业务接口。<br />后续将随订单与售后模块逐步实现。</p><RouterLink to="/" class="button primary">返回项目总览</RouterLink></section>
</template>
