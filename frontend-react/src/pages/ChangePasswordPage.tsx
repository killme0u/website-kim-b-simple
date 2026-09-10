import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { Alert, Button, Card, Field, Input } from '../shared/ui';

export function ChangePasswordPage() {
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [mismatch, setMismatch] = useState(false);
  const user = useAuthStore(state => state.user);
  const navigate = useNavigate();

  if (!user?.mustChangePassword) {
    return (
      <Card className="mx-auto mt-10 max-w-md p-6">
        <p className="text-slate-500">비밀번호 변경이 필요하지 않습니다.</p>
      </Card>
    );
  }

  const change = useMutation({
    mutationFn: () => api.post('/members/change-password', { newPassword }),
    onSuccess: () => {
      navigate('/me', { replace: true });
    },
  });

  const handleSubmit = (event: FormEvent) => {
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
      <div className="mb-6">
        <p className="text-sm font-semibold uppercase tracking-wider text-amber-600">Password Change Required</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">비밀번호 변경</h1>
      </div>
      <p className="mb-6 text-sm text-slate-600">
        보안을 위해 비밀번호를 변경해야 합니다. 변경 전 다른 페이지로 이동할 수 없습니다.
      </p>
      {change.isSuccess ? (
        <div className="space-y-5 text-center">
          <p className="text-lg font-semibold text-emerald-700">비밀번호가 변경되었습니다.</p>
          <Button onClick={() => navigate('/me')} className="w-full">
            마이페이지로 이동
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-5">
          {mismatch && <Alert tone="error">비밀번호가 일치하지 않습니다.</Alert>}
          {change.isError && (
            <Alert tone="error">{errorMessage(change.error, '비밀번호 변경에 실패했습니다.')}</Alert>
          )}
          <Field label="새 비밀번호" htmlFor="newPassword" hint="영문, 숫자, 특수문자를 포함해 8자 이상">
            <Input
              id="newPassword"
              type="password"
              minLength={8}
              value={newPassword}
              onChange={event => setNewPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </Field>
          <Field label="새 비밀번호 확인" htmlFor="confirm">
            <Input
              id="confirm"
              type="password"
              minLength={8}
              value={confirm}
              onChange={event => setConfirm(event.target.value)}
              required
              autoComplete="new-password"
            />
          </Field>
          <Button type="submit" className="w-full" disabled={change.isPending}>
            {change.isPending ? '변경 중...' : '비밀번호 변경'}
          </Button>
        </form>
      )}
    </Card>
  );
}
