import { memo, useMemo } from 'react'
import type { Seat } from '../api/types'
import { money } from '../lib/format'

interface Props {
  seats: Seat[]
  selected: Set<number>
  /** Seats held by the current user's own pending booking. */
  mine: Set<number>
  onToggle: (seat: Seat) => void
  disabled?: boolean
}

/** Theatre-style seat grid, grouped by price category, screen at the top. */
export const SeatMap = memo(function SeatMap({ seats, selected, mine, onToggle, disabled }: Props) {
  const sections = useMemo(() => {
    const rows = new Map<string, Seat[]>()
    for (const s of seats) {
      const list = rows.get(s.row) ?? []
      list.push(s)
      rows.set(s.row, list)
    }
    // Consecutive rows with the same category form one priced section.
    const result: { category: string; price: number; rows: [string, Seat[]][] }[] = []
    for (const [row, list] of [...rows.entries()].sort(([a], [b]) => a.length - b.length || a.localeCompare(b))) {
      list.sort((a, b) => a.number - b.number)
      const last = result.at(-1)
      if (last && last.category === list[0].category) last.rows.push([row, list])
      else result.push({ category: list[0].category, price: list[0].priceCents, rows: [[row, list]] })
    }
    return result
  }, [seats])

  return (
    <div className="overflow-x-auto pb-2">
      <div className="mx-auto w-fit min-w-full px-2">
        <div className="mx-auto mb-10 max-w-md">
          <div className="h-1.5 rounded-full bg-gradient-to-r from-transparent via-brand-400 to-transparent shadow-[0_6px_30px_rgba(244,63,94,0.45)]" />
          <p className="mt-2 text-center text-[11px] uppercase tracking-[0.35em] text-zinc-500">Stage this way</p>
        </div>

        {sections.map((section) => (
          <section key={section.category + section.rows[0][0]} className="mb-6">
            <p className="mb-3 text-center text-xs font-semibold uppercase tracking-wider text-zinc-400">
              {section.category} · {money(section.price)}
            </p>
            <div className="space-y-1.5">
              {section.rows.map(([row, list]) => (
                <div key={row} className="flex items-center justify-center gap-1.5">
                  <span className="w-6 shrink-0 text-center text-xs font-medium text-zinc-500">{row}</span>
                  <div className="flex gap-1.5">
                    {list.map((seat, i) => (
                      <SeatButton
                        key={seat.id}
                        seat={seat}
                        aisle={list.length > 10 && i === Math.floor(list.length / 2)}
                        isSelected={selected.has(seat.id)}
                        isMine={mine.has(seat.id)}
                        disabled={disabled}
                        onToggle={onToggle}
                      />
                    ))}
                  </div>
                  <span className="w-6 shrink-0 text-center text-xs font-medium text-zinc-500">{row}</span>
                </div>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  )
})

function SeatButton({ seat, aisle, isSelected, isMine, disabled, onToggle }: {
  seat: Seat; aisle: boolean; isSelected: boolean; isMine: boolean; disabled?: boolean; onToggle: (s: Seat) => void
}) {
  const state = isMine ? 'mine' : isSelected ? 'selected' : seat.status
  const look: Record<string, string> = {
    AVAILABLE: 'bg-ink-800 ring-1 ring-zinc-600 text-zinc-400 hover:ring-brand-400 hover:text-white',
    selected: 'bg-brand-500 text-white ring-1 ring-brand-400 animate-pop',
    mine: 'bg-emerald-500 text-white ring-1 ring-emerald-300',
    HELD: 'seat-held bg-ink-900 ring-1 ring-amber-500/40 text-transparent cursor-not-allowed',
    BOOKED: 'bg-ink-700/60 text-transparent cursor-not-allowed',
  }
  const clickable = !disabled && (seat.status === 'AVAILABLE' || isSelected)
  const statusText = isMine ? 'held for you' : isSelected ? 'selected' : seat.status.toLowerCase()
  return (
    <button
      type="button"
      onClick={() => clickable && onToggle(seat)}
      disabled={!clickable && !isMine}
      aria-pressed={isSelected}
      aria-label={`Seat ${seat.row}${seat.number}, ${seat.category}, ${money(seat.priceCents)}, ${statusText}`}
      title={`${seat.row}${seat.number} · ${seat.category} · ${money(seat.priceCents)}`}
      className={`grid h-7 w-7 place-items-center rounded-t-lg rounded-b-sm text-[10px] font-semibold transition
        focus-visible:outline-2 focus-visible:outline-brand-400 ${aisle ? 'ml-4' : ''} ${look[state]}`}
    >
      {seat.number}
    </button>
  )
}

export function SeatLegend() {
  const item = (cls: string, label: string) => (
    <span className="flex items-center gap-2">
      <span className={`h-4 w-4 rounded-t-md rounded-b-sm ${cls}`} />
      {label}
    </span>
  )
  return (
    <div className="flex flex-wrap justify-center gap-x-5 gap-y-2 text-xs text-zinc-400">
      {item('bg-ink-800 ring-1 ring-zinc-600', 'Available')}
      {item('bg-brand-500', 'Selected')}
      {item('bg-emerald-500', 'Held for you')}
      {item('seat-held bg-ink-900 ring-1 ring-amber-500/40', 'Someone is paying')}
      {item('bg-ink-700/60', 'Sold')}
    </div>
  )
}
