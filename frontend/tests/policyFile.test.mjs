import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
const js = ts.transpileModule(readFileSync(new URL('../src/utils/policyFile.ts', import.meta.url), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { readPolicyFile } = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'))
function file(name, content) {
    const bytes = new TextEncoder().encode(content)
    return { name, size: bytes.length, arrayBuffer: async () => bytes.buffer }
}
test('imports UTF-8 markdown without rendering markup and normalizes BOM and CRLF', async () => {
    const result = await readPolicyFile(file('政策.MD', '\uFEFF# 材料\r\n<script>不执行</script>'))
    assert.equal(result.title, '政策')
    assert.equal(result.content, '# 材料\n<script>不执行</script>')
})
test('rejects unsupported, oversized, empty and non-UTF8 files', async () => {
    await assert.rejects(() => readPolicyFile(file('政策.pdf', 'text')), /仅支持/)
    await assert.rejects(() => readPolicyFile({ ...file('政策.txt', 'text'), size: 128 * 1024 + 1 }), /128 KB/)
    await assert.rejects(() => readPolicyFile(file('政策.txt', 'a'.repeat(20001))), /20000/)
    await assert.rejects(() => readPolicyFile(file('政策.txt', ' \n')), /为空/)
    await assert.rejects(
        () => readPolicyFile({ name: '政策.txt', size: 1, arrayBuffer: async () => new Uint8Array([255]).buffer }),
        /UTF-8/,
    )
})
