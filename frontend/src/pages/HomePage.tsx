import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { EventCard } from '../components/EventCard'
import { ErrorBox, Spinner } from '../components/ui'

export function HomePage() {
  const { data, isPending, isError, error, refetch } = useQuery({ queryKey: ['events'], queryFn: api.events })
  const [city, setCity] = useState('All')
  const [search, setSearch] = useState('')

  const cities = useMemo(() => ['All', ...new Set((data ?? []).map((e) => e.city))], [data])
  const shown = (data ?? []).filter((e) =>
    (city === 'All' || e.city === city) &&
    (search === '' || `${e.title} ${e.venue}`.toLowerCase().includes(search.toLowerCase())))

  return (
    <div>
      <section className="mb-10 overflow-hidden rounded-3xl bg-gradient-to-br from-brand-600/30 via-ink-900 to-ink-900 p-8 ring-1 ring-ink-800 sm:p-12">
        <h1 className="max-w-2xl text-3xl font-extrabold tracking-tight sm:text-5xl">
          Grab your seat. <span className="text-brand-400">Watch others grab theirs.</span>
        </h1>
        <p className="mt-4 max-w-xl text-zinc-300">
          Live seat maps update the instant anyone books. Your seats are locked for 5 minutes while you pay,
          so nobody can take them from under you.
        </p>
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search concerts, comedy, movies…"
          aria-label="Search events"
          className="mt-6 w-full max-w-md rounded-xl bg-ink-950/70 px-4 py-3 text-sm ring-1 ring-ink-700 placeholder:text-zinc-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
        />
      </section>

      <div className="mb-6 flex flex-wrap items-center gap-2">
        {cities.map((c) => (
          <button
            key={c}
            onClick={() => setCity(c)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ring-1 transition ${
              city === c ? 'bg-brand-500 text-white ring-brand-500' : 'text-zinc-300 ring-ink-700 hover:ring-zinc-500'}`}
          >
            {c}
          </button>
        ))}
      </div>

      {isPending ? <Spinner label="Loading events…" />
        : isError ? <ErrorBox error={error} onRetry={() => refetch()} />
        : shown.length === 0 ? <p className="py-16 text-center text-zinc-400">No upcoming events match your filters.</p>
        : (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {shown.map((e) => <EventCard key={e.id} event={e} />)}
          </div>
        )}
    </div>
  )
}
