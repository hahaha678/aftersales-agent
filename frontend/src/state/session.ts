import { reactive } from 'vue'
import type { User, LoginResult } from '../api/types'
const KEY = 'aftercare.session'
function stored(): { token: string; expiresAt: string } {
    try {
        const value = JSON.parse(sessionStorage.getItem(KEY) || 'null')
        if (
            typeof value?.token === 'string' &&
            typeof value?.expiresAt === 'string' &&
            Date.parse(value.expiresAt) > Date.now()
        )
            return value
        sessionStorage.removeItem(KEY)
    } catch {
        /* Storage unavailable: memory session only. */
    }
    return { token: '', expiresAt: '' }
}
const initial = stored()
export const session = reactive({
    token: initial.token,
    expiresAt: initial.expiresAt,
    user: null as User | null,
    verificationError: '',
})
export function saveSession(result: LoginResult) {
    session.token = result.accessToken
    session.expiresAt = result.expiresAt
    session.user = result.user
    session.verificationError = ''
    try {
        sessionStorage.setItem(KEY, JSON.stringify({ token: session.token, expiresAt: session.expiresAt }))
    } catch {
        /* memory only */
    }
}
export function clearSession() {
    session.token = ''
    session.expiresAt = ''
    session.user = null
    session.verificationError = ''
    try {
        sessionStorage.removeItem(KEY)
    } catch {
        /* memory only */
    }
}
