import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { Button, Card } from '../shared/ui';
import type { Board } from '../types';

export function HomePage() {
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const { data: boards = [] } = useQuery({
    queryKey: ['boards'],
    queryFn: async () => (await api.get<Board[]>('/boards')).data,
    staleTime: 10 * 60 * 1000,
  });

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Community</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">게시판 목록</h1>
        <p className="mt-2 text-slate-600">관심 있는 게시판을 선택해 이야기를 시작해 보세요.</p>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {boards.map(board => (
          <Card key={board.slug} className="p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-xl font-semibold text-slate-900">{board.name}</h2>
                  {board.requiresAuthToRead && (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">회원 전용</span>
                  )}
                </div>
                <p className="mt-2 text-sm text-slate-500">
                  {board.requiresAuthToRead && !isAuthenticated
                    ? '로그인 후 열람할 수 있는 게시판입니다.'
                    : '게시글을 확인하고 새로운 글을 작성해 보세요.'}
                </p>
              </div>
              <Link to={`/boards/${board.slug}`}>
                <Button size="sm">들어가기</Button>
              </Link>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
