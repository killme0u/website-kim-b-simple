import { Field } from '../shared/ui';

export function CaptchaField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <Field
      label="자동 가입 방지"
      htmlFor="captcha-token"
      hint="운영 환경에서는 CAPTCHA provider 위젯이 발급한 토큰을 사용합니다."
    >
      <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <label className="flex items-center gap-3 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={Boolean(value)}
            onChange={event => onChange(event.target.checked ? (import.meta.env.VITE_CAPTCHA_TOKEN || 'dev-captcha') : '')}
            className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
          />
          저는 사람이 맞습니다.
        </label>
        <input
          id="captcha-token"
          type="text"
          value={value}
          onChange={event => onChange(event.target.value)}
          placeholder="CAPTCHA 토큰"
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
          required
        />
      </div>
    </Field>
  );
}
