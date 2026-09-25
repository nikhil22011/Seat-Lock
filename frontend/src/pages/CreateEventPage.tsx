import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/client'
import type { RowSpec } from '../api/types'
import { money } from '../lib/format'
import { Button, Field, PageTitle } from '../components/ui'

interface Tier {
  category: string
  price: string
  rows: string
  seatsPerRow: string
}

const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

/** Turns price tiers into lettered rows, front to back: tier 1 gets A, B…, tier 2 continues. */
function buildRows(tiers: Tier[]): RowSpec[] {
  const rows: RowSpec[] = []
  for (const t of tiers) {
    for (let i = 0; i < Number(t.rows || 0); i++) {
      rows.push({
        label: LETTERS[rows.length] ?? '?',
        seatCount: Number(t.seatsPerRow || 0),
        category: t.category.trim(),
        priceCents: Math.round(Number(t.price || 0) * 100),
      })
    }
  }
  return rows
}

export function CreateEventPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [info, setInfo] = useState({ title: '', description: '', venue: '', city: '', startsAt: '' })
  const [tiers, setTiers] = useState<Tier[]>([
    { category: 'Premium', price: '1499', rows: '3', seatsPerRow: '12' },
    { category: 'Regular', price: '699', rows: '5', seatsPerRow: '16' },
  ])
  const [fields, setFields] = useState<Record<string, string>>({})

  const rows = buildRows(tiers)
  const totalSeats = rows.reduce((s, r) => s + r.seatCount, 0)
  const tooManyRows = rows.length > 26

  const createM = useMutation({
    mutationFn: () => api.createEvent({
      ...info,
      startsAt: new Date(info.startsAt).toISOString(),
      imageUrl: null,
      rows,
    }),
    onSuccess: (event) => {
      qc.invalidateQueries({ queryKey: ['organizer-events'] })
      qc.invalidateQueries({ queryKey: ['events'] })
      navigate(`/events/${event.id}`)
    },
    onError: (e) => setFields(e instanceof ApiError && e.fields ? e.fields : {}),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setFields({})
    createM.mutate()
  }

  const setI = (k: keyof typeof info) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setInfo({ ...info, [k]: e.target.value })
  const setT = (i: number, k: keyof Tier, v: string) => setTiers(tiers.map((t, j) => (j === i ? { ...t, [k]: v } : t)))

  return (
    <form onSubmit={submit} className="mx-auto max-w-3xl">
      <PageTitle title="Create an event" subtitle="Seats are generated automatically from your price tiers." />

      <section className="space-y-4 rounded-2xl bg-ink-900 p-6 ring-1 ring-ink-800">
        <Field label="Event title" required value={info.title} onChange={setI('title')} error={fields.title} placeholder="e.g. Indie Night Live" />
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium text-zinc-300">Description</span>
          <textarea
            value={info.description} onChange={setI('description')} rows={3} maxLength={2000}
            className="w-full rounded-lg bg-ink-900 px-3.5 py-2.5 text-sm ring-1 ring-ink-700 focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Venue" required value={info.venue} onChange={setI('venue')} error={fields.venue} />
          <Field label="City" required value={info.city} onChange={setI('city')} error={fields.city} />
        </div>
        <Field label="Starts at" type="datetime-local" required value={info.startsAt} onChange={setI('startsAt')}
          error={fields.startsAt && 'Must be in the future'} />
      </section>

      <section className="mt-6 rounded-2xl bg-ink-900 p-6 ring-1 ring-ink-800">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-semibold">Seating & pricing</h2>
          <span className="text-sm text-zinc-400">{rows.length} rows · {totalSeats} seats</span>
        </div>
        <div className="space-y-3">
          {tiers.map((t, i) => (
            <div key={i} className="grid grid-cols-2 items-end gap-3 rounded-xl bg-ink-800/50 p-3 sm:grid-cols-[1.4fr_1fr_0.8fr_0.8fr_auto]">
              <Field label="Category" required value={t.category} onChange={(e) => setT(i, 'category', e.target.value)} />
              <Field label="Price (₹)" type="number" min={0} required value={t.price} onChange={(e) => setT(i, 'price', e.target.value)} />
              <Field label="Rows" type="number" min={1} max={26} required value={t.rows} onChange={(e) => setT(i, 'rows', e.target.value)} />
              <Field label="Seats/row" type="number" min={1} max={40} required value={t.seatsPerRow} onChange={(e) => setT(i, 'seatsPerRow', e.target.value)} />
              <Button type="button" variant="ghost" disabled={tiers.length === 1} onClick={() => setTiers(tiers.filter((_, j) => j !== i))} aria-label="Remove tier">
                ✕
              </Button>
            </div>
          ))}
        </div>
        <Button type="button" variant="secondary" className="mt-3"
          onClick={() => setTiers([...tiers, { category: '', price: '', rows: '2', seatsPerRow: '16' }])}>
          + Add price tier
        </Button>

        {/* Mini preview */}
        <div className="mt-6 space-y-1 overflow-x-auto rounded-xl bg-ink-950 p-4">
          {rows.slice(0, 26).map((r) => (
            <div key={r.label} className="flex items-center justify-center gap-[3px]">
              <span className="w-4 text-[10px] text-zinc-500">{r.label}</span>
              {Array.from({ length: Math.min(r.seatCount, 40) }, (_, n) => (
                <span key={n} className={`h-2 w-2 rounded-sm ${tierColor(tiers, r.category)}`} />
              ))}
            </div>
          ))}
          <p className="pt-2 text-center text-xs text-zinc-500">
            {tiers.map((t) => `${t.category || '?'} ${money(Number(t.price || 0) * 100)}`).join(' · ')}
          </p>
        </div>
        {tooManyRows && <p className="mt-3 text-sm text-red-300">Maximum 26 rows (A–Z).</p>}
      </section>

      {createM.isError && !Object.keys(fields).length && (
        <p role="alert" className="mt-4 text-sm text-red-300">{createM.error.message}</p>
      )}
      <div className="mt-6 flex justify-end gap-3">
        <Button type="button" variant="ghost" onClick={() => navigate('/organizer')}>Cancel</Button>
        <Button type="submit" disabled={createM.isPending || tooManyRows || totalSeats === 0}>
          {createM.isPending ? 'Publishing…' : 'Publish event'}
        </Button>
      </div>
    </form>
  )
}

function tierColor(tiers: Tier[], category: string) {
  const colors = ['bg-brand-500', 'bg-amber-400', 'bg-sky-400', 'bg-emerald-400', 'bg-violet-400']
  const i = tiers.findIndex((t) => t.category.trim() === category)
  return colors[Math.max(0, i) % colors.length]
}
