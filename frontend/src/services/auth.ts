import { request, ApiError } from '../api/http'
import type { LoginResult, User } from '../api/types'
import { session, saveSession, clearSession } from '../state/session'
export async function login(username: string, password: string) {
  saveSession(await request<LoginResult>('/sessions', { method: 'POST', body: { username, password }, auth: false }))
}
export async function restoreSession() {
  const token = session.token
  if (!token) return
  if (Date.parse(session.expiresAt) <= Date.now()) { clearSession(); return }
  if (session.user) return
  try {
    const user = await request<User>('/users/me')
    if (session.token === token) { session.user = user; session.verificationError = '' }
  } catch (error) {
    if (session.token === token) session.verificationError = error instanceof Error ? error.message : '暂时无法确认账户信息。'
  }
}
export async function logout() {
  try { await request<void>('/sessions/current', { method: 'DELETE' }) }
  catch (error) { if (!(error instanceof ApiError && error.status === 401)) throw error }
  clearSession()
}
