import { create } from 'zustand';
import type { User } from '../types';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  setUser: (user: User | null) => void;
  logout: () => void;
}

const signedOut = { user: null, isAuthenticated: false, isAdmin: false } as const;

export const useAuthStore = create<AuthState>(set => ({
  ...signedOut,
  setUser: user => set({
    user,
    isAuthenticated: user !== null,
    isAdmin: user?.role === 'ADMIN',
  }),
  logout: () => set(signedOut),
}));
