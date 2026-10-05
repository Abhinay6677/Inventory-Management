import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { useAuth } from '../../hooks/useAuth'
import { getErrorMessage } from '../../utils/errors'
import { getSession } from '../../utils/storage'
import { defaultRouteForRole, isRouteAllowedForRole, normalizeRole } from '../../utils/roles'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    try {
      await login({ email, password })
      toast.success('Logged in successfully')
      const fromRoute = (location.state as { from?: string } | null)?.from
      const session = getSession()
      const role = normalizeRole(session?.user?.role)
      const safeRoute = fromRoute && isRouteAllowedForRole(role, fromRoute)
        ? fromRoute
        : defaultRouteForRole(role)

      navigate(safeRoute, { replace: true })
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  return (
    <motion.form
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
      className="needs-validation auth-form"
      onSubmit={handleSubmit}
    >
      <div className="mb-3">
        <label className="form-label">Email</label>
        <input className="form-control" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
      </div>
      <div className="mb-3">
        <label className="form-label">Password</label>
        <input className="form-control" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
      </div>
      <button className="btn btn-primary w-100" type="submit" disabled={loading}>
        {loading ? 'Signing in...' : 'Login'}
      </button>
      <div className="text-center mt-3">
        <Link to="/register" className="auth-form-link">Create a new account</Link>
      </div>
    </motion.form>
  )
}
