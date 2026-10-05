interface Props {
  tone?: 'light' | 'dark'
  showText?: boolean
  title?: string
  subtitle?: string
  className?: string
}

export function AppLogo({
  tone = 'dark',
  showText = true,
  title = 'Inventory Management',
  subtitle = 'Operations Portal',
  className = '',
}: Props) {
  return (
    <div className={`app-logo tone-${tone} ${className}`.trim()}>
      <span className="app-logo-icon" aria-hidden="true">
        <svg viewBox="0 0 48 48" className="app-logo-svg">
          <rect x="6" y="6" width="36" height="36" rx="11" className="logo-bg" />
          <rect x="14" y="13" width="5" height="22" rx="2.2" className="logo-bar" />
          <rect x="22" y="17" width="5" height="18" rx="2.2" className="logo-bar-soft" />
          <rect x="30" y="11" width="5" height="24" rx="2.2" className="logo-bar" />
          <path d="M15.5 30.5l2.2 2.1 4.1-4.8" className="logo-check" />
        </svg>
      </span>
      {showText ? (
        <span className="app-logo-text">
          <span className="app-logo-title">{title}</span>
          <span className="app-logo-subtitle">{subtitle}</span>
        </span>
      ) : null}
    </div>
  )
}
