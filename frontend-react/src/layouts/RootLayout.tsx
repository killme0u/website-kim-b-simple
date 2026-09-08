import React from 'react';
import { Outlet, Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { api } from '../lib/axios';
import { Button } from '@/shared/ui';

export const RootLayout: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuthStore();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = React.useState(searchParams.get('q') || '');

  const handleLogout = async () => {
    try {
      await api.post('/auth/logout');
      logout();
      navigate('/');
    } catch (e) {
      console.error('Logout failed', e);
    }
  };

  const handleSearch = (event: React.FormEvent) => {
    event.preventDefault();
    if (search.trim()) {
      setSearchParams({ q: search.trim(), page: '0' });
      navigate(`/boards/free?q=${encodeURIComponent(search.trim())}&page=0`);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
          <Link to="/" className="text-xl font-bold tracking-tight text-slate-900">BoardSystem</Link>
          <form onSubmit={handleSearch} className="hidden flex-1 justify-center px-8 md:flex">
            <div className="flex w-full max-w-md gap-2">
              <input
                value={search}
                onChange={event => setSearch(event.target.value)}
                placeholder="게시글 검색"
                aria-label="게시글 검색"
                className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
              />
              <Button type="submit" variant="outline" size="sm">검색</Button>
            </div>
          </form>
          <div className="flex items-center gap-2">
            {isAuthenticated ? (
              <div className="flex items-center gap-3">
                <span className="hidden text-sm text-slate-600 sm:inline">{user?.name}님</span>
                <Link to="/me" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
                  내 페이지
                </Link>
                <Button type="button" variant="ghost" size="sm" onClick={handleLogout}>로그아웃</Button>
              </div>
            ) : (
              <>
                <Link to="/login" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">로그인</Link>
                <Link to="/signup" className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-700">회원가입</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <nav className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4 py-2 text-sm font-medium text-slate-600">
          <Link to="/" className="rounded-lg px-3 py-2 hover:bg-slate-100">홈</Link>
          <Link to="/boards/free" className="rounded-lg px-3 py-2 hover:bg-slate-100">게시판</Link>
          <Link to="/boards/qna" className="rounded-lg px-3 py-2 hover:bg-slate-100">Q&A</Link>
          <Link to="/boards/archive" className="rounded-lg px-3 py-2 hover:bg-slate-100">자료실</Link>
        </div>
      </nav>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6 text-sm text-slate-500">© 2026 BoardSystem</div>
      </footer>
    </div>
  );
};