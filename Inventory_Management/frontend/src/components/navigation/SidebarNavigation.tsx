import { NavLink } from 'react-router-dom'
import { FiActivity, FiBarChart2, FiBell, FiBookOpen, FiBox, FiCheckSquare, FiClipboard, FiUsers } from 'react-icons/fi'
import type { Role } from '../../api/contracts'
import { useAuth } from '../../hooks/useAuth'
import { APPROVAL_ACCESS_ROLES, ORDER_ACCESS_ROLES, PHASE2_RAG_ACCESS_ROLES, STOCK_ACCESS_ROLES, SUPPLIER_ACCESS_ROLES } from '../../utils/roles'

const items: Array<{ to: string; label: string; icon: typeof FiBarChart2; roles?: Role[] }> = [
  { to: '/dashboard', label: 'Dashboard', icon: FiBarChart2 },
  { to: '/products', label: 'Products', icon: FiBox },
  { to: '/suppliers', label: 'Suppliers', icon: FiUsers, roles: SUPPLIER_ACCESS_ROLES },
  { to: '/orders', label: 'Purchase Orders', icon: FiClipboard, roles: ORDER_ACCESS_ROLES },
  { to: '/stock', label: 'Stock', icon: FiBell, roles: STOCK_ACCESS_ROLES },
  { to: '/stock/audit', label: 'Audit Log', icon: FiActivity, roles: STOCK_ACCESS_ROLES },
  { to: '/approvals', label: 'Approvals', icon: FiCheckSquare, roles: APPROVAL_ACCESS_ROLES },
  { to: '/phase2/rag', label: "FAQ's", icon: FiBookOpen, roles: PHASE2_RAG_ACCESS_ROLES },
]

export function SidebarNavigation() {
  const { hasRole } = useAuth()

  return (
    <nav className="sidebar-nav">
      {items
        .filter((item) => !item.roles || hasRole(item.roles))
        .map((item) => {
          const Icon = item.icon
          return (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}>
              <Icon className="sidebar-link-icon" />
              <span>{item.label}</span>
            </NavLink>
          )
        })}
    </nav>
  )
}
