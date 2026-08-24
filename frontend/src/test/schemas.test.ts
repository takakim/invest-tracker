import { describe, expect, it } from 'vitest';
import { portfolioSchema, accountSchema, instrumentSchema, positionSchema, transactionSchema } from '../forms/schemas';

describe('portfolioSchema', () => {
  it('validates a correct portfolio input and normalizes currency', () => {
    const result = portfolioSchema.safeParse({
      name: '  Tech & Growth  ',
      baseCurrency: 'gbp',
      costBasisMethod: 'FIFO',
      returnMethod: 'XIRR',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe('Tech & Growth');
      expect(result.data.baseCurrency).toBe('GBP');
      expect(result.data.costBasisMethod).toBe('FIFO');
      expect(result.data.returnMethod).toBe('XIRR');
    }
  });

  it('rejects invalid names', () => {
    expect(
      portfolioSchema.safeParse({
        name: '',
        baseCurrency: 'USD',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);

    expect(
      portfolioSchema.safeParse({
        name: '   ',
        baseCurrency: 'USD',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);

    expect(
      portfolioSchema.safeParse({
        name: 'a'.repeat(121),
        baseCurrency: 'USD',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);
  });

  it('rejects invalid currency codes', () => {
    expect(
      portfolioSchema.safeParse({
        name: 'Portfolio',
        baseCurrency: 'US',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);

    expect(
      portfolioSchema.safeParse({
        name: 'Portfolio',
        baseCurrency: 'USDD',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);

    expect(
      portfolioSchema.safeParse({
        name: 'Portfolio',
        baseCurrency: '123',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);
  });

  it('rejects invalid methods', () => {
    expect(
      portfolioSchema.safeParse({
        name: 'Portfolio',
        baseCurrency: 'USD',
        costBasisMethod: 'INVALID',
        returnMethod: 'XIRR',
      }).success,
    ).toBe(false);
  });
});

describe('accountSchema', () => {
  it('validates a correct account input', () => {
    const result = accountSchema.safeParse({
      name: '  ISA Account  ',
      brokerName: '  Interactive Brokers  ',
      accountCurrency: 'usd',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe('ISA Account');
      expect(result.data.brokerName).toBe('Interactive Brokers');
      expect(result.data.accountCurrency).toBe('USD');
    }
  });

  it('rejects empty fields and long names', () => {
    expect(
      accountSchema.safeParse({
        name: '',
        brokerName: 'Broker',
        accountCurrency: 'GBP',
      }).success,
    ).toBe(false);

    expect(
      accountSchema.safeParse({
        name: 'Name',
        brokerName: '',
        accountCurrency: 'GBP',
      }).success,
    ).toBe(false);

    expect(
      accountSchema.safeParse({
        name: 'a'.repeat(121),
        brokerName: 'Broker',
        accountCurrency: 'GBP',
      }).success,
    ).toBe(false);
  });
});

describe('instrumentSchema', () => {
  it('validates and normalizes optional fields', () => {
    const result = instrumentSchema.safeParse({
      name: '  Apple Inc  ',
      assetClass: 'STOCK',
      ticker: ' AAPL ',
      isin: ' US0378331005 ',
      exchange: ' NASDAQ ',
      currency: 'usd',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe('Apple Inc');
      expect(result.data.assetClass).toBe('STOCK');
      expect(result.data.ticker).toBe('AAPL');
      expect(result.data.isin).toBe('US0378331005');
      expect(result.data.exchange).toBe('NASDAQ');
      expect(result.data.currency).toBe('USD');
    }
  });

  it('transforms empty strings to undefined for optional fields', () => {
    const result = instrumentSchema.safeParse({
      name: 'Cash USD',
      assetClass: 'CASH',
      ticker: '',
      isin: '   ',
      exchange: '',
      currency: 'USD',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.ticker).toBeUndefined();
      expect(result.data.isin).toBeUndefined();
      expect(result.data.exchange).toBeUndefined();
    }
  });

  it('rejects invalid names or asset classes', () => {
    expect(
      instrumentSchema.safeParse({
        name: '',
        assetClass: 'STOCK',
        currency: 'USD',
      }).success,
    ).toBe(false);

    expect(
      instrumentSchema.safeParse({
        name: 'Test',
        assetClass: 'UNKNOWN_CLASS',
        currency: 'USD',
      }).success,
    ).toBe(false);

    expect(
      instrumentSchema.safeParse({
        name: 'a'.repeat(161),
        assetClass: 'STOCK',
        currency: 'USD',
      }).success,
    ).toBe(false);
  });
});

describe('positionSchema', () => {
  it('validates valid position inputs', () => {
    const result = positionSchema.safeParse({
      instrumentId: 'inst-123',
      quantity: 50.5,
      costBasisAmount: 1500,
      costBasisCurrency: 'usd',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.instrumentId).toBe('inst-123');
      expect(result.data.quantity).toBe(50.5);
      expect(result.data.costBasisAmount).toBe(1500);
      expect(result.data.costBasisCurrency).toBe('USD');
    }
  });

  it('rejects negative quantity or missing instrument', () => {
    expect(
      positionSchema.safeParse({
        instrumentId: '',
        quantity: 10,
      }).success,
    ).toBe(false);

    expect(
      positionSchema.safeParse({
        instrumentId: 'inst-123',
        quantity: -1,
      }).success,
    ).toBe(false);
  });
});

describe('transactionSchema', () => {
  it('validates a correct BUY transaction', () => {
    const result = transactionSchema.safeParse({
      type: 'BUY',
      tradeDate: '2026-08-24T12:00',
      instrumentId: 'inst-123',
      quantity: 10,
      price: 150,
      grossAmount: 1500,
      feeAmount: 5,
      currency: 'usd',
      notes: 'Test buy',
    });

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.type).toBe('BUY');
      expect(result.data.grossAmount).toBe(1500);
      expect(result.data.currency).toBe('USD');
      expect(result.data.notes).toBe('Test buy');
    }
  });

  it('rejects invalid transaction type or negative gross amount', () => {
    expect(
      transactionSchema.safeParse({
        type: 'INVALID_TYPE',
        tradeDate: '2026-08-24T12:00',
        grossAmount: 100,
        currency: 'USD',
      }).success,
    ).toBe(false);

    expect(
      transactionSchema.safeParse({
        type: 'BUY',
        tradeDate: '2026-08-24T12:00',
        grossAmount: -50,
        currency: 'USD',
      }).success,
    ).toBe(false);
  });
});
