import { useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, errorMessage, isUnauthorized } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { LoginRequired } from '../components/LoginRequired';
import { Alert, Button, Card, Input } from '../shared/ui';
import type { Board, PageResponse, PostListItem } from '../types';

const MEMBERS_ONLY_MESSAGE = '회원 전용 게시판입니다. 로그인 후 이용해 주세요.';

export function BoardPage() {
  const { slug } = useParams<{ slug: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Number(searchParams.get('page') ?? 0);
  const queryKeyword = searchParams.get('q') ?? '';
  const [keyword, setKeyword] = useState(queryKeyword);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);

  const boardQuery = useQuery({
    queryKey: ['board', slug],
    queryFn: async () => (await api.get<Board>(`/boards/${slug}`)).data,
  });

  const postsQuery = useQuery({
    queryKey: ['posts', slug, page, queryKeyword],
    queryFn: async () => (
      await api.get<PageResponse<PostListItem>>(`/boards/${slug}/posts`, {
        params: { page, size: 10, keyword: queryKeyword },
      })
    ).data,
    enabled: boardQuery.isSuccess,
    placeholderData: keepPreviousData,
  });

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    setSearchParams(keyword ? { q: keyword, page: '0' } : { page: '0' });
  };

  // 회원 전용 게시판(Q&A·자료실)에 비로그인으로 들어오면 서버가 401을 돌려준다.
  if (isUnauthorized(boardQuery.error)) {
    return <LoginRequired message={errorMessage(boardQuery.error, MEMBERS_ONLY_MESSAGE)} />;
  }
  if (boardQuery.isError) {
    return <Alert tone="error">{errorMessage(boardQuery.error, '게시판을 불러오지 못했습니다.')}</Alert>;
  }
  if (!boardQuery.data) {
    return <p className="text-sm text-slate-500">게시판을 불러오는 중입니다...</p>;
  }

  const board = boardQuery.data;
  const posts = postsQuery.data?.content ?? [];
  const totalPages = Math.max(postsQuery.data?.totalPages ?? 1, 1);
  const canWrite = !board.requiresAuthToWrite || isAuthenticated;

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Board</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{board.name}</h1>
        </div>
        {canWrite ? (
          <Link to={`/boards/${slug}/posts/new`}><Button size="sm">글쓰기</Button></Link>
        ) : (
          <Link to="/login"><Button size="sm" variant="outline">로그인 후 글쓰기</Button></Link>
        )}
      </div>

      <form onSubmit={handleSearch} className="flex gap-2">
        <Input
          type="text"
          placeholder="검색어 입력..."
          className="flex-1"
          value={keyword}
          onChange={event => setKeyword(event.target.value)}
        />
        <Button type="submit" variant="secondary" size="sm">검색</Button>
      </form>

      {postsQuery.isError && (
        <Alert tone="error">{errorMessage(postsQuery.error, '게시글 목록을 불러오지 못했습니다.')}</Alert>
      )}

      <Card className="overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[680px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3 font-semibold">번호</th>
                <th className="px-4 py-3 font-semibold">제목</th>
                <th className="px-4 py-3 font-semibold">작성자</th>
                <th className="px-4 py-3 font-semibold">조회수</th>
                <th className="px-4 py-3 font-semibold">추천수</th>
                <th className="px-4 py-3 font-semibold">작성일</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {posts.map(post => (
                <tr key={post.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-500">{post.id}</td>
                  <td className="px-4 py-3 font-medium">
                    <Link to={`/posts/${post.id}`} className="text-indigo-600 hover:underline">{post.title}</Link>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{post.authorName}</td>
                  <td className="px-4 py-3 text-slate-600">{post.viewCount}</td>
                  <td className="px-4 py-3 text-slate-600">{post.likeCount}</td>
                  <td className="px-4 py-3 text-slate-600">{new Date(post.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {posts.length === 0 && <p className="px-4 py-10 text-center text-sm text-slate-500">게시글이 없습니다.</p>}
      </Card>

      <div className="flex items-center justify-center gap-3">
        <Button
          variant="outline"
          size="sm"
          disabled={page === 0}
          onClick={() => setSearchParams({ q: queryKeyword, page: String(page - 1) })}
        >
          이전
        </Button>
        <span className="text-sm font-medium text-slate-600">{page + 1} / {totalPages}</span>
        <Button
          variant="outline"
          size="sm"
          disabled={page >= totalPages - 1}
          onClick={() => setSearchParams({ q: queryKeyword, page: String(page + 1) })}
        >
          다음
        </Button>
      </div>
    </div>
  );
}
