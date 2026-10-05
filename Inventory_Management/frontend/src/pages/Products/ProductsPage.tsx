import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { FiDownload, FiEye, FiPlus, FiRefreshCw, FiTruck } from 'react-icons/fi'
import toast from 'react-hot-toast'
import { motion } from 'framer-motion'
import { productService } from '../../services/productService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { SearchBar } from '../../components/common/SearchBar'
import { FilterPanel } from '../../components/common/FilterPanel'
import { DataTable } from '../../components/common/DataTable'
import { Pagination } from '../../components/common/Pagination'
import { categoryLabel, formatCurrency } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type { Category, ProductResponse } from '../../api/contracts'
import { PRODUCT_CREATE_ROLES, STOCK_UPDATE_ROLES } from '../../utils/roles'

function exportProductsCsv(rows: ProductResponse[]) {
  const header = ['SKU', 'Name', 'Category', 'Available', 'On Hand', 'Reorder Point', 'Reorder Qty', 'Cost Price', 'Unit Price', 'Supplier']
  const body = rows.map((p) => [
    p.sku, p.name, p.category,
    p.stockLevel?.quantityAvailable ?? 0,
    p.stockLevel?.quantityOnHand ?? 0,
    p.reorderPoint, p.reorderQuantity,
    p.costPrice, p.unitPrice,
    p.supplierName ?? '',
  ])
  const csv = [header, ...body].map((r) => r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `products-${new Date().toISOString().slice(0, 10)}.csv`; a.click()
  URL.revokeObjectURL(url)
}

const pageSize = 8

export function ProductsPage() {
  const { hasRole } = useAuth()
  const canCreateProduct = hasRole(PRODUCT_CREATE_ROLES)
  const canUpdateStock = hasRole(STOCK_UPDATE_ROLES)
  const [products, setProducts] = useState<ProductResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<Category | ''>('')
  const [lowStock, setLowStock] = useState(false)
  const [page, setPage] = useState(1)

  async function load() {
    setLoading(true)
    try {
      const data = await productService.getProducts({
        category: category || undefined,
        lowStock: lowStock || undefined,
      })
      setProducts(data)
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [category, lowStock])

  const filtered = useMemo(() => {
    const search = query.trim().toLowerCase()
    return products.filter((product) => {
      if (!search) return true
      return [product.sku, product.name, product.category, product.supplierName ?? '']
        .join(' ')
        .toLowerCase()
        .includes(search)
    })
  }, [products, query])

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const currentRows = filtered.slice((page - 1) * pageSize, page * pageSize)

  useEffect(() => {
    setPage(1)
  }, [query, category, lowStock])

  if (loading) return <LoadingSpinner label="Loading products..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title="Products"
        actions={
          <div className="inline-actions">
            {canCreateProduct && (
              <Link to="/products/new" className="btn btn-primary action-btn">
                <FiPlus className="me-2" />
                Create Product
              </Link>
            )}
            <button className="btn btn-outline-secondary action-btn" type="button" onClick={() => exportProductsCsv(filtered)}>
              <FiDownload className="me-1" /> Export CSV
            </button>
          </div>
        }
      />

      <FilterPanel>
        <div className="row g-3 align-items-end">
          <div className="col-lg-5">
            <SearchBar value={query} onChange={setQuery} placeholder="Search SKU, name, supplier..." />
          </div>
          <div className="col-lg-3">
            <label className="form-label">Category</label>
            <select className="form-select" value={category} onChange={(event) => setCategory(event.target.value as Category | '')}>
              <option value="">All categories</option>
              <option value="grocery">Grocery</option>
              <option value="electronics">Electronics</option>
              <option value="clothing">Clothing</option>
              <option value="household">Household</option>
              <option value="personal_care">Personal Care</option>
            </select>
          </div>
          <div className="col-lg-2">
            <div className="form-check mt-4">
              <input className="form-check-input" id="lowStock" type="checkbox" checked={lowStock} onChange={(event) => setLowStock(event.target.checked)} />
              <label className="form-check-label" htmlFor="lowStock">Low stock only</label>
            </div>
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
            { header: 'SKU', render: (row) => row.sku },
            { header: 'Name', render: (row) => row.name },
            { header: 'Category', render: (row) => categoryLabel(row.category) },
            { header: 'Available', render: (row) => row.stockLevel?.quantityAvailable ?? 0 },
            { header: 'Reorder', render: (row) => row.reorderPoint },
            { header: 'Cost Value', render: (row) => formatCurrency((row.stockLevel?.quantityOnHand ?? 0) * row.costPrice) },
            {
              header: 'Actions',
              render: (row) => (
                <div className="inline-actions">
                  <Link className="btn btn-sm btn-outline-primary action-btn" to={`/products/${row.id}`}>
                    <FiEye className="me-1" />
                    View
                  </Link>
                  {canUpdateStock && (
                    <Link className="btn btn-sm btn-outline-success action-btn" to={`/products/${row.id}#stock`}>
                      <FiTruck className="me-1" />
                      Update Stock
                    </Link>
                  )}
                </div>
              ),
            },
          ]}
          emptyMessage="No products matched your filters."
        />

        <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} />
      </div>

      {filtered.length === 0 && (
        <div className="mt-4">
          <EmptyState title="No products found" description="Create a product or change the filters to see live API data." />
        </div>
      )}
    </motion.div>
  )
}
