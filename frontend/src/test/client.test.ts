import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request, portfolioApi, accountApi, instrumentApi } from '../api';

describe('API Client & Error Handling', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('handles successful JSON response', async () => {
    const mockData = { id: '123', name: 'Test Portfolio' };
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => mockData,
    } as Response);

    const result = await request<{ id: string; name: string }>('/api/v1/portfolios/123');
    expect(result).toEqual(mockData);
  });

  it('handles 204 No Content response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 204,
    } as Response);

    const result = await request<void>('/api/v1/portfolios/123', { method: 'DELETE' });
    expect(result).toBeUndefined();
  });

  it('parses RFC 9457 Problem Details on HTTP errors', async () => {
    const problemPayload = {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'Validation failed',
      errors: ['name: must not be blank', 'baseCurrency: invalid'],
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => problemPayload,
    } as Response);

    await expect(request('/api/v1/portfolios', { method: 'POST', body: '{}' })).rejects.toThrow(
      'Validation failed',
    );

    try {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        json: async () => problemPayload,
      } as Response);

      await request('/api/v1/portfolios', { method: 'POST', body: '{}' });
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiErr = err as ApiError;
      expect(apiErr.problem.title).toBe('Bad Request');
      expect(apiErr.problem.status).toBe(400);
      expect(apiErr.problem.errors).toEqual([
        'name: must not be blank',
        'baseCurrency: invalid',
      ]);
    }
  });

  it('handles non-JSON error response gracefully', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      json: async () => {
        throw new Error('Not JSON');
      },
    } as unknown as Response);

    await expect(request('/api/v1/portfolios')).rejects.toThrow('Bad Gateway');
  });

  it('portfolioApi, accountApi, and instrumentApi call expected endpoints', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
    } as Response);

    await portfolioApi.list();
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/portfolios', expect.anything());

    await accountApi.list('p-1');
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/portfolios/p-1/accounts', expect.anything());

    await instrumentApi.list();
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/instruments', expect.anything());
  });
});
