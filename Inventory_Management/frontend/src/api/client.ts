import axios from 'axios'
import { clearSession, getSession } from '../utils/storage'
import { getErrorMessage } from '../utils/errors'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

export const api = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const session = getSession()
  if (session?.token) {
    config.headers.Authorization = `Bearer ${session.token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      clearSession()
      if (!['#/login', '#/register'].includes(window.location.hash)) {
        window.location.hash = '#/login'
      }
    }
    return Promise.reject(new Error(getErrorMessage(error)))
  },
)
