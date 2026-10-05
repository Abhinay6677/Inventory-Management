import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiPlus, FiTrash2 } from 'react-icons/fi'
import { PageHeader } from '../../components/common/PageHeader'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { supplierService } from '../../services/supplierService'
import { productService } from '../../services/productService'
import { purchaseOrderService } from '../../services/purchaseOrderService'
import { getErrorMessage } from '../../utils/errors'
import { formatCurrency } from '../../utils/format'
import type { ProductResponse, SupplierResponse } from '../../api/contracts'

interface ItemRow {
  productId: string
  quantityOrdered: string
  unitCost: string
}

export function PurchaseOrderFormPage() {
  const navigate = useNavigate()
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([])
  const [products, setProducts] = useState<ProductResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [supplierId, setSupplierId] = useState('')
  const [expectedDelivery, setExpectedDelivery] = useState('')
  const [items, setItems] = useState<ItemRow[]>([{ productId: '', quantityOrdered: '1', unitCost: '0' }])

  useEffect(() => {
    async function load() {
      try {
        const [supplierData, productData] = await Promise.all([
          supplierService.getSuppliers(),
          productService.getProducts(),
        ])
        setSuppliers(supplierData)
        setProducts(productData)
      } catch (error) {
        toast.error(getErrorMessage(error))
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const total = useMemo(() => items.reduce((sum, item) => sum + Number(item.quantityOrdered || 0) * Number(item.unitCost || 0), 0), [items])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      const response = await purchaseOrderService.createOrder({
        supplierId: Number(supplierId),
        expectedDelivery: expectedDelivery || null,
        items: items.map((item) => ({
          productId: Number(item.productId),
          quantityOrdered: Number(item.quantityOrdered),
          unitCost: Number(item.unitCost),
        })),
      })
      toast.success('Purchase order created')
      navigate(`/orders/${response.id}`)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingSpinner label="Loading suppliers and products..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader title="Create Purchase Order" />
      <div className="page-card p-4">
        <form onSubmit={handleSubmit}>
          <div className="row g-3 mb-3">
            <div className="col-md-6">
              <label className="form-label">Supplier</label>
              <select className="form-select" required value={supplierId} onChange={(event) => setSupplierId(event.target.value)}>
                <option value="">Select supplier</option>
                {suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
              </select>
            </div>
            <div className="col-md-6">
              <label className="form-label">Expected Delivery</label>
              <input className="form-control" type="date" value={expectedDelivery} onChange={(event) => setExpectedDelivery(event.target.value)} />
            </div>
          </div>

          <div className="mb-3">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <h2 className="h5 mb-0">Items</h2>
              <button className="btn btn-outline-primary btn-sm" type="button" onClick={() => setItems([...items, { productId: '', quantityOrdered: '1', unitCost: '0' }])}>
                <FiPlus className="me-2" />
                Add Item
              </button>
            </div>
            <div className="table-responsive">
              <table className="table align-middle">
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Qty</th>
                    <th>Unit Cost</th>
                    <th>Line Total</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {items.map((item, index) => {
                    const lineTotal = Number(item.quantityOrdered || 0) * Number(item.unitCost || 0)
                    return (
                      <tr key={index}>
                        <td>
                          <select
                            className="form-select"
                            required
                            value={item.productId}
                            onChange={(event) => {
                              const product = products.find((productItem) => String(productItem.id) === event.target.value)
                              const next = [...items]
                              next[index] = {
                                ...item,
                                productId: event.target.value,
                                unitCost: product ? String(product.costPrice) : item.unitCost,
                              }
                              setItems(next)
                            }}
                          >
                            <option value="">Select product</option>
                            {products.map((product) => <option key={product.id} value={product.id}>{product.sku} - {product.name}</option>)}
                          </select>
                        </td>
                        <td>
                          <input className="form-control" type="number" min="1" value={item.quantityOrdered} onChange={(event) => {
                            const next = [...items]
                            next[index] = { ...item, quantityOrdered: event.target.value }
                            setItems(next)
                          }} />
                        </td>
                        <td>
                          <input className="form-control" type="number" min="0" step="0.01" value={item.unitCost} onChange={(event) => {
                            const next = [...items]
                            next[index] = { ...item, unitCost: event.target.value }
                            setItems(next)
                          }} />
                        </td>
                        <td>{formatCurrency(lineTotal)}</td>
                        <td>
                          <button className="btn btn-outline-danger btn-sm" type="button" onClick={() => setItems(items.filter((_, itemIndex) => itemIndex !== index))} disabled={items.length === 1}>
                            <FiTrash2 />
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="d-flex justify-content-between align-items-center">
            <strong>Total: {formatCurrency(total)}</strong>
            <div className="d-flex gap-2">
              <button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Saving...' : 'Create Purchase Order'}</button>
              <button className="btn btn-outline-secondary" type="button" onClick={() => navigate('/orders')}>Cancel</button>
            </div>
          </div>
        </form>
      </div>
    </motion.div>
  )
}
