import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { api } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { setUser } = useAuthStore();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const params = new URLSearchParams({ username, password });
      await api.post('/auth/login', params, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      });
      const meRes = await api.get('/me');
      setUser(meRes.data);
      navigate('/');
    } catch (e: any) {
      setError(e.response?.data?.message || '로그인에 실패했습니다.');
    }
  };

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <div className="mb-6">
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Welcome back</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">로그인</h1>
      </div>
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="이메일 / 아이디" htmlFor="username">
          <Input id="username" value={username} onChange={e => setUsername(e.target.value)} required autoComplete="username" />
        </Field>
        <Field label="비밀번호" htmlFor="password">
          <Input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} required autoComplete="current-password" />
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
        <Button type="submit" className="w-full">로그인</Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        계정이 없으신가요? <Link to="/signup" className="font-semibold text-indigo-600 hover:underline">회원가입</Link>
      </p>
    </Card>
  );
};
