<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { request } from '../api/http'
import ErrorNotice from './ErrorNotice.vue'

const props = defineProps<{ requestId: string; status: string; staff: boolean }>()
type Evidence = { id: string; mediaType: string; byteSize: number; createdAt: string }
const images = ref<(Evidence & { url: string })[]>([])
const error = ref<unknown>(null),
    busy = ref(false),
    loading = ref(false)
const selected = ref<File | null>(null),
    preview = ref(''),
    enlarged = ref('')
const input = ref<HTMLInputElement | null>(null)
const controller = new AbortController()
const path = `/${props.staff ? 'staff/' : ''}aftersales/${props.requestId}/evidence`
let disposed = false

function choose(event: Event) {
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = ''
    selected.value = null
    error.value = null
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) return
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
        error.value = new Error('请选择不超过 5 MB 的 JPEG 或 PNG 图片。')
        return
    }
    selected.value = file
    preview.value = URL.createObjectURL(file)
}

async function load() {
    error.value = null
    loading.value = true
    try {
        const values = await request<Evidence[]>(path, { signal: controller.signal })
        // 使用带 Bearer 的请求获取图片，令牌不出现在图片地址中。
        const loaded: (Evidence & { url: string })[] = []
        try {
            for (const value of values) {
                const blob = await request<Blob>(`${path}/${value.id}/content`, {
                    responseType: 'blob',
                    signal: controller.signal,
                })
                loaded.push({ ...value, url: URL.createObjectURL(blob) })
            }
            if (disposed) return
            images.value.forEach((image) => URL.revokeObjectURL(image.url))
            images.value = loaded.splice(0)
        } finally {
            loaded.forEach((image) => URL.revokeObjectURL(image.url))
        }
    } catch (cause) {
        if (!disposed) error.value = cause
    } finally {
        loading.value = false
    }
}

async function upload() {
    if (!selected.value || busy.value) return
    busy.value = true
    error.value = null
    try {
        const body = new FormData()
        body.append('file', selected.value)
        await request<Evidence>(path, { method: 'POST', body, signal: controller.signal, timeoutMs: 30000 })
        selected.value = null
        URL.revokeObjectURL(preview.value)
        preview.value = ''
        if (input.value) input.value.value = ''
        await load()
    } catch (cause) {
        if (!disposed) error.value = cause
    } finally {
        busy.value = false
    }
}
onMounted(load)
onBeforeUnmount(() => {
    disposed = true
    controller.abort()
    images.value.forEach((image) => URL.revokeObjectURL(image.url))
    if (preview.value) URL.revokeObjectURL(preview.value)
})
</script>

<template>
    <section class="surface">
        <h2>
            售后图片凭证 <small>（{{ images.length }}/5）</small>
        </h2>
        <p class="muted">供客服核对商品问题，不会发送给 AI 模型。上传后作为申请记录保留，不支持修改或删除。</p>
        <ErrorNotice :error="error">
            <button type="button" :disabled="loading || busy" @click="load">重新加载图片</button>
        </ErrorNotice>
        <p v-if="loading">正在加载图片…</p>
        <p v-else-if="!images.length" class="muted">暂无图片凭证</p>
        <div class="evidence-grid">
            <button
                v-for="(image, index) in images"
                :key="image.id"
                class="image-button"
                type="button"
                @click="enlarged = image.url"
                :aria-label="`放大凭证 ${index + 1}`"
            >
                <img :src="image.url" :alt="`售后凭证 ${index + 1}`" />
            </button>
        </div>
        <form v-if="!staff && status === 'PENDING' && images.length < 5" @submit.prevent="upload">
            <p>JPEG / PNG，每张最多 5 MB，最多 1600 万像素。请先预览，再确认上传。</p>
            <input
                ref="input"
                type="file"
                accept="image/jpeg,image/png"
                aria-label="选择图片凭证"
                :disabled="busy || loading"
                @change="choose"
            />
            <img v-if="preview" :src="preview" alt="待上传图片预览" class="selected-preview" />
            <button type="submit" class="primary-button" :disabled="!selected || busy || loading">
                {{ busy ? '正在上传…' : '确认上传' }}
            </button>
        </form>
        <p v-else-if="!staff && status !== 'PENDING'" class="muted">审核开始处理后不能追加图片。</p>
        <dialog :open="!!enlarged" class="image-dialog" @keydown.esc.prevent="enlarged = ''">
            <button type="button" @click="enlarged = ''">关闭图片</button>
            <img v-if="enlarged" :src="enlarged" alt="放大的售后凭证" />
        </dialog>
    </section>
</template>

<style scoped>
.evidence-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    margin: 16px 0;
}
.image-button {
    padding: 0;
    background: transparent;
    border: 1px solid #dce5df;
    cursor: zoom-in;
    border-radius: 8px;
    overflow: hidden;
}
.image-button img {
    width: 140px;
    height: 140px;
    object-fit: cover;
    display: block;
}
.selected-preview {
    display: block;
    max-width: 240px;
    max-height: 200px;
    margin: 16px 0;
}
form .primary-button {
    display: block;
    margin-top: 16px;
}
.image-dialog[open] {
    position: fixed;
    inset: 5vh 5vw;
    z-index: 100;
    width: 90vw;
    height: 90vh;
    box-sizing: border-box;
    border: 1px solid #dce5df;
    background: #fff;
    box-shadow: 0 0 0 100vmax #0009;
}
.image-dialog img {
    display: block;
    max-width: 100%;
    max-height: calc(100% - 50px);
    margin: 12px auto;
    object-fit: contain;
}
</style>
