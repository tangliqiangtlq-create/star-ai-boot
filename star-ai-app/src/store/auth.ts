import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { env } from '@/lib/config/env';

export interface UserInfo {
  id: string;
  username: string;
  nickname: string;
  avatarUrl?: string;
  ageVerified: boolean;
  region: string;
  isCreator: boolean;
  creatorLevel?: number;
  creatorId?: string;
}

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  isAuthenticated: boolean;
  isCapacitor: boolean;

  setToken: (token: string) => void;
  setUser: (user: UserInfo) => void;
  logout: () => void;
  initCapacitor: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,
      isCapacitor: false,

      setToken: (token) => {
        if (typeof window !== 'undefined') {
          localStorage.setItem(env.TOKEN_KEY, token);
        }
        set({ token, isAuthenticated: true });
      },

      setUser: (user) => set({ user }),

      logout: () => {
        if (typeof window !== 'undefined') {
          localStorage.removeItem(env.TOKEN_KEY);
        }
        set({ token: null, user: null, isAuthenticated: false });
      },

      initCapacitor: () => {
        const isCap = typeof document !== 'undefined' && !!document.querySelector('meta[name="capacitor"]');
        set({ isCapacitor: isCap });
      }
    }),
    {
      name: 'star-ai-auth',
      partialize: (state) => ({ token: state.token, user: state.user, isAuthenticated: state.isAuthenticated })
    }
  )
);
