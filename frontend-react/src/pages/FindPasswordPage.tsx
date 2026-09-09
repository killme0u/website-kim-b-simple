import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';
import { CaptchaField } from '../components/CaptchaField';

export function FindPasswordPage() {
  const [searchParams] = useSearchParams();
  // 메일의 재설정 링크(/find-password?token=...)로 들어오면 곧바로 새 비밀번호를 받는다.
  const token = searchParams.get('token');

  return token
    ? <ResetPasswordForm token={token} />
    : <RequestResetForm />;
}

function RequestResetForm() {
  const [email, setEmail] = useState('');
  const [captchaToken, setCaptchaToken] = useState('');

  const request = useMutation({
    mutationFn: () => api.post('/members/password-reset/request', { email, captchaToken }),
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    request.mutate();
  };

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <h1 className="text-2xl font-bold text-slate-900">비밀번호 찾기</h1>
      <p className="mt-2 text-sm text-slate-500">가입 정보가 확인되면 재설정 링크를 메일로 보내 드립니다.</p>
      <form onSubmit={submit} className="mt-6 space-y-5">
        {request.isError && (
          <Alert tone="error">{errorMessage(request.error, '비밀번호 찾기 요청에 실패했습니다.')}</Alert>
        )}
        {request.isSuccess && <Alert>입력하신 메일로 재설정 안내를 보냈습니다.</Alert>}
        <Field label="이메일" htmlFor="email">
          <Input id="email" type="email" value={email} onChange={event => setEmail(event.target.value)} required />
        </Field>
        <CaptchaField value={captchaToken} onChange={setCaptchaToken} />
        <Button type="submit" className="w-full" disabled={!captchaToken || request.isPending}>
          재설정 메일 받기
        </Button>
      </form>
      <Link to="/login" className="mt-5 block text-center text-sm text-indigo-600 hover:underline">로그인으로 돌아가기</Link>
    </Card>
  );
}

function ResetPasswordForm({ token }: { token: string }) {
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [mismatch, setMismatch] = useState(false);

  const change = useMutation({
    mutationFn: () => api.post('/members/password-reset/change', { token, newPassword }),
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (newPassword !== confirm) {
      setMismatch(true);
      return;
    }
    setMismatch(false);
    change.mutate();
  };

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <h1 className="text-2xl font-bold text-slate-900">비밀번호 재설정</h1>
      {change.isSuccess ? (
        <div className="mt-6 space-y-5 text-center">
          <p className="text-lg font-semibold text-emerald-700">비밀번호가 변경되었습니다.</p>
          <Link to="/login"><Button>로그인으로 이동</Button></Link>
        </div>
      ) : (
        <form onSubmit={submit} className="mt-6 space-y-5">
          {mismatch && <Alert tone="error">비밀번호가 일치하지 않습니다.</Alert>}
          {change.isError && (
            <Alert tone="error">{errorMessage(change.error, '비밀번호 재설정에 실패했습니다.')}</Alert>
          )}
          <Field label="새 비밀번호" htmlFor="newPassword" hint="영문, 숫자, 특수문자를 포함해 8자 이상">
            <Input id="newPassword" type="password" minLength={8} value={newPassword} onChange={event => setNewPassword(event.target.value)} required autoComplete="new-password" />
          </Field>
          <Field label="새 비밀번호 확인" htmlFor="confirm">
            <Input id="confirm" type="password" minLength={8} value={confirm} onChange={event => setConfirm(event.target.value)} required autoComplete="new-password" />
          </Field>
          <Button type="submit" className="w-full" disabled={change.isPending}>비밀번호 변경</Button>
        </form>
      )}
      <Link to="/login" className="mt-5 block text-center text-sm text-indigo-600 hover:underline">로그인으로 돌아가기</Link>
    </Card>
  );
}
