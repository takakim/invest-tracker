import { describe, expect, it } from 'vitest';
import type { CostBasisMethod, ReturnMethod } from './api';

describe('frontend API types', () => {
  it('keeps calculation methods explicit', () => {
    const costBasis: CostBasisMethod = 'FIFO';
    const returns: ReturnMethod = 'XIRR';
    expect(costBasis).toBe('FIFO');
    expect(returns).toBe('XIRR');
  });
});
