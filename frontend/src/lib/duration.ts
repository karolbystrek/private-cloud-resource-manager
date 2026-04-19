export function formatMinutesAsHoursAndMinutes(minutes: number): string {
  if (!Number.isFinite(minutes)) {
    return 'Unlimited';
  }

  const normalizedMinutes = Math.max(0, Math.trunc(minutes));
  const hours = Math.floor(normalizedMinutes / 60);
  const remainingMinutes = normalizedMinutes % 60;

  if (remainingMinutes === 0) {
    return `${hours}h`;
  }

  if (hours === 0) {
    return `${remainingMinutes}m`;
  }

  return `${hours}h ${remainingMinutes}m`;
}
