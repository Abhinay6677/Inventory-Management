import { api } from '../api/client'
import type { SupplierCatalogResponse, SupplierPerformanceResponse, SupplierRequest, SupplierResponse } from '../api/contracts'

export const supplierService = {
  getSuppliers: async () => {
    const { data } = await api.get<SupplierResponse[]>('/suppliers')
    return data
  },
  getSupplierById: async (id: number) => {
    const { data } = await api.get<SupplierResponse>(`/suppliers/${id}`)
    return data
  },
  getSupplierCatalog: async (id: number) => {
    const { data } = await api.get<SupplierCatalogResponse>(`/suppliers/${id}/catalog`)
    return data
  },
  getSupplierPerformance: async (id: number) => {
    const { data } = await api.get<SupplierPerformanceResponse>(`/suppliers/${id}/performance`)
    return data
  },
  createSupplier: async (payload: SupplierRequest) => {
    const { data } = await api.post<SupplierResponse>('/suppliers', payload)
    return data
  },
  updateSupplier: async (id: number, payload: SupplierRequest) => {
    const { data } = await api.put<SupplierResponse>(`/suppliers/${id}`, payload)
    return data
  },
  deleteSupplier: async (id: number) => {
    await api.delete(`/suppliers/${id}`)
  },
}
