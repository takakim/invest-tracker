import React from 'react';
import { TableHead, TableRow, TableCell, TableSortLabel, Box } from '@mui/material';
import { visuallyHidden } from '@mui/utils';
import { Order } from '../utils/sorting';

export interface HeadCell<T> {
  id: keyof T | string;
  label: string;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  minWidth?: number;
}

interface SortableTableHeadProps<T> {
  headCells: HeadCell<T>[];
  order: Order;
  orderBy: string;
  onRequestSort: (property: string) => void;
}

export function SortableTableHead<T>({
  headCells,
  order,
  orderBy,
  onRequestSort,
}: SortableTableHeadProps<T>) {
  const createSortHandler = (property: string) => () => {
    onRequestSort(property);
  };

  return (
    <TableHead>
      <TableRow>
        {headCells.map((headCell) => (
          <TableCell
            key={String(headCell.id)}
            align={headCell.align || 'left'}
            sortDirection={orderBy === headCell.id ? order : false}
            sx={{ minWidth: headCell.minWidth, fontWeight: 700 }}
          >
            {headCell.sortable !== false ? (
              <TableSortLabel
                active={orderBy === headCell.id}
                direction={orderBy === headCell.id ? order : 'asc'}
                onClick={createSortHandler(String(headCell.id))}
              >
                {headCell.label}
                {orderBy === headCell.id ? (
                  <Box component="span" sx={visuallyHidden}>
                    {order === 'desc' ? 'sorted descending' : 'sorted ascending'}
                  </Box>
                ) : null}
              </TableSortLabel>
            ) : (
              headCell.label
            )}
          </TableCell>
        ))}
      </TableRow>
    </TableHead>
  );
}
