import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell,
  Legend, Pie, PieChart, RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { FiAlertTriangle, FiClipboard, FiPackage, FiPlusCircle, FiShoppingBag, FiTruck } from 'react-icons/fi'
import toast from 'react-hot-toast'
import { dashboardService } from '../../services/dashboardService'
import { productService } from '../../services/productService'
import { stockService } from '../../services/stockService'
import { purchaseOrderService } from '../../services/purchaseOrderService'
import { supplierService } from '../../services/supplierService'
import { DashboardCard } from '../../components/common/DashboardCard'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { DataTable } from '../../components/common/DataTable'
import { formatCurrency, formatDate, statusBadgeClass, categoryLabel } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type {
  Category,
  DashboardResponse,
  ProductResponse,
  PurchaseOrderResponse,
  Role,
  StockAlertResponse,
  SupplierResponse,
} from '../../api/contracts'

const CHART_COLORS = ['#2563eb', '#0ea5e9', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899']

export function DashboardPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'warehouse_staff'
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null)
  const [products, setProducts] = useState<ProductResponse[]>([])
  const [alerts, setAlerts] = useState<StockAlertResponse[]>([])
  const [orders, setOrders] = useState<PurchaseOrderResponse[]>([])
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        if (role === 'store_manager') {
          const [dashboardData, productData, alertData, orderData] = await Promise.all([
            dashboardService.getDashboard(),
            productService.getProducts(),
            stockService.getLowAlerts(),
            purchaseOrderService.getOrders(),
          ])
          setDashboard(dashboardData); setProducts(productData); setAlerts(alertData); setOrders(orderData); setSuppliers([])
        } else if (role === 'inventory_analyst') {
          const [dashboardData, productData, alertData] = await Promise.all([
            dashboardService.getDashboard(),
            productService.getProducts(),
            stockService.getLowAlerts(),
          ])
          setDashboard(dashboardData); setProducts(productData); setAlerts(alertData); setOrders([]); setSuppliers([])
        } else if (role === 'procurement_officer') {
          const [dashboardData, orderData, supplierData] = await Promise.all([
            dashboardService.getDashboard(),
            purchaseOrderService.getOrders(),
            supplierService.getSuppliers(),
          ])
          setDashboard(dashboardData); setProducts([]); setAlerts([]); setOrders(orderData); setSuppliers(supplierData)
        } else {
          const [dashboardData, productData, alertData, orderData] = await Promise.all([
            dashboardService.getDashboard(),
            productService.getProducts(),
            stockService.getLowAlerts(),
            purchaseOrderService.getOrders(),
          ])
          setDashboard(dashboardData); setProducts(productData); setAlerts(alertData); setOrders(orderData); setSuppliers([])
        }
      } catch (error) {
        toast.error(getErrorMessage(error))
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [role])

  // ── Store Manager: stacked bar (onHand + reserved) per category ──────────────
  const smCategoryStacked = useMemo(() => {
    const grouped = new Map<string, { onHand: number; reserved: number; value: number }>()
    products.forEach((p) => {
      const key = categoryLabel(p.category as Category)
      const prev = grouped.get(key) ?? { onHand: 0, reserved: 0, value: 0 }
      grouped.set(key, {
        onHand: prev.onHand + (p.stockLevel?.quantityOnHand ?? 0),
        reserved: prev.reserved + (p.stockLevel?.quantityReserved ?? 0),
        value: prev.value + Math.max(0, p.stockLevel?.quantityOnHand ?? 0) * p.costPrice,
      })
    })
    return Array.from(grouped.entries()).map(([name, v]) => ({ name, ...v }))
  }, [products])

  const smStockTrend = useMemo(() => products.slice(0, 8).map((p) => ({
    name: p.sku.replace('SKU-', ''),
    available: p.stockLevel?.quantityAvailable ?? 0,
    reserved: p.stockLevel?.quantityReserved ?? 0,
  })), [products])

  const latestOrders = useMemo(() => [...orders]
    .sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime())
    .slice(0, 6), [orders])

  // ── Inventory Analyst: horizontal urgency bars (% of reorder point consumed) ──
  const iaUrgencyBars = useMemo(() => products
    .filter((p) => p.reorderPoint > 0)
    .map((p) => {
      const avail = Math.max(0, p.stockLevel?.quantityAvailable ?? 0)
      const urgency = Math.min(100, Math.max(0, Math.round((1 - avail / (p.reorderPoint * 2)) * 100)))
      return { name: p.sku.replace('SKU-', ''), product: p.name, urgency, available: avail, reorderPoint: p.reorderPoint }
    })
    .sort((a, b) => b.urgency - a.urgency)
    .slice(0, 10), [products])

  const iaCategoryValue = useMemo(() => {
    const grouped = new Map<string, number>()
    products.forEach((p) => {
      const key = categoryLabel(p.category as Category)
      grouped.set(key, (grouped.get(key) ?? 0) + Math.max(0, (p.stockLevel?.quantityOnHand ?? 0)) * p.costPrice)
    })
    return Array.from(grouped.entries()).map(([name, value]) => ({ name, value: Number(value.toFixed(2)) }))
  }, [products])

  const reorderCandidates = useMemo(() => products
    .filter((p) => (p.stockLevel?.quantityAvailable ?? 0) <= p.reorderPoint)
    .map((p) => ({ id: p.id, sku: p.sku, name: p.name, available: p.stockLevel?.quantityAvailable ?? 0, reorderPoint: p.reorderPoint, reorderQuantity: p.reorderQuantity }))
    .sort((a, b) => (a.available - a.reorderPoint) - (b.available - b.reorderPoint))
    .slice(0, 8), [products])

  // ── Procurement Officer: supplier spend bars + status pie ───────────────────
  const poSupplierSpend = useMemo(() => {
    const grouped = new Map<string, number>()
    orders.forEach((o) => {
      const key = o.supplierName ?? 'Unknown'
      grouped.set(key, (grouped.get(key) ?? 0) + o.totalAmount)
    })
    return Array.from(grouped.entries())
      .map(([name, total]) => ({ name: name.split(' ')[0], total: Number(total.toFixed(2)) }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 8)
  }, [orders])

  const procurementStatus = useMemo(() => {
    const grouped = new Map<string, number>()
    orders.forEach((o) => grouped.set(o.status, (grouped.get(o.status) ?? 0) + 1))
    return Array.from(grouped.entries()).map(([name, value]) => ({ name, value }))
  }, [orders])

  // ── Warehouse Staff: stacked available vs reserved + receivable POs ─────────
  const wsStockBreakdown = useMemo(() => products.slice(0, 10).map((p) => ({
    name: p.sku.replace('SKU-', ''),
    available: p.stockLevel?.quantityAvailable ?? 0,
    reserved: p.stockLevel?.quantityReserved ?? 0,
  })), [products])

  const receivableOrders = useMemo(() => orders.filter((o) => o.status === 'acknowledged').slice(0, 8), [orders])

  // ── Radar data for WS: stock health per category ────────────────────────────
  const wsRadarData = useMemo(() => {
    const grouped = new Map<string, { available: number; reorderPoint: number; count: number }>()
    products.forEach((p) => {
      const key = categoryLabel(p.category as Category)
      const prev = grouped.get(key) ?? { available: 0, reorderPoint: 0, count: 0 }
      grouped.set(key, {
        available: prev.available + (p.stockLevel?.quantityAvailable ?? 0),
        reorderPoint: prev.reorderPoint + p.reorderPoint,
        count: prev.count + 1,
      })
    })
    return Array.from(grouped.entries()).map(([subject, v]) => ({
      subject,
      stockHealth: v.reorderPoint > 0 ? Math.min(100, Math.round((v.available / (v.reorderPoint * v.count)) * 100)) : 100,
    }))
  }, [products])

  const roleTitle: Record<Role, string> = {
    store_manager: 'Store Manager Dashboard',
    inventory_analyst: 'Inventory Analyst Dashboard',
    procurement_officer: 'Procurement Dashboard',
    warehouse_staff: 'Warehouse Dashboard',
  }

  if (loading) return <LoadingSpinner label="Loading dashboard..." />
  if (!dashboard) return <EmptyState title="Dashboard unavailable" description="Unable to load dashboard metrics from backend." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader title={roleTitle[role]} description="Live role-specific operational overview" />

      {/* ── STORE MANAGER ─────────────────────────────────────────── */}
      {role === 'store_manager' && (
        <>
          <div className="row g-3 mb-4">
            <div className="col-6 col-sm-3"><DashboardCard title="Total Products"       value={dashboard.totalProducts}  icon={<FiPackage />}       tone="primary" /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Low Stock Alerts"     value={dashboard.lowStockCount}  icon={<FiAlertTriangle />} tone="warning" /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Out of Stock"         value={dashboard.outOfStockCount} icon={<FiAlertTriangle />} tone="danger"  /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Open Purchase Orders" value={dashboard.openPoCount}    icon={<FiClipboard />}     tone="success" /></div>
          </div>

          {/* Stacked bar: onHand vs reserved by category */}
          <div className="row g-3 mb-3">
            <div className="col-lg-8">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Stock On-Hand vs Reserved by Category</h2>
                <ResponsiveContainer width="100%" height={270}>
                  <BarChart data={smCategoryStacked} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e8eef8" />
                    <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#607491' }} />
                    <YAxis tick={{ fontSize: 12, fill: '#607491' }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: '0.8rem' }} />
                    <Bar dataKey="onHand" name="On Hand" fill="#2563eb" radius={[0, 0, 0, 0]} stackId="a" />
                    <Bar dataKey="reserved" name="Reserved" fill="#0ea5e9" radius={[6, 6, 0, 0]} stackId="a" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="col-lg-4">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Latest Purchase Orders</h2>
                <DataTable
                  rows={latestOrders}
                  columns={[
                    { header: 'PO',     render: (row) => row.poNumber },
                    { header: 'Status', render: (row) => <span className={`badge ${statusBadgeClass(row.status)}`}>{row.status}</span> },
                    { header: 'Total',  render: (row) => formatCurrency(row.totalAmount) },
                  ]}
                  emptyMessage="No purchase orders available."
                />
              </div>
            </div>
          </div>

          {/* Area trend + reorder suggestions */}
          <div className="row g-3">
            <div className="col-lg-7">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Stock Availability vs Reserved (Top 8)</h2>
                <ResponsiveContainer width="100%" height={240}>
                  <AreaChart data={smStockTrend} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                    <defs>
                      <linearGradient id="availGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%"  stopColor="#2563eb" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#2563eb" stopOpacity={0.02} />
                      </linearGradient>
                      <linearGradient id="resGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%"  stopColor="#f59e0b" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#f59e0b" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e8eef8" />
                    <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#607491' }} />
                    <YAxis tick={{ fontSize: 11, fill: '#607491' }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: '0.8rem' }} />
                    <Area type="monotone" dataKey="available" stroke="#2563eb" strokeWidth={2} fill="url(#availGrad)" name="Available" />
                    <Area type="monotone" dataKey="reserved"  stroke="#f59e0b" strokeWidth={2} fill="url(#resGrad)"  name="Reserved" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="col-lg-5">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Reorder Suggestions</h2>
                {reorderCandidates.length === 0 ? (
                  <p className="text-muted small">All products are above their reorder points.</p>
                ) : (
                  <DataTable
                    rows={reorderCandidates}
                    columns={[
                      { header: 'SKU',    render: (row) => row.sku },
                      { header: 'Avail.', render: (row) => <span className="text-danger fw-bold">{row.available}</span> },
                      { header: 'Point',  render: (row) => row.reorderPoint },
                      {
                        header: 'Action',
                        render: (row) => (
                          <Link className="btn btn-xs btn-outline-primary action-btn" to={`/orders/new?productId=${row.id}`} style={{ fontSize: '0.75rem', padding: '0.2rem 0.45rem' }}>
                            <FiPlusCircle className="me-1" />PO
                          </Link>
                        ),
                      },
                    ]}
                    emptyMessage=""
                  />
                )}
              </div>
            </div>
          </div>
        </>
      )}

      {/* ── INVENTORY ANALYST ──────────────────────────────────────── */}
      {role === 'inventory_analyst' && (
        <>
          <div className="row g-3 mb-4">
            <div className="col-6 col-sm-3"><DashboardCard title="Total Products"   value={dashboard.totalProducts}  icon={<FiPackage />}       tone="primary" /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Low Stock Alerts" value={dashboard.lowStockCount}  icon={<FiAlertTriangle />} tone="warning" /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Out of Stock"     value={dashboard.outOfStockCount} icon={<FiAlertTriangle />} tone="danger"  /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Total Stock Value" value={formatCurrency(dashboard.totalStockValue ?? 0)} icon={<FiPackage />} tone="info" /></div>
          </div>

          {/* Horizontal urgency bar chart — unique to analyst */}
          <div className="row g-3 mb-3">
            <div className="col-lg-7">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Reorder Urgency Score (% consumed toward reorder)</h2>
                <ResponsiveContainer width="100%" height={290}>
                  <BarChart layout="vertical" data={iaUrgencyBars} margin={{ top: 4, right: 20, left: 40, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e8eef8" horizontal={false} />
                    <XAxis type="number" domain={[0, 100]} tickFormatter={(v) => `${v}%`} tick={{ fontSize: 11, fill: '#607491' }} />
                    <YAxis dataKey="name" type="category" width={60} tick={{ fontSize: 11, fill: '#607491' }} />
                    <Tooltip formatter={(v: unknown) => [`${v}%`, 'Urgency']} />
                    <Bar dataKey="urgency" radius={[0, 6, 6, 0]}>
                      {iaUrgencyBars.map((entry, _index) => (
                        <Cell key={entry.name} fill={entry.urgency > 75 ? '#ef4444' : entry.urgency > 50 ? '#f59e0b' : '#22c55e'} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="col-lg-5">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Stock Value by Category</h2>
                <ResponsiveContainer width="100%" height={290}>
                  <PieChart>
                    <Pie data={iaCategoryValue} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90} paddingAngle={3} cx="50%" cy="45%">
                      {iaCategoryValue.map((entry, index) => (
                        <Cell key={entry.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(v: unknown) => formatCurrency(Number(v ?? 0))} />
                    <Legend wrapperStyle={{ fontSize: '0.78rem' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          <div className="row g-3">
            <div className="col-12">
              <div className="page-card dashboard-panel">
                <h2 className="dashboard-panel-title">Reorder Priority Queue</h2>
                <DataTable
                  rows={reorderCandidates}
                  columns={[
                    { header: 'SKU',          render: (row) => row.sku },
                    { header: 'Product',       render: (row) => row.name },
                    { header: 'Available',     render: (row) => <span className="text-danger fw-bold">{row.available}</span> },
                    { header: 'Reorder Point', render: (row) => row.reorderPoint },
                    { header: 'Reorder Qty',   render: (row) => row.reorderQuantity },
                  ]}
                  emptyMessage="No products are below reorder threshold."
                />
              </div>
            </div>
          </div>
        </>
      )}

      {/* ── PROCUREMENT OFFICER ────────────────────────────────────── */}
      {role === 'procurement_officer' && (
        <>
          <div className="row g-3 mb-4">
            <div className="col-6 col-sm-3"><DashboardCard title="Open POs"         value={dashboard.openPoCount}                                          icon={<FiClipboard />}  tone="primary" /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Draft Orders"     value={orders.filter((o) => o.status === 'draft').length}              icon={<FiShoppingBag />} tone="info"   /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Submitted Orders" value={orders.filter((o) => o.status === 'submitted').length}          icon={<FiClipboard />}  tone="warning" /></div>
            <div className="col-6 col-sm-3"><DashboardCard title="Active Suppliers" value={suppliers.filter((s) => s.isActive).length}                    icon={<FiTruck />}       tone="success" /></div>
          </div>

          {/* Supplier spend bar + status donut */}
          <div className="row g-3">
            <div className="col-lg-7">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Spend by Supplier</h2>
                <ResponsiveContainer width="100%" height={270}>
                  <BarChart data={poSupplierSpend} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                    <defs>
                      <linearGradient id="spendGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%"   stopColor="#8b5cf6" stopOpacity={1} />
                        <stop offset="100%" stopColor="#6d28d9" stopOpacity={0.8} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e8eef8" />
                    <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#607491' }} />
                    <YAxis tickFormatter={(v: number) => `₹${(v / 1000).toFixed(0)}k`} tick={{ fontSize: 12, fill: '#607491' }} />
                    <Tooltip formatter={(v: unknown) => formatCurrency(Number(v ?? 0))} />
                    <Bar dataKey="total" name="Total Spend" fill="url(#spendGrad)" radius={[7, 7, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="col-lg-5">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Order Status Mix</h2>
                <ResponsiveContainer width="100%" height={270}>
                  <PieChart>
                    <Pie
                      data={procurementStatus}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={55}
                      outerRadius={90}
                      paddingAngle={4}
                      cx="50%"
                      cy="45%"
                    >
                      {procurementStatus.map((entry, index) => (
                        <Cell key={entry.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: '0.8rem' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          <div className="row g-3 mt-1">
            <div className="col-12">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Recent Purchase Orders</h2>
                <DataTable
                  rows={latestOrders}
                  columns={[
                    { header: 'PO',      render: (row) => row.poNumber },
                    { header: 'Supplier',render: (row) => row.supplierName ?? '-' },
                    { header: 'Status',  render: (row) => <span className={`badge ${statusBadgeClass(row.status)}`}>{row.status}</span> },
                    { header: 'Total',   render: (row) => formatCurrency(row.totalAmount) },
                    { header: 'Created', render: (row) => formatDate(row.createdAt) },
                  ]}
                  emptyMessage="No purchase orders available."
                />
              </div>
            </div>
          </div>
        </>
      )}

      {/* ── WAREHOUSE STAFF ────────────────────────────────────────── */}
      {role === 'warehouse_staff' && (
        <>
          <div className="row g-3 mb-4">
            <div className="col-6 col-sm-3">
              <DashboardCard title="Receivable POs"   value={receivableOrders.length}                                                              icon={<FiClipboard />}     tone="success" />
            </div>
            <div className="col-6 col-sm-3">
              <DashboardCard title="Low Stock Alerts" value={dashboard.lowStockCount}                                                              icon={<FiAlertTriangle />} tone="warning" />
            </div>
            <div className="col-6 col-sm-3">
              <DashboardCard title="Out of Stock"     value={dashboard.outOfStockCount}                                                            icon={<FiAlertTriangle />} tone="danger"  />
            </div>
            <div className="col-6 col-sm-3">
              <DashboardCard title="Available Units"  value={products.reduce((s, p) => s + (p.stockLevel?.quantityAvailable ?? 0), 0)}            icon={<FiPackage />}       tone="primary" />
            </div>
          </div>

          {/* Stacked available+reserved bars + radar health */}
          <div className="row g-3 mb-3">
            <div className="col-lg-7">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Stock Availability vs Reserved (Top 10)</h2>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={wsStockBreakdown} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e8eef8" />
                    <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#607491' }} />
                    <YAxis tick={{ fontSize: 11, fill: '#607491' }} />
                    <Tooltip />
                    <Legend wrapperStyle={{ fontSize: '0.8rem' }} />
                    <Bar dataKey="available" name="Available" fill="#22c55e" radius={[0, 0, 0, 0]} stackId="a" />
                    <Bar dataKey="reserved"  name="Reserved"  fill="#f59e0b" radius={[6, 6, 0, 0]} stackId="a" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="col-lg-5">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Stock Health by Category</h2>
                <ResponsiveContainer width="100%" height={260}>
                  <RadarChart data={wsRadarData} cx="50%" cy="50%" outerRadius={80}>
                    <PolarGrid stroke="#e8eef8" />
                    <PolarAngleAxis dataKey="subject" tick={{ fontSize: 11, fill: '#607491' }} />
                    <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fontSize: 10, fill: '#607491' }} />
                    <Radar name="Health %" dataKey="stockHealth" stroke="#22c55e" fill="#22c55e" fillOpacity={0.4} />
                    <Tooltip formatter={(v: unknown) => [`${v}%`, 'Health']} />
                  </RadarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          <div className="row g-3">
            <div className="col-lg-6">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">POs Ready to Receive</h2>
                <DataTable
                  rows={receivableOrders}
                  columns={[
                    { header: 'PO',       render: (row) => row.poNumber },
                    { header: 'Supplier', render: (row) => row.supplierName ?? '-' },
                    { header: 'Expected', render: (row) => formatDate(row.expectedDelivery) },
                    { header: 'Total',    render: (row) => formatCurrency(row.totalAmount) },
                  ]}
                  emptyMessage="No approved purchase orders to receive."
                />
              </div>
            </div>
            <div className="col-lg-6">
              <div className="page-card dashboard-panel h-100">
                <h2 className="dashboard-panel-title">Active Low-Stock Items</h2>
                <DataTable
                  rows={alerts.slice(0, 8)}
                  columns={[
                    { header: 'SKU',          render: (row) => row.productSku },
                    { header: 'Product',       render: (row) => row.productName },
                    { header: 'Available',     render: (row) => <span className="text-danger fw-bold">{row.quantityAvailable}</span> },
                    { header: 'Reorder Point', render: (row) => row.reorderPoint },
                  ]}
                  emptyMessage="No low-stock items right now."
                />
              </div>
            </div>
          </div>
        </>
      )}
    </motion.div>
  )
}