import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiBox, FiEdit2, FiTrendingUp, FiTrash2 } from 'react-icons/fi'
import { supplierService } from '../../services/supplierService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { DataTable } from '../../components/common/DataTable'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { formatCurrency } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type { SupplierCatalogResponse, SupplierPerformanceResponse, SupplierResponse } from '../../api/contracts'
import { SUPPLIER_MANAGEMENT_ROLES } from '../../utils/roles'

export function SupplierDetailPage() {
  const { hasRole } = useAuth()
  const canManageSupplier = hasRole(SUPPLIER_MANAGEMENT_ROLES)
  const navigate = useNavigate()
  const { id } = useParams()
  const supplierId = Number(id)
  const [supplier, setSupplier] = useState<SupplierResponse | null>(null)
  const [catalog, setCatalog] = useState<SupplierCatalogResponse | null>(null)
  const [performance, setPerformance] = useState<SupplierPerformanceResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [tab, setTab] = useState<'catalog' | 'performance'>('catalog')

  async function load() {
    setLoading(true)
    try {
      const [supplierData, catalogData, perfData] = await Promise.all([
        supplierService.getSupplierById(supplierId),
        supplierService.getSupplierCatalog(supplierId),
        supplierService.getSupplierPerformance(supplierId),
      ])
      setSupplier(supplierData)
      setCatalog(catalogData)
      setPerformance(perfData)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!Number.isNaN(supplierId)) load()
  }, [supplierId])

  async function handleDeleteSupplier() {
    try {
      await supplierService.deleteSupplier(supplierId)
      toast.success('Supplier deleted')
      navigate('/suppliers')
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setShowDeleteConfirm(false)
    }
  }

  if (loading) return <LoadingSpinner label="Loading supplier details..." />
  if (!supplier || !catalog) return <EmptyState title="Supplier not found" description="The supplier could not be loaded from the backend." icon={<FiBox />} />

  const onTimePct = performance?.onTimePercent ?? 0
  const onTimeColor = onTimePct >= 80 ? 'text-success' : onTimePct >= 50 ? 'text-warning' : 'text-danger'

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title={supplier.name}
        description={`${supplier.supplierCode} • ${supplier.contactEmail ?? 'No email'}`}
        actions={
          canManageSupplier ? (
            <div className="inline-actions">
              <Link className="btn btn-outline-secondary action-btn" to={`/suppliers/${supplier.id}/edit`}>
                <FiEdit2 className="me-2" />
                Edit
              </Link>
              <button className="btn btn-outline-danger action-btn" type="button" onClick={() => setShowDeleteConfirm(true)}>
                <FiTrash2 className="me-2" />
                Delete
              </button>
            </div>
          ) : null
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-lg-4">
          <div className="page-card p-3">
            <h2 className="h5">Supplier Profile</h2>
            <dl className="row mb-0">
              <dt className="col-6">Payment Terms</dt><dd className="col-6">{supplier.paymentTermsDays} days</dd>
              <dt className="col-6">Lead Time</dt><dd className="col-6">{supplier.leadTimeDays} days</dd>
              <dt className="col-6">Status</dt><dd className="col-6">{supplier.isActive ? 'Active' : 'Inactive'}</dd>
            </dl>
          </div>
        </div>

        {performance && (
          <div className="col-lg-8">
            <div className="page-card p-3">
              <h2 className="h5 mb-3">
                <FiTrendingUp className="me-2 text-primary" />
                Performance Summary
              </h2>
              <div className="row g-3">
                <div className="col-6 col-md-3 text-center">
                  <div className="fw-bold fs-4">{performance.totalOrders}</div>
                  <div className="text-muted small">Total Orders</div>
                </div>
                <div className="col-6 col-md-3 text-center">
                  <div className="fw-bold fs-4">{performance.receivedOrders}</div>
                  <div className="text-muted small">Received</div>
                </div>
                <div className="col-6 col-md-3 text-center">
                  <div className={`fw-bold fs-4 ${onTimeColor}`}>{onTimePct}%</div>
                  <div className="text-muted small">On-Time Rate</div>
                </div>
                <div className="col-6 col-md-3 text-center">
                  <div className="fw-bold fs-4">{formatCurrency(performance.totalSpend)}</div>
                  <div className="text-muted small">Total Spend</div>
                </div>
              </div>
              <div className="mt-3 d-flex gap-4 pt-3 border-top">
                <div><span className="text-muted small">Promised Lead Time: </span><strong>{performance.promisedLeadTimeDays} days</strong></div>
                <div><span className="text-muted small">Avg Actual Lead Time: </span><strong>{performance.averageActualLeadDays} days</strong></div>
                <div><span className="text-muted small">On-Time Orders: </span><strong>{performance.onTimeOrders} / {performance.receivedOrders}</strong></div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Tabs */}
      <ul className="nav nav-tabs mb-3">
        <li className="nav-item">
          <button className={`nav-link ${tab === 'catalog' ? 'active' : ''}`} type="button" onClick={() => setTab('catalog')}>
            <FiBox className="me-2" />Product Catalog
          </button>
        </li>
        <li className="nav-item">
          <button className={`nav-link ${tab === 'performance' ? 'active' : ''}`} type="button" onClick={() => setTab('performance')}>
            <FiTrendingUp className="me-2" />Order History
          </button>
        </li>
      </ul>

      {tab === 'catalog' && (
        <div className="page-card p-3">
          <DataTable
            rows={catalog.products}
            columns={[
              { header: 'SKU', render: (row) => row.sku },
              { header: 'Product', render: (row) => row.name },
              { header: 'Available', render: (row) => row.quantityAvailable },
              { header: 'Cost', render: (row) => formatCurrency(row.costPrice) },
              { header: 'Price', render: (row) => formatCurrency(row.unitPrice) },
            ]}
            emptyMessage="No catalog items found for this supplier."
          />
        </div>
      )}

      {tab === 'performance' && performance && (
        <div className="page-card p-3">
          <h2 className="h5 mb-3">Detailed Performance</h2>
          <div className="row g-3">
            <div className="col-md-6">
              <table className="table table-sm">
                <tbody>
                  <tr><td className="text-muted">Total Orders</td><td className="fw-semibold">{performance.totalOrders}</td></tr>
                  <tr><td className="text-muted">Orders Received</td><td className="fw-semibold">{performance.receivedOrders}</td></tr>
                  <tr><td className="text-muted">On-Time Deliveries</td><td className="fw-semibold">{performance.onTimeOrders}</td></tr>
                  <tr><td className="text-muted">On-Time Rate</td><td className={`fw-bold ${onTimeColor}`}>{performance.onTimePercent}%</td></tr>
                </tbody>
              </table>
            </div>
            <div className="col-md-6">
              <table className="table table-sm">
                <tbody>
                  <tr><td className="text-muted">Promised Lead Time</td><td className="fw-semibold">{performance.promisedLeadTimeDays} days</td></tr>
                  <tr><td className="text-muted">Avg Actual Lead Time</td><td className="fw-semibold">{performance.averageActualLeadDays} days</td></tr>
                  <tr><td className="text-muted">Total Spend</td><td className="fw-semibold">{formatCurrency(performance.totalSpend)}</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        title="Delete supplier"
        message={`Delete ${supplier.name}? Products linked to this supplier will lose the supplier reference.`}
        show={showDeleteConfirm}
        onCancel={() => setShowDeleteConfirm(false)}
        onConfirm={handleDeleteSupplier}
        confirmText="Delete"
      />
    </motion.div>
  )
}
