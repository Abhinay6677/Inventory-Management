import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { FiDownload, FiEye, FiPlus, FiRefreshCw } from 'react-icons/fi'
import toast from 'react-hot-toast'
import { motion } from 'framer-motion'
import { purchaseOrderService } from '../../services/purchaseOrderService'
import { supplierService } from '../../services/supplierService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { FilterPanel } from '../../components/common/FilterPanel'
import { SearchBar } from '../../components/common/SearchBar'
import { DataTable } from '../../components/common/DataTable'
import { Pagination } from '../../components/common/Pagination'
import { formatCurrency, formatDate, statusBadgeClass } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type { POStatus, PurchaseOrderResponse, SupplierResponse } from '../../api/contracts'
import { ORDER_CREATE_ROLES, SUPPLIER_ACCESS_ROLES } from '../../utils/roles'

function exportOrdersCsv(rows: PurchaseOrderResponse[]) {
  const header = ['PO Number', 'Supplier', 'Status', 'Total', 'Order Date', 'Expected Delivery', 'Received Date']
  const body = rows.map((o) => [o.poNumber, o.supplierName ?? '', o.status, o.totalAmount, o.orderDate ?? '', o.expectedDelivery ?? '', o.receivedDate ?? ''])
  const csv = [header, ...body].map((r) => r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `purchase-orders-${new Date().toISOString().slice(0, 10)}.csv`; a.click()
  URL.revokeObjectURL(url)
}

const pageSize = 8

export function PurchaseOrdersPage() {
  const { hasRole } = useAuth()
  const canCreatePurchaseOrder = hasRole(ORDER_CREATE_ROLES)
  const canViewSuppliers = hasRole(SUPPLIER_ACCESS_ROLES)
  const [orders, setOrders] = useState<PurchaseOrderResponse[]>([])
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<POStatus | ''>('')
  const [supplierId, setSupplierId] = useState('')
  const [page, setPage] = useState(1)

  async function load() {
    setLoading(true)
    try {
      const [orderData, supplierData] = await Promise.all([
        purchaseOrderService.getOrders({
          status: status || undefined,
          supplierId: canViewSuppliers && supplierId ? Number(supplierId) : undefined,
        }),
        canViewSuppliers ? supplierService.getSuppliers() : Promise.resolve([]),
      ])
      setOrders(orderData)
      setSuppliers(supplierData)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load()   }, [canViewSuppliers, status, supplierId])

  const filtered = useMemo(() => {
    const search = query.trim().toLowerCase()
    return orders.filter((order) => !search || [order.poNumber, order.supplierName ?? '', order.status].join(' ').toLowerCase().includes(search))
  }, [orders, query])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const currentRows = filtered.slice((page - 1) * pageSize, page * pageSize)

  useEffect(() => { setPage(1) }, [query, status, supplierId])

  if (loading) return <LoadingSpinner label="Loading purchase orders..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title="Purchase Orders"
        actions={
          <div className="inline-actions">
            {canCreatePurchaseOrder && <Link className="btn btn-primary action-btn" to="/orders/new"><FiPlus className="me-2" />Create Purchase Order</Link>}
            <button className="btn btn-outline-secondary action-btn" type="button" onClick={() => exportOrdersCsv(filtered)}>
              <FiDownload className="me-1" /> Export CSV
            </button>
          </div>
        }
      />

      <FilterPanel>
        <div className="row g-3 align-items-end">
          <div className="col-lg-5">
            <SearchBar value={query} onChange={setQuery} placeholder="Search PO number or supplier..." />
          </div>
          <div className="col-lg-3">
            <label className="form-label">Status</label>
            <select className="form-select" value={status} onChange={(event) => setStatus(event.target.value as POStatus | '')}>
              <option value="">All statuses</option>
              <option value="draft">Draft</option>
              <option value="submitted">Submitted</option>
              <option value="acknowledged">Acknowledged</option>
              <option value="received">Received</option>
              <option value="cancelled">Cancelled</option>
            </select>
          </div>
          {canViewSuppliers && (
            <div className="col-lg-2">
              <label className="form-label">Supplier</label>
              <select className="form-select" value={supplierId} onChange={(event) => setSupplierId(event.target.value)}>
                <option value="">All suppliers</option>
                {suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
              </select>
            </div>
          )}
          <div className="col-lg-2 text-lg-end">
            <button className="btn btn-outline-secondary w-100" type="button" onClick={load}>
              <FiRefreshCw className="me-2" />
              Refresh
            </button>
          </div>
        </div>
      </FilterPanel>

      <div className="page-card p-3">
        <DataTable
          rows={currentRows}
          columns={[
            { header: 'PO Number', render: (row) => row.poNumber },
            { header: 'Supplier', render: (row) => row.supplierName },
            { header: 'Status', render: (row) => <span className={`badge ${statusBadgeClass(row.status)}`}>{row.status}</span> },
            { header: 'Total', render: (row) => formatCurrency(row.totalAmount) },
            { header: 'Created', render: (row) => formatDate(row.createdAt) },
            {
              header: 'Actions',
              render: (row) => (
                <div className="inline-actions">
                  <Link className="btn btn-sm btn-outline-primary action-btn" to={`/orders/${row.id}`}>
                    <FiEye className="me-1" />
                    View
                  </Link>
                </div>
              ),
            },
          ]}
          emptyMessage="No purchase orders matched your filters."
        />
        <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} />
      </div>

      {filtered.length === 0 && (
        <div className="mt-4">
          <EmptyState title="No purchase orders found" description="Create a live PO to get started." />
        </div>
      )}
    </motion.div>
  )
}
