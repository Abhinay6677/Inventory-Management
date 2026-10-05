import type { RagAskRequest, RagAskResponse } from '../api/contracts'

const defaultRagApiUrl = import.meta.env.VITE_PHASE2_RAG_API_URL ?? '/api/v1/rag/ask'

export const ragService = {
  ask: async (request: RagAskRequest): Promise<RagAskResponse> => {
    const response = await fetch(defaultRagApiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      const body = await response.text()
      throw new Error(body || `RAG request failed with status ${response.status}`)
    }

    return (await response.json()) as RagAskResponse
  },
}
