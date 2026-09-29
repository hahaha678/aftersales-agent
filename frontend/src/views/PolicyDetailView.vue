<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { policy, type Policy } from '../api/knowledge'
import ErrorNotice from '../components/ErrorNotice.vue'
const route = useRoute(),
    row = ref<Policy | null>(null),
    error = ref<unknown>(null)
let generation = 0
watch(
    () => route.params.id,
    async (id) => {
        const current = ++generation
        row.value = null
        error.value = null
        try {
            const result = await policy(String(id))
            if (current === generation) row.value = result
        } catch (cause) {
            if (current === generation) error.value = cause
        }
    },
    { immediate: true },
)
</script>
<template>
    <section class="surface" style="padding: 28px">
        <ErrorNotice :error="error" />
        <template v-if="row">
            <h1>{{ row.title }}</h1>
            <p>
                版本 {{ row.version }} · 适用范围 {{ row.scope }} · {{ row.effectiveFrom }} 至 {{ row.effectiveUntil }}
            </p>
            <p v-if="row.status !== 'PUBLISHED'">此版本当前未发布或已下线，仅供核对原文。</p>
            <pre style="white-space: pre-wrap; overflow-wrap: anywhere; font: inherit">{{ row.content }}</pre>
        </template>
    </section>
</template>
