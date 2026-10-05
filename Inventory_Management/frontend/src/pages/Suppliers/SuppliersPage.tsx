import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { FiEye, FiPlus, FiRefreshCw } from 'react-icons/fi'
import toast from 'react-hot-toast'
import { motion } from 'framer-motion'
import { supplierService } from '../../services/supplierService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { SearchBar } from '../../components/common/SearchBar'
import { FilterPanel } from '../../components/common/FilterPanel'
import { DataTable } from '../../components/common/DataTable'
import { Pagination } from '../../components/common/Pagination'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type { SupplierResponse } from '../../api/contracts'
import { SUPPLIER_MANAGEMENT_ROLES } from '../../utils/roles'

const pageSize = 8

export function SuppliersPage() {
  const { hasRole } = useAuth()
  const canCreateSupplier = hasRole(SUPPLIER_MANAGEMENT_ROLES)
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)

  async function load() {
    setLoading(true)
    try {
      setSuppliers(await supplierService.getSuppliers())
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const filtered = useMemo(() => {
    const search = query.trim().toLowerCase()
    return suppliers.filter((supplier) => !search || [supplier.name, supplier.supplierCode, supplier.contactEmail ?? ''].join(' ').toLowerCase().includes(search))
  }, [suppliers, query])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const currentRows = filtered.slice((page - 1) * pageSize, page * pageSize)

  useEffect(() => { setPage(1) }, [query])

  if (loading) return <LoadingSpinner label="Loading suppliers..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title="Suppliers"
        actions={canCreateSupplier ? <Link className="btn btn-primary" to="/suppliers/new"><FiPlus className="me-2" />Create Supplier</Link> : null}
      />

      <FilterPanel>
        <div className="row g-3 align-items-end">
          <div className="col-lg-10">
            <SearchBar value={query} onChange={setQuery} placeholder="Search supplier name, code, or email..." />
          </div>
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
            { header: 'Code', render: (row) => row.supplierCode },
            { header: 'Name', render: (row) => row.name },
            { header: 'Email', render: (row) => row.contactEmail ?? '-' },
            { header: 'Lead Time', render: (row) => `${row.leadTimeDays} days` },
            {
              header: 'Actions',
              render: (row) => (
                <div className="inline-actions">
                  <Link className="btn btn-sm btn-outline-primary action-btn" to={`/suppliers/${row.id}`}>
                    <FiEye className="me-1" />
                    View
                  </Link>
                </div>
              ),
            },
          ]}
          emptyMessage="No suppliers matched your filters."
        />
        <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} />
      </div>

      {filtered.length === 0 && (
        <div className="mt-4">
          <EmptyState title="No suppliers found" description="Create a supplier to seed the catalog screens." />
        </div>
      )}
    </motion.div>
  )
}
