<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

const navigation = [
  { to: '/', label: '项目总览', symbol: '◫' },
  { to: '/assistant', label: '智能售后', symbol: '✦' },
  { to: '/orders', label: '我的订单', symbol: '▤' },
  { to: '/aftersales', label: '售后记录', symbol: '↺' },
]
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"><span class="brand-icon">a.</span><span>售后助手<small>AFTERCARE</small></span></RouterLink>
      <p class="nav-label">工作空间</p>
      <nav aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to" class="nav-item" exact-active-class="active">
          <span aria-hidden="true">{{ item.symbol }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="sidebar-note"><span class="dot"></span> 开发预览<small>从一次咨询，到一次安心解决。</small></div>
    </aside>
    <div class="main-area">
      <header class="topbar"><span>电商智能售后工作台</span><span class="version">项目骨架 · v0.1</span></header>
      <main id="main-content"><RouterView /></main>
      <footer>AFTERCARE <span>以清晰的流程，连接每一步服务。</span></footer>
    </div>
  </div>
</template>
