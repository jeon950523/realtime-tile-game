import axios from 'axios'

import type { ApiErrorResponse } from '@/types/api'

export function getApiErrorCode(error: unknown): string | null {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) {
    return null
  }
  return error.response?.data?.error?.code ?? null
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) {
    return fallback
  }
  return error.response?.data?.error?.message ?? fallback
}
