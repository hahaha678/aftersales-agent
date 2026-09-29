<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import * as api from '../api/knowledge'
import { session } from '../state/session'
import ErrorNotice from '../components/ErrorNotice.vue'
import { readPolicyFile } from '../utils/policyFile'
const configured = ref<boolean | null>(null),
    editing = ref<api.Policy | null>(null)
function edit(row: api.Policy) {
    editing.value = row
    Object.assign(form, {
        policyKey: row.policyKey,
        version: row.version,
        title: row.title,
        scope: row.scope,
        content: row.content,
        effectiveFrom: row.effectiveFrom,
        effectiveUntil: row.effectiveUntil,
    })
    message.value = '正在编辑未发布草稿，保存时会检查是否有其他客服修改。'
}
function reset() {
    editing.value = null
    Object.assign(form, {
        policyKey: '',
        version: 1,
        title: '',
        scope: 'MOUSE',
        content: '',
        effectiveFrom: '',
        effectiveUntil: '',
    })
}
async function importFile(event: Event) {
    const input = event.target as HTMLInputElement,
        file = input.files?.[0]
    if (!file) return
    await action(async () => {
        if (form.content.trim()) throw new Error('原文已有内容，请先保存或清空原文后再导入，避免覆盖。')
        const result = await readPolicyFile(file)
        form.content = result.content
        if (!form.title) form.title = result.title
        message.value = '文件已读入表单，尚未保存或发布，请补全资料后保存草稿。'
    })
    input.value = ''
}
const rows = ref<api.Policy[]>([]),
    sources = ref<api.PolicySource[]>([])
const question = ref(''),
    scope = ref('MOUSE'),
    message = ref(''),
    busy = ref(false),
    error = ref<unknown>(null)
const form = reactive({
    policyKey: '',
    version: 1,
    title: '',
    scope: 'MOUSE',
    content: '',
    effectiveFrom: '',
    effectiveUntil: '',
})
async function load() {
    configured.value = (await api.knowledgeStatus()).configured
    if (session.user?.role === 'STAFF') rows.value = await api.policies()
}
async function action(work: () => Promise<void>) {
    if (busy.value) return
    busy.value = true
    error.value = null
    try {
        await work()
    } catch (cause) {
        error.value = cause
    } finally {
        busy.value = false
    }
}
async function search() {
    await action(async () => {
        sources.value = []
        const result = await api.searchPolicies(question.value, scope.value)
        sources.value = result.sources
        message.value = result.message
    })
}
async function save() {
    await action(async () => {
        if (editing.value) {
            const saved = await api.editPolicy(editing.value.id, {
                expectedFingerprint: editing.value.fingerprint,
                title: form.title,
                content: form.content,
                effectiveFrom: form.effectiveFrom,
                effectiveUntil: form.effectiveUntil,
            })
            editing.value = saved
        } else {
            editing.value = await api.createPolicy({ ...form })
        }
        message.value = '草稿已保存，请核对后发布。'
        await load()
    })
}
async function publish(row: api.Policy) {
    await action(async () => {
        if (
            editing.value?.id === row.id &&
            (form.content !== editing.value.content ||
                form.title !== editing.value.title ||
                form.effectiveFrom !== editing.value.effectiveFrom ||
                form.effectiveUntil !== editing.value.effectiveUntil)
        )
            throw new Error('当前草稿还有未保存的修改，请先保存再发布。')
        await api.publishPolicy(row.id)
        if (editing.value?.id === row.id) reset()
        message.value = '政策已发布，同范围旧版本已下线。'
        await load()
    })
}
async function archive(row: api.Policy) {
    await action(async () => {
        await api.archivePolicy(row.id)
        await load()
    })
}
onMounted(() => action(load))
</script>
<template>
    <section class="surface policy-page">
        <h1>售后政策知识库</h1>
        <p>检索当前生效的政策，查看原文和适用范围。演示政策不代表真实商家承诺。</p>
        <ErrorNotice :error="error" />
        <p v-if="configured === false" role="status">
            向量服务尚未配置。现在可以录入、导入和编辑草稿；完成模型配置后再发布和检索。
        </p>
        <p v-else-if="configured === true">向量服务已配置（未验证模型下载和服务连通性）。模型准备期间可先保存草稿。</p>
        <p v-if="message" role="status">{{ message }}</p>
        <form @submit.prevent="search" class="policy-form">
            <label
                >适用范围<select v-model="scope">
                    <option value="MOUSE">鼠标</option>
                    <option value="GLOBAL">通用</option>
                </select></label
            >
            <label
                >政策问题<input v-model="question" required maxlength="500" placeholder="鼠标按键故障需要哪些材料？"
            /></label>
            <button class="button primary" :disabled="busy">{{ busy ? '处理中…' : '检索政策' }}</button>
        </form>
        <article v-for="source in sources" :key="source.sourceId" class="policy-source">
            <RouterLink :to="'/policies/' + source.policyId">{{ source.title }} · v{{ source.version }}</RouterLink>
            <p>适用范围：{{ source.scope }} · 来源：{{ source.sourceId }}</p>
            <pre>{{ source.excerpt }}</pre>
        </article>
    </section>
    <section v-if="session.user?.role === 'STAFF'" class="surface policy-page">
        <h2>客服政策管理</h2>
        <p>未发布草稿可以编辑。已发布政策需增加版本号新建，保留历史引用原文。</p>
        <p v-if="editing">正在编辑：{{ editing.title }} · v{{ editing.version }}</p>
        <button v-if="editing" class="button secondary" :disabled="busy" @click="reset">结束编辑，创建新草稿</button>
        <form @submit.prevent="save" class="policy-form">
            <fieldset :disabled="busy" style="border: 0; padding: 0; display: contents">
                <label
                    >导入文本文件（UTF-8，最多 128 KB、20000 字符）<input
                        type="file"
                        accept=".txt,.md"
                        @change="importFile"
                /></label>
                <label
                    >政策标识<input
                        v-model="form.policyKey"
                        :disabled="!!editing"
                        required
                        pattern="[A-Za-z0-9_-]{1,64}"
                        placeholder="mouse-materials"
                /></label>
                <label
                    >版本号<input v-model.number="form.version" :disabled="!!editing" type="number" min="1" required
                /></label>
                <label>标题<input v-model="form.title" maxlength="120" required /></label>
                <label
                    >范围<select v-model="form.scope" :disabled="!!editing">
                        <option value="MOUSE">鼠标</option>
                        <option value="GLOBAL">通用</option>
                    </select></label
                >
                <label>生效日期<input v-model="form.effectiveFrom" type="date" required /></label>
                <label>截止日期<input v-model="form.effectiveUntil" type="date" required /></label>
                <label
                    >政策原文（纯文本或 Markdown）<textarea
                        v-model="form.content"
                        rows="9"
                        required
                        maxlength="20000"
                    />
                </label>
                <button class="button primary" :disabled="busy">保存草稿</button>
            </fieldset>
        </form>
        <article v-for="row in rows" :key="row.id" class="policy-source">
            <RouterLink :to="'/policies/' + row.id">{{ row.title }} · v{{ row.version }}</RouterLink>
            <p>
                {{ row.scope }} ·
                {{ row.status === 'DRAFT' ? '草稿' : row.status === 'PUBLISHED' ? '已发布' : '已下线' }} ·
                {{ row.effectiveFrom }} 至 {{ row.effectiveUntil }}
            </p>
            <button v-if="row.status === 'DRAFT'" class="button primary" :disabled="busy" @click="publish(row)">
                核对并发布
            </button>
            <button v-if="row.status === 'DRAFT'" class="button secondary" :disabled="busy" @click="edit(row)">
                编辑草稿
            </button>
            <button v-if="row.status === 'PUBLISHED'" class="button secondary" :disabled="busy" @click="archive(row)">
                下线
            </button>
        </article>
    </section>
</template>
<style scoped>
.policy-page {
    padding: 28px;
    margin-bottom: 24px;
}
.policy-form {
    display: grid;
    gap: 16px;
    margin: 24px 0;
}
label {
    display: grid;
    gap: 8px;
}
input,
select,
textarea {
    padding: 10px;
    border: 1px solid #ccd6cf;
    border-radius: 6px;
    font: inherit;
}
.policy-source {
    border-top: 1px solid #ccd6cf;
    padding: 18px 0;
}
pre {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    font: inherit;
}
</style>
