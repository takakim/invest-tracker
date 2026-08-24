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

export const positionSchema = z.object({
  instrumentId: z.string().trim().min(1, 'Please select an instrument'),
  quantity: z
    .coerce
    .number({ message: 'Quantity must be a number' })
    .min(0, 'Quantity must be non-negative'),
  costBasisAmount: z
    .union([
      z.coerce.number().min(0, 'Cost basis amount must be non-negative'),
      z.literal(''),
      z.undefined(),
      z.null(),
    ])
    .optional()
    .transform((val) => (val === '' || val === null || val === undefined ? undefined : Number(val))),
  costBasisCurrency: z
    .string()
    .trim()
    .optional()
    .or(z.literal(''))
    .transform((val) => (val && val.trim() ? val.trim().toUpperCase() : undefined)),
});

export type PositionFormData = z.input<typeof positionSchema>;

export const transactionTypeEnum = z.enum([
  'BUY',
  'SELL',
  'DIVIDEND',
  'FEE',
  'DEPOSIT',
  'WITHDRAWAL',
  'INTEREST',
  'STOCK_SPLIT',
  'REVERSE_STOCK_SPLIT',
  'TRANSFER',
]);

export const transactionSchema = z.object({
  type: transactionTypeEnum,
  tradeDate: z.string().trim().min(1, 'Trade date is required'),
  instrumentId: z
    .string()
    .trim()
    .optional()
    .or(z.literal(''))
    .transform((v) => (v && v.trim() ? v.trim() : undefined)),
  quantity: z
    .union([z.coerce.number().min(0, 'Quantity must be non-negative'), z.literal(''), z.undefined(), z.null()])
    .optional()
    .transform((val) => (val === '' || val === null || val === undefined ? undefined : Number(val))),
  price: z
    .union([z.coerce.number().min(0, 'Price must be non-negative'), z.literal(''), z.undefined(), z.null()])
    .optional()
    .transform((val) => (val === '' || val === null || val === undefined ? undefined : Number(val))),
  grossAmount: z.coerce.number({ message: 'Gross amount is required' }).min(0, 'Gross amount must be non-negative'),
  feeAmount: z
    .union([z.coerce.number().min(0, 'Fee amount must be non-negative'), z.literal(''), z.undefined(), z.null()])
    .optional()
    .transform((val) => (val === '' || val === null || val === undefined ? undefined : Number(val))),
  taxAmount: z
    .union([z.coerce.number().min(0, 'Tax amount must be non-negative'), z.literal(''), z.undefined(), z.null()])
    .optional()
    .transform((val) => (val === '' || val === null || val === undefined ? undefined : Number(val))),
  currency: currencyCodeSchema,
  notes: z
    .string()
    .trim()
    .max(255, 'Notes must be at most 255 characters')
    .optional()
    .or(z.literal(''))
    .transform((v) => (v && v.trim() ? v.trim() : undefined)),
});

export type TransactionFormData = z.input<typeof transactionSchema>;
