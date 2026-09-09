import { Link, useLocation } from 'react-router-dom';
import { Button, Card } from '../shared/ui';

/** 회원 전용 게시판처럼 서버가 401을 돌려준 화면에서 로그인 경로를 안내한다. */
export function LoginRequired({ message }: { message?: string }) {
  const location = useLocation();

  return (
    <Card className="mx-auto max-w-md p-8 text-center">
      <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Members only</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">로그인이 필요합니다</h1>
      <p className="mt-3 text-sm text-slate-500">{message ?? '회원 전용 게시판입니다. 로그인 후 이용해 주세요.'}</p>
      <div className="mt-6 flex justify-center gap-2">
        <Link to="/login" state={{ from: location.pathname + location.search }}>
          <Button>로그인</Button>
        </Link>
        <Link to="/signup">
          <Button variant="outline">회원가입</Button>
        </Link>
      </div>
    </Card>
  );
}
