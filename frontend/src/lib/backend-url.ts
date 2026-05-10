export function getBackendUrlForServer(): string {
  const url = process.env.BACKEND_INTERNAL_URL ?? process.env.NEXT_PUBLIC_BACKEND_URL;
  if (!url) {
    throw new Error('Backend URL is not configured.');
  }
  return url;
}
