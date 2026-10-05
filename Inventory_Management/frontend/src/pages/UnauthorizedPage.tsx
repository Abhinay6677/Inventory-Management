import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { FiShieldOff } from 'react-icons/fi'

export function UnauthorizedPage() {
  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
      <div className="page-card p-4 text-center centered-state-card">
        <div className="state-icon state-icon-danger">
          <FiShieldOff className="display-6 mb-0" />
        </div>
        <h1 className="h4">Access denied</h1>
        <p className="text-muted mb-4">Your role does not have permission to access this page.</p>
        <Link className="btn btn-primary" to="/dashboard">Back to dashboard</Link>
      </div>
    </motion.div>
  )
}
