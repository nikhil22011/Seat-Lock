import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from '../api/client'
import type { Role, User } from '../api/types'

interface AuthState {
  user: User | null
  /** True until we've checked whether a saved token is still valid. */
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (name: string, email: string, password: string, role: Role) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)

  const logout = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (!tokenStore.get()) return
    api.me()
      .then(setUser)
      .catch(logout)
      .finally(() => setLoading(false))
  }, [logout])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    logout,
    login: async (email, password) => {
      const res = await api.login(email, password)
      tokenStore.set(res.token)
      setUser(res.user)
      return res.user
    },
    register: async (name, email, password, role) => {
      const res = await api.register(name, email, password, role)
      tokenStore.set(res.token)
      setUser(res.user)
      return res.user
    },
  }), [user, loading, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
