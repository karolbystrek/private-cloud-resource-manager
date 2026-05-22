import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { ACCESS_TOKEN_COOKIE, buildClearSessionPath, buildLoginPath } from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';

export async function getRequiredAccessToken(nextPath: string): Promise<string> {
  const accessToken = (await cookies()).get(ACCESS_TOKEN_COOKIE)?.value;
  if (!accessToken) {
    redirect(buildLoginPath(nextPath));
  }
  return accessToken;
}

export async function brokerFetch(
  backendPath: string,
  init: RequestInit,
  nextPath: string,
): Promise<Response> {
  const accessToken = await getRequiredAccessToken(nextPath);
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${accessToken}`);

  const response = await fetch(`${getBackendUrlForServer()}${backendPath}`, {
    ...init,
    headers,
  });

  if (response.status === 401) {
    redirect(buildClearSessionPath(nextPath));
  }

  return response;
}
