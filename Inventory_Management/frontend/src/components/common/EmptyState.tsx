import type { ReactNode } from 'react'

interface Props {
  title: string
  description: string
  icon?: ReactNode
  action?: ReactNode
}

export function EmptyState({ title, description, icon, action }: Props) {
  return (
    <div className="empty-state page-card">
      <div className="card-body text-center py-5">
        <div className="empty-icon mb-3">{icon}</div>
        <h3 className="h5">{title}</h3>
        <p className="text-muted mb-3">{description}</p>
        {action}
      </div>
    </div>
  )
}
