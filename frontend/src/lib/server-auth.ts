import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import {
  ACCESS_TOKEN_COOKIE,
  buildClearSessionPath,
  buildLoginPath,
  buildRefreshPath,
  REFRESH_TOKEN_COOKIE,
} from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';

export type ServerSession = {
  accessToken: string;
};

export async function requireSession(nextPath: string): Promise<ServerSession> {
  const cookieStore = await cookies();
  const accessToken = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value;
  if (!accessToken) {
    const refreshToken = cookieStore.get(REFRESH_TOKEN_COOKIE)?.value;
    if (refreshToken) {
      redirect(buildRefreshPath(nextPath));
    }
    redirect(buildLoginPath(nextPath));
  }
  return { accessToken };
}

export async function brokerFetch(
  backendPath: string,
  init: RequestInit,
  nextPath: string,
): Promise<Response> {
  const { accessToken } = await requireSession(nextPath);
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
