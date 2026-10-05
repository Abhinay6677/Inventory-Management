import { Outlet, useLocation } from 'react-router-dom'
import { AppLogo } from '../components/branding/AppLogo'

export function AuthLayout() {
  const location = useLocation()
  const registering = location.pathname === '/register'

  return (
    <div className="auth-shell">
      <div className="auth-frame">
        <div className="auth-card">
          <div className="auth-brand-top">
            <AppLogo tone="light" showText={false} className="auth-standalone-logo" />
          </div>
          <div className="text-center mb-4 auth-heading">
            <h2 className="h4 mb-1">{registering ? 'Create your account' : 'Welcome back'}</h2>
            <p className="text-muted mb-0">{registering ? 'Create access to the inventory workspace' : 'Sign in to continue to your workspace'}</p>
          </div>
          <Outlet />
        </div>
      </div>
    </div>
  )
}
