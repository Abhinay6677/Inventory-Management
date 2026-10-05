import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiActivity, FiPlusCircle } from 'react-icons/fi'
import { stockService } from '../../services/stockService'
import { productService } from '../../services/productService'
import { approvalService } from '../../services/approvalService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { DataTable } from '../../components/common/DataTable'
import { FilterPanel } from '../../components/common/FilterPanel'
import { ModalDialog } from '../../components/common/ModalDialog'
import { formatDate, movementBadgeClass, statusBadgeClass } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import { STOCK_UPDATE_ROLES } from '../../utils/roles'
import type { ProductResponse, StockAlertResponse, StockUpdateRequest } from '../../api/contracts'

export function StockPage() {
  const { hasRole } = useAuth()
  const canUpdateStock = hasRole(STOCK_UPDATE_ROLES)
  const [alerts, setAlerts] = useState<StockAlertResponse[]>([])
  const [products, setProducts] = useState<ProductResponse[]>([])
  const [selectedProductId, setSelectedProductId] = useState<number | ''>('')
  const [selectedProduct, setSelectedProduct] = useState<ProductResponse | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [showStockModal, setShowStockModal] = useState(false)
  const [quickProdId, setQuickProdId] = useState<number | ''>('')
  const [stockForm, setStockForm] = useState<StockUpdateRequest>({
    movementType: 'receipt',
    quantity: 0,
    referenceNumber: '',
    notes: '',
    recordedBy: 'frontend-user',
  })

  async function loadAll() {
    setLoading(true)
    try {
      const [alertData, productData] = await Promise.all([
        stockService.getLowAlerts(),
        productService.getProducts(),
      ])
      setAlerts(alertData)
      setProducts(productData)
      setSelectedProductId(productData[0]?.id ?? '')
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAll() }, [])

  useEffect(() => {
    if (typeof selectedProductId !== 'number') {
      setSelectedProduct(null)
      return
    }
    const productId = selectedProductId
    let mounted = true
    async function loadProductHistory() {
      setHistoryLoading(true)
      try {
        const product = await productService.getProductById(productId)
        if (mounted) setSelectedProduct(product)
      } catch (error) {
        if (mounted) toast.error(getErrorMessage(error))
      } finally {
        if (mounted) setHistoryLoading(false)
      }
    }
    loadProductHistory()
    return () => { mounted = false }
  }, [selectedProductId])

  const movements = selectedProduct?.recentMovements ?? []

  useEffect(() => {
    if (!selectedProductId && products.length) setSelectedProductId(products[0].id)
  }, [products, selectedProductId])

  async function handleStockUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (typeof quickProdId !== 'number') {
      toast.error('Select a product first')
      return
    }
    try {
      const response = await approvalService.requestStockMovement(quickProdId, stockForm)
      toast.success(`${response.message ?? 'Approval request submitted'} (#${response.id})`)
      setShowStockModal(false)
      setStockForm({ movementType: 'receipt', quantity: 0, referenceNumber: '', notes: '', recordedBy: 'frontend-user' })
    } catch (error) {
      toast.error(getErrorMessage(error))
    }
  }

  if (loading) return <LoadingSpinner label="Loading stock information..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title="Stock"
        actions={
          <div className="inline-actions">
            {canUpdateStock && (
              <button className="btn btn-primary action-btn" type="button" onClick={() => setShowStockModal(true)}>
                <FiPlusCircle className="me-1" /> Quick Stock Update
              </button>
            )}
            <Link className="btn btn-outline-secondary action-btn" to="/stock/audit">
              <FiActivity className="me-1" /> Audit Log
            </Link>
          </div>
        }
      />

      <div className="row g-4">
        <div className="col-12">
          <div className="page-card p-3">
            <h2 className="h5 mb-3">Low Stock Alerts</h2>
            <DataTable
              rows={alerts}
              columns={[
                { header: 'SKU', render: (row) => row.productSku },
                { header: 'Product', render: (row) => row.productName },
                { header: 'Type', render: (row) => <span className={`badge ${row.alertType === 'out_of_stock' ? 'bg-danger' : statusBadgeClass('submitted')}`}>{row.alertType}</span> },
                { header: 'Available', render: (row) => row.quantityAvailable },
                { header: 'Triggered', render: (row) => formatDate(row.triggeredAt) },
              ]}
              emptyMessage="No stock alerts are currently active."
            />
          </div>
        </div>

        <div className="col-12">
          <div className="page-card p-3">
            <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-2 mb-3">
              <h2 className="h5 mb-0">Stock Movement History</h2>
              {selectedProduct ? (
                <small className="text-muted">
                  {selectedProduct.sku} • {selectedProduct.name} • {movements.length} movement{movements.length === 1 ? '' : 's'}
                </small>
              ) : null}
            </div>
            <FilterPanel>
              <label className="form-label">Select Product</label>
              <select className="form-select" value={selectedProductId} onChange={(event) => setSelectedProductId(event.target.value ? Number(event.target.value) : '')}>
                <option value="">Choose product</option>
                {products.map((product) => <option key={product.id} value={product.id}>{product.sku} - {product.name}</option>)}
              </select>
            </FilterPanel>
            {historyLoading ? (
              <LoadingSpinner label="Loading stock movements..." />
            ) : selectedProduct ? (
              <DataTable
                rows={movements}
                columns={[
                  { header: 'Type', render: (row) => <span className={`badge ${movementBadgeClass(row.movementType)}`}>{row.movementType}</span> },
                  { header: 'Qty', render: (row) => <span className={row.quantity < 0 ? 'text-danger fw-bold' : 'text-success fw-bold'}>{row.quantity > 0 ? `+${row.quantity}` : row.quantity}</span> },
                  { header: 'Reference', render: (row) => row.referenceNumber ?? '-' },
                  { header: 'Time', render: (row) => formatDate(row.recordedAt) },
                ]}
                emptyMessage="No recent movements are available for this product."
              />
            ) : (
              <EmptyState title="Choose a product" description="Pick a product to view its live backend movement history." icon={<FiActivity />} />
            )}
          </div>
        </div>
      </div>

      {/* Quick Stock Update Modal */}
      <ModalDialog
        title="Quick Stock Update"
        show={showStockModal}
        onClose={() => setShowStockModal(false)}
        footer={null}
      >
        <form onSubmit={handleStockUpdate}>
          <div className="row g-3">
            <div className="col-12">
              <label className="form-label">Product</label>
              <select className="form-select" value={quickProdId} onChange={(e) => setQuickProdId(e.target.value ? Number(e.target.value) : '')} required>
                <option value="">Select product…</option>
                {products.map((p) => <option key={p.id} value={p.id}>{p.sku} – {p.name}</option>)}
              </select>
            </div>
            <div className="col-md-6">
              <label className="form-label">Movement Type</label>
              <select className="form-select" value={stockForm.movementType} onChange={(e) => setStockForm({ ...stockForm, movementType: e.target.value as StockUpdateRequest['movementType'] })}>
                <option value="receipt">Receipt</option>
                <option value="sale">Sale</option>
                <option value="adjustment">Adjustment</option>
                <option value="transfer">Transfer</option>
                <option value="returnm">Return</option>
              </select>
            </div>
            <div className="col-md-6">
              <label className="form-label">Quantity</label>
              <input className="form-control" type="number" value={stockForm.quantity} onChange={(e) => setStockForm({ ...stockForm, quantity: Number(e.target.value) })} required />
            </div>
            <div className="col-md-6">
              <label className="form-label">Reference Number</label>
              <input className="form-control" value={stockForm.referenceNumber} onChange={(e) => setStockForm({ ...stockForm, referenceNumber: e.target.value })} />
            </div>
            <div className="col-md-6">
              <label className="form-label">Notes</label>
              <input className="form-control" value={stockForm.notes} onChange={(e) => setStockForm({ ...stockForm, notes: e.target.value })} />
            </div>
          </div>
          <div className="mt-4 d-flex gap-2">
            <button className="btn btn-primary" type="submit">Update Stock</button>
            <button className="btn btn-outline-secondary" type="button" onClick={() => setShowStockModal(false)}>Cancel</button>
          </div>
        </form>
      </ModalDialog>
    </motion.div>
  )
}
