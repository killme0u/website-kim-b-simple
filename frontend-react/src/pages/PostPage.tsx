import React, { useCallback, useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { Alert, Button, Card, Dialog, Input, Textarea } from '../shared/ui';

export const PostPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [post, setPost] = useState<any>(null);
  const [comments, setComments] = useState<any[]>([]);
  const [newComment, setNewComment] = useState('');
  const [guestPassword, setGuestPassword] = useState('');
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [error, setError] = useState('');
  const { isAuthenticated, isAdmin } = useAuthStore();
  const navigate = useNavigate();

  const fetchData = useCallback(() => {
    api.get(`/posts/${id}`).then(res => setPost(res.data)).catch(() => setError('게시글을 불러오지 못했습니다.'));
    api.get(`/posts/${id}/comments`).then(res => setComments(res.data)).catch(() => setError('댓글을 불러오지 못했습니다.'));
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleDeletePost = async () => {
    try {
      await api.delete(`/posts/${id}`, isAdmin ? undefined : { params: { guestPassword } });
      setGuestPassword('');
      setDeleteDialogOpen(false);
      navigate(`/boards/${post.boardSlug}`);
    } catch (e: any) {
      setError(e.response?.data?.message || '게시글 삭제에 실패했습니다.');
    }
  };

  const handleLike = async () => {
    try {
      await api.post(`/posts/${id}/like`);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '로그인이 필요합니다.');
    }
  };

  const handleCommentSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    try {
      await api.post(`/posts/${id}/comments`, { content: newComment });
      setNewComment('');
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '댓글 등록에 실패했습니다.');
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    if (!window.confirm('댓글을 삭제하시겠습니까?')) return;
    try {
      await api.delete(`/comments/${commentId}`);
      fetchData();
    } catch (e: any) {
      setError(e.response?.data?.message || '댓글 삭제에 실패했습니다.');
    }
  };

  if (!post) return <p className="text-sm text-slate-500">게시글을 불러오는 중입니다...</p>;

  return (
    <div className="space-y-6">
      {error && <Alert tone="error">{error}</Alert>}
      <Card className="p-6">
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-500">
          <span>{post.boardSlug}</span>
          <span>작성일: {new Date(post.createdAt).toLocaleString()}</span>
          <span>작성자: {post.authorName}</span>
          <span>조회 {post.viewCount}</span>
        </div>
        <h1 className="mt-4 border-b border-slate-200 pb-4 text-2xl font-bold text-slate-900">{post.title}</h1>
        <article className="prose prose-slate max-w-none py-6">
          <ReactMarkdown>{post.content}</ReactMarkdown>
        </article>

        {post.attachments?.length > 0 && (
          <div className="rounded-xl bg-slate-50 p-4">
            <h2 className="text-sm font-semibold text-slate-900">첨부파일</h2>
            <ul className="mt-2 space-y-2 text-sm">
              {post.attachments.map((a: any) => {
                const src = `/api/files/${a.storedName}`;
                if (a.mediaKind === 'IMAGE') return <li key={a.storedName}><img src={src} alt={a.originalName} className="max-h-64 rounded-lg" /></li>;
                if (a.mediaKind === 'VIDEO') return <li key={a.storedName}><video src={src} controls className="max-h-64" /></li>;
                if (a.mediaKind === 'AUDIO') return <li key={a.storedName}><audio src={src} controls /></li>;
                return <li key={a.storedName}><a href={`${src}?download=true`} className="text-indigo-600 hover:underline">{a.originalName}</a></li>;
              })}
            </ul>
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4">
          <Button variant="outline" size="sm" onClick={handleLike}>♥ 추천 {post.likeCount}</Button>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => navigate(`/boards/${post.boardSlug}`)}>목록</Button>
            {post.owner && <Link to={`/posts/${id}/edit`}><Button variant="outline" size="sm">수정</Button></Link>}
            {(post.owner || isAdmin) && (
              <Button variant="danger" size="sm" onClick={() => isAdmin ? handleDeletePost() : setDeleteDialogOpen(true)}>삭제</Button>
            )}
          </div>
        </div>
      </Card>

      <Card className="p-6">
        <h2 className="text-xl font-bold text-slate-900">댓글 <span className="text-slate-400">({comments.length})</span></h2>
        <div className="mt-5 divide-y divide-slate-100">
          {comments.map(c => (
            <div key={c.id} className="py-4 first:pt-0">
              <div className="flex justify-between gap-4 text-sm">
                <span className="font-medium text-slate-700">
                  {c.authorName} <span className="ml-2 font-normal text-slate-400">{new Date(c.createdAt).toLocaleString()}</span>
                </span>
                {c.owner && <button type="button" onClick={() => handleDeleteComment(c.id)} className="text-sm text-rose-600 hover:underline">삭제</button>}
              </div>
              <p className="mt-2 whitespace-pre-wrap text-slate-600">{c.content}</p>
            </div>
          ))}
        </div>
        <form onSubmit={handleCommentSubmit} className="mt-5 flex flex-col gap-2 sm:flex-row">
          <Textarea
            rows={2}
            placeholder={isAuthenticated ? '댓글을 입력하세요...' : '로그인 후 작성 가능합니다.'}
            value={newComment}
            onChange={e => setNewComment(e.target.value)}
            disabled={!isAuthenticated}
            className="flex-1"
          />
          <Button type="submit" disabled={!isAuthenticated}>등록</Button>
        </form>
      </Card>

      <Dialog
        open={deleteDialogOpen}
        title="게시글 삭제"
        description="삭제하려면 작성자 비밀번호를 입력하세요."
        onClose={() => {
          setDeleteDialogOpen(false);
          setGuestPassword('');
        }}
      >
        <div className="space-y-4">
          <Input
            type="password"
            value={guestPassword}
            onChange={e => setGuestPassword(e.target.value)}
            placeholder="작성자 비밀번호"
            autoFocus
          />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setDeleteDialogOpen(false)}>취소</Button>
            <Button variant="danger" onClick={handleDeletePost} disabled={!guestPassword}>삭제</Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
};
