import { useEffect, useState } from 'react'

/** Live mm:ss countdown to `until`. Calls onExpire once when it hits zero. */
export function Countdown({ until, onExpire }: { until: string; onExpire: () => void }) {
  const target = new Date(until).getTime()
  const [left, setLeft] = useState(() => Math.max(0, target - Date.now()))

  useEffect(() => {
    let fired = false
    const tick = () => {
      const ms = Math.max(0, target - Date.now())
      setLeft(ms)
      if (ms === 0 && !fired) {
        fired = true
        onExpire()
      }
    }
    tick()
    const id = setInterval(tick, 250)
    return () => clearInterval(id)
  }, [target, onExpire])

  const secs = Math.ceil(left / 1000)
  const urgent = secs <= 60
  return (
    <span className={`font-mono text-lg font-bold tabular-nums ${urgent ? 'text-red-400' : 'text-amber-300'}`} aria-live="polite">
      {String(Math.floor(secs / 60)).padStart(2, '0')}:{String(secs % 60).padStart(2, '0')}
    </span>
  )
}
