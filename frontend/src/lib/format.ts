const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })

/** Prices travel as integer paise; format as rupees. */
export const money = (paise: number) => inr.format(paise / 100)

export const eventDate = (iso: string) =>
  new Date(iso).toLocaleString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

export const time = (iso: string) =>
  new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })

/** A stable, pleasant gradient per event so cards look designed even without a poster image. */
export function posterGradient(id: number) {
  const palettes = [
    ['#f43f5e', '#7c3aed'],
    ['#f59e0b', '#db2777'],
    ['#06b6d4', '#6366f1'],
    ['#10b981', '#0ea5e9'],
    ['#8b5cf6', '#ec4899'],
    ['#ef4444', '#f97316'],
  ]
  const [a, b] = palettes[id % palettes.length]
  return `linear-gradient(135deg, ${a}, ${b})`
}

export function newIdempotencyKey() {
  return crypto.randomUUID()
}
