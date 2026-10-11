'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuthStore } from '@/store/auth';

interface AuthGuardProps {
  children: React.ReactNode;
  requireCreator?: boolean;
}

const PUBLIC_PATHS = ['/', '/browse', '/login', '/register', '/subscription'];

export function AuthGuard({ children, requireCreator }: AuthGuardProps) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, user, initCapacitor } = useAuthStore();

  useEffect(() => {
    initCapacitor();

    const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));
    if (!isAuthenticated && !isPublic) {
      router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
    }
    if (requireCreator && !user?.isCreator) {
      router.replace('/');
    }
  }, [isAuthenticated, user, requireCreator, pathname, router, initCapacitor]);

  if (requireCreator && !user?.isCreator) return null;

  return <>{children}</>;
}
