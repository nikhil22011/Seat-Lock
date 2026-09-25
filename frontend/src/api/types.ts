export type Role = 'USER' | 'ORGANIZER'

export interface User {
  id: number
  name: string
  email: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

export interface EventSummary {
  id: number
  title: string
  venue: string
  city: string
  startsAt: string
  imageUrl: string | null
  minPriceCents: number
  maxPriceCents: number
  totalSeats: number
  availableSeats: number
}

export interface PriceCategory {
  category: string
  priceCents: number
}

export interface EventDetail {
  id: number
  title: string
  description: string | null
  venue: string
  city: string
  startsAt: string
  imageUrl: string | null
  organizerName: string
  categories: PriceCategory[]
  totalSeats: number
  availableSeats: number
}

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED'

export interface Seat {
  id: number
  row: string
  number: number
  category: string
  priceCents: number
  status: SeatStatus
}

/** Real-time delta pushed over WebSocket. */
export interface SeatUpdate {
  id: number
  status: SeatStatus
}

export type BookingStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED'

export interface Booking {
  id: number
  status: BookingStatus
  event: { id: number; title: string; venue: string; city: string; startsAt: string }
  seats: string[]
  amountCents: number
  expiresAt: string
  createdAt: string
  confirmedAt: string | null
  checkedInAt: string | null
  ticketCode: string | null
}

export interface PayResponse {
  paymentStatus: 'SUCCEEDED' | 'FAILED'
  message: string
  booking: Booking
}

export interface CheckInResult {
  bookingId: number
  attendeeName: string
  eventTitle: string
  seats: string[]
  checkedInAt: string
}

export interface OrganizerEvent {
  id: number
  title: string
  venue: string
  city: string
  startsAt: string
  totalSeats: number
  bookedSeats: number
  availableSeats: number
  revenueCents: number
  checkedIn: number
}

export interface RowSpec {
  label: string
  seatCount: number
  category: string
  priceCents: number
}

export interface CreateEventRequest {
  title: string
  description: string
  venue: string
  city: string
  startsAt: string
  imageUrl: string | null
  rows: RowSpec[]
}
