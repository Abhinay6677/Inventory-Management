import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { FiCheckCircle, FiDownload, FiSend, FiThumbsUp, FiXCircle } from 'react-icons/fi'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import { purchaseOrderService } from '../../services/purchaseOrderService'
import { LoadingSpinner } from '../../components/common/LoadingSpinner'
import { EmptyState } from '../../components/common/EmptyState'
import { PageHeader } from '../../components/common/PageHeader'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { DataTable } from '../../components/common/DataTable'
import { formatCurrency, formatDate, statusBadgeClass } from '../../utils/format'
import { getErrorMessage } from '../../utils/errors'
import { useAuth } from '../../hooks/useAuth'
import type { PurchaseOrderResponse } from '../../api/contracts'
import { ORDER_APPROVE_ROLES, ORDER_CREATE_ROLES, ORDER_RECEIVE_ROLES } from '../../utils/roles'

function exportPOPdf(order: PurchaseOrderResponse) {
  const doc = new jsPDF()
  doc.setFontSize(18)
  doc.setTextColor(31, 77, 184)
  doc.text('Purchase Order', 14, 20)

  doc.setFontSize(11)
  doc.setTextColor(40, 40, 40)
  doc.text(`PO Number: ${order.poNumber}`, 14, 32)
  doc.text(`Supplier: ${order.supplierName ?? '-'}`, 14, 39)
  doc.text(`Status: ${order.status.toUpperCase()}`, 14, 46)
  doc.text(`Order Date: ${formatDate(order.orderDate)}`, 14, 53)
  doc.text(`Expected Delivery: ${formatDate(order.expectedDelivery)}`, 14, 60)
  doc.text(`Total Amount: ${formatCurrency(order.totalAmount)}`, 14, 67)

  autoTable(doc, {
    startY: 76,
    head: [['Product', 'SKU', 'Qty Ordered', 'Qty Received', 'Unit Cost', 'Line Total']],
    body: order.items.map((item) => [
      item.productName ?? '-',
      item.productSku ?? '-',
      item.quantityOrdered,
      item.quantityReceived ?? 0,
      formatCurrency(item.unitCost),
      formatCurrency(item.quantityOrdered * item.unitCost),
    ]),
    headStyles: { fillColor: [31, 77, 184] },
    alternateRowStyles: { fillColor: [245, 248, 255] },
  })

  const pageCount = doc.getNumberOfPages()
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(9)
    doc.setTextColor(150)
    doc.text(
      `Generated ${new Date().toLocaleString()} — Inventory Management Portal`,
      14,
      doc.internal.pageSize.height - 8,
    )
  }

  doc.save(`${order.poNumber}.pdf`)
}

export function PurchaseOrderDetailPage() {
  const { hasRole } = useAuth()
  const canSubmitPurchaseOrder = hasRole(ORDER_CREATE_ROLES)
  const canApprovePurchaseOrder = hasRole(ORDER_APPROVE_ROLES)
  const canReceivePurchaseOrder = hasRole(ORDER_RECEIVE_ROLES)
  const { id } = useParams()
  const orderId = Number(id)
  const [order, setOrder] = useState<PurchaseOrderResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [confirmReceive, setConfirmReceive] = useState(false)
  const [confirmSubmit, setConfirmSubmit] = useState(false)
  const [confirmApprove, setConfirmApprove] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [saving, setSaving] = useState(false)

  async function load() {
    setLoading(true)
    try {
      setOrder(await purchaseOrderService.getOrderById(orderId))
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!Number.isNaN(orderId)) load()
  }, [orderId])

  async function runOrderAction(
    action: () => Promise<PurchaseOrderResponse>,
    successMessage: string,
    closeDialog: () => void,
  ) {
    setSaving(true)
    try {
      const updated = await action()
      setOrder(updated)
      toast.success(successMessage)
      closeDialog()
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <LoadingSpinner label="Loading purchase order details..." />
  if (!order) return <EmptyState title="Purchase order not found" description="The purchase order could not be loaded from the backend." />

  const canSubmit = canSubmitPurchaseOrder && order.status === 'draft'
  const canApprove = canApprovePurchaseOrder && order.status === 'submitted'
  const canCancel = canSubmitPurchaseOrder && order.status !== 'received' && order.status !== 'cancelled'
  const canReceive = canReceivePurchaseOrder && order.status === 'acknowledged'

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <PageHeader
        title={order.poNumber}
        description={`${order.supplierName ?? '-'} • ${formatDate(order.createdAt)}`}
        actions={
          <div className="inline-actions">
            {canSubmit && (
              <button className="btn btn-primary action-btn" type="button" onClick={() => setConfirmSubmit(true)}>
                <FiSend className="me-2" />
                Submit PO
              </button>
            )}
            {canApprove && (
              <button className="btn btn-warning action-btn" type="button" onClick={() => setConfirmApprove(true)}>
                <FiThumbsUp className="me-2" />
                Approve PO
              </button>
            )}
            {canReceive && (
              <button className="btn btn-success action-btn" type="button" onClick={() => setConfirmReceive(true)}>
                <FiCheckCircle className="me-2" />
                Receive PO
              </button>
            )}
            {canCancel && (
              <button className="btn btn-outline-danger action-btn" type="button" onClick={() => setConfirmCancel(true)}>
                <FiXCircle className="me-2" />
                Cancel PO
              </button>
            )}
            <button className="btn btn-outline-secondary action-btn" type="button" onClick={() => exportPOPdf(order)}>
              <FiDownload className="me-2" />
              PDF
            </button>
          </div>
        }
      />

      <div className="row g-3 mb-4">
        <div className="col-lg-4">
          <div className="page-card p-3">
            <h2 className="h5">Summary</h2>
            <dl className="row mb-0">
              <dt className="col-5">Status</dt><dd className="col-7"><span className={`badge ${statusBadgeClass(order.status)}`}>{order.status}</span></dd>
              <dt className="col-5">Order Date</dt><dd className="col-7">{formatDate(order.orderDate)}</dd>
              <dt className="col-5">Expected</dt><dd className="col-7">{formatDate(order.expectedDelivery)}</dd>
              <dt className="col-5">Received</dt><dd className="col-7">{formatDate(order.receivedDate)}</dd>
              <dt className="col-5">Total</dt><dd className="col-7">{formatCurrency(order.totalAmount)}</dd>
            </dl>
          </div>
        </div>
        <div className="col-lg-8">
          <div className="page-card p-3">
            <h2 className="h5 mb-3">Line Items</h2>
            <DataTable
              rows={order.items}
              columns={[
                { header: 'Product', render: (row) => row.productName },
                { header: 'SKU', render: (row) => row.productSku },
                { header: 'Ordered', render: (row) => row.quantityOrdered },
                { header: 'Received', render: (row) => row.quantityReceived ?? 0 },
                { header: 'Unit Cost', render: (row) => formatCurrency(row.unitCost) },
                { header: 'Line Total', render: (row) => formatCurrency(row.quantityOrdered * row.unitCost) },
              ]}
              emptyMessage="No items were returned for this purchase order."
            />
          </div>
        </div>
      </div>

      <ConfirmDialog
        title="Submit purchase order"
        message={`Submit ${order.poNumber} for receiving?`}
        show={confirmSubmit}
        onCancel={() => setConfirmSubmit(false)}
        onConfirm={() => runOrderAction(() => purchaseOrderService.submitOrder(orderId), 'Purchase order submitted', () => setConfirmSubmit(false))}
        confirmText={saving ? 'Submitting...' : 'Submit'}
      />

      <ConfirmDialog
        title="Receive purchase order"
        message={`Mark ${order.poNumber} as received and update inventory stock?`}
        show={confirmReceive}
        onCancel={() => setConfirmReceive(false)}
        onConfirm={() => runOrderAction(() => purchaseOrderService.receiveOrder(orderId), 'Purchase order received', () => setConfirmReceive(false))}
        confirmText={saving ? 'Receiving...' : 'Receive'}
      />

      <ConfirmDialog
        title="Approve purchase order"
        message={`Approve ${order.poNumber} so warehouse staff can receive it?`}
        show={confirmApprove}
        onCancel={() => setConfirmApprove(false)}
        onConfirm={() => runOrderAction(() => purchaseOrderService.approveOrder(orderId), 'Purchase order approved', () => setConfirmApprove(false))}
        confirmText={saving ? 'Approving...' : 'Approve'}
      />

      <ConfirmDialog
        title="Cancel purchase order"
        message={`Cancel ${order.poNumber}?`}
        show={confirmCancel}
        onCancel={() => setConfirmCancel(false)}
        onConfirm={() => runOrderAction(() => purchaseOrderService.cancelOrder(orderId), 'Purchase order cancelled', () => setConfirmCancel(false))}
        confirmText={saving ? 'Cancelling...' : 'Cancel PO'}
      />
    </motion.div>
  )
}
