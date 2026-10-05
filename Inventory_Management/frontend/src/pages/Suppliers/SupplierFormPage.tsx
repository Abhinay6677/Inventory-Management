import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { supplierService } from '../../services/supplierService'
import { PageHeader } from '../../components/common/PageHeader'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { getErrorMessage } from '../../utils/errors'

export function SupplierFormPage() {
  const navigate = useNavigate()
  const { id } = useParams()
  const supplierId = Number(id)
  const isEditMode = !Number.isNaN(supplierId)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(isEditMode)
  const [form, setForm] = useState({
    name: '',
    supplierCode: '',
    contactEmail: '',
    paymentTermsDays: '30',
    leadTimeDays: '7',
  })

  useEffect(() => {
    async function loadSupplier() {
      try {
        const supplier = await supplierService.getSupplierById(supplierId)
        setForm({
          name: supplier.name,
          supplierCode: supplier.supplierCode,
          contactEmail: supplier.contactEmail ?? '',
          paymentTermsDays: String(supplier.paymentTermsDays),
          leadTimeDays: String(supplier.leadTimeDays),
        })
      } catch (error) {
        toast.error(getErrorMessage(error))
      } finally {
        setLoading(false)
      }
    }

    if (isEditMode) {
      loadSupplier()
    }
  }, [isEditMode, supplierId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      const payload = {
        name: form.name,
        supplierCode: form.supplierCode,
        contactEmail: form.contactEmail || undefined,
        paymentTermsDays: Number(form.paymentTermsDays),
        leadTimeDays: Number(form.leadTimeDays),
      }

      const response = isEditMode
        ? await supplierService.updateSupplier(supplierId, payload)
        : await supplierService.createSupplier(payload)

      toast.success(isEditMode ? 'Supplier updated' : 'Supplier created')
      navigate(`/suppliers/${response.id}`)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingSpinner label="Loading supplier..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader title={isEditMode ? 'Edit Supplier' : 'Create Supplier'} />
      <div className="page-card p-4">
        <form onSubmit={handleSubmit}>
          <div className="row g-3">
            <div className="col-md-6">
              <label className="form-label">Name</label>
              <input className="form-control" required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </div>
            <div className="col-md-6">
              <label className="form-label">Supplier Code</label>
              <input className="form-control" required value={form.supplierCode} onChange={(event) => setForm({ ...form, supplierCode: event.target.value })} />
            </div>
            <div className="col-md-6">
              <label className="form-label">Contact Email</label>
              <input className="form-control" type="email" value={form.contactEmail} onChange={(event) => setForm({ ...form, contactEmail: event.target.value })} />
            </div>
            <div className="col-md-3">
              <label className="form-label">Payment Terms Days</label>
              <input className="form-control" type="number" min="0" value={form.paymentTermsDays} onChange={(event) => setForm({ ...form, paymentTermsDays: event.target.value })} />
            </div>
            <div className="col-md-3">
              <label className="form-label">Lead Time Days</label>
              <input className="form-control" type="number" min="0" value={form.leadTimeDays} onChange={(event) => setForm({ ...form, leadTimeDays: event.target.value })} />
            </div>
          </div>
          <div className="d-flex gap-2 mt-4">
            <button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Saving...' : isEditMode ? 'Update Supplier' : 'Create Supplier'}</button>
            <button className="btn btn-outline-secondary" type="button" onClick={() => navigate(isEditMode ? `/suppliers/${supplierId}` : '/suppliers')}>Cancel</button>
          </div>
        </form>
      </div>
    </motion.div>
  )
}
