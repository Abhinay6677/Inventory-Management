import type { Role } from '../api/contracts'

export const ALL_ROLES: Role[] = ['store_manager', 'inventory_analyst', 'procurement_officer', 'warehouse_staff']
export const DASHBOARD_ACCESS_ROLES: Role[] = [...ALL_ROLES]
export const SUPPLIER_ACCESS_ROLES: Role[] = ['store_manager', 'procurement_officer']
export const SUPPLIER_MANAGEMENT_ROLES: Role[] = ['procurement_officer']
export const PRODUCT_CREATE_ROLES: Role[] = ['store_manager']
export const PRODUCT_EDIT_ROLES: Role[] = ['store_manager', 'inventory_analyst']
export const PRODUCT_DELETE_ROLES: Role[] = ['store_manager']
export const ORDER_ACCESS_ROLES: Role[] = ['store_manager', 'procurement_officer', 'warehouse_staff']
export const ORDER_CREATE_ROLES: Role[] = ['store_manager', 'procurement_officer']
export const ORDER_APPROVE_ROLES: Role[] = ['store_manager']
export const ORDER_RECEIVE_ROLES: Role[] = ['warehouse_staff']
export const STOCK_ACCESS_ROLES: Role[] = ['store_manager', 'inventory_analyst', 'warehouse_staff']
export const STOCK_UPDATE_ROLES: Role[] = ['warehouse_staff']
export const PRODUCT_APPROVAL_REVIEW_ROLES: Role[] = ['inventory_analyst']
export const STOCK_APPROVAL_REVIEW_ROLES: Role[] = ['store_manager']
export const APPROVAL_ACCESS_ROLES: Role[] = ['store_manager', 'inventory_analyst']
export const PHASE2_RAG_ACCESS_ROLES: Role[] = [...ALL_ROLES]

const FALLBACK_ROLE: Role = 'warehouse_staff'

export function normalizeRole(role: string | undefined | null): Role {
  switch (role?.toLowerCase()) {
    case 'store_manager':
    case 'inventory_analyst':
    case 'procurement_officer':
    case 'warehouse_staff':
      return role.toLowerCase() as Role
    case 'manager':
      return 'store_manager'
    case 'staff':
      return 'warehouse_staff'
    default:
      return FALLBACK_ROLE
  }
}

export function roleLabel(role: Role): string {
  switch (role) {
    case 'store_manager':
      return 'Store Manager'
    case 'inventory_analyst':
      return 'Inventory Analyst'
    case 'procurement_officer':
      return 'Procurement Officer'
    case 'warehouse_staff':
      return 'Warehouse Staff'
  }
}

export function defaultRouteForRole(role: Role): string {
  switch (role) {
    case 'store_manager':
    case 'inventory_analyst':
    case 'procurement_officer':
    case 'warehouse_staff':
      return '/dashboard'
  }
}

function normalizePath(path: string): string {
  if (!path) return '/'
  const clean = path.split('?')[0].split('#')[0]
  if (!clean.startsWith('/')) return `/${clean}`
  return clean
}

export function isRouteAllowedForRole(role: Role, path: string): boolean {
  const normalizedPath = normalizePath(path)

  if (normalizedPath === '/' || normalizedPath === '/login' || normalizedPath === '/register' || normalizedPath === '/unauthorized') {
    return true
  }

  if (normalizedPath === '/dashboard') {
    return DASHBOARD_ACCESS_ROLES.includes(role)
  }

  if (normalizedPath === '/approvals') {
    return APPROVAL_ACCESS_ROLES.includes(role)
  }

  if (normalizedPath === '/phase2/rag') {
    return PHASE2_RAG_ACCESS_ROLES.includes(role)
  }

  if (normalizedPath === '/stock' || normalizedPath === '/stock/audit') {
    return STOCK_ACCESS_ROLES.includes(role)
  }

  if (normalizedPath === '/products/new') {
    return PRODUCT_CREATE_ROLES.includes(role)
  }

  if (normalizedPath.endsWith('/edit') && normalizedPath.startsWith('/products/')) {
    return PRODUCT_EDIT_ROLES.includes(role)
  }

  if (normalizedPath === '/products' || normalizedPath.startsWith('/products/')) {
    return ALL_ROLES.includes(role)
  }

  if (normalizedPath === '/suppliers/new') {
    return SUPPLIER_MANAGEMENT_ROLES.includes(role)
  }

  if (normalizedPath.endsWith('/edit') && normalizedPath.startsWith('/suppliers/')) {
    return SUPPLIER_MANAGEMENT_ROLES.includes(role)
  }

  if (normalizedPath === '/suppliers' || normalizedPath.startsWith('/suppliers/')) {
    return SUPPLIER_ACCESS_ROLES.includes(role)
  }

  if (normalizedPath === '/orders/new') {
    return ORDER_CREATE_ROLES.includes(role)
  }

  if (normalizedPath === '/orders' || normalizedPath.startsWith('/orders/')) {
    return ORDER_ACCESS_ROLES.includes(role)
  }

  return true
}
