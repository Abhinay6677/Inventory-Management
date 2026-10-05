import type { ReactNode } from 'react'

interface Props {
  title: string
  description?: string
  actions?: ReactNode
}

export function PageHeader({ title, description, actions }: Props) {
  return (
    <div className="page-header d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
      <div className="page-header-content">
        <h1 className="h3 mb-1">{title}</h1>
        {description && <p className="text-muted mb-0">{description}</p>}
      </div>
      <div className="page-header-actions">{actions}</div>
    </div>
  )
}
