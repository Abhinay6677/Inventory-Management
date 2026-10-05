import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { FiBell, FiLogOut, FiMenu, FiMoon, FiShield, FiSun } from 'react-icons/fi'
import { AppLogo } from '../branding/AppLogo'
import { useAuth } from '../../hooks/useAuth'
import { roleLabel } from '../../utils/roles'
import { useTheme } from '../../context/ThemeContext'
import { stockService } from '../../services/stockService'
import type { StockAlertResponse } from '../../api/contracts'

interface Props {
  onToggleSidebar: () => void
}

export function Topbar({ onToggleSidebar }: Props) {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const [alerts, setAlerts] = useState<StockAlertResponse[]>([])
  const [bellOpen, setBellOpen] = useState(false)
  const bellRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    stockService.getLowAlerts().then(setAlerts).catch(() => {})
    const interval = setInterval(() => {
      stockService.getLowAlerts().then(setAlerts).catch(() => {})
    }, 60000)
    return () => clearInterval(interval)
  }, [])

  // Close dropdown on outside click
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (bellRef.current && !bellRef.current.contains(e.target as Node)) {
        setBellOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const alertCount = alerts.length

  return (
    <header className="topbar">
      <div className="topbar-left">
        <button className="btn btn-light topbar-menu-btn d-lg-none" type="button" onClick={onToggleSidebar}>
          <FiMenu />
        </button>
        <Link to="/" className="topbar-brand-link">
          <AppLogo showText={false} className="topbar-logo-icon" />
          <span className="topbar-brand-name">Inventory Portal</span>
        </Link>
      </div>

      <div className="ms-auto d-flex align-items-center gap-2 topbar-user">
        {/* Dark mode toggle */}
        <button
          className="btn btn-light topbar-icon-btn"
          type="button"
          onClick={toggleTheme}
          title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {theme === 'dark' ? <FiSun /> : <FiMoon />}
        </button>

        {/* Low-stock bell */}
        <div className="topbar-bell-wrap" ref={bellRef}>
          <button
            className="btn btn-light topbar-icon-btn position-relative"
            type="button"
            onClick={() => setBellOpen((prev) => !prev)}
            title="Stock alerts"
          >
            <FiBell />
            {alertCount > 0 && (
              <span className="topbar-bell-badge">{alertCount > 99 ? '99+' : alertCount}</span>
            )}
          </button>

          {bellOpen && (
            <div className="topbar-bell-dropdown">
              <div className="topbar-bell-header">
                <span className="fw-semibold">Stock Alerts</span>
                <Link to="/stock" className="text-primary small" onClick={() => setBellOpen(false)}>
                  View all
                </Link>
              </div>
              {alerts.length === 0 ? (
                <div className="topbar-bell-empty">No active stock alerts</div>
              ) : (
                <ul className="topbar-bell-list">
                  {alerts.slice(0, 8).map((a) => (
                    <li key={a.id} className={`topbar-bell-item ${a.alertType === 'out_of_stock' ? 'is-critical' : ''}`}>
                      <span className="topbar-bell-sku">{a.productSku}</span>
                      <span className="topbar-bell-msg">{a.productName}</span>
                      <span className={`badge ${a.alertType === 'out_of_stock' ? 'bg-danger' : 'bg-warning text-dark'} ms-auto`}>
                        {a.alertType === 'out_of_stock' ? 'Out' : `${a.quantityAvailable} left`}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* User info */}
        <div className="small text-end topbar-user-meta">
          <div className="fw-semibold">{user?.fullName || user?.email}</div>
          <div className="text-muted d-flex align-items-center justify-content-end gap-1">
            <FiShield />
            <span>{user?.role ? roleLabel(user.role) : ''}</span>
          </div>
        </div>

        <button className="btn btn-outline-danger btn-sm topbar-logout-btn" type="button" onClick={logout}>
          <FiLogOut className="me-2" />
          Logout
        </button>
      </div>
    </header>
  )
}

