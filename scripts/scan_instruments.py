import glob
import os
import csv

def scan_csv_instruments():
    instruments = {}
    csv_files = glob.glob('docs/csv/**/*.csv', recursive=True) + glob.glob('docs/**/*.csv', recursive=True)
    for path in csv_files:
        if not os.path.isfile(path):
            continue
        with open(path, 'r', encoding='utf-8', errors='ignore') as f:
            reader = csv.reader(f)
            header = None
            for row in reader:
                if not row:
                    continue
                if header is None:
                    header = [c.strip().lower() for c in row]
                    continue
                
                # Check Freetrade format
                if len(row) >= 11 and ('freetrade' in path.lower() or 'activity-feed' in path.lower()):
                    title = row[0].strip() if len(row) > 0 else ''
                    ticker = row[6].strip() if len(row) > 6 else ''
                    isin = row[7].strip() if len(row) > 7 else ''
                    price = row[8].strip() if len(row) > 8 else ''
                    curr = row[3].strip() if len(row) > 3 else ''
                    if ticker or isin:
                        key = ticker if ticker else isin
                        instruments[key] = {'title': title, 'ticker': ticker, 'isin': isin, 'price': price, 'currency': curr, 'file': path}

                # Check Trading212 format
                elif len(row) >= 10 and 'trading212' in path.lower():
                    isin = row[2].strip() if len(row) > 2 else ''
                    ticker = row[3].strip() if len(row) > 3 else ''
                    name = row[4].strip() if len(row) > 4 else ''
                    price = row[8].strip() if len(row) > 8 else ''
                    curr = row[9].strip() if len(row) > 9 else ''
                    if ticker or isin:
                        key = ticker if ticker else isin
                        instruments[key] = {'title': name, 'ticker': ticker, 'isin': isin, 'price': price, 'currency': curr, 'file': path}

    print(f"Total unique instruments found in CSVs: {len(instruments)}")
    for k, v in sorted(instruments.items()):
        print(f"Ticker: {v['ticker']:<8} ISIN: {v['isin']:<14} Curr: {v['currency']:<4} Price: {v['price']:<10} Name: {v['title']}")

if __name__ == '__main__':
    scan_csv_instruments()
