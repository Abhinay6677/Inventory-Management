import type { AuthResponse } from '../api/contracts'

const SESSION_KEY = 'inventory-management-session'

export interface AuthSession {
  token: string
  user: Pick<AuthResponse, 'email' | 'fullName' | 'role'>
}

export function getSession(): AuthSession | null {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null

  try {
    return JSON.parse(raw) as AuthSession
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function setSession(session: AuthSession) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}
