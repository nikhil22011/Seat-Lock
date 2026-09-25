import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Html5Qrcode } from 'html5-qrcode'
import { api, ApiError } from '../api/client'
import type { CheckInResult } from '../api/types'
import { time } from '../lib/format'
import { Button, PageTitle } from '../components/ui'

type Outcome = { ok: true; result: CheckInResult } | { ok: false; message: string } | null

const SCANNER_ID = 'qr-scanner'

export function CheckInPage() {
  const [outcome, setOutcome] = useState<Outcome>(null)
  const [code, setCode] = useState('')
  const [scanning, setScanning] = useState(false)
  const [camError, setCamError] = useState<string | null>(null)
  const scanner = useRef<Html5Qrcode | null>(null)
  // The camera fires many frames per second; don't submit the same code twice in a row.
  const lastScan = useRef<{ code: string; at: number }>({ code: '', at: 0 })
  const busy = useRef(false)

  const verify = async (value: string) => {
    const trimmed = value.trim()
    if (!trimmed || busy.current) return
    busy.current = true
    try {
      setOutcome({ ok: true, result: await api.checkIn(trimmed) })
    } catch (e) {
      let message = e instanceof Error ? e.message : 'Check-in failed'
      if (e instanceof ApiError && e.code === 'ALREADY_CHECKED_IN') {
        const d = e.details as { checkedInAt?: string; attendeeName?: string; seats?: string[] }
        message = `Already used! ${d.attendeeName ?? 'This ticket'} (${d.seats?.join(', ')}) entered at ${d.checkedInAt ? time(d.checkedInAt) : 'an earlier time'}.`
      }
      setOutcome({ ok: false, message })
    } finally {
      busy.current = false
    }
  }

  const startCamera = async () => {
    setCamError(null)
    try {
      const s = new Html5Qrcode(SCANNER_ID)
      scanner.current = s
      await s.start({ facingMode: 'environment' }, { fps: 10, qrbox: 220 }, (text) => {
        const now = Date.now()
        if (text === lastScan.current.code && now - lastScan.current.at < 4000) return
        lastScan.current = { code: text, at: now }
        void verify(text)
      }, () => {})
      setScanning(true)
    } catch {
      setCamError('Could not open the camera. Allow camera access, or paste the ticket code below.')
    }
  }

  const stopCamera = async () => {
    try { await scanner.current?.stop() } catch { /* already stopped */ }
    scanner.current = null
    setScanning(false)
  }

  useEffect(() => () => { void scanner.current?.stop().catch(() => {}) }, [])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    void verify(code)
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageTitle title="Gate check-in" subtitle="Scan a ticket QR code. Each ticket works exactly once." />

      <div className="grid gap-6 md:grid-cols-2">
        <div className="rounded-2xl bg-ink-900 p-5 ring-1 ring-ink-800">
          <div id={SCANNER_ID} className="aspect-square w-full overflow-hidden rounded-xl bg-ink-950" />
          {camError && <p className="mt-3 text-sm text-amber-300">{camError}</p>}
          <Button className="mt-4 w-full" variant={scanning ? 'secondary' : 'primary'} onClick={scanning ? stopCamera : startCamera}>
            {scanning ? 'Stop camera' : 'Start camera scanner'}
          </Button>
          <form onSubmit={submit} className="mt-4 flex gap-2">
            <input
              value={code} onChange={(e) => setCode(e.target.value)} placeholder="…or paste ticket code (SL1.…)"
              aria-label="Ticket code"
              className="min-w-0 flex-1 rounded-lg bg-ink-950 px-3 py-2 text-sm ring-1 ring-ink-700 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
            <Button type="submit" variant="secondary">Verify</Button>
          </form>
        </div>

        <div aria-live="assertive">
          {!outcome ? (
            <div className="grid h-full min-h-48 place-items-center rounded-2xl border-2 border-dashed border-ink-700 p-6 text-center text-zinc-500">
              Waiting for a ticket…
            </div>
          ) : outcome.ok ? (
            <div className="h-full rounded-2xl bg-emerald-500/15 p-6 ring-2 ring-emerald-500">
              <p className="text-5xl">✅</p>
              <p className="mt-3 text-2xl font-extrabold text-emerald-300">Welcome in!</p>
              <p className="mt-2 text-lg font-semibold">{outcome.result.attendeeName}</p>
              <p className="text-sm text-zinc-300">{outcome.result.eventTitle}</p>
              <p className="mt-4 text-3xl font-bold tracking-wide">{outcome.result.seats.join(' · ')}</p>
              <p className="mt-3 text-xs text-zinc-400">Checked in at {time(outcome.result.checkedInAt)}</p>
            </div>
          ) : (
            <div className="h-full rounded-2xl bg-red-500/15 p-6 ring-2 ring-red-500">
              <p className="text-5xl">⛔</p>
              <p className="mt-3 text-2xl font-extrabold text-red-300">Do not admit</p>
              <p className="mt-2 text-zinc-200">{outcome.message}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
