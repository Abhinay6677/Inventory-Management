import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { FiCompass } from 'react-icons/fi'

export function NotFoundPage() {
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <div className="page-card p-4 text-center centered-state-card">
        <div className="state-icon state-icon-info">
          <FiCompass className="display-6 mb-0" />
        </div>
        <h1 className="h3">Page not found</h1>
        <p className="text-muted">The requested route does not exist.</p>
        <Link className="btn btn-primary" to="/dashboard">Go to dashboard</Link>
      </div>
    </motion.div>
  )
}
