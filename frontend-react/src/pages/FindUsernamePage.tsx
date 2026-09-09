import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';

export function FindUsernamePage() {
  const [email, setEmail] = useState('');

  const recover = useMutation({
    mutationFn: async () => (
      await api.post<{ message?: string }>('/members/username-recovery', { email })
    ).data,
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    recover.mutate();
  };

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <h1 className="text-2xl font-bold text-slate-900">아이디 찾기</h1>
      <p className="mt-2 text-sm text-slate-500">가입할 때 사용한 이메일을 입력해 주세요.</p>
      <form onSubmit={submit} className="mt-6 space-y-5">
        {recover.isError && (
          <Alert tone="error">{errorMessage(recover.error, '아이디 찾기 요청에 실패했습니다.')}</Alert>
        )}
        {recover.isSuccess && (
          <Alert>{recover.data?.message ?? '가입 이메일로 아이디 안내를 전송했습니다.'}</Alert>
        )}
        <Field label="이메일" htmlFor="email">
          <Input id="email" type="email" value={email} onChange={event => setEmail(event.target.value)} required />
        </Field>
        <Button type="submit" className="w-full" disabled={recover.isPending}>아이디 안내 받기</Button>
      </form>
      <Link to="/login" className="mt-5 block text-center text-sm text-indigo-600 hover:underline">로그인으로 돌아가기</Link>
    </Card>
  );
}
