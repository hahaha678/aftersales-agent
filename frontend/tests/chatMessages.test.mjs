import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import { createRenderer, h } from 'vue'
const source = readFileSync(new URL('../src/utils/chatMessages.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { mergeMessages, visibleMessages, messageKey } = await import(
    'data:text/javascript;base64,' + Buffer.from(js).toString('base64')
)
const message = (id, role = 'USER', runId = 'run-' + id) => ({
    id,
    runId,
    role,
    content: 'text ' + id,
    status: 'SUCCEEDED',
    createdAt: '',
})
test('numeric ordering preserves BIGINT precision and previously loaded history', () => {
    const old = [message('9'), message('10'), message('9007199254740993'), message('9007199254740992')]
    const result = mergeMessages(old, [{ ...old[1], content: 'updated' }, message('100')])
    assert.deepEqual(
        result.map((x) => x.id),
        ['9', '10', '100', '9007199254740992', '9007199254740993'],
    )
    assert.equal(result[1].content, 'updated')
})
test('stream completion preserves the actual Vue bubble node', () => {
    const removed = []
    const renderer = createRenderer({
        createElement: (tag) => ({ tag, children: [] }),
        createText: (text) => ({ text }),
        createComment: (text) => ({ text }),
        setText: (node, text) => {
            node.text = text
        },
        setElementText: (node, text) => {
            node.text = text
        },
        parentNode: (node) => node.parent,
        nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null,
        patchProp: () => {},
        insert: (node, parent, anchor = null) => {
            if (node.parent) {
                const a = node.parent.children
                a.splice(a.indexOf(node), 1)
            }
            node.parent = parent
            const i = anchor ? parent.children.indexOf(anchor) : -1
            parent.children.splice(i < 0 ? parent.children.length : i, 0, node)
        },
        remove: (node) => {
            removed.push(node)
            const a = node.parent.children
            a.splice(a.indexOf(node), 1)
        },
    })
    const root = { children: [] },
        run = { id: 'run', status: 'RUNNING', createdAt: '' }
    const history = [message('9', 'USER', 'run')]
    const view = (rows) =>
        h(
            'div',
            rows.map((m) => h('article', { key: messageKey(m) }, m.content)),
        )
    renderer.render(view(visibleMessages(history, run, 'streamed reply')), root)
    const bubble = root.children[0].children[1]
    const persisted = mergeMessages(history, [message('10', 'ASSISTANT', 'run')])
    renderer.render(view(visibleMessages(persisted, null, '')), root)
    assert.equal(root.children[0].children[1], bubble)
    assert.equal(removed.length, 0)
    assert.equal(root.children[0].children.length, 2)
})
test('persisted fast replies do not get a second streaming bubble', () => {
    const run = { id: 'run', status: 'SUCCEEDED', createdAt: '' }
    const rows = [message('9', 'USER', 'run'), message('10', 'ASSISTANT', 'run')]
    assert.equal(visibleMessages(rows, run, 'reply').length, 2)
})
