import type { ReactNode } from 'react'

export interface TableColumn<T> {
  header: string
  render: (row: T) => ReactNode
  className?: string
}

interface Props<T> {
  columns: TableColumn<T>[]
  rows: T[]
  emptyMessage?: string
}

export function DataTable<T>({ columns, rows, emptyMessage = 'No records found.' }: Props<T>) {
  if (!rows.length) {
    return <div className="alert alert-light border data-table-empty mb-0">{emptyMessage}</div>
  }

  return (
    <div className="table-responsive data-table-wrap">
      <table className="table table-hover align-middle mb-0">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.header} className={column.className}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column.header} className={column.className}>{column.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
