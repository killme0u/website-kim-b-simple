import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { Alert, Button, Card, Field, Input } from '../shared/ui';

type PasswordStrength = 'weak' | 'fair' | 'good' | 'strong';

function calculatePasswordStrength(password: string): PasswordStrength {
  if (!password) return 'weak';

  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (password.length >= 16) score++;
  if (/[a-z]/.test(password)) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[!@#$%^&*()_+\-=\[\]{};:'",.<>?/\\|`~]/.test(password)) score++;

  if (score <= 2) return 'weak';
  if (score <= 4) return 'fair';
  if (score <= 5) return 'good';
  return 'strong';
}

function getStrengthLabel(strength: PasswordStrength): string {
  const labels = {
    weak: '약함',
    fair: '보통',
    good: '좋음',
    strong: '강함',
  };
  return labels[strength];
}

function getStrengthColor(strength: PasswordStrength): string {
  const colors = {
    weak: 'bg-red-500',
    fair: 'bg-yellow-500',
    good: 'bg-blue-500',
    strong: 'bg-emerald-500',
  };
  return colors[strength];
}

function getStrengthPercent(strength: PasswordStrength): number {
  const percents = {
    weak: 25,
    fair: 50,
    good: 75,
    strong: 100,
  };
  return percents[strength];
}

export function ChangePasswordPage() {
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [mismatch, setMismatch] = useState(false);
  const user = useAuthStore(state => state.user);
  const navigate = useNavigate();
  const strength = calculatePasswordStrength(newPassword);

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
            <div className="space-y-2">
              <Input
                id="newPassword"
                type="password"
                minLength={8}
                value={newPassword}
                onChange={event => setNewPassword(event.target.value)}
                required
                autoComplete="new-password"
              />
              {newPassword && (
                <div className="space-y-1">
                  <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
                    <div
                      className={`h-full ${getStrengthColor(strength)} transition-all duration-200`}
                      style={{ width: `${getStrengthPercent(strength)}%` }}
                    />
                  </div>
                  <p className="text-xs text-slate-600">
                    강도: <span className="font-medium">{getStrengthLabel(strength)}</span>
                  </p>
                </div>
              )}
            </div>
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
          <Button type="submit" className="w-full" disabled={change.isPending || newPassword.length < 8 || newPassword !== confirm}>
            {change.isPending ? '변경 중...' : '비밀번호 변경'}
          </Button>
        </form>
      )}
    </Card>
  );
}
