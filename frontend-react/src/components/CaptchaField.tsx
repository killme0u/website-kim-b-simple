import { useEffect, useId, useRef } from 'react';
import { Field } from '../shared/ui';

// Cloudflare가 공개한 "항상 통과" 테스트 site key. 계정 없이 로컬 개발에 쓸 수 있다.
const TURNSTILE_TEST_SITE_KEY = '1x00000000000000000000AA';

export function CaptchaField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | null>(null);
  const fieldId = useId();

  useEffect(() => {
    let cancelled = false;

    const mount = () => {
      if (cancelled || !containerRef.current || !window.turnstile) return;
      widgetIdRef.current = window.turnstile.render(containerRef.current, {
        sitekey: import.meta.env.VITE_TURNSTILE_SITE_KEY || TURNSTILE_TEST_SITE_KEY,
        callback: onChange,
        'expired-callback': () => onChange(''),
        'error-callback': () => onChange(''),
      });
    };

    if (window.turnstile) {
      mount();
    } else {
      // script는 async/defer 로 로드되므로 아직 준비 안 됐을 수 있다. ready()가 큐잉해준다.
      const check = window.setInterval(() => {
        if (window.turnstile) {
          window.clearInterval(check);
          window.turnstile.ready(mount);
        }
      }, 50);
      return () => window.clearInterval(check);
    }

    return () => {
      cancelled = true;
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
      }
    };
    // onChange는 부모의 useState setter라 참조가 안정적이다. 위젯을 매 렌더 재마운트하지 않기 위해 의도적으로 생략한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Field label="자동 가입 방지" htmlFor={fieldId} hint="아래 위젯이 자동으로 CAPTCHA 검증을 처리합니다.">
      <div ref={containerRef} id={fieldId} data-value={value ? 'verified' : undefined} />
    </Field>
  );
}
