import { ModalDialog } from './ModalDialog'

interface Props {
  title: string
  message: string
  show: boolean
  onCancel: () => void
  onConfirm: () => void
  confirmText?: string
}

export function ConfirmDialog({ title, message, show, onCancel, onConfirm, confirmText = 'Confirm' }: Props) {
  return (
    <ModalDialog
      title={title}
      show={show}
      onClose={onCancel}
      footer={
        <>
          <button className="btn btn-outline-secondary" type="button" onClick={onCancel}>
            Cancel
          </button>
          <button className="btn btn-danger" type="button" onClick={onConfirm}>
            {confirmText}
          </button>
        </>
      }
    >
      <p className="mb-0">{message}</p>
    </ModalDialog>
  )
}
