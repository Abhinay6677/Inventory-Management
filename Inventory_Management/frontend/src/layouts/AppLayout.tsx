import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { AppLogo } from '../components/branding/AppLogo'
import { SidebarNavigation } from '../components/navigation/SidebarNavigation'
import { Topbar } from '../components/navigation/Topbar'

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="app-shell">
      <aside className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
        <div className="sidebar-header">
          <AppLogo tone="light" title="Inventory Portal" subtitle="Operations console" />
        </div>
        <SidebarNavigation />
      </aside>
      {sidebarOpen && <button className="sidebar-backdrop" aria-label="Close sidebar" type="button" onClick={() => setSidebarOpen(false)} />}
      <div className="app-main">
        <Topbar onToggleSidebar={() => setSidebarOpen((value) => !value)} />
        <main className="content-area">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
