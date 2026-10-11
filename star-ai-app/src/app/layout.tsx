import type { Metadata, Viewport } from 'next';
import './globals.css';
import { Providers } from '@/components/common/Providers';
import { Toaster } from 'sonner';

export const metadata: Metadata = {
  title: { default: 'Star AI · 18+ 视频付费', template: '%s · Star AI' },
  description: 'AI 生成 + 创作者生态 · 18+ 视频付费分润平台',
  manifest: '/manifest.json'
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#ffffff' },
    { media: '(prefers-color-scheme: dark)', color: '#0a0a0a' }
  ],
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="antialiased">
        <Providers>{children}</Providers>
        <Toaster richColors position="top-center" />
      </body>
    </html>
  );
}
