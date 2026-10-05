import type { ReactNode } from 'react'

interface Props {
  title: string
  value: string | number
  subtitle?: string
  icon?: ReactNode
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

export function DashboardCard({ title, value, subtitle, icon, tone = 'primary' }: Props) {
  return (
    <div className="card dashboard-card border-0 h-100">
      <div className="card-body">
        <div className="d-flex justify-content-between align-items-start">
          <div>
            <p className="text-muted mb-1 dashboard-title">{title}</p>
            <h3 className={`mb-1 dashboard-value tone-${tone}`}>{value}</h3>
            {subtitle && <div className="small text-muted">{subtitle}</div>}
          </div>
          <div className={`dashboard-icon tone-${tone}`}>{icon}</div>
        </div>
      </div>
    </div>
  )
}
