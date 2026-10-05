import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiCheck, FiX } from 'react-icons/fi'
import { PageHeader } from '../../components/common/PageHeader'
import { DataTable } from '../../components/common/DataTable'
import { EmptyState } from '../../components/common/EmptyState'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { useAuth } from '../../hooks/useAuth'
import { approvalService } from '../../services/approvalService'
import type { ApprovalRequestResponse } from '../../api/contracts'
import { formatDate } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { PRODUCT_APPROVAL_REVIEW_ROLES, STOCK_APPROVAL_REVIEW_ROLES } from '../../utils/roles'

type ApprovalTab = 'pending' | 'history'

function approvalStatusBadge(status: ApprovalRequestResponse['status']) {
  switch (status) {
    case 'approved':
      return 'bg-success'
    case 'rejected':
      return 'bg-danger'
    default:
      return 'bg-secondary'
  }
}

function sortByCreatedAtDesc(items: ApprovalRequestResponse[]) {
  return [...items].sort((left, right) => {
    const leftTime = left.createdAt ? new Date(left.createdAt).getTime() : 0
    const rightTime = right.createdAt ? new Date(right.createdAt).getTime() : 0
    return rightTime - leftTime
  })
}

export function ApprovalCenterPage() {
  const { hasRole } = useAuth()
  const canReviewProductApprovals = hasRole(PRODUCT_APPROVAL_REVIEW_ROLES)
  const canReviewStockApprovals = hasRole(STOCK_APPROVAL_REVIEW_ROLES)
  const [activeTab, setActiveTab] = useState<ApprovalTab>('pending')
  const [loading, setLoading] = useState(true)
  const [productRequests, setProductRequests] = useState<ApprovalRequestResponse[]>([])
  const [stockRequests, setStockRequests] = useState<ApprovalRequestResponse[]>([])
  const [historyProductRequests, setHistoryProductRequests] = useState<ApprovalRequestResponse[]>([])
  const [historyStockRequests, setHistoryStockRequests] = useState<ApprovalRequestResponse[]>([])

  async function load() {
    setLoading(true)
    try {
      const [pendingProducts, pendingStock, approvedProducts, rejectedProducts, approvedStock, rejectedStock] = await Promise.all([
        canReviewProductApprovals ? approvalService.getProductRequests('pending') : Promise.resolve([]),
        canReviewStockApprovals ? approvalService.getStockRequests('pending') : Promise.resolve([]),
        canReviewProductApprovals ? approvalService.getProductRequests('approved') : Promise.resolve([]),
        canReviewProductApprovals ? approvalService.getProductRequests('rejected') : Promise.resolve([]),
        canReviewStockApprovals ? approvalService.getStockRequests('approved') : Promise.resolve([]),
        canReviewStockApprovals ? approvalService.getStockRequests('rejected') : Promise.resolve([]),
      ])
      setProductRequests(pendingProducts)
      setStockRequests(pendingStock)
      setHistoryProductRequests(sortByCreatedAtDesc([...approvedProducts, ...rejectedProducts]))
      setHistoryStockRequests(sortByCreatedAtDesc([...approvedStock, ...rejectedStock]))
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [canReviewProductApprovals, canReviewStockApprovals])

  async function reviewProductRequest(requestId: number, decision: 'approve' | 'reject') {
    try {
      if (decision === 'approve') {
        await approvalService.approveProductRequest(requestId)
      } else {
        await approvalService.rejectProductRequest(requestId)
      }
      toast.success(`Product request ${decision === 'approve' ? 'approved' : 'rejected'}`)
      await load()
    } catch (error) {
      toast.error(getErrorMessage(error))
    }
  }

  async function reviewStockRequest(requestId: number, decision: 'approve' | 'reject') {
    try {
      if (decision === 'approve') {
        await approvalService.approveStockRequest(requestId)
      } else {
        await approvalService.rejectStockRequest(requestId)
      }
      toast.success(`Stock request ${decision === 'approve' ? 'approved' : 'rejected'}`)
      await load()
    } catch (error) {
      toast.error(getErrorMessage(error))
    }
  }

  if (loading) return <LoadingSpinner label="Loading approval requests..." />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title="Approval Center"
        description="Review pending requests or browse approved and rejected history."
        actions={
          <div className="inline-actions">
            <button
              className={`btn btn-sm ${activeTab === 'pending' ? 'btn-primary' : 'btn-outline-secondary'}`}
              type="button"
              onClick={() => setActiveTab('pending')}
            >
              Pending
            </button>
            <button
              className={`btn btn-sm ${activeTab === 'history' ? 'btn-primary' : 'btn-outline-secondary'}`}
              type="button"
              onClick={() => setActiveTab('history')}
            >
              History
            </button>
          </div>
        }
      />

      {activeTab === 'pending' && canReviewProductApprovals && (
        <div className="page-card p-3 mb-4">
          <h2 className="h5 mb-3">Product Master Requests</h2>
          <DataTable
            rows={productRequests}
            columns={[
              { header: 'Request', render: (row) => `#${row.id}` },
              { header: 'Action', render: (row) => row.productAction ?? '-' },
              { header: 'Product', render: (row) => row.productName ?? row.productSku ?? '-' },
              { header: 'Requested By', render: (row) => row.requestedBy },
              { header: 'Requested At', render: (row) => formatDate(row.createdAt) },
              {
                header: 'Actions',
                render: (row) => (
                  <div className="inline-actions">
                    <button className="btn btn-sm btn-outline-success action-btn" type="button" onClick={() => reviewProductRequest(row.id, 'approve')}>
                      <FiCheck className="me-1" /> Approve
                    </button>
                    <button className="btn btn-sm btn-outline-danger action-btn" type="button" onClick={() => reviewProductRequest(row.id, 'reject')}>
                      <FiX className="me-1" /> Reject
                    </button>
                  </div>
                ),
              },
            ]}
            emptyMessage="No pending product requests."
          />
        </div>
      )}

      {activeTab === 'pending' && canReviewStockApprovals && (
        <div className="page-card p-3">
          <h2 className="h5 mb-3">Stock Movement Requests</h2>
          <DataTable
            rows={stockRequests}
            columns={[
              { header: 'Request', render: (row) => `#${row.id}` },
              { header: 'SKU', render: (row) => row.productSku ?? '-' },
              { header: 'Product', render: (row) => row.productName ?? '-' },
              { header: 'Type', render: (row) => row.movementType ?? '-' },
              { header: 'Qty', render: (row) => row.quantity ?? 0 },
              { header: 'Requested By', render: (row) => row.requestedBy },
              { header: 'Requested At', render: (row) => formatDate(row.createdAt) },
              {
                header: 'Actions',
                render: (row) => (
                  <div className="inline-actions">
                    <button className="btn btn-sm btn-outline-success action-btn" type="button" onClick={() => reviewStockRequest(row.id, 'approve')}>
                      <FiCheck className="me-1" /> Approve
                    </button>
                    <button className="btn btn-sm btn-outline-danger action-btn" type="button" onClick={() => reviewStockRequest(row.id, 'reject')}>
                      <FiX className="me-1" /> Reject
                    </button>
                  </div>
                ),
              },
            ]}
            emptyMessage="No pending stock requests."
          />
        </div>
      )}

      {activeTab === 'history' && canReviewProductApprovals && (
        <div className="page-card p-3 mb-4">
          <h2 className="h5 mb-3">Product Request History</h2>
          <DataTable
            rows={historyProductRequests}
            columns={[
              { header: 'Request', render: (row) => `#${row.id}` },
              { header: 'Action', render: (row) => row.productAction ?? '-' },
              { header: 'Product', render: (row) => row.productName ?? row.productSku ?? '-' },
              { header: 'Status', render: (row) => <span className={`badge ${approvalStatusBadge(row.status)}`}>{row.status}</span> },
              { header: 'Requested By', render: (row) => row.requestedBy },
              { header: 'Reviewed By', render: (row) => row.reviewedBy ?? '-' },
              { header: 'Requested At', render: (row) => formatDate(row.createdAt) },
              { header: 'Reviewed At', render: (row) => formatDate(row.reviewedAt) },
              { header: 'Review Notes', render: (row) => row.reviewNotes ?? '-' },
            ]}
            emptyMessage="No approved or rejected product requests found."
          />
        </div>
      )}

      {activeTab === 'history' && canReviewStockApprovals && (
        <div className="page-card p-3">
          <h2 className="h5 mb-3">Stock Request History</h2>
          <DataTable
            rows={historyStockRequests}
            columns={[
              { header: 'Request', render: (row) => `#${row.id}` },
              { header: 'SKU', render: (row) => row.productSku ?? '-' },
              { header: 'Product', render: (row) => row.productName ?? '-' },
              { header: 'Type', render: (row) => row.movementType ?? '-' },
              { header: 'Qty', render: (row) => row.quantity ?? 0 },
              { header: 'Status', render: (row) => <span className={`badge ${approvalStatusBadge(row.status)}`}>{row.status}</span> },
              { header: 'Requested By', render: (row) => row.requestedBy },
              { header: 'Reviewed By', render: (row) => row.reviewedBy ?? '-' },
              { header: 'Requested At', render: (row) => formatDate(row.createdAt) },
              { header: 'Reviewed At', render: (row) => formatDate(row.reviewedAt) },
              { header: 'Review Notes', render: (row) => row.reviewNotes ?? '-' },
            ]}
            emptyMessage="No approved or rejected stock requests found."
          />
        </div>
      )}

      {activeTab === 'pending' && !canReviewProductApprovals && !canReviewStockApprovals && (
        <EmptyState title="No approval access" description="Your role does not have access to review requests." />
      )}
    </motion.div>
  )
}
