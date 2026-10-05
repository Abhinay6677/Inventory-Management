import type { ReactNode } from 'react'

interface Props {
  title: string
  show: boolean
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
}

export function ModalDialog({ title, show, onClose, children, footer }: Props) {
  if (!show) return null

  return (
    <div className="modal fade show d-block themed-modal" tabIndex={-1} role="dialog">
      <div className="modal-dialog modal-lg modal-dialog-centered">
        <div className="modal-content themed-modal-content">
          <div className="modal-header">
            <h5 className="modal-title">{title}</h5>
            <button className="btn-close" type="button" onClick={onClose} />
          </div>
          <div className="modal-body">{children}</div>
          {footer && <div className="modal-footer">{footer}</div>}
        </div>
      </div>
      <div className="modal-backdrop fade show" onClick={onClose} />
    </div>
  )
}
