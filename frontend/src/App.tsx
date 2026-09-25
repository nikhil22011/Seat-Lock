import { lazy } from 'react'
import { Link, Route, Routes } from 'react-router-dom'
import { Layout, RequireAuth } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { EventPage } from './pages/EventPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { TicketsPage } from './pages/TicketsPage'

// Organizer-only screens (and the heavy QR scanner library) load on demand.
const OrganizerPage = lazy(() => import('./pages/OrganizerPage').then((m) => ({ default: m.OrganizerPage })))
const CreateEventPage = lazy(() => import('./pages/CreateEventPage').then((m) => ({ default: m.CreateEventPage })))
const CheckInPage = lazy(() => import('./pages/CheckInPage').then((m) => ({ default: m.CheckInPage })))

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="events/:id" element={<EventPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="tickets" element={<RequireAuth role="USER"><TicketsPage /></RequireAuth>} />
        <Route path="organizer" element={<RequireAuth role="ORGANIZER"><OrganizerPage /></RequireAuth>} />
        <Route path="organizer/new" element={<RequireAuth role="ORGANIZER"><CreateEventPage /></RequireAuth>} />
        <Route path="checkin" element={<RequireAuth role="ORGANIZER"><CheckInPage /></RequireAuth>} />
        <Route path="*" element={
          <div className="py-24 text-center">
            <p className="text-6xl font-black text-ink-700">404</p>
            <p className="mt-2 text-zinc-400">This page doesn't exist.</p>
            <Link to="/" className="mt-4 inline-block font-semibold text-brand-400 hover:underline">Back to events</Link>
          </div>
        } />
      </Route>
    </Routes>
  )
}
