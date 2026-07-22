export interface RuntimeLocation {
  protocol: string
  host: string
}

function explicitEndpoint(value: string | undefined): string | null {
  if (value === undefined || value.trim() === '') {
    return null
  }
  return value
}

export function resolveApiBaseUrl(
  configuredValue: string | undefined = import.meta.env.VITE_API_BASE_URL,
): string {
  return explicitEndpoint(configuredValue) ?? ''
}

export function resolveWebSocketUrl(
  configuredValue: string | undefined = import.meta.env.VITE_WS_URL,
  location?: RuntimeLocation,
): string {
  const configuredEndpoint = explicitEndpoint(configuredValue)
  if (configuredEndpoint !== null) {
    return configuredEndpoint
  }

  const browserLocation = location ?? currentBrowserLocation()
  const protocol = browserLocation.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${browserLocation.host}/ws`
}

function currentBrowserLocation(): RuntimeLocation {
  if (typeof window === 'undefined') {
    throw new Error('브라우저 Location 없이 Same-origin WebSocket URL을 계산할 수 없습니다.')
  }
  return window.location
}
