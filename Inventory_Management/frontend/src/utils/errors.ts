import type { AxiosError } from 'axios'
import type { ApiErrorBody } from '../api/contracts'

export function getErrorMessage(error: unknown) {
  const axiosError = error as AxiosError<ApiErrorBody>
  const responseError = axiosError?.response?.data?.error
  if (responseError) return responseError

  if (axiosError?.message) return axiosError.message
  return 'Something went wrong'
}
