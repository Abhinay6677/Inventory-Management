import { describe, expect, it } from 'vitest'
import {
  defaultRouteForRole,
  isRouteAllowedForRole,
  normalizeRole,
  roleLabel,
} from './roles'

describe('roles utils', () => {
  it('normalizes aliases and unknown values safely', () => {
    expect(normalizeRole('manager')).toBe('store_manager')
    expect(normalizeRole('staff')).toBe('warehouse_staff')
    expect(normalizeRole('unknown')).toBe('warehouse_staff')
  })

  it('provides labels for all supported roles', () => {
    expect(roleLabel('store_manager')).toBe('Store Manager')
    expect(roleLabel('inventory_analyst')).toBe('Inventory Analyst')
    expect(roleLabel('procurement_officer')).toBe('Procurement Officer')
    expect(roleLabel('warehouse_staff')).toBe('Warehouse Staff')
  })

  it('routes users to dashboard by default', () => {
    expect(defaultRouteForRole('store_manager')).toBe('/dashboard')
    expect(defaultRouteForRole('inventory_analyst')).toBe('/dashboard')
    expect(defaultRouteForRole('procurement_officer')).toBe('/dashboard')
    expect(defaultRouteForRole('warehouse_staff')).toBe('/dashboard')
  })

  it('enforces route restrictions correctly', () => {
    expect(isRouteAllowedForRole('procurement_officer', '/approvals')).toBe(false)
    expect(isRouteAllowedForRole('inventory_analyst', '/orders/new')).toBe(false)
    expect(isRouteAllowedForRole('warehouse_staff', '/orders/12')).toBe(true)
    expect(isRouteAllowedForRole('store_manager', '/suppliers')).toBe(true)
    expect(isRouteAllowedForRole('warehouse_staff', '/phase2/rag')).toBe(true)
  })
})
