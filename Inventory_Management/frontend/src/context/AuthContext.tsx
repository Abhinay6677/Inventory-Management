import { createContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { AuthResponse, LoginRequest, RegisterRequest, Role } from '../api/contracts'
import { authService } from '../services/authService'
import { clearSession, getSession, setSession } from '../utils/storage'
import { normalizeRole } from '../utils/roles'

type AuthUser = Pick<AuthResponse, 'email' | 'fullName' | 'role'>

interface AuthContextValue {
  user: AuthUser | null
  token: string | null
  isAuthenticated: boolean
  hasRole: (roles: Role[]) => boolean
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

interface Props {
  children: ReactNode
}

export function AuthProvider({ children }: Props) {
  const [session, setSessionState] = useState(getSession())

  useEffect(() => {
    const syncSession = () => setSessionState(getSession())
    window.addEventListener('storage', syncSession)
    return () => window.removeEventListener('storage', syncSession)
  }, [])

  const auth = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ? { ...session.user, role: normalizeRole(session.user.role) } : null,
      token: session?.token ?? null,
      isAuthenticated: Boolean(session?.token),
      hasRole: (roles) => {
        if (!session?.user) return false
        return roles.includes(normalizeRole(session.user.role))
      },
      login: async (payload) => {
        const response = await authService.login(payload)
        const role = normalizeRole(response.role)
        const nextSession = {
          token: response.token,
          user: { email: response.email, fullName: response.fullName, role },
        }
        setSession(nextSession)
        setSessionState(nextSession)
      },
      register: async (payload) => {
        await authService.register(payload)
        clearSession()
        setSessionState(null)
      },
      logout: () => {
        clearSession()
        setSessionState(null)
        window.location.hash = '#/login'
      },
    }),
    [session],
  )

  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}
