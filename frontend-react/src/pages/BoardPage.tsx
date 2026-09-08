import React, { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../lib/axios';
import { Button, Card, Input } from '../shared/ui';

export const BoardPage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Number(searchParams.get('page') || 0);
  const queryKeyword = searchParams.get('q') || '';
  const [board, setBoard] = useState<any>(null);
  const [posts, setPosts] = useState<any[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [keyword, setKeyword] = useState(queryKeyword);

  useEffect(() => {
    api.get(`/boards/${slug}`).then(res => setBoard(res.data)).catch(console.error);
  }, [slug]);

  useEffect(() => {
    api.get(`/boards/${slug}/posts`, { params: { page, size: 10, keyword: queryKeyword } })
      .then(res => {
        setPosts(res.data.content);
        setTotalPages(res.data.totalPages);
      })
      .catch(console.error);
  }, [slug, page, queryKeyword]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchParams(keyword ? { q: keyword, page: '0' } : { page: '0' });
  };

  if (!board) return <p className="text-sm text-slate-500">게시판을 불러오는 중입니다...</p>;

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Board</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{board.name}</h1>
        </div>
        <Link to={`/boards/${slug}/posts/new`}><Button size="sm">글쓰기</Button></Link>
      </div>

      <form onSubmit={handleSearch} className="flex gap-2">
        <Input
          type="text"
          placeholder="검색어 입력..."
          className="flex-1"
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
        />
        <Button type="submit" variant="secondary" size="sm">검색</Button>
      </form>

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
};
