import { create } from 'zustand';

export interface User {
  id: number;
  username: string;
  name: string;
  email: string;
  role: 'MEMBER' | 'ADMIN';
  mustChangePassword?: boolean;
}

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  setUser: (user: User | null) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isAdmin: false,
  setUser: (user) => set({ 
    user, 
    isAuthenticated: !!user,
    isAdmin: user?.role === 'ADMIN'
  }),
  logout: () => set({ user: null, isAuthenticated: false, isAdmin: false }),
}));