import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import type { SeatUpdate } from '../api/types'

function socketUrl() {
  const configured = import.meta.env.VITE_WS_URL
  if (configured) return configured
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}/ws`
}

/**
 * Subscribes to live seat changes for one event. Reconnects automatically, and calls
 * `onReconnect` so the page can refetch anything it missed while disconnected.
 */
export function useSeatSocket(eventId: number, onUpdate: (u: SeatUpdate[]) => void, onReconnect: () => void) {
  const [connected, setConnected] = useState(false)
  const handlers = useRef({ onUpdate, onReconnect })
  handlers.current = { onUpdate, onReconnect }

  useEffect(() => {
    let firstConnect = true
    const client = new Client({
      brokerURL: socketUrl(),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        if (!firstConnect) handlers.current.onReconnect()
        firstConnect = false
        client.subscribe(`/topic/events/${eventId}/seats`, (msg) => {
          handlers.current.onUpdate(JSON.parse(msg.body) as SeatUpdate[])
        })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    return () => { void client.deactivate() }
  }, [eventId])

  return connected
}
