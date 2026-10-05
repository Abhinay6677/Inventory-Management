import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiActivity, FiArrowLeft, FiDownload } from 'react-icons/fi'
import { stockService } from '../../services/stockService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { PageHeader } from '../../components/common/PageHeader'
import { DataTable } from '../../components/common/DataTable'
import { formatDate, movementBadgeClass } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import type { AuditLogEntry } from '../../api/contracts'

function exportAuditCsv(entries: AuditLogEntry[]) {
  const header = ['ID', 'Product SKU', 'Product Name', 'Movement Type', 'Quantity', 'Reference', 'Notes', 'Recorded By', 'Recorded At']
  const rows = entries.map((e) => [
    e.id,
    e.productSku ?? '',
    e.productName ?? '',
    e.movementType,
    e.quantity,
    e.referenceNumber ?? '',
    e.notes ?? '',
    e.recordedBy ?? '',
    e.recordedAt ?? '',
  ])
  const csv = [header, ...rows].map((r) => r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

export function AuditLogPage() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('')

  useEffect(() => {
    stockService.getAuditLog()
      .then(setEntries)
      .catch((err) => toast.error(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  const filtered = filter
    ? entries.filter((e) =>
        (e.productSku ?? '').toLowerCase().includes(filter.toLowerCase()) ||
        (e.productName ?? '').toLowerCase().includes(filter.toLowerCase()) ||
        e.movementType.toLowerCase().includes(filter.toLowerCase()) ||
        (e.recordedBy ?? '').toLowerCase().includes(filter.toLowerCase()),
      )
    : entries

  if (loading) return <LoadingSpinner label="Loading audit log..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title="Stock Audit Log"
        description={`${entries.length} total movements, newest first`}
        actions={
          <div className="inline-actions">
            <Link className="btn btn-outline-secondary action-btn" to="/stock">
              <FiArrowLeft className="me-1" /> Back to Stock
            </Link>
            <button className="btn btn-outline-primary action-btn" type="button" onClick={() => exportAuditCsv(filtered)}>
              <FiDownload className="me-1" /> Export CSV
            </button>
          </div>
        }
      />

      <div className="page-card p-3">
        <div className="mb-3">
          <input
            className="form-control"
            style={{ maxWidth: 340 }}
            placeholder="Filter by SKU, product, type, recorded by…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <DataTable
          rows={filtered}
          columns={[
            {
              header: 'Time',
              render: (row) => (
                <span className="text-muted small" style={{ whiteSpace: 'nowrap' }}>
                  {formatDate(row.recordedAt)}
                </span>
              ),
            },
            { header: 'SKU', render: (row) => <span className="fw-semibold">{row.productSku ?? '—'}</span> },
            { header: 'Product', render: (row) => row.productName ?? '—' },
            {
              header: 'Type',
              render: (row) => (
                <span className={`badge ${movementBadgeClass(row.movementType)}`}>{row.movementType}</span>
              ),
            },
            {
              header: 'Qty',
              render: (row) => (
                <span className={`fw-bold ${row.quantity < 0 ? 'text-danger' : 'text-success'}`}>
                  {row.quantity > 0 ? `+${row.quantity}` : row.quantity}
                </span>
              ),
            },
            { header: 'Reference', render: (row) => row.referenceNumber ?? '—' },
            { header: 'Notes', render: (row) => <span className="text-muted small">{row.notes ?? '—'}</span> },
            { header: 'Recorded By', render: (row) => row.recordedBy ?? '—' },
          ]}
          emptyMessage="No stock movements found."
        />
      </div>

      {entries.length === 0 && !loading && (
        <div className="text-center py-5 text-muted">
          <FiActivity size={40} className="mb-3 opacity-50" />
          <p>No stock movements have been recorded yet.</p>
        </div>
      )}
    </motion.div>
  )
}
