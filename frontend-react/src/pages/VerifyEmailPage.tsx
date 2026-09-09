import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';

/** 회원가입 인증 메일의 링크(/verify-email?token=...)가 도착하는 화면. */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const [token, setToken] = useState(searchParams.get('token') ?? '');
  const autoSubmitted = useRef(false);

  const verify = useMutation({
    mutationFn: (rawToken: string) =>
      api.get('/members/verify-email', { params: { token: rawToken } }),
  });

  const tokenFromLink = searchParams.get('token');
  const { mutate } = verify;
  useEffect(() => {
    if (tokenFromLink && !autoSubmitted.current) {
      autoSubmitted.current = true;
      mutate(tokenFromLink);
    }
  }, [tokenFromLink, mutate]);

  return (
    <Card className="mx-auto mt-10 max-w-md p-6">
      <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Verify email</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">이메일 인증</h1>

      {verify.isSuccess ? (
        <div className="mt-6 space-y-5 text-center">
          <p className="text-lg font-semibold text-emerald-700">인증이 완료되었습니다. 이제 로그인할 수 있습니다.</p>
          <Link to="/login"><Button>로그인으로 이동</Button></Link>
        </div>
      ) : (
        <form
          className="mt-6 space-y-5"
          onSubmit={event => {
            event.preventDefault();
            verify.mutate(token);
          }}
        >
          {verify.isError && (
            <Alert tone="error">{errorMessage(verify.error, '이메일 인증에 실패했습니다.')}</Alert>
          )}
          <Field label="인증 코드" htmlFor="token" hint="메일로 받은 링크를 눌렀다면 자동으로 인증됩니다.">
            <Input id="token" value={token} onChange={event => setToken(event.target.value)} required />
          </Field>
          <Button type="submit" className="w-full" disabled={!token || verify.isPending}>
            {verify.isPending ? '확인 중...' : '인증 확인'}
          </Button>
        </form>
      )}
    </Card>
  );
}
