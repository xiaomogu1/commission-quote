import { describe, expect, it, vi } from 'vitest';
import { QuoteError, requestQuote } from './quoteApi';

const REQUEST = { loanAmount: 500000, loanTermInMonths: 360, riskBand: 'MEDIUM' as const };

describe('requestQuote', () => {
  it('posts to our service without any vendor credentials', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ quoteId: 'Q-1', commissionRate: 0.006, totalCommission: 3000 })),
    );

    await requestQuote(REQUEST);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/commission-quotes');
    expect(JSON.stringify(init?.headers)).not.toMatch(/api-key/i);
  });

  it('turns a network failure into a retryable error', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(requestQuote(REQUEST)).rejects.toMatchObject({ retryable: true });
  });

  it('copes with an error response that is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }));

    const error = await requestQuote(REQUEST).catch((e) => e);

    expect(error).toBeInstanceOf(QuoteError);
    expect(error.message).toBe('Something went wrong. Please try again.');
  });
});
