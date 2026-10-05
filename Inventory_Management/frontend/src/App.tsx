import { HashRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import { useAuth } from './hooks/useAuth'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { AppLayout } from './layouts/AppLayout'
import { AuthLayout } from './layouts/AuthLayout'
import { LoginPage } from './pages/Auth/LoginPage'
import { RegisterPage } from './pages/Auth/RegisterPage'
import { DashboardPage } from './pages/Dashboard/DashboardPage'
import { ProductsPage } from './pages/Products/ProductsPage'
import { ProductFormPage } from './pages/Products/ProductFormPage'
import { ProductDetailPage } from './pages/Products/ProductDetailPage'
import { SuppliersPage } from './pages/Suppliers/SuppliersPage'
import { SupplierFormPage } from './pages/Suppliers/SupplierFormPage'
import { SupplierDetailPage } from './pages/Suppliers/SupplierDetailPage'
import { PurchaseOrdersPage } from './pages/PurchaseOrders/PurchaseOrdersPage'
import { PurchaseOrderFormPage } from './pages/PurchaseOrders/PurchaseOrderFormPage'
import { PurchaseOrderDetailPage } from './pages/PurchaseOrders/PurchaseOrderDetailPage'
import { StockPage } from './pages/Stock/StockPage'
import { AuditLogPage } from './pages/Stock/AuditLogPage'
import { ApprovalCenterPage } from './pages/Approvals/ApprovalCenterPage'
import { InventoryManualQaPage } from './pages/Rag/InventoryManualQaPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { UnauthorizedPage } from './pages/UnauthorizedPage'
import {
  ALL_ROLES,
  APPROVAL_ACCESS_ROLES,
  DASHBOARD_ACCESS_ROLES,
  defaultRouteForRole,
  ORDER_ACCESS_ROLES,
  ORDER_CREATE_ROLES,
  PHASE2_RAG_ACCESS_ROLES,
  PRODUCT_CREATE_ROLES,
  PRODUCT_EDIT_ROLES,
  STOCK_ACCESS_ROLES,
  SUPPLIER_ACCESS_ROLES,
  SUPPLIER_MANAGEMENT_ROLES,
} from './utils/roles'

function RoleHomeRedirect() {
  const { user } = useAuth()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return <Navigate to={defaultRouteForRole(user.role)} replace />
}

function ProductDetailRouteResolver() {
  const { id } = useParams()
  const { hasRole } = useAuth()

  if (id === 'new') {
    if (!hasRole(PRODUCT_CREATE_ROLES)) {
      return <Navigate to="/unauthorized" replace />
    }
    return <ProductFormPage />
  }

  return <ProductDetailPage />
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <HashRouter>
          <Toaster position="top-right" />
          <Routes>
            <Route element={<AuthLayout />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={ALL_ROLES} />}>
              <Route element={<AppLayout />}>
                <Route path="/products" element={<ProductsPage />} />
                <Route path="/products/:id" element={<ProductDetailRouteResolver />} />
                <Route path="/unauthorized" element={<UnauthorizedPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={ALL_ROLES} />}>
              <Route path="/" element={<RoleHomeRedirect />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={DASHBOARD_ACCESS_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={SUPPLIER_ACCESS_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/suppliers" element={<SuppliersPage />} />
                  <Route path="/suppliers/:id" element={<SupplierDetailPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={ORDER_ACCESS_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/orders" element={<PurchaseOrdersPage />} />
                  <Route path="/orders/:id" element={<PurchaseOrderDetailPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={STOCK_ACCESS_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/stock" element={<StockPage />} />
                  <Route path="/stock/audit" element={<AuditLogPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={APPROVAL_ACCESS_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/approvals" element={<ApprovalCenterPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={PHASE2_RAG_ACCESS_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/phase2/rag" element={<InventoryManualQaPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={PRODUCT_CREATE_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/products/new" element={<ProductFormPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={PRODUCT_EDIT_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/products/:id/edit" element={<ProductFormPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={SUPPLIER_MANAGEMENT_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/suppliers/new" element={<SupplierFormPage />} />
                  <Route path="/suppliers/:id/edit" element={<SupplierFormPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={ORDER_CREATE_ROLES} />}>
              <Route element={<AppLayout />}>
                  <Route path="/orders/new" element={<PurchaseOrderFormPage />} />
              </Route>
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </HashRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
