import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { Role } from '../api/types'
import { useAuth } from '../lib/auth'
import { Button, Field } from '../components/ui'

function AuthCard({ title, subtitle, children }: { title: string; subtitle: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-md rounded-2xl bg-ink-900 p-6 ring-1 ring-ink-800 sm:p-8">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-1 text-sm text-zinc-400">{subtitle}</p>
      <div className="mt-6">{children}</div>
    </div>
  )
}

function useAfterAuth() {
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  return (role: Role) => navigate(from ?? (role === 'ORGANIZER' ? '/organizer' : '/'), { replace: true })
}

export function LoginPage() {
  const { login } = useAuth()
  const done = useAfterAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      done((await login(email, password)).role)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  const fillDemo = (who: 'user' | 'organizer') => {
    setEmail(`${who}@seatlock.dev`)
    setPassword('password123')
  }

  return (
    <AuthCard title="Welcome back" subtitle={<>New here? <Link to="/register" className="text-brand-400 hover:underline">Create an account</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
        <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</Button>
      </form>
      <div className="mt-6 rounded-xl bg-ink-800/60 p-4 text-sm">
        <p className="font-medium text-zinc-300">Demo accounts</p>
        <div className="mt-2 flex gap-2">
          <Button variant="secondary" className="flex-1 !py-2" onClick={() => fillDemo('user')}>Ticket buyer</Button>
          <Button variant="secondary" className="flex-1 !py-2" onClick={() => fillDemo('organizer')}>Organizer</Button>
        </div>
      </div>
    </AuthCard>
  )
}

export function RegisterPage() {
  const { register } = useAuth()
  const done = useAfterAuth()
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [role, setRole] = useState<Role>('USER')
  const [error, setError] = useState<string | null>(null)
  const [fields, setFields] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setFields({})
    try {
      done((await register(form.name, form.email, form.password, role)).role)
    } catch (err) {
      if (err instanceof ApiError && err.fields) setFields(err.fields)
      setError(err instanceof Error ? err.message : 'Sign-up failed')
    } finally {
      setBusy(false)
    }
  }

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })

  return (
    <AuthCard title="Create your account" subtitle={<>Already have one? <Link to="/login" className="text-brand-400 hover:underline">Log in</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <div className="grid grid-cols-2 gap-2 rounded-xl bg-ink-800 p-1" role="radiogroup" aria-label="Account type">
          {(['USER', 'ORGANIZER'] as Role[]).map((r) => (
            <button
              type="button" key={r} role="radio" aria-checked={role === r} onClick={() => setRole(r)}
              className={`rounded-lg py-2 text-sm font-semibold transition ${role === r ? 'bg-brand-500 text-white' : 'text-zinc-400 hover:text-white'}`}
            >
              {r === 'USER' ? 'I want tickets' : 'I host events'}
            </button>
          ))}
        </div>
        <Field label="Full name" required autoComplete="name" value={form.name} onChange={set('name')} error={fields.name} />
        <Field label="Email" type="email" required autoComplete="email" value={form.email} onChange={set('email')} error={fields.email} />
        <Field label="Password" type="password" required minLength={8} autoComplete="new-password" value={form.password}
          onChange={set('password')} error={fields.password} hint="At least 8 characters" />
        {error && !Object.keys(fields).length && <p role="alert" className="text-sm text-red-300">{error}</p>}
        <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</Button>
      </form>
    </AuthCard>
  )
}
