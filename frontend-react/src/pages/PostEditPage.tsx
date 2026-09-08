import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../lib/axios';
import { useAuthStore } from '../store/authStore';
import { Button, Card, Field, Input, Textarea } from '../shared/ui';

export const PostEditPage: React.FC = () => {
  const { slug, id } = useParams<{ slug?: string; id?: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [guestNickname, setGuestNickname] = useState('');
  const [guestPassword, setGuestPassword] = useState('');
  const [attachments, setAttachments] = useState<any[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isEdit) {
      api.get(`/posts/${id}`).then(res => {
        setTitle(res.data.title);
        setContent(res.data.content);
        setAttachments(res.data.attachments || []);
      }).catch(() => setError('게시글을 불러오지 못했습니다.'));
    }
  }, [id, isEdit]);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files?.length) return;
    const formData = new FormData();
    formData.append('file', e.target.files[0]);
    try {
      const res = await api.post('/files', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
      setAttachments(prev => [...prev, res.data]);
    } catch {
      setError('파일 업로드에 실패했습니다.');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const payload = {
      title,
      content,
      guestNickname: !isAuthenticated ? guestNickname : undefined,
      guestPassword: guestPassword || undefined,
      attachments: isEdit ? undefined : attachments,
    };

    try {
      if (isEdit) {
        await api.put(`/posts/${id}`, payload);
        navigate(`/posts/${id}`);
      } else {
        const res = await api.post(`/boards/${slug}/posts`, payload);
        navigate(`/posts/${res.data.id}`);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '게시글 저장에 실패했습니다.');
    }
  };

  return (
    <Card className="mx-auto max-w-2xl p-6">
      <div className="mb-6">
        <p className="text-sm font-semibold uppercase tracking-wider text-indigo-600">Post</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">{isEdit ? '게시물 수정' : '새 게시물 작성'}</h1>
      </div>
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
        {!isAuthenticated && !isEdit && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="작성자명" htmlFor="guestNickname">
              <Input id="guestNickname" value={guestNickname} onChange={e => setGuestNickname(e.target.value)} required />
            </Field>
            <Field label="작성자 비밀번호" htmlFor="guestPassword" hint="수정·삭제 시 필요합니다.">
              <Input id="guestPassword" type="password" value={guestPassword} onChange={e => setGuestPassword(e.target.value)} required />
            </Field>
          </div>
        )}
        {isEdit && !isAuthenticated && (
          <Field label="작성자 비밀번호" htmlFor="guestPassword">
            <Input id="guestPassword" type="password" value={guestPassword} onChange={e => setGuestPassword(e.target.value)} required />
          </Field>
        )}
        <Field label="제목" htmlFor="title">
          <Input id="title" value={title} onChange={e => setTitle(e.target.value)} required />
        </Field>
        <Field label="내용" htmlFor="content">
          <Textarea id="content" rows={12} value={content} onChange={e => setContent(e.target.value)} required />
        </Field>
        {!isEdit && (
          <Field label="첨부파일" htmlFor="attachment" hint="자료실 게시판에서만 사용하세요.">
            <Input id="attachment" type="file" onChange={handleFileChange} className="file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-1 file:text-sm file:font-medium file:text-indigo-700" />
            <div className="space-y-1 text-sm text-slate-500">
              {attachments.map(a => <p key={a.storedName}>{a.originalName}</p>)}
            </div>
          </Field>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>취소</Button>
          <Button type="submit">{isEdit ? '수정 완료' : '등록'}</Button>
        </div>
      </form>
    </Card>
  );
};
