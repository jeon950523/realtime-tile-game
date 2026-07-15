export interface HealthResponse {
  application: string
  status: 'UP'
  database: 'UP'
}

export interface RealtimeHealthMessage {
  type: 'SYSTEM_HEALTH'
  status: 'UP'
  timestamp: string
}
