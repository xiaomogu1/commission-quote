export type RiskBand = 'LOW' | 'MEDIUM' | 'HIGH';

export interface QuoteRequest {
  loanAmount: number;
  loanTermInMonths: number;
  riskBand: RiskBand;
}

export interface Quote {
  quoteId: string;
  commissionRate: number; // fraction: 0.006 means 0.60%
  totalCommission: number;
}

/** A failed quote request, with what the UI needs to show. */
export class QuoteError extends Error {
  constructor(
    message: string,
    readonly retryable: boolean,
    readonly fieldErrors: Record<string, string> = {},
    readonly reference?: string,
  ) {
    super(message);
  }
}

/**
 * Calls our lending quote service, never the vendor directly:
 * the vendor's api-key only exists on the server.
 */
export async function requestQuote(request: QuoteRequest): Promise<Quote> {
  let response: Response;
  try {
    response = await fetch('/api/commission-quotes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    throw new QuoteError("Couldn't reach the quote service. Please try again.", true);
  }

  if (response.ok) {
    return response.json();
  }

  // Error body from the service: { detail, retryable, errors, correlationId }
  const problem = await response.json().catch(() => ({}));
  throw new QuoteError(
    problem.detail ?? 'Something went wrong. Please try again.',
    problem.retryable ?? false,
    problem.errors ?? {},
    problem.correlationId,
  );
}
