import { useState, type FormEvent } from 'react';
import ReactMarkdown from 'react-markdown';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage, isUnauthorized } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { LoginRequired } from '../components/LoginRequired';
import { Alert, Button, Card, Dialog, Input, Textarea } from '../shared/ui';
import type { Attachment, Comment, Post } from '../types';

const MEMBERS_ONLY_MESSAGE = '회원 전용 게시판의 글입니다. 로그인 후 이용해 주세요.';

function AttachmentItem({ attachment }: { attachment: Attachment }) {
  const src = `/api/files/${attachment.storedName}`;
  switch (attachment.mediaKind) {
    case 'IMAGE':
      return <img src={src} alt={attachment.originalName} className="max-h-64 rounded-lg" />;
    case 'VIDEO':
      return <video src={src} controls className="max-h-64" />;
    case 'AUDIO':
      return <audio src={src} controls />;
    default:
      return <a href={`${src}?download=true`} className="text-indigo-600 hover:underline">{attachment.originalName}</a>;
  }
}

export function PostPage() {
  const { id } = useParams<{ id: string }>();
  const [newComment, setNewComment] = useState('');
  const [guestPassword, setGuestPassword] = useState('');
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [actionError, setActionError] = useState('');
  const { isAuthenticated, isAdmin } = useAuthStore();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const postQuery = useQuery({
    queryKey: ['post', id],
    queryFn: async () => (await api.get<Post>(`/posts/${id}`)).data,
  });

  const commentsQuery = useQuery({
    queryKey: ['comments', id],
    queryFn: async () => (await api.get<Comment[]>(`/posts/${id}/comments`)).data,
    enabled: postQuery.isSuccess,
  });

  const invalidatePost = () => {
    void queryClient.invalidateQueries({ queryKey: ['post', id] });
    void queryClient.invalidateQueries({ queryKey: ['comments', id] });
  };

  const deletePost = useMutation({
    mutationFn: () => api.delete(`/posts/${id}`, isAdmin ? undefined : { params: { guestPassword } }),
    onSuccess: () => {
      setGuestPassword('');
      setDeleteDialogOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['posts'] });
      navigate(`/boards/${postQuery.data?.boardSlug ?? ''}`);
    },
    onError: error => setActionError(errorMessage(error, '게시글 삭제에 실패했습니다.')),
  });

  const toggleLike = useMutation({
    mutationFn: () => api.post(`/posts/${id}/like`),
    onSuccess: invalidatePost,
    onError: error => setActionError(errorMessage(error, '추천하려면 로그인이 필요합니다.')),
  });

  const addComment = useMutation({
    mutationFn: () => api.post(`/posts/${id}/comments`, { content: newComment }),
    onSuccess: () => {
      setNewComment('');
      invalidatePost();
    },
    onError: error => setActionError(errorMessage(error, '댓글 등록에 실패했습니다.')),
  });

  const deleteComment = useMutation({
    mutationFn: (commentId: number) => api.delete(`/comments/${commentId}`),
    onSuccess: invalidatePost,
    onError: error => setActionError(errorMessage(error, '댓글 삭제에 실패했습니다.')),
  });

  const handleCommentSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!newComment.trim()) return;
    setActionError('');
    addComment.mutate();
  };

  const handleDeleteComment = (commentId: number) => {
    if (window.confirm('댓글을 삭제하시겠습니까?')) {
      deleteComment.mutate(commentId);
    }
  };

  // 회원 전용 게시판의 글은 비로그인 상태에서 401이 돌아온다.
  if (isUnauthorized(postQuery.error)) {
    return <LoginRequired message={errorMessage(postQuery.error, MEMBERS_ONLY_MESSAGE)} />;
  }
  if (postQuery.isError) {
    return <Alert tone="error">{errorMessage(postQuery.error, '게시글을 불러오지 못했습니다.')}</Alert>;
  }
  if (!postQuery.data) {
    return <p className="text-sm text-slate-500">게시글을 불러오는 중입니다...</p>;
  }

  const post = postQuery.data;
  const comments = commentsQuery.data ?? [];

  return (
    <div className="space-y-6">
      {actionError && <Alert tone="error">{actionError}</Alert>}
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
              {post.attachments.map(attachment => (
                <li key={attachment.storedName}>
                  <AttachmentItem attachment={attachment} />
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4">
          <Button variant="outline" size="sm" onClick={() => toggleLike.mutate()}>♥ 추천 {post.likeCount}</Button>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => navigate(`/boards/${post.boardSlug}`)}>목록</Button>
            {post.owner && <Link to={`/posts/${id}/edit`}><Button variant="outline" size="sm">수정</Button></Link>}
            {(post.owner || isAdmin) && (
              <Button
                variant="danger"
                size="sm"
                onClick={() => (isAdmin ? deletePost.mutate() : setDeleteDialogOpen(true))}
              >
                삭제
              </Button>
            )}
          </div>
        </div>
      </Card>

      <Card className="p-6">
        <h2 className="text-xl font-bold text-slate-900">댓글 <span className="text-slate-400">({comments.length})</span></h2>
        <div className="mt-5 divide-y divide-slate-100">
          {comments.map(comment => (
            <div key={comment.id} className="py-4 first:pt-0">
              <div className="flex justify-between gap-4 text-sm">
                <span className="font-medium text-slate-700">
                  {comment.authorName}
                  <span className="ml-2 font-normal text-slate-400">{new Date(comment.createdAt).toLocaleString()}</span>
                </span>
                {comment.owner && (
                  <button
                    type="button"
                    onClick={() => handleDeleteComment(comment.id)}
                    className="text-sm text-rose-600 hover:underline"
                  >
                    삭제
                  </button>
                )}
              </div>
              <p className="mt-2 whitespace-pre-wrap text-slate-600">{comment.content}</p>
            </div>
          ))}
        </div>
        <form onSubmit={handleCommentSubmit} className="mt-5 flex flex-col gap-2 sm:flex-row">
          <Textarea
            rows={2}
            placeholder={isAuthenticated ? '댓글을 입력하세요...' : '로그인 후 작성 가능합니다.'}
            value={newComment}
            onChange={event => setNewComment(event.target.value)}
            disabled={!isAuthenticated}
            className="flex-1"
          />
          <Button type="submit" disabled={!isAuthenticated || addComment.isPending}>등록</Button>
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
            onChange={event => setGuestPassword(event.target.value)}
            placeholder="작성자 비밀번호"
            autoFocus
          />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setDeleteDialogOpen(false)}>취소</Button>
            <Button variant="danger" onClick={() => deletePost.mutate()} disabled={!guestPassword}>삭제</Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
