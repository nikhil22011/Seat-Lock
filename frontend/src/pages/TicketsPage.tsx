import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { api } from '../api/client'
import type { Booking } from '../api/types'
import { eventDate, money, time } from '../lib/format'
import { Badge, ErrorBox, PageTitle, Spinner } from '../components/ui'

export function TicketsPage() {
  const { data, isPending, isError, error, refetch } = useQuery({ queryKey: ['my-bookings'], queryFn: api.myBookings })

  if (isPending) return <Spinner />
  if (isError) return <ErrorBox error={error} onRetry={() => refetch()} />

  const tickets = data.filter((b) => b.status === 'CONFIRMED')
  const other = data.filter((b) => b.status !== 'CONFIRMED')

  return (
    <div>
      <PageTitle title="My tickets" subtitle="Show the QR code at the venue entrance." />
      {tickets.length === 0 ? (
        <div className="rounded-2xl bg-ink-900 p-10 text-center ring-1 ring-ink-800">
          <p className="text-zinc-400">No tickets yet.</p>
          <Link to="/" className="mt-3 inline-block font-semibold text-brand-400 hover:underline">Browse events →</Link>
        </div>
      ) : (
        <div className="grid gap-5 md:grid-cols-2">
          {tickets.map((b) => <Ticket key={b.id} booking={b} />)}
        </div>
      )}

      {other.length > 0 && (
        <section className="mt-12">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-zinc-500">Other bookings</h2>
          <ul className="divide-y divide-ink-800 rounded-2xl bg-ink-900 ring-1 ring-ink-800">
            {other.map((b) => (
              <li key={b.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 text-sm">
                <span>
                  <Link to={`/events/${b.event.id}`} className="font-medium hover:underline">{b.event.title}</Link>
                  <span className="text-zinc-500"> · {b.seats.join(', ') || '—'}</span>
                </span>
                <StatusBadge booking={b} />
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function Ticket({ booking: b }: { booking: Booking }) {
  const used = b.checkedInAt !== null
  return (
    <article className={`flex overflow-hidden rounded-2xl bg-ink-900 ring-1 ${used ? 'ring-ink-800 opacity-70' : 'ring-brand-500/30'}`}>
      <div className="flex-1 p-5">
        <StatusBadge booking={b} />
        <h3 className="mt-2 text-lg font-bold leading-snug">{b.event.title}</h3>
        <p className="text-sm text-zinc-400">{b.event.venue}, {b.event.city}</p>
        <p className="mt-3 text-sm font-semibold text-brand-400">{eventDate(b.event.startsAt)}</p>
        <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
          <div><dt className="text-xs text-zinc-500">Seats</dt><dd className="font-semibold">{b.seats.join(', ')}</dd></div>
          <div><dt className="text-xs text-zinc-500">Paid</dt><dd className="font-semibold">{money(b.amountCents)}</dd></div>
        </dl>
        <p className="mt-4 text-xs text-zinc-600">Booking #{b.id}</p>
      </div>
      {/* Perforated edge */}
      <div className="relative w-0 border-l-2 border-dashed border-ink-700">
        <span className="absolute -left-3 -top-3 h-6 w-6 rounded-full bg-ink-950" />
        <span className="absolute -bottom-3 -left-3 h-6 w-6 rounded-full bg-ink-950" />
      </div>
      <div className="flex flex-col items-center justify-center gap-2 p-5">
        {b.ticketCode && (
          <div className={`rounded-lg bg-white p-2 ${used ? 'grayscale' : ''}`}>
            <QRCodeSVG value={b.ticketCode} size={112} />
          </div>
        )}
        <span className="text-[10px] uppercase tracking-widest text-zinc-500">{used ? `Used ${time(b.checkedInAt!)}` : 'Scan at gate'}</span>
      </div>
    </article>
  )
}

function StatusBadge({ booking: b }: { booking: Booking }) {
  if (b.status === 'CONFIRMED') return b.checkedInAt ? <Badge tone="zinc">Checked in</Badge> : <Badge tone="green">Confirmed</Badge>
  if (b.status === 'HELD') return <Badge tone="amber">Awaiting payment</Badge>
  if (b.status === 'EXPIRED') return <Badge tone="red">Hold expired</Badge>
  return <Badge tone="zinc">Cancelled</Badge>
}
