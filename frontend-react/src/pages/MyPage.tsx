import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { LoginRequired } from '../components/LoginRequired';
import { Alert, Card } from '../shared/ui';
import type { Comment, PageResponse, PostListItem } from '../types';

export function MyPage() {
  const { user, isAuthenticated } = useAuthStore();

  const postsQuery = useQuery({
    queryKey: ['my-posts'],
    queryFn: async () => (
      await api.get<PageResponse<PostListItem>>('/me/posts', { params: { page: 0, size: 5 } })
    ).data,
    enabled: isAuthenticated,
  });

  const commentsQuery = useQuery({
    queryKey: ['my-comments'],
    queryFn: async () => (
      await api.get<PageResponse<Comment>>('/me/comments', { params: { page: 0, size: 5 } })
    ).data,
    enabled: isAuthenticated,
  });

  if (!isAuthenticated) {
    return <LoginRequired message="마이페이지는 로그인 후 이용할 수 있습니다." />;
  }

  const posts = postsQuery.data?.content ?? [];
  const comments = commentsQuery.data?.content ?? [];
  const loadError = postsQuery.error ?? commentsQuery.error;

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Account</p>
        <h1 className="mt-2 text-3xl font-bold text-slate-900">마이페이지</h1>
      </div>
      {loadError && (
        <Alert tone="error">{errorMessage(loadError, '마이페이지 정보를 불러오지 못했습니다.')}</Alert>
      )}
      <Card className="p-6">
        <h2 className="text-lg font-semibold text-slate-900">내 정보</h2>
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-slate-500">아이디</dt><dd className="font-medium text-slate-800">{user?.username}</dd></div>
          <div><dt className="text-slate-500">이름</dt><dd className="font-medium text-slate-800">{user?.name}</dd></div>
          <div><dt className="text-slate-500">이메일</dt><dd className="font-medium text-slate-800">{user?.email}</dd></div>
          <div><dt className="text-slate-500">권한</dt><dd className="font-medium text-slate-800">{user?.role}</dd></div>
        </dl>
        {user?.status === 'PENDING' && (
          <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-700">
            이메일 인증이 아직 완료되지 않았습니다. 가입 시 받은 인증 메일의 링크를 눌러 주세요.
          </p>
        )}
      </Card>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-slate-900">내 게시글</h2>
          <div className="mt-4 divide-y divide-slate-100">
            {posts.map(post => (
              <Link key={post.id} to={`/posts/${post.id}`} className="block py-3 text-sm text-indigo-600 hover:underline">
                {post.title}
              </Link>
            ))}
            {posts.length === 0 && <p className="py-3 text-sm text-slate-500">작성한 게시글이 없습니다.</p>}
          </div>
        </Card>
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-slate-900">내 댓글</h2>
          <div className="mt-4 divide-y divide-slate-100">
            {comments.map(comment => (
              <Link key={comment.id} to={`/posts/${comment.postId}`} className="block py-3 text-sm text-slate-600 hover:underline">
                {comment.content}
              </Link>
            ))}
            {comments.length === 0 && <p className="py-3 text-sm text-slate-500">작성한 댓글이 없습니다.</p>}
          </div>
        </Card>
      </div>
    </div>
  );
}
