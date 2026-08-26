export const exportApi = {
  getPositionsCsvUrl: (portfolioId: string): string =>
    `/api/v1/portfolios/${portfolioId}/export/positions.csv`,

  getTransactionsCsvUrl: (portfolioId: string): string =>
    `/api/v1/portfolios/${portfolioId}/export/transactions.csv`,

  downloadPositionsCsv: async (portfolioId: string): Promise<void> => {
    const response = await fetch(`/api/v1/portfolios/${portfolioId}/export/positions.csv`);
    if (!response.ok) throw new Error('Failed to download positions CSV');
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `positions-${portfolioId}.csv`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  },

  downloadTransactionsCsv: async (portfolioId: string): Promise<void> => {
    const response = await fetch(`/api/v1/portfolios/${portfolioId}/export/transactions.csv`);
    if (!response.ok) throw new Error('Failed to download transactions CSV');
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `transactions-${portfolioId}.csv`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  },
};
