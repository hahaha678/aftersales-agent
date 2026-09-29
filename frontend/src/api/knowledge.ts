import { request } from './http'
export interface Policy {
    id: string
    policyKey: string
    version: number
    title: string
    scope: string
    content: string
    status: string
    effectiveFrom: string
    effectiveUntil: string
    fingerprint: string
}
export interface PolicySource {
    sourceId: string
    policyId: string
    title: string
    version: number
    scope: string
    excerpt: string
    sourcePath: string
    score: number
}
export const policies = () => request<Policy[]>('/staff/policies')
export const policy = (id: string) => request<Policy>(`/policies/${id}`)
export const knowledgeStatus = () => request<{ configured: boolean }>('/knowledge/status')
export const editPolicy = (
    id: string,
    body: {
        expectedFingerprint: string
        title: string
        content: string
        effectiveFrom: string
        effectiveUntil: string
    },
) => request<Policy>(`/staff/policies/${id}`, { method: 'PUT', body })
export const createPolicy = (body: Omit<Policy, 'id' | 'status' | 'fingerprint'>) =>
    request<Policy>('/staff/policies', { method: 'POST', body })
export const publishPolicy = (id: string) =>
    request<Policy>(`/staff/policies/${id}/publication`, { method: 'POST', timeoutMs: 30000 })
export const archivePolicy = (id: string) => request<Policy>(`/staff/policies/${id}/archival`, { method: 'POST' })
export const searchPolicies = (question: string, scope: string) =>
    request<{ message: string; sources: PolicySource[] }>('/knowledge/searches', {
        method: 'POST',
        body: { question, scope },
        timeoutMs: 30000,
    })
export const runSources = (id: string) => request<PolicySource[]>(`/agent-runs/${id}/sources`)
