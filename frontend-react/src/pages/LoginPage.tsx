import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { useRefreshSession } from '../lib/session';
import { Alert, Button, Card, Field, Input } from '../shared/ui';
import type { User } from '../types';

export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const setUser = useAuthStore(state => state.setUser);
  const refreshSession = useRefreshSession();
  const navigate = useNavigate();
  const location = useLocation();

  // 회원 전용 게시판에서 넘어왔다면 로그인 후 원래 자리로 돌려보낸다.
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const login = useMutation({
    mutationFn: async () => {
      await api.post('/auth/login', new URLSearchParams({ username, password }), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      });
      const { data } = await api.get<User>('/me');
      return data;
    },
    onSuccess: async user => {
      setUser(user);
      await refreshSession();
      navigate(from, { replace: true });
    },
  });

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    login.mutate();
  };

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <div className="mb-6">
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Welcome back</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">로그인</h1>
      </div>
      <form onSubmit={handleSubmit} className="space-y-5">
        {login.isError && <Alert tone="error">{errorMessage(login.error, '로그인에 실패했습니다.')}</Alert>}
        <Field label="이메일 / 아이디" htmlFor="username">
          <Input id="username" value={username} onChange={event => setUsername(event.target.value)} required autoComplete="username" />
        </Field>
        <Field label="비밀번호" htmlFor="password">
          <Input id="password" type="password" value={password} onChange={event => setPassword(event.target.value)} required autoComplete="current-password" />
        </Field>
        <div className="flex items-center justify-between text-sm">
          <label className="flex items-center gap-2 text-slate-600">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-indigo-600" />
            로그인 상태 유지
          </label>
          <span className="flex gap-3">
            <Link to="/find-username" className="text-indigo-600 hover:underline">아이디 찾기</Link>
            <Link to="/find-password" className="text-indigo-600 hover:underline">비밀번호 찾기</Link>
          </span>
        </div>
        <Button type="submit" className="w-full" disabled={login.isPending}>
          {login.isPending ? '로그인 중...' : '로그인'}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        계정이 없으신가요? <Link to="/signup" className="font-semibold text-indigo-600 hover:underline">회원가입</Link>
      </p>
    </Card>
  );
}
