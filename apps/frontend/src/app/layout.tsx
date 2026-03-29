import '@/app/globals.css';

import { ReactNode } from 'react';
import { Metadata } from 'next';
import { cookies } from 'next/headers';
import { IBM_Plex_Sans, JetBrains_Mono } from 'next/font/google';

import { cn } from '@/lib/utils';
import { Header } from '@/components/header';
import { ThemeProvider } from '@/components/theme-provider';

const ibmPlexSans = IBM_Plex_Sans({
  subsets: ['latin'],
  variable: '--font-sans',
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ['latin'],
  variable: '--font-mono',
});

export const metadata: Metadata = {
  title: 'Private Cloud Resource Manager',
  description: 'On-premise cloud for batch jobs',
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const cookieStore = await cookies();
  const hasSession = Boolean(
    cookieStore.get('access_token')?.value
    || cookieStore.get('refresh_token')?.value,
  );

  return (
    <html
      lang="en"
      className={cn(jetbrainsMono.variable, 'font-sans', ibmPlexSans.variable)}
      suppressHydrationWarning
    >
      <body>
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <Header hasSession={hasSession} />
          <main className="w-full">{children}</main>
        </ThemeProvider>
      </body>
    </html>
  );
}
