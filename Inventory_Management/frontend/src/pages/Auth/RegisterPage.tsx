import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import type { Role } from '../../api/contracts'
import { useAuth } from '../../hooks/useAuth'
import { getErrorMessage } from '../../utils/errors'
import { roleLabel } from '../../utils/roles'

export function RegisterPage() {
  const navigate = useNavigate()
  const { register } = useAuth()
  const [form, setForm] = useState({
    email: '',
    password: '',
    fullName: '',
    role: 'warehouse_staff' as Role,
  })
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const passwordsDoNotMatch = confirmPassword.length > 0 && form.password !== confirmPassword

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (form.password !== confirmPassword) {
      toast.error('Password confirmation does not match')
      return
    }

    setLoading(true)
    try {
      await register(form)
      toast.success('Account created. Please login to continue.')
      navigate('/login', { replace: true })
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  return (
    <motion.form initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }} className="auth-form" onSubmit={handleSubmit}>
      <div className="mb-3">
        <label className="form-label">Full name</label>
        <input className="form-control" value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} />
      </div>
      <div className="mb-3">
        <label className="form-label">Email</label>
        <input className="form-control" type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
      </div>
      <div className="mb-3">
        <label className="form-label">Password</label>
        <input className="form-control" type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} required />
      </div>
      <div className="mb-3">
        <label className="form-label">Confirm password</label>
        <input
          className={`form-control${passwordsDoNotMatch ? ' is-invalid' : ''}`}
          type="password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          required
        />
        {passwordsDoNotMatch ? <div className="invalid-feedback">Passwords must match.</div> : null}
      </div>
      <div className="mb-3">
        <label className="form-label">Role</label>
        <select className="form-select" value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value as Role })}>
          <option value="store_manager">{roleLabel('store_manager')}</option>
          <option value="inventory_analyst">{roleLabel('inventory_analyst')}</option>
          <option value="procurement_officer">{roleLabel('procurement_officer')}</option>
          <option value="warehouse_staff">{roleLabel('warehouse_staff')}</option>
        </select>
      </div>
      <button className="btn btn-primary w-100" type="submit" disabled={loading || passwordsDoNotMatch}>
        {loading ? 'Creating account...' : 'Register'}
      </button>
      <div className="text-center mt-3">
        <Link to="/login" className="auth-form-link">Back to login</Link>
      </div>
    </motion.form>
  )
}
