import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, isUnauthorized } from './axios';
import { useAuthStore } from '../store/authStore';
import type { User } from '../types';

export const sessionQueryKey = ['session'] as const;

async function fetchSession(): Promise<User | null> {
  try {
    const { data } = await api.get<User>('/me');
    return data;
  } catch (error) {
    // 비로그인 상태의 401은 오류가 아니라 "세션 없음"이다.
    if (isUnauthorized(error)) return null;
    throw error;
  }
}

/** /api/me 결과를 조회해 전역 인증 상태와 동기화한다. */
export function useSessionSync() {
  const setUser = useAuthStore(state => state.setUser);
  const { data, isSuccess } = useQuery({
    queryKey: sessionQueryKey,
    queryFn: fetchSession,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  useEffect(() => {
    if (isSuccess) setUser(data);
  }, [isSuccess, data, setUser]);
}

/** 로그인·로그아웃 직후 세션을 다시 읽게 한다. */
export function useRefreshSession() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: sessionQueryKey });
}
