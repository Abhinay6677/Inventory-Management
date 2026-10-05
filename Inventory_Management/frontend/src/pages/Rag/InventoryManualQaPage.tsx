import { useMemo, useState } from 'react'
import { FiLoader, FiSend } from 'react-icons/fi'
import toast from 'react-hot-toast'
import { PageHeader } from '../../components/common/PageHeader'
import { ragService } from '../../services/ragService'

type QaItem = {
  question: string
  answer: string
  sourceCount?: number
}

export function InventoryManualQaPage() {
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [history, setHistory] = useState<QaItem[]>([])

  const canSubmit = useMemo(() => question.trim().length > 0 && !loading, [question, loading])
  const displayHistory = useMemo(() => [...history].reverse(), [history])

  async function submitQuestion(trimmed: string) {
    const pending: QaItem = { question: trimmed, answer: '' }
    setHistory((prev) => [...prev, pending])
    setQuestion('')
    setLoading(true)

    try {
      const response = await ragService.ask({ question: trimmed })
      setHistory((prev) => {
        if (!prev.length) {
          return [{ question: trimmed, answer: response.answer, sourceCount: response.sourceCount }]
        }
        const next = [...prev]
        const last = next[next.length - 1]
        next[next.length - 1] = {
          ...last,
          answer: response.answer,
          sourceCount: response.sourceCount,
        }
        return next
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to fetch RAG answer.'
      toast.error(message)
      setHistory((prev) => {
        if (!prev.length) {
          return [
            {
              question: trimmed,
              answer: 'Unable to fetch an answer right now. Please retry in a few seconds.',
              sourceCount: 0,
            },
          ]
        }
        const next = [...prev]
        const last = next[next.length - 1]
        next[next.length - 1] = {
          ...last,
          answer: 'Unable to fetch an answer right now. Please retry in a few seconds.',
          sourceCount: 0,
        }
        return next
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="rag-page">
      <PageHeader title="Live Inventory Chat" description="Ask inventory questions and get real-time answers from the live database." />

      <div className="page-card mb-3">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            const trimmed = question.trim()
            if (!trimmed || loading) {
              return
            }
            void submitQuestion(trimmed)
          }}
          className="d-flex gap-2 flex-column flex-md-row"
        >
          <input
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Ask about stock, suppliers, orders, or dashboard metrics"
            className="form-control"
            maxLength={500}
          />
          <button className="btn btn-primary rag-ask-btn" type="submit" disabled={!canSubmit}>
            {loading ? <FiLoader className="spin" /> : <FiSend />}
            <span>{loading ? 'Asking...' : 'Ask'}</span>
          </button>
        </form>
      </div>

      <div className="page-card rag-chat">
        {displayHistory.length === 0 && (
          <p className="text-muted mb-0">No conversation yet. Ask your first question to begin.</p>
        )}

        {displayHistory.map((item, idx) => (
          <div key={`${item.question}-${idx}`} className="rag-chat-item rag-qa-item">
            <div className="rag-chat-meta">
              <strong>Question</strong>
            </div>
            <p className="mb-2">{item.question}</p>
            <div className="rag-chat-meta">
              <strong>Answer</strong>
              {typeof item.sourceCount === 'number' && <span className="badge text-bg-light">sources: {item.sourceCount}</span>}
            </div>
            <p className="mb-0">{item.answer || 'Fetching answer...'}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
