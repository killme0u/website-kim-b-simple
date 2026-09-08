import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';
import { CaptchaField } from '../components/CaptchaField';

export const FindPasswordPage: React.FC = () => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [captchaToken, setCaptchaToken] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    setError('');
    try {
      const response = await api.post('/members/password-reset/request', { email, captchaToken });
      setMessage(response.data?.message || '임시 비밀번호 발송 요청을 처리했습니다.');
    } catch (err: any) {
      setError(err.response?.data?.message || '비밀번호 찾기 요청에 실패했습니다.');
    }
  };

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <h1 className="text-2xl font-bold text-slate-900">비밀번호 찾기</h1>
      <p className="mt-2 text-sm text-slate-500">가입 정보가 확인되면 안내 메일을 발송합니다.</p>
      <form onSubmit={submit} className="mt-6 space-y-5">
        {error && <Alert tone="error">{error}</Alert>}
        {message && <Alert>{message}</Alert>}
        <Field label="아이디" htmlFor="username">
          <Input id="username" value={username} onChange={e => setUsername(e.target.value)} required />
        </Field>
        <Field label="이메일" htmlFor="email">
          <Input id="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required />
        </Field>
        <CaptchaField value={captchaToken} onChange={setCaptchaToken} />
        <Button type="submit" className="w-full" disabled={!captchaToken}>임시 비밀번호 받기</Button>
      </form>
      <Link to="/login" className="mt-5 block text-center text-sm text-indigo-600 hover:underline">로그인으로 돌아가기</Link>
    </Card>
  );
};
