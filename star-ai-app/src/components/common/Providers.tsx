'use client';

import { useEffect } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30000, retry: 1, refetchOnWindowFocus: false }
        }
      })
  );

  useEffect(() => {
    // Capacitor 运行环境检测: document.querySelector('meta[name="capacitor"]') !== null
    const isCapacitor = typeof document !== 'undefined' && document.querySelector('meta[name="capacitor"]');
    if (isCapacitor) {
      document.documentElement.classList.add('app');
    }
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
