export function LoadingSpinner({ label = 'Loading...' }: { label?: string }) {
  return (
    <div className="loading-state d-flex flex-column align-items-center justify-content-center py-5">
      <div className="spinner-border text-primary loading-spinner" role="status" aria-hidden="true" />
      <div className="mt-3 text-muted loading-label">{label}</div>
    </div>
  )
}
