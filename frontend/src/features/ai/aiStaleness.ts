/**
 * Staleness threshold in milliseconds: 7 days (1 week).
 */
export const STALENESS_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * Checks whether an AI evaluation is considered stale (older than 7 days / 1 week).
 * Returns true if evaluatedAt is older than 7 days or if explicit isStale flag is true.
 */
export function isEvaluationStale(
  evaluatedAt?: string | Date | null,
  isStaleProp?: boolean
): boolean {
  if (isStaleProp === true) return true;
  if (!evaluatedAt) return false;
  const time = typeof evaluatedAt === 'string' ? new Date(evaluatedAt).getTime() : evaluatedAt.getTime();
  if (isNaN(time)) return false;
  return Date.now() - time > STALENESS_THRESHOLD_MS;
}

/**
 * Returns elapsed days since evaluation.
 */
export function getStalenessDays(evaluatedAt?: string | Date | null): number {
  if (!evaluatedAt) return 0;
  const time = typeof evaluatedAt === 'string' ? new Date(evaluatedAt).getTime() : evaluatedAt.getTime();
  if (isNaN(time)) return 0;
  return Math.floor((Date.now() - time) / (24 * 60 * 60 * 1000));
}
