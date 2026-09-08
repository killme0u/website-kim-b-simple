import React, { useEffect, useState } from 'react';
import { api } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { Card } from '../shared/ui';

export const MyPage: React.FC = () => {
  const user = useAuthStore(state => state.user);
  const [posts, setPosts] = useState<any[]>([]);
  const [comments, setComments] = useState<any[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      api.get('/me/posts', { params: { page: 0, size: 5 } }),
      api.get('/me/comments', { params: { page: 0, size: 5 } }),
    ]).then(([postResponse, commentResponse]) => {
      setPosts(postResponse.data.content || []);
      setComments(commentResponse.data.content || []);
    }).catch((err: any) => setError(err.response?.data?.message || '마이페이지 정보를 불러오지 못했습니다.'));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Account</p>
        <h1 className="mt-2 text-3xl font-bold text-slate-900">마이페이지</h1>
      </div>
      {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
      <Card className="p-6">
        <h2 className="text-lg font-semibold text-slate-900">내 정보</h2>
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-slate-500">아이디</dt><dd className="font-medium text-slate-800">{user?.username}</dd></div>
          <div><dt className="text-slate-500">이름</dt><dd className="font-medium text-slate-800">{user?.name}</dd></div>
          <div><dt className="text-slate-500">이메일</dt><dd className="font-medium text-slate-800">{user?.email}</dd></div>
          <div><dt className="text-slate-500">권한</dt><dd className="font-medium text-slate-800">{user?.role}</dd></div>
        </dl>
      </Card>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-slate-900">내 게시글</h2>
          <div className="mt-4 divide-y divide-slate-100">
            {posts.map(post => <a key={post.id} href={`/posts/${post.id}`} className="block py-3 text-sm text-indigo-600 hover:underline">{post.title}</a>)}
            {!posts.length && <p className="py-3 text-sm text-slate-500">작성한 게시글이 없습니다.</p>}
          </div>
        </Card>
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-slate-900">내 댓글</h2>
          <div className="mt-4 divide-y divide-slate-100">
            {comments.map(comment => <p key={comment.id} className="py-3 text-sm text-slate-600">{comment.content}</p>)}
            {!comments.length && <p className="py-3 text-sm text-slate-500">작성한 댓글이 없습니다.</p>}
          </div>
        </Card>
      </div>
    </div>
  );
};