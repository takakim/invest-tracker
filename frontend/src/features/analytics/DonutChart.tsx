import React, { useMemo, useState } from 'react';
import { Box, Typography } from '@mui/material';

export interface DonutSliceItem {
  id: string;
  label: string;
  value: number;
  percentage: number; // 0 to 1 (e.g. 0.25 for 25%) or 0 to 100
  color?: string;
}

export interface DonutChartProps {
  items: DonutSliceItem[];
  size?: number;
  centerTitle?: string;
  centerSubtitle?: string;
  currency?: string;
  hoveredId?: string | null;
  onHover?: (item: DonutSliceItem | null) => void;
}

export const DONUT_PALETTE = [
  '#2563eb', // Blue
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#8b5cf6', // Violet
  '#ec4899', // Pink
  '#06b6d4', // Cyan
  '#f97316', // Orange
  '#14b8a6', // Teal
  '#6366f1', // Indigo
  '#84cc16', // Lime
];

function polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180.0;
  return {
    x: centerX + radius * Math.cos(angleInRadians),
    y: centerY + radius * Math.sin(angleInRadians),
  };
}

function describeDonutSlice(
  x: number,
  y: number,
  innerRadius: number,
  outerRadius: number,
  startAngle: number,
  endAngle: number
) {
  const angleDiff = Math.max(0.01, endAngle - startAngle);
  const effectiveEnd = angleDiff >= 359.99 ? startAngle + 359.99 : endAngle;
  const startOuter = polarToCartesian(x, y, outerRadius, startAngle);
  const endOuter = polarToCartesian(x, y, outerRadius, effectiveEnd);
  const startInner = polarToCartesian(x, y, innerRadius, startAngle);
  const endInner = polarToCartesian(x, y, innerRadius, effectiveEnd);

  const largeArcFlag = angleDiff > 180 ? '1' : '0';

  return [
    `M ${startOuter.x.toFixed(2)} ${startOuter.y.toFixed(2)}`,
    `A ${outerRadius} ${outerRadius} 0 ${largeArcFlag} 1 ${endOuter.x.toFixed(2)} ${endOuter.y.toFixed(2)}`,
    `L ${endInner.x.toFixed(2)} ${endInner.y.toFixed(2)}`,
    `A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 0 ${startInner.x.toFixed(2)} ${startInner.y.toFixed(2)}`,
    'Z',
  ].join(' ');
}

export function DonutChart({
  items,
  size = 220,
  centerTitle,
  centerSubtitle,
  currency,
  hoveredId: controlledHoveredId,
  onHover,
}: DonutChartProps) {
  const [internalHoveredId, setInternalHoveredId] = useState<string | null>(null);
  const activeHoveredId = controlledHoveredId !== undefined ? controlledHoveredId : internalHoveredId;

  const center = size / 2;
  const baseOuterRadius = (size / 2) * 0.88;
  const innerRadius = baseOuterRadius * 0.62;

  // Normalized percentages (normalize to 1)
  const normalizedItems = useMemo(() => {
    // If percentages sum close to 100, normalize from 100
    const sumPct = items.reduce((acc, it) => acc + (Number(it.percentage) || 0), 0);
    const isHundredScale = sumPct > 1.5;

    let totalWeight = 0;
    const cleanItems = items.map((it, idx) => {
      let weight = Number(it.percentage) || 0;
      if (isHundredScale) weight = weight / 100;
      totalWeight += weight;
      return {
        ...it,
        normWeight: weight,
        color: it.color || DONUT_PALETTE[idx % DONUT_PALETTE.length],
      };
    });

    if (totalWeight === 0) totalWeight = 1;

    let currentAngle = 0;
    return cleanItems.map((it) => {
      const share = it.normWeight / totalWeight;
      const angleSpan = share * 360;
      const startAngle = currentAngle;
      const endAngle = currentAngle + angleSpan;
      currentAngle = endAngle;

      return {
        ...it,
        share,
        startAngle,
        endAngle,
      };
    });
  }, [items]);

  const activeItem = useMemo(() => {
    return normalizedItems.find((it) => it.id === activeHoveredId) || null;
  }, [normalizedItems, activeHoveredId]);

  const handleMouseEnter = (item: DonutSliceItem) => {
    setInternalHoveredId(item.id);
    if (onHover) onHover(item);
  };

  const handleMouseLeave = () => {
    setInternalHoveredId(null);
    if (onHover) onHover(null);
  };

  if (items.length === 0) {
    return (
      <Box sx={{ width: size, height: size, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Typography variant="caption" color="text.secondary">No allocation data</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ width: size, height: size, position: 'relative', display: 'inline-block' }}>
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        style={{ overflow: 'visible', userSelect: 'none' }}
      >
        <g>
          {normalizedItems.map((slice) => {
            const isHovered = activeHoveredId === slice.id;
            const outerRadius = isHovered ? baseOuterRadius + 4 : baseOuterRadius;
            const pathD = describeDonutSlice(
              center,
              center,
              innerRadius,
              outerRadius,
              slice.startAngle,
              slice.endAngle
            );

            return (
              <path
                key={slice.id}
                d={pathD}
                fill={slice.color}
                opacity={activeHoveredId && !isHovered ? 0.45 : 1}
                stroke="#1e293b"
                strokeWidth={isHovered ? 2.5 : 1.5}
                style={{
                  transition: 'opacity 0.2s ease, transform 0.2s ease',
                  cursor: 'pointer',
                }}
                onMouseEnter={() => handleMouseEnter(slice)}
                onMouseLeave={handleMouseLeave}
              />
            );
          })}
        </g>
      </svg>

      {/* Center Readout Text */}
      <Box
        sx={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          textAlign: 'center',
          pointerEvents: 'none',
          maxWidth: innerRadius * 1.8,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {activeItem ? (
          <>
            <Typography
              variant="caption"
              sx={{
                fontWeight: 700,
                color: activeItem.color,
                display: 'block',
                lineHeight: 1.1,
                fontSize: '0.75rem',
                textTransform: 'uppercase',
                letterSpacing: 0.5,
              }}
              noWrap
            >
              {activeItem.label}
            </Typography>
            <Typography
              variant="body2"
              sx={{ fontWeight: 800, lineHeight: 1.2, mt: 0.25, fontSize: '0.95rem' }}
            >
              {(activeItem.share * 100).toFixed(1)}%
            </Typography>
            {activeItem.value > 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontSize: '0.7rem' }} noWrap>
                {currency ? `${currency} ` : ''}
                {Number(activeItem.value).toLocaleString(undefined, { maximumFractionDigits: 0 })}
              </Typography>
            )}
          </>
        ) : (
          <>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: 'block', fontWeight: 600, fontSize: '0.7rem', textTransform: 'uppercase' }}
            >
              {centerSubtitle || 'Total Assets'}
            </Typography>
            <Typography
              variant="body2"
              sx={{ fontWeight: 800, fontSize: size < 200 ? '0.85rem' : '1rem', lineHeight: 1.2, mt: 0.25 }}
              noWrap
            >
              {centerTitle || (currency ? `${currency}` : '100%')}
            </Typography>
          </>
        )}
      </Box>
    </Box>
  );
}
