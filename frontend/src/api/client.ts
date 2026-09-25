import type {
  AuthResponse, Booking, CheckInResult, CreateEventRequest, EventDetail, EventSummary,
  OrganizerEvent, PayResponse, Seat, User,
} from './types'

const API_BASE = import.meta.env.VITE_API_URL ?? ''
const TOKEN_KEY = 'seatlock.token'

/** Error thrown for any non-2xx response. `code` mirrors the backend's machine-readable code. */
export class ApiError extends Error {
  status: number
  code: string
  fields?: Record<string, string>
  /** The full error body, for extra details like `checkedInAt`. */
  details: Record<string, unknown>

  constructor(status: number, code: string, message: string, fields?: Record<string, string>, details: Record<string, unknown> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
    this.details = details
  }
}

export const tokenStore = {
  get(): string | null {
    try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    } catch { /* storage unavailable (private mode) - stay logged in for this tab only */ }
  },
}

let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = tokenStore.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let res: Response
  try {
    res = await fetch(API_BASE + path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Is the backend running?')
  }

  if (res.status === 401 && token) onUnauthorized()
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(
      res.status,
      body?.code ?? (res.status === 401 ? 'UNAUTHORIZED' : 'ERROR'),
      body?.detail ?? (res.status === 401 ? 'Please log in to continue' : `Request failed (${res.status})`),
      body?.fields,
      body ?? {},
    )
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

const json = (body: unknown) => JSON.stringify(body)

export const api = {
  register: (name: string, email: string, password: string, role: 'USER' | 'ORGANIZER') =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: json({ name, email, password, role }) }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: json({ email, password }) }),
  me: () => request<User>('/api/auth/me'),

  events: () => request<EventSummary[]>('/api/events'),
  event: (id: number) => request<EventDetail>(`/api/events/${id}`),
  seats: (id: number) => request<Seat[]>(`/api/events/${id}/seats`),
  createEvent: (body: CreateEventRequest) =>
    request<EventDetail>('/api/events', { method: 'POST', body: json(body) }),
  organizerEvents: () => request<OrganizerEvent[]>('/api/organizer/events'),

  hold: (eventId: number, seatIds: number[]) =>
    request<Booking>(`/api/events/${eventId}/holds`, { method: 'POST', body: json({ seatIds }) }),
  pay: (bookingId: number, idempotencyKey: string, simulate?: 'FAIL') =>
    request<PayResponse>(`/api/bookings/${bookingId}/pay`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: json({ simulate: simulate ?? null }),
    }),
  cancel: (bookingId: number) => request<Booking>(`/api/bookings/${bookingId}`, { method: 'DELETE' }),
  myBookings: () => request<Booking[]>('/api/bookings/me'),

  checkIn: (code: string) => request<CheckInResult>('/api/checkin', { method: 'POST', body: json({ code }) }),
}
