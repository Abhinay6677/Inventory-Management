import { api } from '../api/client'
import type { AuditLogEntry, StockAlertResponse } from '../api/contracts'

export const stockService = {
  getLowAlerts: async () => {
    const { data } = await api.get<StockAlertResponse[]>('/stock/low-alerts')
    return data
  },
  getAuditLog: async () => {
    const { data } = await api.get<AuditLogEntry[]>('/stock/audit')
    return data
  },
}
