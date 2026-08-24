import { z } from 'zod';

const currencyCodeSchema = z
  .string()
  .trim()
  .regex(/^[A-Za-z]{3}$/, 'Currency must be a 3-letter ISO code (e.g. USD, GBP, EUR)')
  .transform((val) => val.toUpperCase());

export const portfolioSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Portfolio name is required')
    .max(120, 'Portfolio name must be at most 120 characters'),
  baseCurrency: currencyCodeSchema,
  costBasisMethod: z.enum(['FIFO', 'LIFO', 'AVERAGE_COST'] as const, {
    error: 'Please select a cost basis method',
  }),
  returnMethod: z.enum(['XIRR', 'TWR', 'MWR'] as const, {
    error: 'Please select a return method',
  }),
});

export type PortfolioFormData = z.input<typeof portfolioSchema>;

export const accountSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Account name is required')
    .max(120, 'Account name must be at most 120 characters'),
  brokerName: z
    .string()
    .trim()
    .min(1, 'Broker / custodian name is required')
    .max(120, 'Broker name must be at most 120 characters'),
  accountCurrency: currencyCodeSchema,
});

export type AccountFormData = z.input<typeof accountSchema>;

export const instrumentSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Instrument name is required')
    .max(160, 'Instrument name must be at most 160 characters'),
  assetClass: z.enum([
    'STOCK',
    'ETF',
    'MUTUAL_FUND',
    'BOND',
    'REIT',
    'CRYPTO',
    'CASH',
    'OTHER',
  ] as const, {
    error: 'Please select an asset class',
  }),
  ticker: z
    .string()
    .trim()
    .max(32, 'Ticker must be at most 32 characters')
    .optional()
    .or(z.literal(''))
    .transform((v) => (v && v.trim() ? v.trim() : undefined)),
  isin: z
    .string()
    .trim()
    .max(12, 'ISIN must be at most 12 characters')
    .optional()
    .or(z.literal(''))
    .transform((v) => (v && v.trim() ? v.trim() : undefined)),
  exchange: z
    .string()
    .trim()
    .max(80, 'Exchange name must be at most 80 characters')
    .optional()
    .or(z.literal(''))
    .transform((v) => (v && v.trim() ? v.trim() : undefined)),
  currency: currencyCodeSchema,
});

export type InstrumentFormData = z.input<typeof instrumentSchema>;
