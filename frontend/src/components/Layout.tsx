import { Suspense } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import type { Role } from '../api/types'
import { Spinner } from './ui'

export function Layout() {
  const { user, logout } = useAuth()
  const link = ({ isActive }: { isActive: boolean }) =>
    `rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-ink-800 text-white' : 'text-zinc-400 hover:text-white'}`

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-30 border-b border-ink-800 bg-ink-950/85 backdrop-blur">
        <nav className="mx-auto flex h-16 max-w-6xl items-center gap-2 px-4">
          <Link to="/" className="mr-4 flex items-center gap-2 text-lg font-extrabold tracking-tight">
            <img src="/favicon.svg" alt="" className="h-7 w-7" />
            <span>Seat<span className="text-brand-500">Lock</span></span>
          </Link>
          <NavLink to="/" end className={link}>Events</NavLink>
          {user?.role === 'USER' && <NavLink to="/tickets" className={link}>My tickets</NavLink>}
          {user?.role === 'ORGANIZER' && (
            <>
              <NavLink to="/organizer" className={link}>Dashboard</NavLink>
              <NavLink to="/checkin" className={link}>Check-in</NavLink>
            </>
          )}
          <div className="ml-auto flex items-center gap-2">
            {user ? (
              <>
                <span className="hidden text-sm text-zinc-400 sm:inline">Hi, {user.name.split(' ')[0]}</span>
                <button onClick={logout} className="rounded-lg px-3 py-2 text-sm text-zinc-400 hover:text-white">Log out</button>
              </>
            ) : (
              <>
                <NavLink to="/login" className={link}>Log in</NavLink>
                <Link to="/register" className="rounded-lg bg-brand-500 px-3.5 py-2 text-sm font-semibold text-white hover:bg-brand-600">
                  Sign up
                </Link>
              </>
            )}
          </div>
        </nav>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Suspense fallback={<Spinner />}>
          <Outlet />
        </Suspense>
      </main>
      <footer className="border-t border-ink-800 py-6 text-center text-xs text-zinc-500">
        SeatLock · real-time seat booking with zero double bookings
      </footer>
    </div>
  )
}

/** Guards a route: sends guests to login (and back afterwards), and wrong roles home. */
export function RequireAuth({ role, children }: { role?: Role; children: React.ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (role && user.role !== role) return <Navigate to="/" replace />
  return <>{children}</>
}
