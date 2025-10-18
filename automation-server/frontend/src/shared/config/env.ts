const DEFAULT_API_BASE_URL = 'http://localhost:8000/api'

export const getEnv = () => ({
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL,
})

export type AppEnv = ReturnType<typeof getEnv>
