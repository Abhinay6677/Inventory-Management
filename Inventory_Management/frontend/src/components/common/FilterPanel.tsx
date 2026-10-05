import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
}

export function FilterPanel({ children }: Props) {
  return <div className="filter-panel page-card mb-4"><div className="card-body">{children}</div></div>
}
