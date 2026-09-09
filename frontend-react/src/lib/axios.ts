import axios from 'axios';
import { useAuthStore } from '../store/authStore';

export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  // Spring Security의 csrf().spa() 설정이 매 응답마다 XSRF-TOKEN 쿠키를 내려준다.
  // 그 값을 그대로 X-XSRF-TOKEN 헤더로 돌려보내야 로그아웃 등 변경 요청이 통과한다.
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

export function isUnauthorized(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

export function errorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback;
  }
  return fallback;
}

// 세션이 끊기면 클라이언트 상태도 함께 비운다. 비로그인 상태에서의 401은 no-op이다.
api.interceptors.response.use(
  response => response,
  (error: unknown) => {
    if (isUnauthorized(error)) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  },
);
