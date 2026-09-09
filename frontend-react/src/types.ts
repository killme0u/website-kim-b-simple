export type MemberRole = 'USER' | 'ADMIN';
export type MemberStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
export type MediaKind = 'IMAGE' | 'VIDEO' | 'AUDIO' | 'FILE';

export interface User {
  id: number;
  username: string;
  name: string;
  email: string;
  status: MemberStatus;
  role: MemberRole;
  mustChangePassword: boolean;
}

export interface Board {
  slug: string;
  name: string;
  requiresAuthToRead: boolean;
  requiresAuthToWrite: boolean;
  allowsComment: boolean;
  allowsAttachment: boolean;
}

export interface Attachment {
  originalName: string;
  storedName: string;
  contentType: string;
  mediaKind: MediaKind;
  byteSize: number;
}

export interface PostListItem {
  id: number;
  title: string;
  authorName: string;
  viewCount: number;
  likeCount: number;
  createdAt: string;
}

export interface Post extends PostListItem {
  boardSlug: string;
  content: string;
  owner: boolean;
  attachments: Attachment[];
}

export interface Comment {
  id: number;
  postId: number;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  owner: boolean;
}

/** Spring Data {@code Page<T>} 직렬화 형태 중 화면에서 쓰는 필드만. */
export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
}
