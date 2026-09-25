import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { QRCodeSVG } from 'qrcode.react'
import { api, ApiError } from '../api/client'
import type { Booking, Seat, SeatUpdate } from '../api/types'
import { useAuth } from '../lib/auth'
import { useSeatSocket } from '../lib/useSeatSocket'
import { eventDate, money, newIdempotencyKey } from '../lib/format'
import { SeatLegend, SeatMap } from '../components/SeatMap'
import { EventPoster } from '../components/EventCard'
import { Countdown } from '../components/Countdown'
import { Badge, Button, ErrorBox, Spinner } from '../components/ui'

const MAX_SEATS = 10
type Notice = { tone: 'error' | 'info' | 'success'; text: string } | null

export function EventPage() {
  const eventId = Number(useParams().id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const eventQ = useQuery({ queryKey: ['event', eventId], queryFn: () => api.event(eventId) })
  const seatsQ = useQuery({ queryKey: ['seats', eventId], queryFn: () => api.seats(eventId) })
  const seats = useMemo(() => seatsQ.data ?? [], [seatsQ.data])

  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [booking, setBooking] = useState<Booking | null>(null)
  const [notice, setNotice] = useState<Notice>(null)

  const seatById = useMemo(() => new Map(seats.map((s) => [s.id, s])), [seats])
  const idByLabel = useMemo(() => new Map(seats.map((s) => [`${s.row}${s.number}`, s.id])), [seats])
  const mine = useMemo(
    () => new Set(booking?.status === 'HELD' ? booking.seats.map((l) => idByLabel.get(l)!).filter(Boolean) : []),
    [booking, idByLabel],
  )

  // Coming back to the page mid-checkout (e.g. after a refresh)? Resume the existing hold.
  const myBookingsQ = useQuery({ queryKey: ['my-bookings'], queryFn: api.myBookings, enabled: user?.role === 'USER' })
  useEffect(() => {
    const active = myBookingsQ.data?.find(
      (b) => b.event.id === eventId && b.status === 'HELD' && new Date(b.expiresAt).getTime() > Date.now(),
    )
    if (active) setBooking((current) => current ?? active)
  }, [myBookingsQ.data, eventId])

  // ---- live updates -------------------------------------------------------
  const selectedRef = useRef(selected)
  selectedRef.current = selected
  // Seats we are holding right now. Our own "HELD" broadcast can beat the HTTP response back,
  // and must not be mistaken for someone else taking them.
  const holdingRef = useRef<Set<number>>(new Set())

  const onSeatUpdate = useCallback((updates: SeatUpdate[]) => {
    qc.setQueryData<Seat[]>(['seats', eventId], (old) => {
      if (!old) return old
      const byId = new Map(updates.map((u) => [u.id, u.status]))
      return old.map((s) => (byId.has(s.id) ? { ...s, status: byId.get(s.id)! } : s))
    })
    // If someone grabbed a seat we had only *selected* (not held), drop it and tell the user.
    const lost = updates.filter(
      (u) => u.status !== 'AVAILABLE' && selectedRef.current.has(u.id) && !holdingRef.current.has(u.id),
    )
    if (lost.length) {
      setSelected((prev) => {
        const next = new Set(prev)
        lost.forEach((u) => next.delete(u.id))
        return next
      })
      const labels = lost.map((u) => seatLabel(qc.getQueryData<Seat[]>(['seats', eventId]), u.id)).join(', ')
      setNotice({ tone: 'info', text: `Seat ${labels} was just taken by someone else.` })
    }
  }, [qc, eventId])

  const refreshSeats = useCallback(() => qc.invalidateQueries({ queryKey: ['seats', eventId] }), [qc, eventId])
  const live = useSeatSocket(eventId, onSeatUpdate, refreshSeats)

  // ---- selection & hold ---------------------------------------------------
  const toggle = useCallback((seat: Seat) => {
    setNotice(null)
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(seat.id)) next.delete(seat.id)
      else if (next.size >= MAX_SEATS) setNotice({ tone: 'info', text: `You can pick up to ${MAX_SEATS} seats.` })
      else next.add(seat.id)
      return next
    })
  }, [])

  const holdM = useMutation({
    mutationFn: () => {
      holdingRef.current = new Set(selected)
      return api.hold(eventId, [...selected])
    },
    onSettled: () => { holdingRef.current = new Set() },
    onSuccess: (b) => {
      setBooking(b)
      setSelected(new Set())
      setNotice(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'SEAT_UNAVAILABLE') {
        setSelected(new Set())
        refreshSeats()
      }
      setNotice({ tone: 'error', text: e.message })
    },
  })

  const startHold = () => {
    if (!user) return navigate('/login', { state: { from: `/events/${eventId}` } })
    if (user.role !== 'USER') return setNotice({ tone: 'info', text: 'Organizer accounts can’t buy tickets. Log in as a user.' })
    holdM.mutate()
  }

  const onHoldExpired = useCallback(() => {
    setBooking((b) => (b?.status === 'HELD' ? null : b))
    setNotice({ tone: 'error', text: 'Your 5-minute hold expired and the seats were released. Please pick again.' })
    refreshSeats()
  }, [refreshSeats])

  if (eventQ.isPending || seatsQ.isPending) return <Spinner />
  if (eventQ.isError) return <ErrorBox error={eventQ.error} onRetry={() => eventQ.refetch()} />
  if (seatsQ.isError) return <ErrorBox error={seatsQ.error} onRetry={() => seatsQ.refetch()} />
  const event = eventQ.data

  const selectedSeats = [...selected].map((id) => seatById.get(id)!).filter(Boolean)
    .sort((a, b) => a.row.localeCompare(b.row) || a.number - b.number)
  const selectedTotal = selectedSeats.reduce((sum, s) => sum + s.priceCents, 0)

  return (
    <div>
      <div className="mb-8 flex flex-col gap-5 sm:flex-row sm:items-center">
        <EventPoster id={event.id} title={event.title} imageUrl={event.imageUrl} className="h-28 w-full shrink-0 rounded-2xl sm:w-44" />
        <div className="min-w-0">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand-400">{eventDate(event.startsAt)}</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">{event.title}</h1>
          <p className="mt-1 text-zinc-400">{event.venue}, {event.city} · by {event.organizerName}</p>
          {event.description && <p className="mt-2 max-w-2xl text-sm text-zinc-400">{event.description}</p>}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="min-w-0 rounded-2xl bg-ink-900 p-4 ring-1 ring-ink-800 sm:p-6">
          <div className="mb-6 flex items-center justify-between gap-3">
            <h2 className="font-semibold">Choose your seats</h2>
            <span className={`flex items-center gap-2 text-xs ${live ? 'text-emerald-400' : 'text-zinc-500'}`}>
              <span className={`h-2 w-2 rounded-full ${live ? 'animate-pulse bg-emerald-400' : 'bg-zinc-600'}`} />
              {live ? 'Live' : 'Connecting…'}
            </span>
          </div>
          <SeatMap seats={seats} selected={selected} mine={mine} onToggle={toggle} disabled={booking?.status === 'HELD'} />
          <div className="mt-6 border-t border-ink-800 pt-5">
            <SeatLegend />
          </div>
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          {notice && <NoticeBar notice={notice} onClose={() => setNotice(null)} />}
          {booking?.status === 'CONFIRMED' ? (
            <Confirmed booking={booking} />
          ) : booking?.status === 'HELD' ? (
            <Checkout
              booking={booking}
              onPaid={(b) => { setBooking(b); qc.invalidateQueries({ queryKey: ['my-bookings'] }) }}
              onCancelled={() => { setBooking(null); setNotice({ tone: 'info', text: 'Hold cancelled. Seats released.' }) }}
              onExpired={onHoldExpired}
              setNotice={setNotice}
            />
          ) : (
            <div className="rounded-2xl bg-ink-900 p-5 ring-1 ring-ink-800">
              <h2 className="font-semibold">Your selection</h2>
              {selectedSeats.length === 0 ? (
                <p className="mt-3 text-sm text-zinc-400">Tap seats on the map to select them. Up to {MAX_SEATS} per booking.</p>
              ) : (
                <ul className="mt-3 space-y-1.5 text-sm">
                  {selectedSeats.map((s) => (
                    <li key={s.id} className="flex justify-between">
                      <span>{s.row}{s.number} <span className="text-zinc-500">· {s.category}</span></span>
                      <span>{money(s.priceCents)}</span>
                    </li>
                  ))}
                </ul>
              )}
              <div className="mt-4 flex items-center justify-between border-t border-ink-800 pt-4">
                <span className="text-zinc-400">Total</span>
                <span className="text-xl font-bold">{money(selectedTotal)}</span>
              </div>
              <Button className="mt-4 w-full" disabled={selectedSeats.length === 0 || holdM.isPending} onClick={startHold}>
                {holdM.isPending ? 'Locking seats…' : user ? `Hold ${selectedSeats.length || ''} seat${selectedSeats.length === 1 ? '' : 's'}` : 'Log in to book'}
              </Button>
              <p className="mt-3 text-center text-xs text-zinc-500">Seats are reserved for 5 minutes while you pay.</p>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}

function Checkout({ booking, onPaid, onCancelled, onExpired, setNotice }: {
  booking: Booking
  onPaid: (b: Booking) => void
  onCancelled: () => void
  onExpired: () => void
  setNotice: (n: Notice) => void
}) {
  const [simulateFail, setSimulateFail] = useState(false)
  // One key per payment *attempt*. A network retry reuses it, so the server can't charge twice.
  const attemptKey = useRef<string | null>(null)

  const payM = useMutation({
    mutationFn: () => {
      attemptKey.current ??= newIdempotencyKey()
      return api.pay(booking.id, attemptKey.current, simulateFail ? 'FAIL' : undefined)
    },
    onSuccess: (res) => {
      attemptKey.current = null
      if (res.paymentStatus === 'SUCCEEDED') {
        setNotice({ tone: 'success', text: res.message })
        onPaid(res.booking)
      } else {
        setNotice({ tone: 'error', text: `${res.message}. Your seats are still held, so try again.` })
      }
    },
    onError: (e) => {
      if (!(e instanceof ApiError && e.code === 'NETWORK')) attemptKey.current = null
      if (e instanceof ApiError && e.code === 'HOLD_EXPIRED') onExpired()
      else setNotice({ tone: 'error', text: e.message })
    },
  })

  const cancelM = useMutation({
    mutationFn: () => api.cancel(booking.id),
    onSuccess: onCancelled,
    onError: (e) => setNotice({ tone: 'error', text: e.message }),
  })

  return (
    <div className="rounded-2xl bg-ink-900 p-5 ring-1 ring-emerald-500/40">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Seats locked 🔒</h2>
        <Countdown until={booking.expiresAt} onExpire={onExpired} />
      </div>
      <p className="mt-1 text-sm text-zinc-400">Nobody else can take these while the timer runs.</p>
      <div className="mt-4 flex flex-wrap gap-1.5">
        {booking.seats.map((s) => <Badge key={s} tone="green">{s}</Badge>)}
      </div>
      <div className="mt-4 flex items-center justify-between border-t border-ink-800 pt-4">
        <span className="text-zinc-400">Amount</span>
        <span className="text-xl font-bold">{money(booking.amountCents)}</span>
      </div>

      <label className="mt-4 flex cursor-pointer items-center gap-2 rounded-lg bg-ink-800 px-3 py-2 text-xs text-zinc-400">
        <input type="checkbox" checked={simulateFail} onChange={(e) => setSimulateFail(e.target.checked)} className="accent-brand-500" />
        Demo: simulate a declined card
      </label>

      <Button className="mt-3 w-full" disabled={payM.isPending || cancelM.isPending} onClick={() => payM.mutate()}>
        {payM.isPending ? 'Processing payment…' : `Pay ${money(booking.amountCents)} (test mode)`}
      </Button>
      <Button variant="ghost" className="mt-2 w-full" disabled={payM.isPending || cancelM.isPending} onClick={() => cancelM.mutate()}>
        Cancel and pick different seats
      </Button>
    </div>
  )
}

function Confirmed({ booking }: { booking: Booking }) {
  return (
    <div className="rounded-2xl bg-ink-900 p-5 text-center ring-1 ring-emerald-500/40">
      <p className="text-3xl">🎉</p>
      <h2 className="mt-2 text-lg font-bold">You're going!</h2>
      <p className="mt-1 text-sm text-zinc-400">Seats {booking.seats.join(', ')} · {money(booking.amountCents)}</p>
      {booking.ticketCode && (
        <div className="mx-auto mt-5 w-fit rounded-xl bg-white p-3">
          <QRCodeSVG value={booking.ticketCode} size={168} />
        </div>
      )}
      <p className="mt-3 text-xs text-zinc-500">Show this QR code at the entrance.</p>
      <Link to="/tickets" className="mt-4 inline-block text-sm font-semibold text-brand-400 hover:underline">View all my tickets →</Link>
    </div>
  )
}

function NoticeBar({ notice, onClose }: { notice: NonNullable<Notice>; onClose: () => void }) {
  const tone = {
    error: 'border-red-500/30 bg-red-500/10 text-red-200',
    info: 'border-amber-500/30 bg-amber-500/10 text-amber-100',
    success: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-200',
  }[notice.tone]
  return (
    <div role="alert" className={`mb-4 flex items-start gap-3 rounded-xl border p-3 text-sm ${tone}`}>
      <span className="flex-1">{notice.text}</span>
      <button onClick={onClose} aria-label="Dismiss" className="opacity-70 hover:opacity-100">✕</button>
    </div>
  )
}

function seatLabel(seats: Seat[] | undefined, id: number) {
  const s = seats?.find((x) => x.id === id)
  return s ? `${s.row}${s.number}` : `#${id}`
}
