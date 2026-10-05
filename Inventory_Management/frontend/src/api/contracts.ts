export type Role = 'store_manager' | 'inventory_analyst' | 'procurement_officer' | 'warehouse_staff'

export type Category =
  | 'grocery'
  | 'electronics'
  | 'clothing'
  | 'household'
  | 'personal_care'

export type MovementType = 'receipt' | 'sale' | 'adjustment' | 'transfer' | 'returnm'

export type POStatus = 'draft' | 'submitted' | 'acknowledged' | 'received' | 'cancelled'
export type ApprovalStatus = 'pending' | 'approved' | 'rejected'
export type ProductApprovalAction = 'create' | 'update' | 'delete'

export interface ApiErrorBody {
  error?: string
  status?: number
}

export interface AuthResponse {
  token: string
  tokenType: string
  email: string
  fullName?: string
  role: Role
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName?: string
  role?: Role
}

export interface SupplierRequest {
  name: string
  supplierCode: string
  contactEmail?: string
  paymentTermsDays?: number
  leadTimeDays?: number
}

export interface SupplierResponse {
  id: number
  name: string
  supplierCode: string
  contactEmail?: string | null
  paymentTermsDays: number
  leadTimeDays: number
  isActive: boolean
}

export interface SupplierCatalogItem {
  productId: number
  sku: string
  name: string
  category: Category
  costPrice: number
  unitPrice: number
  unitOfMeasure: string
  reorderPoint: number
  reorderQuantity: number
  quantityAvailable: number
}

export interface SupplierCatalogResponse {
  supplierId: number
  supplierName: string
  supplierCode: string
  products: SupplierCatalogItem[]
}

export interface ProductRequest {
  name: string
  category: Category
  unitPrice: number
  costPrice: number
  unitOfMeasure?: string
  reorderPoint?: number
  reorderQuantity?: number
  supplierId?: number | null
  initialStock?: number
}

export interface ProductReorderRequest {
  reorderPoint: number
  reorderQuantity: number
}

export interface StockUpdateRequest {
  movementType: MovementType
  quantity: number
  referenceNumber?: string
  notes?: string
  recordedBy?: string
}

export interface StockLevelResponse {
  quantityOnHand: number
  quantityReserved: number
  quantityAvailable: number
  lastUpdated?: string | null
}

export interface StockMovementResponse {
  id: number
  movementType: MovementType
  quantity: number
  referenceNumber?: string | null
  notes?: string | null
  recordedAt?: string | null
  recordedBy?: string | null
}

export interface ProductResponse {
  id: number
  sku: string
  name: string
  category: Category
  unitPrice: number
  costPrice: number
  unitOfMeasure: string
  reorderPoint: number
  reorderQuantity: number
  supplierId?: number | null
  supplierName?: string | null
  createdAt?: string | null
  stockLevel?: StockLevelResponse | null
  recentMovements?: StockMovementResponse[] | null
}

export interface POItemRequest {
  productId: number
  quantityOrdered: number
  unitCost: number
}

export interface PurchaseOrderRequest {
  supplierId: number
  items: POItemRequest[]
  expectedDelivery?: string | null
}

export interface PurchaseOrderItemResponse {
  id: number
  productId?: number | null
  productSku?: string | null
  productName?: string | null
  quantityOrdered: number
  unitCost: number
  quantityReceived?: number | null
}

export interface PurchaseOrderResponse {
  id: number
  poNumber: string
  supplierId?: number | null
  supplierName?: string | null
  status: POStatus
  totalAmount: number
  orderDate?: string | null
  expectedDelivery?: string | null
  receivedDate?: string | null
  createdAt?: string | null
  items: PurchaseOrderItemResponse[]
}

export interface StockAlertResponse {
  id: number
  productId: number
  productSku: string
  productName: string
  alertType: string
  message: string
  isResolved: boolean
  triggeredAt?: string | null
  quantityAvailable: number
  reorderPoint: number
}

export interface DashboardResponse {
  totalProducts: number
  lowStockCount: number
  outOfStockCount: number
  openPoCount: number
  totalStockValue: number
}

export interface AuditLogEntry {
  id: number
  productId?: number | null
  productSku?: string | null
  productName?: string | null
  movementType: MovementType
  quantity: number
  referenceNumber?: string | null
  notes?: string | null
  recordedAt?: string | null
  recordedBy?: string | null
}

export interface SupplierPerformanceResponse {
  supplierId: number
  supplierName: string
  supplierCode: string
  promisedLeadTimeDays: number
  totalOrders: number
  receivedOrders: number
  onTimeOrders: number
  onTimePercent: number
  averageActualLeadDays: number
  totalSpend: number
}

export interface ApprovalRequestResponse {
  id: number
  workflowType: 'product_master' | 'stock_movement'
  status: ApprovalStatus
  productAction?: ProductApprovalAction | null
  productId?: number | null
  productSku?: string | null
  productName?: string | null
  category?: Category | null
  unitPrice?: number | null
  costPrice?: number | null
  unitOfMeasure?: string | null
  reorderPoint?: number | null
  reorderQuantity?: number | null
  supplierId?: number | null
  initialStock?: number | null
  movementType?: MovementType | null
  quantity?: number | null
  referenceNumber?: string | null
  notes?: string | null
  requestedBy: string
  reviewedBy?: string | null
  reviewNotes?: string | null
  createdAt?: string | null
  reviewedAt?: string | null
  message?: string | null
}

export interface RagAskRequest {
  question: string
}

export interface RagAskResponse {
  question: string
  answer: string
  sourceCount: number
}
