import { useState, type FormEvent } from 'react';
import { QuoteError, requestQuote, type Quote, type QuoteRequest, type RiskBand } from './api/quoteApi';

const aud = new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' });
const percent = new Intl.NumberFormat('en-AU', { style: 'percent', minimumFractionDigits: 2 });

export default function App() {
  const [loanAmount, setLoanAmount] = useState('');
  const [loanTermInMonths, setLoanTermInMonths] = useState('');
  const [riskBand, setRiskBand] = useState<RiskBand>('LOW');

  const [loading, setLoading] = useState(false);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [error, setError] = useState<QuoteError | null>(null);

  async function generateQuote() {
    const request: QuoteRequest = {
      loanAmount: Number(loanAmount),
      loanTermInMonths: Number(loanTermInMonths),
      riskBand,
    };
    setLoading(true);
    setQuote(null);
    setError(null);
    try {
      setQuote(await requestQuote(request));
    } catch (e) {
      setError(e instanceof QuoteError ? e : new QuoteError('Something went wrong. Please try again.', true));
    } finally {
      setLoading(false);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault(); // the browser has already checked required/min/max/step
    generateQuote();
  }

  return (
    <main>
      <h1>Commission quote</h1>

      <form onSubmit={handleSubmit}>
        <label htmlFor="loanAmount">Loan amount ($)</label>
        <input
          id="loanAmount"
          type="number"
          required
          min={1000}
          max={5000000}
          step={0.01}
          value={loanAmount}
          onChange={(e) => setLoanAmount(e.target.value)}
        />
        {error?.fieldErrors.loanAmount && <p className="field-error">Loan amount {error.fieldErrors.loanAmount}</p>}

        <label htmlFor="loanTermInMonths">Loan term (months)</label>
        <input
          id="loanTermInMonths"
          type="number"
          required
          min={1}
          max={360}
          step={1}
          value={loanTermInMonths}
          onChange={(e) => setLoanTermInMonths(e.target.value)}
        />
        {error?.fieldErrors.loanTermInMonths && (
          <p className="field-error">Loan term {error.fieldErrors.loanTermInMonths}</p>
        )}

        <label htmlFor="riskBand">Risk band</label>
        <select id="riskBand" value={riskBand} onChange={(e) => setRiskBand(e.target.value as RiskBand)}>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
        </select>

        <button type="submit" disabled={loading}>
          {loading ? 'Generating…' : 'Generate Quote'}
        </button>
      </form>

      {error && (
        <div className="error" role="alert">
          <p>{error.message}</p>
          {error.reference && <p>Reference: {error.reference}</p>}
          {error.retryable && (
            <button type="button" onClick={generateQuote} disabled={loading}>
              Try again
            </button>
          )}
        </div>
      )}

      {quote && (
        <section className="result" aria-live="polite">
          <h2>Quote</h2>
          <dl>
            <dt>Quote ID</dt>
            <dd>{quote.quoteId}</dd>
            <dt>Commission rate</dt>
            <dd>{percent.format(quote.commissionRate)}</dd>
            <dt>Total commission</dt>
            <dd>{aud.format(quote.totalCommission)}</dd>
          </dl>
        </section>
      )}
    </main>
  );
}
