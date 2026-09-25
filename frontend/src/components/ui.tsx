import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-zinc-400" role="status">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-zinc-600 border-t-brand-500" />
      {label}
    </div>
  )
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong'
  return (
    <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-200">
      {message}
      {onRetry && (
        <button onClick={onRetry} className="ml-3 font-semibold underline underline-offset-2">Try again</button>
      )}
    </div>
  )
}

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'

export function Button({ variant = 'primary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  const styles: Record<Variant, string> = {
    primary: 'bg-brand-500 text-white hover:bg-brand-600 disabled:bg-ink-700 disabled:text-zinc-500',
    secondary: 'bg-ink-800 text-zinc-100 ring-1 ring-ink-700 hover:bg-ink-700',
    ghost: 'text-zinc-300 hover:bg-ink-800 hover:text-white',
    danger: 'bg-red-500/10 text-red-300 ring-1 ring-red-500/30 hover:bg-red-500/20',
  }
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition
        disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400
        ${styles[variant]} ${className}`}
    />
  )
}

export function Field({ label, error, hint, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-zinc-300">{label}</span>
      <input
        {...props}
        className={`w-full rounded-lg bg-ink-900 px-3.5 py-2.5 text-sm text-zinc-100 ring-1 placeholder:text-zinc-600
          focus:outline-none focus:ring-2 focus:ring-brand-500 ${error ? 'ring-red-500/60' : 'ring-ink-700'}`}
      />
      {error ? <span className="mt-1 block text-xs text-red-300">{error}</span>
        : hint ? <span className="mt-1 block text-xs text-zinc-500">{hint}</span> : null}
    </label>
  )
}

export function Badge({ tone, children }: { tone: 'green' | 'amber' | 'zinc' | 'red' | 'brand'; children: ReactNode }) {
  const tones = {
    green: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
    amber: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
    zinc: 'bg-zinc-500/15 text-zinc-300 ring-zinc-500/30',
    red: 'bg-red-500/15 text-red-300 ring-red-500/30',
    brand: 'bg-brand-500/15 text-brand-400 ring-brand-500/30',
  }
  return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ${tones[tone]}`}>{children}</span>
}

export function PageTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 text-zinc-400">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}
