import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { Alert, Button, Card } from '../shared/ui';
import type { PageResponse, PostListItem } from '../types';

export function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Number(searchParams.get('page') ?? 0);
  const queryKeyword = searchParams.get('q') ?? '';

  const resultsQuery = useQuery({
    queryKey: ['search', queryKeyword, page],
    queryFn: async () => (
      await api.get<PageResponse<PostListItem>>('/posts/search', {
        params: { keyword: queryKeyword, page, size: 10 },
      })
    ).data,
    enabled: !!queryKeyword,
    placeholderData: keepPreviousData,
  });

  const posts = resultsQuery.data?.content ?? [];
  const totalPages = Math.max(resultsQuery.data?.totalPages ?? 1, 1);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Search Results</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
          "{queryKeyword}" 검색 결과
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          {resultsQuery.isLoading ? '검색 중...' : `총 ${resultsQuery.data?.totalElements ?? 0}개의 게시글을 찾았습니다.`}
        </p>
      </div>

      {resultsQuery.isError && (
        <Alert tone="error">{errorMessage(resultsQuery.error, '검색 중 오류가 발생했습니다.')}</Alert>
      )}

      {!queryKeyword ? (
        <Alert tone="info">검색어를 입력해 주세요.</Alert>
      ) : posts.length === 0 && !resultsQuery.isLoading ? (
        <Alert tone="info">검색 결과가 없습니다.</Alert>
      ) : (
        <>
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-semibold">번호</th>
                    <th className="px-4 py-3 font-semibold">게시판</th>
                    <th className="px-4 py-3 font-semibold">제목</th>
                    <th className="px-4 py-3 font-semibold">작성자</th>
                    <th className="px-4 py-3 font-semibold">조회수</th>
                    <th className="px-4 py-3 font-semibold">작성일</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {posts.map(post => (
                    <tr key={post.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 text-sm font-medium text-slate-900">{post.sequenceNumber ?? post.id}</td>
                      <td className="px-4 py-3 text-sm text-slate-500">{post.boardName}</td>
                      <td className="px-4 py-3 font-medium">
                        <Link to={`/posts/${post.id}`} className="text-indigo-600 hover:underline">{post.title}</Link>
                      </td>
                      <td className="px-4 py-3 text-slate-600">{post.authorName}</td>
                      <td className="px-4 py-3 text-slate-600">{post.viewCount}</td>
                      <td className="px-4 py-3 text-slate-600">{new Date(post.createdAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
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
        </>
      )}
    </div>
  );
}
