import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import App from './App';

const QUOTE = { quoteId: 'Q-123', commissionRate: 0.006, totalCommission: 3000 };

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

async function fillAndSubmit(amount = '500000', term = '360', band = 'MEDIUM') {
  const user = userEvent.setup();
  render(<App />);
  await user.type(screen.getByLabelText('Loan amount ($)'), amount);
  await user.type(screen.getByLabelText('Loan term (months)'), term);
  await user.selectOptions(screen.getByLabelText('Risk band'), band);
  await user.click(screen.getByRole('button', { name: 'Generate Quote' }));
  return user;
}

describe('App', () => {
  it('shows the quote returned by the service', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, QUOTE));

    await fillAndSubmit();

    expect(await screen.findByText('Q-123')).toBeInTheDocument();
    expect(screen.getByText('0.60%')).toBeInTheDocument();
    expect(screen.getByText('$3,000.00')).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/commission-quotes');
    expect(JSON.parse(init!.body as string)).toEqual({ loanAmount: 500000, loanTermInMonths: 360, riskBand: 'MEDIUM' });
  });

  it('disables the button while the quote is being generated', async () => {
    let respond!: (r: Response) => void;
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise((resolve) => (respond = resolve)));

    await fillAndSubmit();

    expect(screen.getByRole('button', { name: 'Generating…' })).toBeDisabled();
    respond(jsonResponse(200, QUOTE));
    expect(await screen.findByRole('button', { name: 'Generate Quote' })).toBeEnabled();
  });

  it('offers to try again when the error is temporary, and succeeds on retry', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(504, {
        detail: 'The quote service took too long to respond. Please try again.',
        retryable: true,
        correlationId: 'abc-123',
      }))
      .mockResolvedValueOnce(jsonResponse(200, QUOTE));

    const user = await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('took too long');
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Q-123')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a support reference and no retry when the error is not temporary', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(502, {
      detail: "We couldn't get a quote. Please contact support and quote the reference below.",
      retryable: false,
      correlationId: 'abc-123',
    }));

    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('Reference: abc-123');
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('shows field errors returned by the service next to the field', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(400, {
      detail: 'Please correct the highlighted fields.',
      errors: { loanTermInMonths: 'must be at most 360 months' },
    }));

    await fillAndSubmit();

    expect(await screen.findByText('Loan term must be at most 360 months')).toBeInTheDocument();
  });

  it('lets the browser reject out-of-range values before anything is sent', async () => {
    const user = userEvent.setup();
    render(<App />);
    const amount = screen.getByLabelText('Loan amount ($)');
    const term = screen.getByLabelText('Loan term (months)');

    await user.type(amount, '999.99');
    await user.type(term, '361');
    expect(amount).toBeInvalid();
    expect(term).toBeInvalid();

    await user.clear(amount);
    await user.type(amount, '1000.123');
    expect(amount).toBeInvalid(); // more than 2 decimal places

    await user.clear(amount);
    await user.clear(term);
    await user.type(amount, '1000');
    await user.type(term, '12');
    expect(amount).toBeValid();
    expect(term).toBeValid();
  });
});
