import type { Category, MovementType, POStatus } from '../api/contracts'

const currency = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})

export function formatCurrency(value?: number | null) {
  return currency.format(value ?? 0)
}

export function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

export function categoryLabel(value: Category) {
  return value.replace('_', ' ')
}

export function statusBadgeClass(status: POStatus) {
  switch (status) {
    case 'received':
      return 'bg-success'
    case 'cancelled':
      return 'bg-danger'
    case 'submitted':
    case 'acknowledged':
      return 'bg-warning text-dark'
    default:
      return 'bg-secondary'
  }
}

export function movementBadgeClass(type: MovementType) {
  switch (type) {
    case 'receipt':
      return 'bg-success'
    case 'sale':
      return 'bg-danger'
    default:
      return 'bg-info text-dark'
  }
}
