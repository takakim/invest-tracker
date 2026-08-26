import { describe, it, expect } from 'vitest';
import { getComparator, sortRows, Order } from '../utils/sorting';

describe('sorting utility', () => {
  interface TestItem {
    id: string;
    name: string;
    value: number;
    optVal?: number | null;
  }

  const items: TestItem[] = [
    { id: '3', name: 'Zebra', value: 10, optVal: 50 },
    { id: '1', name: 'Apple', value: 30, optVal: null },
    { id: '2', name: 'Mango', value: 20, optVal: 100 },
  ];

  it('sorts numeric fields ascending and descending', () => {
    const asc = sortRows(items, 'asc', 'value');
    expect(asc.map((i) => i.id)).toEqual(['3', '2', '1']);

    const desc = sortRows(items, 'desc', 'value');
    expect(desc.map((i) => i.id)).toEqual(['1', '2', '3']);
  });

  it('sorts string fields ascending and descending case-insensitively', () => {
    const asc = sortRows(items, 'asc', 'name');
    expect(asc.map((i) => i.name)).toEqual(['Apple', 'Mango', 'Zebra']);

    const desc = sortRows(items, 'desc', 'name');
    expect(desc.map((i) => i.name)).toEqual(['Zebra', 'Mango', 'Apple']);
  });

  it('handles null or undefined values', () => {
    const asc = sortRows(items, 'asc', 'optVal');
    expect(asc[2].optVal).toBeNull();

    const desc = sortRows(items, 'desc', 'optVal');
    expect(desc[0].optVal).toBe(100);
  });

  it('returns same array if property is empty', () => {
    const same = sortRows(items, 'asc', '');
    expect(same).toEqual(items);
  });

  it('handles equal values gracefully', () => {
    const cmp = getComparator<TestItem>('asc', 'value');
    expect(cmp({ id: '1', name: 'A', value: 10 }, { id: '2', name: 'B', value: 10 })).toBe(0);
  });
});
