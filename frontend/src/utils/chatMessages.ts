import type { Message, Run } from '../api/agent'

// A stream and its persisted message represent the same bubble.
export const messageKey = (message: Pick<Message, 'runId' | 'role'>) => `${message.runId}:${message.role}`

export function mergeMessages(previous: Message[], incoming: Message[]): Message[] {
    const byKey = new Map(previous.map((message) => [messageKey(message), message]))
    for (const message of incoming) byKey.set(messageKey(message), message)
    return [...byKey.values()].sort((a, b) => (BigInt(a.id) < BigInt(b.id) ? -1 : BigInt(a.id) > BigInt(b.id) ? 1 : 0))
}

export function visibleMessages(history: Message[], run: Run | null, answer: string): Message[] {
    if (!run || history.some((message) => message.runId === run.id && message.role === 'ASSISTANT')) return history
    return [
        ...history,
        {
            id: '',
            runId: run.id,
            role: 'ASSISTANT',
            content: answer,
            status: run.status,
            createdAt: run.createdAt,
        },
    ]
}
