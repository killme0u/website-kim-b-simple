import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/axios';
import { Alert, Button, Card, Field, Input } from '../shared/ui';
import { CaptchaField } from '../components/CaptchaField';

export const SignupPage: React.FC = () => {
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
  const [emailVerified, setEmailVerified] = useState(false);
  const [usernameAvailable, setUsernameAvailable] = useState<boolean | null>(null);
  const [nicknameAvailable, setNicknameAvailable] = useState<boolean | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const checkUsername = async () => {
    try {
      const response = await api.get('/members/username-availability', { params: { username } });
      setUsernameAvailable(response.data.available);
    } catch {
      setError('아이디 중복 확인에 실패했습니다.');
    }
  };

  const checkNickname = async () => {
    try {
      const response = await api.get('/members/nickname-availability', { params: { nickname } });
      setNicknameAvailable(response.data.available);
    } catch {
      setError('닉네임 중복 확인에 실패했습니다.');
    }
  };

  const verifyEmail = async () => {
    try {
      await api.get('/members/verify-email', { params: { token: emailToken } });
      setEmailVerified(true);
      setMessage('이메일 인증이 완료되었습니다.');
    } catch (err: any) {
      setError(err.response?.data?.message || '이메일 인증에 실패했습니다.');
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!termsAccepted) return setError('필수 약관에 동의해 주세요.');
    if (usernameAvailable !== true) return setError('아이디 중복 확인을 완료해 주세요.');
    if (nickname && nicknameAvailable !== true) return setError('닉네임 중복 확인을 완료해 주세요.');
    if (password !== passwordConfirm) return setError('비밀번호가 일치하지 않습니다.');

    try {
      await api.post('/members/signup', {
        username, password, name, nickname, email, phone, captchaToken, termsAccepted,
      });
      setMessage('인증 메일을 발송했습니다. 이메일의 인증 코드를 입력해 주세요.');
      setStep(3);
    } catch (err: any) {
      setError(err.response?.data?.message || '회원가입에 실패했습니다.');
    }
  };

  return (
    <Card className="mx-auto max-w-2xl p-6">
      <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Create account</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">회원가입</h1>
      <div className="mt-5 flex items-center gap-2 text-xs font-medium text-slate-500">
        {['약관 동의', '기본 정보 및 CAPTCHA', '가입 완료'].map((label, index) => (
          <React.Fragment key={label}>
            <span className={step === index + 1 ? 'text-indigo-600' : ''}>{index + 1}. {label}</span>
            {index < 2 && <span className="flex-1 border-t border-slate-200" />}
          </React.Fragment>
        ))}
      </div>
      {error && <div className="mt-5"><Alert tone="error">{error}</Alert></div>}
      {message && <div className="mt-5"><Alert>{message}</Alert></div>}

      {step === 1 && (
        <div className="mt-8 space-y-5">
          <label className="flex items-start gap-3 text-sm text-slate-700">
            <input type="checkbox" checked={termsAccepted} onChange={e => setTermsAccepted(e.target.checked)} className="mt-1 h-4 w-4 rounded border-slate-300 text-indigo-600" />
            <span>서비스 이용약관과 개인정보처리방침에 동의합니다. <strong className="text-rose-600">(필수)</strong></span>
          </label>
          <Button type="button" className="w-full" disabled={!termsAccepted} onClick={() => setStep(2)}>다음</Button>
        </div>
      )}

      {step === 2 && (
        <form onSubmit={submit} className="mt-8 space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="아이디" htmlFor="username">
              <div className="flex gap-2"><Input id="username" value={username} onChange={e => { setUsername(e.target.value); setUsernameAvailable(null); }} required /><Button type="button" variant="outline" size="sm" onClick={checkUsername}>중복 확인</Button></div>
              {usernameAvailable !== null && <p className={`text-xs ${usernameAvailable ? 'text-emerald-600' : 'text-rose-600'}`}>{usernameAvailable ? '사용할 수 있습니다.' : '이미 사용 중입니다.'}</p>}
            </Field>
            <Field label="이름" htmlFor="name"><Input id="name" value={name} onChange={e => setName(e.target.value)} required /></Field>
          </div>
          <Field label="닉네임" htmlFor="nickname">
            <div className="flex gap-2"><Input id="nickname" value={nickname} onChange={e => { setNickname(e.target.value); setNicknameAvailable(null); }} /><Button type="button" variant="outline" size="sm" onClick={checkNickname}>중복 확인</Button></div>
          </Field>
          <Field label="이메일" htmlFor="email" hint="가입 요청 후 인증 메일이 발송됩니다.">
            <Input id="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="비밀번호" htmlFor="password" hint="영문, 숫자, 특수문자를 포함해 8자 이상"><Input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} minLength={8} required /></Field>
            <Field label="비밀번호 확인" htmlFor="passwordConfirm"><Input id="passwordConfirm" type="password" value={passwordConfirm} onChange={e => setPasswordConfirm(e.target.value)} minLength={8} required /></Field>
          </div>
          <Field label="휴대전화 번호" htmlFor="phone"><Input id="phone" value={phone} onChange={e => setPhone(e.target.value)} required /></Field>
          <CaptchaField value={captchaToken} onChange={setCaptchaToken} />
          <div className="flex justify-between gap-2"><Button type="button" variant="ghost" onClick={() => setStep(1)}>이전</Button><Button type="submit" disabled={!captchaToken}>회원가입</Button></div>
        </form>
      )}

      {step === 3 && (
        <div className="mt-8 space-y-5 text-center">
          {!emailVerified ? (
            <>
              <p className="text-lg font-semibold text-slate-900">이메일 인증이 필요합니다.</p>
              <p className="text-sm text-slate-500">{email}로 발송된 인증 코드 또는 토큰을 입력해 주세요.</p>
              <div className="flex gap-2 text-left">
                <Input value={emailToken} onChange={e => setEmailToken(e.target.value)} placeholder="이메일 인증 코드" />
                <Button type="button" onClick={verifyEmail} disabled={!emailToken}>인증 확인</Button>
              </div>
            </>
          ) : (
            <>
              <p className="text-lg font-semibold text-emerald-700">회원가입이 완료되었습니다.</p>
              <Button type="button" onClick={() => navigate('/login')}>로그인으로 이동</Button>
            </>
          )}
        </div>
      )}
      {step !== 3 && <p className="mt-6 text-center text-sm text-slate-500">이미 계정이 있으신가요? <Link to="/login" className="font-semibold text-indigo-600 hover:underline">로그인</Link></p>}
    </Card>
  );
};
