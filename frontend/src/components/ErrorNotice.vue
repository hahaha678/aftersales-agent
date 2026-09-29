<script setup lang="ts">
import { ApiError } from '../api/http'
defineProps<{ error: unknown }>()
</script>
<template>
    <div v-if="error" class="error-notice" role="alert">
        <span>{{ error instanceof Error ? error.message : '加载失败，请重试。' }}</span
        ><small v-if="error instanceof ApiError && error.requestId">请求编号：{{ error.requestId }}</small
        ><slot />
    </div>
</template>
