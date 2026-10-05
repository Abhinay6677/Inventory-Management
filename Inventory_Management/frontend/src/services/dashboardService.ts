import { api } from '../api/client'
import type { DashboardResponse } from '../api/contracts'

export const dashboardService = {
  getDashboard: async () => {
    const { data } = await api.get<DashboardResponse>('/dashboard')
    return data
  },
}
