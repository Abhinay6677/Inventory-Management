import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiActivity, FiEdit2, FiPackage, FiRefreshCw, FiTrash2 } from 'react-icons/fi'
import { productService } from '../../services/productService'
import { approvalService } from '../../services/approvalService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { DataTable } from '../../components/common/DataTable'
import { ModalDialog } from '../../components/common/ModalDialog'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { formatCurrency, formatDate, movementBadgeClass } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type { ProductResponse, StockUpdateRequest } from '../../api/contracts'
import { PRODUCT_DELETE_ROLES, PRODUCT_EDIT_ROLES, STOCK_UPDATE_ROLES } from '../../utils/roles'

export function ProductDetailPage() {
  const { hasRole } = useAuth()
  const canEditProduct = hasRole(PRODUCT_EDIT_ROLES)
  const canDeleteProduct = hasRole(PRODUCT_DELETE_ROLES)
  const canUpdateStock = hasRole(STOCK_UPDATE_ROLES)
  const analystOnlyEdit = canEditProduct && !canDeleteProduct
  const navigate = useNavigate()
  const { id } = useParams()
  const productId = Number(id)
  const [product, setProduct] = useState<ProductResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [showStockModal, setShowStockModal] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [stockForm, setStockForm] = useState<StockUpdateRequest>({
    movementType: 'adjustment',
    quantity: 0,
    referenceNumber: '',
    notes: '',
    recordedBy: 'frontend-user',
  })

  async function load() {
    setLoading(true)
    try {
      setProduct(await productService.getProductById(productId))
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!Number.isNaN(productId)) load()
  }, [productId])

  const movements = useMemo(() => product?.recentMovements ?? [], [product])

  async function handleStockUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      const response = await approvalService.requestStockMovement(productId, stockForm)
      toast.success(`${response.message ?? 'Approval request submitted'} (#${response.id})`)
      setShowStockModal(false)
    } catch (error) {
      toast.error(getErrorMessage(error))
    }
  }

  async function handleDeleteProduct() {
    try {
      const response = await approvalService.requestProductDelete(productId)
      toast.success(`${response.message ?? 'Approval request submitted'} (#${response.id})`)
      navigate('/approvals')
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setShowDeleteConfirm(false)
    }
  }

  if (loading) return <LoadingSpinner label="Loading product details..." />
  if (!product) return <EmptyState title="Product not found" description="The product could not be loaded from the backend." icon={<FiPackage />} />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title={product.name}
        description={`${product.sku} • ${product.category}`}
        actions={
          <div className="inline-actions">
            {canEditProduct && (
              <>
                <Link className="btn btn-outline-secondary action-btn" to={`/products/${product.id}/edit`}>
                  <FiEdit2 className="me-2" />
                  {analystOnlyEdit ? 'Update Reorder Settings' : 'Edit'}
                </Link>
              </>
            )}
            {canDeleteProduct && (
              <button className="btn btn-outline-danger action-btn" type="button" onClick={() => setShowDeleteConfirm(true)}>
                <FiTrash2 className="me-2" />
                Delete
              </button>
            )}
            <button className="btn btn-outline-primary action-btn" type="button" onClick={load}>
              <FiRefreshCw className="me-2" />
              Refresh
            </button>
          </div>
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-lg-4">
          <div className="page-card p-3">
            <h2 className="h5">Product Details</h2>
            <dl className="row mb-0">
              <dt className="col-5">Unit Price</dt><dd className="col-7">{formatCurrency(product.unitPrice)}</dd>
              <dt className="col-5">Cost Price</dt><dd className="col-7">{formatCurrency(product.costPrice)}</dd>
              <dt className="col-5">Supplier</dt><dd className="col-7">{product.supplierName ?? '-'}</dd>
              <dt className="col-5">Created</dt><dd className="col-7">{formatDate(product.createdAt)}</dd>
            </dl>
          </div>
        </div>
        <div className="col-lg-4">
          <div className="page-card p-3">
            <h2 className="h5">Stock Summary</h2>
            <dl className="row mb-0">
              <dt className="col-7">On Hand</dt><dd className="col-5">{product.stockLevel?.quantityOnHand ?? 0}</dd>
              <dt className="col-7">Reserved</dt><dd className="col-5">{product.stockLevel?.quantityReserved ?? 0}</dd>
              <dt className="col-7">Available</dt><dd className="col-5">{product.stockLevel?.quantityAvailable ?? 0}</dd>
              <dt className="col-7">Reorder Point</dt><dd className="col-5">{product.reorderPoint}</dd>
            </dl>
            {canUpdateStock && (
              <button className="btn btn-primary mt-3" type="button" onClick={() => setShowStockModal(true)}>
                <FiActivity className="me-2" />
                Update Stock
              </button>
            )}
          </div>
        </div>
        <div className="col-lg-4">
          <div className="page-card p-3">
            <h2 className="h5">Status</h2>
            <p className="mb-2">Reorder quantity: {product.reorderQuantity}</p>
            <p className="mb-0">Inventory value: {formatCurrency((product.stockLevel?.quantityOnHand ?? 0) * product.costPrice)}</p>
          </div>
        </div>
      </div>

      <div className="page-card p-3">
        <h2 className="h5 mb-3">Recent Stock Movements</h2>
        <DataTable
          rows={movements}
          columns={[
            { header: 'Type', render: (row) => <span className={`badge ${movementBadgeClass(row.movementType)}`}>{row.movementType}</span> },
            { header: 'Qty', render: (row) => row.quantity },
            { header: 'Reference', render: (row) => row.referenceNumber ?? '-' },
            { header: 'Recorded By', render: (row) => row.recordedBy ?? '-' },
            { header: 'Time', render: (row) => formatDate(row.recordedAt) },
          ]}
          emptyMessage="No recent movements found."
        />
      </div>

      <ModalDialog
        title={`Update stock for ${product.sku}`}
        show={showStockModal}
        onClose={() => setShowStockModal(false)}
        footer={null}
      >
        <form onSubmit={handleStockUpdate}>
          <div className="row g-3">
            <div className="col-md-4">
              <label className="form-label">Movement Type</label>
              <select className="form-select" value={stockForm.movementType} onChange={(event) => setStockForm({ ...stockForm, movementType: event.target.value as StockUpdateRequest['movementType'] })}>
                <option value="receipt">Receipt</option>
                <option value="sale">Sale</option>
                <option value="adjustment">Adjustment</option>
                <option value="transfer">Transfer</option>
                <option value="returnm">Return</option>
              </select>
            </div>
            <div className="col-md-4">
              <label className="form-label">Quantity</label>
              <input className="form-control" type="number" value={stockForm.quantity} onChange={(event) => setStockForm({ ...stockForm, quantity: Number(event.target.value) })} />
            </div>
            <div className="col-md-4">
              <label className="form-label">Recorded By</label>
              <input className="form-control" value={stockForm.recordedBy} onChange={(event) => setStockForm({ ...stockForm, recordedBy: event.target.value })} />
            </div>
            <div className="col-md-6">
              <label className="form-label">Reference Number</label>
              <input className="form-control" value={stockForm.referenceNumber} onChange={(event) => setStockForm({ ...stockForm, referenceNumber: event.target.value })} />
            </div>
            <div className="col-md-6">
              <label className="form-label">Notes</label>
              <input className="form-control" value={stockForm.notes} onChange={(event) => setStockForm({ ...stockForm, notes: event.target.value })} />
            </div>
          </div>
          <div className="mt-4 d-flex gap-2">
            <button className="btn btn-primary" type="submit">Save</button>
            <button className="btn btn-outline-secondary" type="button" onClick={() => setShowStockModal(false)}>Cancel</button>
          </div>
        </form>
      </ModalDialog>

      <ConfirmDialog
        title="Delete product"
        message={`Delete ${product.name}? This action cannot be undone.`}
        show={showDeleteConfirm}
        onCancel={() => setShowDeleteConfirm(false)}
        onConfirm={handleDeleteProduct}
        confirmText="Delete"
      />
    </motion.div>
  )
}
