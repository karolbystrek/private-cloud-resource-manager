import { ReactNode } from 'react';
import { Metadata } from 'next';
import { IBM_Plex_Mono, IBM_Plex_Sans } from 'next/font/google';
import { cookies } from 'next/headers';
import '@/app/globals.css';
import { Header } from '@/components/header';
import { ThemeProvider } from '@/components/theme-provider';
import { cn } from '@/lib/utils';

const ibmPlexSans = IBM_Plex_Sans({
  subsets: ['latin'],
  variable: '--font-sans',
  weight: ['400', '500', '600', '700'],
});

const ibmPlexMono = IBM_Plex_Mono({
  subsets: ['latin'],
  variable: '--font-mono',
  weight: ['400', '500', '600', '700'],
});

export const metadata: Metadata = {
  title: 'Private Cloud Resource Manager',
  description: 'On-premise cloud for batch jobs',
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const cookieStore = await cookies();
  const hasSession = Boolean(
    cookieStore.get('access_token')?.value || cookieStore.get('refresh_token')?.value,
  );

  return (
    <html
      lang="en"
      className={cn(ibmPlexMono.variable, 'font-sans', ibmPlexSans.variable)}
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
