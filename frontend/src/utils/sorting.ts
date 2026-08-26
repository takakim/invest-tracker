export type Order = 'asc' | 'desc';

export function getComparator<T>(order: Order, orderBy: keyof T | string): (a: T, b: T) => number {
  return (a: T, b: T) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const aVal = (a as any)[orderBy];
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const bVal = (b as any)[orderBy];

    if (aVal === null || aVal === undefined) return 1;
    if (bVal === null || bVal === undefined) return -1;

    // Numeric comparison
    const aNum = typeof aVal === 'number' ? aVal : Number(aVal);
    const bNum = typeof bVal === 'number' ? bVal : Number(bVal);
    if (!isNaN(aNum) && !isNaN(bNum)) {
      return order === 'asc' ? aNum - bNum : bNum - aNum;
    }

    // String comparison
    const aStr = String(aVal).toLowerCase();
    const bStr = String(bVal).toLowerCase();
    if (aStr < bStr) {
      return order === 'asc' ? -1 : 1;
    }
    if (aStr > bStr) {
      return order === 'asc' ? 1 : -1;
    }
    return 0;
  };
}

export function sortRows<T>(rows: T[], order: Order, orderBy: keyof T | string): T[] {
  if (!orderBy) return rows;
  const comparator = getComparator<T>(order, orderBy);
  return [...rows].sort(comparator);
}
