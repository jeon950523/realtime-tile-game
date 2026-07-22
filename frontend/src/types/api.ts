export interface ApiResponse<T> {
  success: true
  data: T
  timestamp: string
}

export interface ApiErrorResponse {
  success: false
  error: {
    code: string
    message: string
    fieldErrors: Array<{
      field: string
      message: string
    }>
  }
  timestamp: string
}
