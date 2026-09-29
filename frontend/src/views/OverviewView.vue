<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { session } from '../state/session'
</script>
<template>
    <section class="page-heading">
        <p class="eyebrow">A LITTLE MORE PEACE OF MIND</p>
        <h1>{{ session.user ? `${session.user.displayName}，欢迎回来。` : '让每一次购买，都安心抵达。' }}</h1>
        <p>订单信息、配送进度与售后服务，在这里清晰连接。</p>
    </section>
    <section class="hero-panel">
        <div>
            <span class="tag">每一步，看得见</span>
            <h2>从下单到签收，<br />你关心的，都在这里。</h2>
            <p>查看订单里的每一件商品，了解实付金额，跟进包裹配送动态。</p>
            <RouterLink to="/orders" class="button light">查看我的订单 <span aria-hidden="true">↗</span></RouterLink>
        </div>
        <ol class="journey">
            <li>
                <span>01</span>
                <div>找到订单<small>按订单号或状态快速查找</small></div>
            </li>
            <li>
                <span>02</span>
                <div>核对商品<small>商品规格、购买数量与实付金额</small></div>
            </li>
            <li>
                <span>03</span>
                <div>跟进配送<small>查看包裹与物流时间线</small></div>
            </li>
        </ol>
    </section>
    <section class="section-heading">
        <h2>你的服务入口</h2>
        <span>所有订单信息，仅对本人可见</span>
    </section>
    <div class="module-grid">
        <RouterLink to="/orders" class="module-card"
            ><span class="module-icon">▤</span><span class="badge available">已开放</span>
            <h3>我的订单</h3>
            <p>查看订单详情、商品信息和物流状态。</p>
            <span class="card-link">查看订单 →</span></RouterLink
        ><RouterLink to="/assistant" class="module-card"
            ><span class="module-icon">✦</span><span class="badge available">对话服务</span>
            <h3>智能售后</h3>
            <p>查订单、看物流，核对并提交售后草稿。</p>
            <span class="card-link">开始对话 →</span></RouterLink
        ><RouterLink to="/aftersales" class="module-card"
            ><span class="module-icon">↺</span><span class="badge available">已开放</span>
            <h3>售后记录</h3>
            <p>查询退货退款申请与客服审核进度。</p>
            <span class="card-link">查看申请 →</span></RouterLink
        >
    </div>
    <div class="welcome-note">
        <span aria-hidden="true">◎</span>
        <div>
            <strong>清楚每一个细节，安心每一次等待。</strong>
            <p>登录后即可查看属于你的订单，退出登录会结束当前设备的会话。</p>
        </div>
    </div>
</template>
