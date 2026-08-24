import type { ProblemDetail } from '../types';

export class ApiError extends Error {
  public readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || 'Request failed');
    this.name = 'ApiError';
    this.problem = problem;
  }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Content-Type') && init?.body) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(url, {
    ...init,
    headers,
  });

  if (!response.ok) {
    let problem: ProblemDetail;
    try {
      const data = await response.json();
      problem = {
        title: data.title || response.statusText || 'Error',
        status: data.status || response.status,
        detail: data.detail || response.statusText || 'An unexpected error occurred',
        instance: data.instance,
        errors: Array.isArray(data.errors) ? data.errors : undefined,
      };
    } catch {
      problem = {
        title: response.statusText || 'Error',
        status: response.status,
        detail: response.statusText || 'An unexpected error occurred',
      };
    }
    throw new ApiError(problem);
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return response.json();
}
