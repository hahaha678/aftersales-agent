<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
    <section class="page-heading">
        <p class="eyebrow">AFTERCARE / SERVICES</p>
        <h1>{{ route.meta.title }}</h1>
        <p>{{ route.meta.description }}</p>
    </section>
    <section class="empty-panel">
        <span class="empty-symbol" aria-hidden="true">◇</span
        ><span class="tag">{{ route.meta.planned ? '即将开放' : '404' }}</span>
        <h2>{{ route.meta.planned ? '这项服务正在准备中' : '页面未找到' }}</h2>
        <p>{{ route.meta.planned ? '你现在可以查看订单、商品信息和物流进度。' : '请检查地址，或从首页继续浏览。' }}</p>
        <RouterLink :to="route.meta.planned ? '/orders' : '/'" class="button primary">{{
            route.meta.planned ? '查看我的订单' : '返回首页'
        }}</RouterLink>
    </section>
</template>
