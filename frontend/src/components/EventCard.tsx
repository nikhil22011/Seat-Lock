import { Link } from 'react-router-dom'
import type { EventSummary } from '../api/types'
import { eventDate, money, posterGradient } from '../lib/format'
import { Badge } from './ui'

export function EventPoster({ id, title, imageUrl, className = '' }: { id: number; title: string; imageUrl: string | null; className?: string }) {
  if (imageUrl) return <img src={imageUrl} alt="" className={`object-cover ${className}`} />
  return (
    <div className={`relative overflow-hidden ${className}`} style={{ background: posterGradient(id) }}>
      <span className="absolute -bottom-6 -right-2 select-none text-[7rem] font-black leading-none text-white/15">
        {title.charAt(0)}
      </span>
    </div>
  )
}

export function EventCard({ event }: { event: EventSummary }) {
  const pctLeft = event.totalSeats ? event.availableSeats / event.totalSeats : 0
  return (
    <Link
      to={`/events/${event.id}`}
      className="group overflow-hidden rounded-2xl bg-ink-900 ring-1 ring-ink-800 transition hover:-translate-y-0.5 hover:ring-brand-500/50"
    >
      <EventPoster id={event.id} title={event.title} imageUrl={event.imageUrl} className="h-40 w-full" />
      <div className="p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-brand-400">{eventDate(event.startsAt)}</p>
        <h3 className="mt-1 line-clamp-2 font-bold leading-snug group-hover:text-white">{event.title}</h3>
        <p className="mt-1 truncate text-sm text-zinc-400">{event.venue}, {event.city}</p>
        <div className="mt-4 flex items-center justify-between">
          <span className="text-sm">
            <span className="text-zinc-500">from </span>
            <span className="font-semibold">{money(event.minPriceCents)}</span>
          </span>
          {event.availableSeats === 0 ? <Badge tone="zinc">Sold out</Badge>
            : pctLeft < 0.2 ? <Badge tone="amber">Filling fast</Badge>
            : <Badge tone="green">{event.availableSeats} seats left</Badge>}
        </div>
      </div>
    </Link>
  )
}
