import { api } from '../api/client'
import type { AuthResponse, LoginRequest, RegisterRequest } from '../api/contracts'

export const authService = {
  login: async (payload: LoginRequest) => {
    const { data } = await api.post<AuthResponse>('/auth/login', payload)
    return data
  },
  register: async (payload: RegisterRequest) => {
    const { data } = await api.post<AuthResponse>('/auth/register', payload)
    return data
  },
}
