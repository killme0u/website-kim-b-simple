import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage, isUnauthorized } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { LoginRequired } from '../components/LoginRequired';
import { Alert, Button, Card, Field, Input, Textarea } from '../shared/ui';
import type { Attachment, Board, Post } from '../types';

export function PostEditPage() {
  const { slug, id } = useParams<{ slug?: string; id?: string }>();
  const isEdit = id !== undefined;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [guestNickname, setGuestNickname] = useState('');
  const [guestPassword, setGuestPassword] = useState('');
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [error, setError] = useState('');

  const boardQuery = useQuery({
    queryKey: ['board', slug],
    queryFn: async () => (await api.get<Board>(`/boards/${slug}`)).data,
    enabled: !isEdit && slug !== undefined,
  });

  const postQuery = useQuery({
    queryKey: ['post', id],
    queryFn: async () => (await api.get<Post>(`/posts/${id}`)).data,
    enabled: isEdit,
  });

  // 수정 화면은 서버에서 읽어온 글로 폼을 한 번 채운다.
  // effect 대신 렌더 중 상태 조정(React 공식 권장 패턴)을 쓰면 추가 렌더 왕복이 없다.
  const [loadedPostId, setLoadedPostId] = useState<number | null>(null);
  if (postQuery.data && loadedPostId !== postQuery.data.id) {
    setLoadedPostId(postQuery.data.id);
    setTitle(postQuery.data.title);
    setContent(postQuery.data.content);
    setAttachments(postQuery.data.attachments ?? []);
  }

  const uploadFile = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      const { data } = await api.post<Attachment>('/files', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return data;
    },
    onSuccess: uploaded => setAttachments(previous => [...previous, uploaded]),
    onError: e => setError(errorMessage(e, '파일 업로드에 실패했습니다.')),
  });

  const save = useMutation({
    mutationFn: async () => {
      const payload = {
        title,
        content,
        guestNickname: isAuthenticated ? undefined : guestNickname,
        guestPassword: guestPassword || undefined,
        attachments: isEdit ? undefined : attachments,
      };
      if (isEdit) {
        await api.put(`/posts/${id}`, payload);
        return Number(id);
      }
      const { data } = await api.post<{ id: number }>(`/boards/${slug}/posts`, payload);
      return data.id;
    },
    onSuccess: postId => {
      void queryClient.invalidateQueries({ queryKey: ['posts'] });
      void queryClient.invalidateQueries({ queryKey: ['post', String(postId)] });
      navigate(`/posts/${postId}`);
    },
    onError: e => setError(errorMessage(e, '게시글 저장에 실패했습니다.')),
  });

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) uploadFile.mutate(file);
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    setError('');
    save.mutate();
  };

  // 회원 전용 게시판은 목록·글쓰기 모두 로그인이 필요하다.
  const gateError = boardQuery.error ?? postQuery.error;
  if (isUnauthorized(gateError)) {
    return <LoginRequired message={errorMessage(gateError, '로그인 후 이용할 수 있습니다.')} />;
  }

  if (!isEdit && boardQuery.isPending) {
    return <p className="text-sm text-slate-500">게시판 정보를 불러오는 중입니다...</p>;
  }

  const board = boardQuery.data;
  const guestWritable = !isEdit && !isAuthenticated && board?.requiresAuthToWrite === false;
  const showGuestCredentials = !isAuthenticated && (isEdit || guestWritable);

  return (
    <Card className="mx-auto max-w-2xl p-6">
      <div className="mb-6">
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Post</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">{isEdit ? '게시물 수정' : '새 게시물 작성'}</h1>
      </div>
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <Alert tone="error">{error}</Alert>}
        {!isEdit && !isAuthenticated && board?.requiresAuthToWrite && (
          <Alert tone="error">이 게시판은 로그인해야 글을 쓸 수 있습니다.</Alert>
        )}
        {showGuestCredentials && !isEdit && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="작성자명" htmlFor="guestNickname">
              <Input id="guestNickname" value={guestNickname} onChange={event => setGuestNickname(event.target.value)} required />
            </Field>
            <Field label="작성자 비밀번호" htmlFor="guestPassword" hint="수정·삭제 시 필요합니다.">
              <Input id="guestPassword" type="password" value={guestPassword} onChange={event => setGuestPassword(event.target.value)} required />
            </Field>
          </div>
        )}
        {showGuestCredentials && isEdit && (
          <Field label="작성자 비밀번호" htmlFor="guestPassword">
            <Input id="guestPassword" type="password" value={guestPassword} onChange={event => setGuestPassword(event.target.value)} required />
          </Field>
        )}
        <Field label="제목" htmlFor="title">
          <Input id="title" value={title} onChange={event => setTitle(event.target.value)} required />
        </Field>
        <Field label="내용" htmlFor="content">
          <Textarea id="content" rows={12} value={content} onChange={event => setContent(event.target.value)} required />
        </Field>
        {!isEdit && board?.allowsAttachment && (
          <Field label="첨부파일" htmlFor="attachment">
            <Input
              id="attachment"
              type="file"
              onChange={handleFileChange}
              disabled={uploadFile.isPending}
              className="file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-1 file:text-sm file:font-medium file:text-indigo-700"
            />
            <div className="space-y-1 text-sm text-slate-500">
              {attachments.map(attachment => <p key={attachment.storedName}>{attachment.originalName}</p>)}
            </div>
          </Field>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>취소</Button>
          <Button type="submit" disabled={save.isPending}>{isEdit ? '수정 완료' : '등록'}</Button>
        </div>
      </form>
    </Card>
  );
}
