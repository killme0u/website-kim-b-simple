import { Fragment, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';
import { CaptchaField } from '../components/CaptchaField';

const STEPS = ['약관 동의', '기본 정보 및 CAPTCHA', '가입 완료'] as const;

export function SignupPage() {
  const [step, setStep] = useState(1);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [username, setUsername] = useState('');
  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [captchaToken, setCaptchaToken] = useState('');
  const [emailToken, setEmailToken] = useState('');
  const [usernameAvailable, setUsernameAvailable] = useState<boolean | null>(null);
  const [nicknameAvailable, setNicknameAvailable] = useState<boolean | null>(null);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  // 서버는 닉네임을 trim해서 저장하므로, 중복 확인도 가입도 같은 값으로 보내야 판정이 어긋나지 않는다.
  const trimmedNickname = nickname.trim();

  const checkUsername = useMutation({
    mutationFn: async () => (
      await api.get<{ available: boolean }>('/members/username-availability', { params: { username } })
    ).data.available,
    onSuccess: setUsernameAvailable,
    onError: e => setError(errorMessage(e, '아이디 중복 확인에 실패했습니다.')),
  });

  const checkNickname = useMutation({
    mutationFn: async () => (
      await api.get<{ available: boolean }>('/members/nickname-availability', { params: { nickname: trimmedNickname } })
    ).data.available,
    onSuccess: setNicknameAvailable,
    onError: e => setError(errorMessage(e, '닉네임 중복 확인에 실패했습니다.')),
  });

  const signup = useMutation({
    mutationFn: () => api.post('/members/signup', {
      username, password, name, nickname: trimmedNickname, email, phone, captchaToken, termsAccepted,
    }),
    onSuccess: () => {
      setError('');
      setStep(3);
    },
    onError: e => setError(errorMessage(e, '회원가입에 실패했습니다.')),
  });

  const verifyEmail = useMutation({
    mutationFn: () => api.get('/members/verify-email', { params: { token: emailToken } }),
    onSuccess: () => setError(''),
    onError: e => setError(errorMessage(e, '이메일 인증에 실패했습니다.')),
  });

  const resendVerification = useMutation({
    mutationFn: () => api.post('/members/verify-email/resend', { email }),
    onError: e => setError(errorMessage(e, '인증 메일 재발송에 실패했습니다.')),
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setError('');
    if (!termsAccepted) return setError('필수 약관에 동의해 주세요.');
    if (usernameAvailable !== true) return setError('아이디 중복 확인을 완료해 주세요.');
    if (trimmedNickname && nicknameAvailable !== true) return setError('닉네임 중복 확인을 완료해 주세요.');
    if (password !== passwordConfirm) return setError('비밀번호가 일치하지 않습니다.');
    signup.mutate();
  };

  return (
    <Card className="mx-auto max-w-2xl p-6">
      <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Create account</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">회원가입</h1>
      <div className="mt-5 flex items-center gap-2 text-xs font-medium text-slate-500">
        {STEPS.map((label, index) => (
          <Fragment key={label}>
            <span className={step === index + 1 ? 'text-indigo-600' : ''}>{index + 1}. {label}</span>
            {index < STEPS.length - 1 && <span className="flex-1 border-t border-slate-200" />}
          </Fragment>
        ))}
      </div>
      {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
      {signup.isSuccess && !verifyEmail.isSuccess && (
        <div className="mt-5"><Alert>{email}로 인증 메일을 발송했습니다. 메일의 링크를 누르거나 인증 코드를 입력해 주세요.</Alert></div>
      )}
      {resendVerification.isSuccess && <div className="mt-5"><Alert>인증 메일을 다시 보냈습니다.</Alert></div>}

      {step === 1 && (
        <div className="mt-8 space-y-5">
          <label className="flex items-start gap-3 text-sm text-slate-700">
            <input type="checkbox" checked={termsAccepted} onChange={event => setTermsAccepted(event.target.checked)} className="mt-1 h-4 w-4 rounded border-slate-300 text-indigo-600" />
            <span>서비스 이용약관과 개인정보처리방침에 동의합니다. <strong className="text-rose-600">(필수)</strong></span>
          </label>
          <Button type="button" className="w-full" disabled={!termsAccepted} onClick={() => setStep(2)}>다음</Button>
        </div>
      )}

      {step === 2 && (
        <form onSubmit={submit} className="mt-8 space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="아이디" htmlFor="username">
              <div className="flex gap-2">
                <Input id="username" value={username} onChange={event => { setUsername(event.target.value); setUsernameAvailable(null); }} required />
                <Button type="button" variant="outline" size="sm" disabled={!username.trim() || checkUsername.isPending} onClick={() => checkUsername.mutate()}>중복 확인</Button>
              </div>
              {usernameAvailable !== null && (
                <p className={`text-xs ${usernameAvailable ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {usernameAvailable ? '사용할 수 있습니다.' : '이미 사용 중입니다.'}
                </p>
              )}
            </Field>
            <Field label="이름" htmlFor="name"><Input id="name" value={name} onChange={event => setName(event.target.value)} required /></Field>
          </div>
          <Field label="닉네임" htmlFor="nickname" hint="비워 두면 닉네임 없이 가입합니다.">
            <div className="flex gap-2">
              <Input id="nickname" value={nickname} onChange={event => { setNickname(event.target.value); setNicknameAvailable(null); }} />
              <Button type="button" variant="outline" size="sm" disabled={!trimmedNickname || checkNickname.isPending} onClick={() => checkNickname.mutate()}>중복 확인</Button>
            </div>
            {nicknameAvailable !== null && (
              <p className={`text-xs ${nicknameAvailable ? 'text-emerald-600' : 'text-rose-600'}`}>
                {nicknameAvailable ? '사용할 수 있습니다.' : '이미 사용 중입니다.'}
              </p>
            )}
          </Field>
          <Field label="이메일" htmlFor="email" hint="가입 요청 후 인증 메일이 발송됩니다.">
            <Input id="email" type="email" value={email} onChange={event => setEmail(event.target.value)} required />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="비밀번호" htmlFor="password" hint="영문, 숫자, 특수문자를 포함해 8자 이상">
              <Input id="password" type="password" value={password} onChange={event => setPassword(event.target.value)} minLength={8} required autoComplete="new-password" />
            </Field>
            <Field label="비밀번호 확인" htmlFor="passwordConfirm">
              <Input id="passwordConfirm" type="password" value={passwordConfirm} onChange={event => setPasswordConfirm(event.target.value)} minLength={8} required autoComplete="new-password" />
            </Field>
          </div>
          <Field label="휴대전화 번호" htmlFor="phone"><Input id="phone" value={phone} onChange={event => setPhone(event.target.value)} required /></Field>
          <CaptchaField value={captchaToken} onChange={setCaptchaToken} />
          <div className="flex justify-between gap-2">
            <Button type="button" variant="ghost" onClick={() => setStep(1)}>이전</Button>
            <Button type="submit" disabled={!captchaToken || signup.isPending}>회원가입</Button>
          </div>
        </form>
      )}

      {step === 3 && (
        <div className="mt-8 space-y-5 text-center">
          {verifyEmail.isSuccess ? (
            <>
              <p className="text-lg font-semibold text-emerald-700">회원가입이 완료되었습니다.</p>
              <Button type="button" onClick={() => navigate('/login')}>로그인으로 이동</Button>
            </>
          ) : (
            <>
              <p className="text-lg font-semibold text-slate-900">이메일 인증이 필요합니다.</p>
              <p className="text-sm text-slate-500">{email}로 발송된 메일의 링크를 누르거나, 아래에 인증 코드를 붙여 넣어 주세요.</p>
              <div className="flex gap-2 text-left">
                <Input value={emailToken} onChange={event => setEmailToken(event.target.value)} placeholder="이메일 인증 코드" />
                <Button type="button" onClick={() => verifyEmail.mutate()} disabled={!emailToken || verifyEmail.isPending}>인증 확인</Button>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => resendVerification.mutate()}
                disabled={resendVerification.isPending}
              >
                인증 메일 다시 받기
              </Button>
            </>
          )}
        </div>
      )}
      {step !== 3 && (
        <p className="mt-6 text-center text-sm text-slate-500">
          이미 계정이 있으신가요? <Link to="/login" className="font-semibold text-indigo-600 hover:underline">로그인</Link>
        </p>
      )}
    </Card>
  );
}
