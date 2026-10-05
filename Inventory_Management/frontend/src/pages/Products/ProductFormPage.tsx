import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { PageHeader } from '../../components/common/PageHeader'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { useAuth } from '../../hooks/useAuth'
import { supplierService } from '../../services/supplierService'
import { productService } from '../../services/productService'
import { approvalService } from '../../services/approvalService'
import { getErrorMessage } from '../../utils/errors'
import type { Category, SupplierResponse } from '../../api/contracts'
import { PRODUCT_CREATE_ROLES } from '../../utils/roles'

export function ProductFormPage() {
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const { id } = useParams()
  const productId = Number(id)
  const isEditMode = !Number.isNaN(productId)
  const canCreateOrFullyManageProduct = hasRole(PRODUCT_CREATE_ROLES)
  const analystReorderMode = isEditMode && !canCreateOrFullyManageProduct
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({
    name: '',
    category: 'grocery' as Category,
    unitPrice: '',
    costPrice: '',
    unitOfMeasure: 'pieces',
    reorderPoint: '10',
    reorderQuantity: '50',
    supplierId: '',
    initialStock: '0',
  })

  useEffect(() => {
    async function load() {
      try {
        const [supplierData, productData] = await Promise.all([
          analystReorderMode ? Promise.resolve([]) : supplierService.getSuppliers(),
          isEditMode ? productService.getProductById(productId) : Promise.resolve(null),
        ])

        setSuppliers(supplierData)

        if (productData) {
          setForm({
            name: productData.name,
            category: productData.category,
            unitPrice: String(productData.unitPrice),
            costPrice: String(productData.costPrice),
            unitOfMeasure: productData.unitOfMeasure,
            reorderPoint: String(productData.reorderPoint),
            reorderQuantity: String(productData.reorderQuantity),
            supplierId: productData.supplierId ? String(productData.supplierId) : '',
            initialStock: String(productData.stockLevel?.quantityOnHand ?? 0),
          })
        }
      } catch (error) {
        toast.error(getErrorMessage(error))
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [analystReorderMode, isEditMode, productId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      if (analystReorderMode) {
        await productService.updateReorderSettings(productId, {
          reorderPoint: Number(form.reorderPoint),
          reorderQuantity: Number(form.reorderQuantity),
        })
        toast.success('Reorder settings updated')
        navigate(`/products/${productId}`)
        return
      }

      const response = isEditMode
        ? await approvalService.requestProductUpdate(productId, {
            name: form.name,
            category: form.category,
            unitPrice: Number(form.unitPrice),
            costPrice: Number(form.costPrice),
            unitOfMeasure: form.unitOfMeasure,
            reorderPoint: Number(form.reorderPoint),
            reorderQuantity: Number(form.reorderQuantity),
            supplierId: form.supplierId ? Number(form.supplierId) : null,
          })
        : await approvalService.requestProductCreate({
            name: form.name,
            category: form.category,
            unitPrice: Number(form.unitPrice),
            costPrice: Number(form.costPrice),
            unitOfMeasure: form.unitOfMeasure,
            reorderPoint: Number(form.reorderPoint),
            reorderQuantity: Number(form.reorderQuantity),
            supplierId: form.supplierId ? Number(form.supplierId) : null,
            initialStock: Number(form.initialStock),
          })

      toast.success(`${response.message ?? 'Approval request submitted'} (#${response.id})`)
      navigate('/approvals')
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingSpinner label={isEditMode ? 'Loading product...' : 'Loading suppliers...'} />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader title={analystReorderMode ? 'Update Reorder Settings' : isEditMode ? 'Edit Product' : 'Create Product'} />
      <div className="page-card p-4">
        <form onSubmit={handleSubmit}>
          <div className="row g-3">
            {!analystReorderMode ? (
              <>
                <div className="col-md-6">
                  <label className="form-label">Name</label>
                  <input className="form-control" required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                </div>
                <div className="col-md-3">
                  <label className="form-label">Category</label>
                  <select className="form-select" value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value as Category })}>
                    <option value="grocery">Grocery</option>
                    <option value="electronics">Electronics</option>
                    <option value="clothing">Clothing</option>
                    <option value="household">Household</option>
                    <option value="personal_care">Personal Care</option>
                  </select>
                </div>
                <div className="col-md-3">
                  <label className="form-label">Supplier</label>
                  <select className="form-select" value={form.supplierId} onChange={(event) => setForm({ ...form, supplierId: event.target.value })}>
                    <option value="">No supplier</option>
                    {suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
                  </select>
                </div>
                <div className="col-md-3">
                  <label className="form-label">Unit Price</label>
                  <input className="form-control" type="number" step="0.01" min="0" required value={form.unitPrice} onChange={(event) => setForm({ ...form, unitPrice: event.target.value })} />
                </div>
                <div className="col-md-3">
                  <label className="form-label">Cost Price</label>
                  <input className="form-control" type="number" step="0.01" min="0" required value={form.costPrice} onChange={(event) => setForm({ ...form, costPrice: event.target.value })} />
                </div>
                <div className="col-md-3">
                  <label className="form-label">Unit Of Measure</label>
                  <input className="form-control" value={form.unitOfMeasure} onChange={(event) => setForm({ ...form, unitOfMeasure: event.target.value })} />
                </div>
                {!isEditMode && (
                  <div className="col-md-3">
                    <label className="form-label">Initial Stock</label>
                    <input className="form-control" type="number" min="0" value={form.initialStock} onChange={(event) => setForm({ ...form, initialStock: event.target.value })} />
                  </div>
                )}
              </>
            ) : (
              <div className="col-12">
                <div className="alert alert-light border mb-0">
                  Inventory analysts can update reorder thresholds and audit product availability.
                </div>
              </div>
            )}
            <div className="col-md-3">
              <label className="form-label">Reorder Point</label>
              <input className="form-control" type="number" min="0" value={form.reorderPoint} onChange={(event) => setForm({ ...form, reorderPoint: event.target.value })} />
            </div>
            <div className="col-md-3">
              <label className="form-label">Reorder Quantity</label>
              <input className="form-control" type="number" min="0" value={form.reorderQuantity} onChange={(event) => setForm({ ...form, reorderQuantity: event.target.value })} />
            </div>
          </div>
          <div className="d-flex gap-2 mt-4">
            <button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Saving...' : analystReorderMode ? 'Update Reorder Settings' : isEditMode ? 'Update Product' : 'Create Product'}</button>
            <button className="btn btn-outline-secondary" type="button" onClick={() => navigate(isEditMode ? `/products/${productId}` : '/products')}>Cancel</button>
          </div>
        </form>
      </div>
    </motion.div>
  )
}
