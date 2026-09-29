/** 文件仅在浏览器读取，确认保存后才通过现有 JSON 接口提交文本。 */
export async function readPolicyFile(file: { name: string; size: number; arrayBuffer: () => Promise<ArrayBuffer> }) {
    if (!/\.(txt|md)$/i.test(file.name)) throw new Error('仅支持 UTF-8 编码的 .txt 或 .md 文件。')
    if (file.size > 128 * 1024) throw new Error('文件不能超过 128 KB。')
    let content: string
    try {
        content = new TextDecoder('utf-8', { fatal: true })
            .decode(await file.arrayBuffer())
            .replace(/^\uFEFF/, '')
            .replace(/\r\n/g, '\n')
    } catch {
        throw new Error('无法读取 UTF-8 文本，请将文件另存为 UTF-8 后重试。')
    }
    if (!content.trim() || content.includes('\0')) throw new Error('文件为空或包含非文本内容。')
    if (content.length > 20000) throw new Error('政策原文不能超过 20000 字符，请拆分后导入。')
    return { title: file.name.replace(/\.(txt|md)$/i, '').slice(0, 120), content }
}
