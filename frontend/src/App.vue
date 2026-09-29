<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { session } from './state/session'
import { logout, restoreSession } from './services/auth'
import ErrorNotice from './components/ErrorNotice.vue'
const router = useRouter(),
    busy = ref(false),
    error = ref<unknown>(null)
watch(
    () => session.token,
    () => {
        error.value = null
    },
)
const navigation = [
    { to: '/', label: '服务首页', symbol: '◫' },
    { to: '/orders', label: '我的订单', symbol: '▤' },
    { to: '/assistant', label: '智能售后', symbol: '✦' },
    { to: '/aftersales', label: '售后记录', symbol: '↺' },
]
async function signOut() {
    if (busy.value) return
    busy.value = true
    error.value = null
    try {
        await logout()
        await router.replace('/login')
    } catch (cause) {
        error.value = cause
    } finally {
        busy.value = false
    }
}
</script>
<template>
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <div class="workspace">
        <aside class="sidebar">
            <RouterLink to="/" class="brand"
                ><span class="brand-icon">a.</span><span>售后助手<small>AFTERCARE</small></span></RouterLink
            >
            <p class="nav-label">我的服务</p>
            <nav aria-label="主导航">
                <RouterLink
                    v-for="item in navigation"
                    :key="item.to"
                    :to="item.to"
                    class="nav-item"
                    active-class="active"
                    ><span aria-hidden="true">{{ item.symbol }}</span
                    >{{ item.label }}</RouterLink
                ><RouterLink
                    v-if="session.user?.role === 'STAFF'"
                    to="/staff/aftersales"
                    class="nav-item"
                    active-class="active"
                    >✓ 客服审核</RouterLink
                >
            </nav>
            <div class="sidebar-note">
                <span class="dot"></span> 每一步，都有回应<small>从一次购买，到一份安心。</small>
            </div>
        </aside>
        <div class="main-area">
            <header class="topbar">
                <span>电商智能售后工作台</span>
                <div v-if="session.token" class="account-area">
                    <span class="avatar" aria-hidden="true">{{ (session.user?.displayName || '我').slice(0, 1) }}</span
                    ><span
                        >{{ session.user?.displayName || '当前账户'
                        }}<small v-if="session.user?.role === 'STAFF'">客服</small></span
                    ><button class="text-button" :disabled="busy" @click="signOut">
                        {{ busy ? '退出中…' : '退出登录' }}
                    </button>
                </div>
                <RouterLink v-else class="button secondary small-button" to="/login">登录账户 →</RouterLink>
            </header>
            <main id="main-content">
                <ErrorNotice :error="error" />
                <div v-if="session.verificationError" class="error-notice" role="alert">
                    {{ session.verificationError
                    }}<button class="text-button" @click="restoreSession">重试账户连接</button>
                </div>
                <RouterView />
            </main>
            <footer>AFTERCARE <span>以清晰的流程，连接每一步服务。</span></footer>
        </div>
    </div>
</template>
