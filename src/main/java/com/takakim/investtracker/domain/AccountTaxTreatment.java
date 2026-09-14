package com.takakim.investtracker.domain;

/**
 * Categorizes an investment account by its fiscal/tax treatment.
 */
public enum AccountTaxTreatment {
    /**
     * Standard taxable account (e.g. General Investment Account / GIA, taxable brokerage).
     * Subject to Capital Gains Tax (CGT) and dividend income tax.
     */
    TAXABLE,

    /**
     * Completely tax-sheltered account (e.g. UK Stocks & Shares ISA, US Roth IRA, Canadian TFSA).
     * Exempt from Capital Gains Tax and dividend income taxes.
     */
    TAX_EXEMPT,

    /**
     * Tax-deferred retirement wrapper (e.g. UK SIPP, US 401(k), Traditional IRA).
     * Gains and dividends accumulate tax-free inside the wrapper; taxed upon drawdown/pension phase.
     */
    TAX_DEFERRED
}
