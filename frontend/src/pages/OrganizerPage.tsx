import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { eventDate, money } from '../lib/format'
import { ErrorBox, PageTitle, Spinner } from '../components/ui'

export function OrganizerPage() {
  // Poll so the dashboard ticks up as sales and check-ins happen.
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['organizer-events'], queryFn: api.organizerEvents, refetchInterval: 5000,
  })

  if (isPending) return <Spinner />
  if (isError) return <ErrorBox error={error} onRetry={() => refetch()} />

  const revenue = data.reduce((s, e) => s + e.revenueCents, 0)
  const sold = data.reduce((s, e) => s + e.bookedSeats, 0)
  const checkedIn = data.reduce((s, e) => s + e.checkedIn, 0)

  return (
    <div>
      <PageTitle
        title="Organizer dashboard"
        subtitle="Live sales across your events"
        action={
          <div className="flex gap-2">
            <Link to="/checkin" className="rounded-lg bg-ink-800 px-4 py-2.5 text-sm font-semibold ring-1 ring-ink-700 hover:bg-ink-700">Open scanner</Link>
            <Link to="/organizer/new" className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-600">+ New event</Link>
          </div>
        }
      />

      <div className="mb-8 grid gap-4 sm:grid-cols-3">
        <Stat label="Total revenue" value={money(revenue)} />
        <Stat label="Tickets sold" value={sold.toLocaleString('en-IN')} />
        <Stat label="Checked in" value={checkedIn.toLocaleString('en-IN')} />
      </div>

      {data.length === 0 ? (
        <div className="rounded-2xl bg-ink-900 p-10 text-center ring-1 ring-ink-800">
          <p className="text-zinc-400">You haven't created any events yet.</p>
          <Link to="/organizer/new" className="mt-3 inline-block font-semibold text-brand-400 hover:underline">Create your first event →</Link>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-2xl bg-ink-900 ring-1 ring-ink-800">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-ink-800 text-xs uppercase tracking-wide text-zinc-500">
              <tr>
                <th className="px-5 py-3 font-semibold">Event</th>
                <th className="px-5 py-3 font-semibold">Sold</th>
                <th className="px-5 py-3 text-right font-semibold">Revenue</th>
                <th className="px-5 py-3 text-right font-semibold">Checked in</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-800">
              {data.map((e) => {
                const pct = e.totalSeats ? Math.round((e.bookedSeats / e.totalSeats) * 100) : 0
                return (
                  <tr key={e.id}>
                    <td className="px-5 py-4">
                      <Link to={`/events/${e.id}`} className="font-semibold hover:underline">{e.title}</Link>
                      <p className="text-xs text-zinc-500">{eventDate(e.startsAt)} · {e.venue}, {e.city}</p>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="h-2 w-28 overflow-hidden rounded-full bg-ink-800">
                          <div className="h-full rounded-full bg-brand-500" style={{ width: `${pct}%` }} />
                        </div>
                        <span className="tabular-nums text-zinc-300">{e.bookedSeats}/{e.totalSeats}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 text-right font-semibold tabular-nums">{money(e.revenueCents)}</td>
                    <td className="px-5 py-4 text-right tabular-nums text-zinc-300">{e.checkedIn}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-ink-900 p-5 ring-1 ring-ink-800">
      <p className="text-sm text-zinc-400">{label}</p>
      <p className="mt-1 text-2xl font-bold tabular-nums">{value}</p>
    </div>
  )
}
