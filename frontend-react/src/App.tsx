import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { RootLayout } from './layouts/RootLayout';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { MyPage } from './pages/MyPage';
import { BoardPage } from './pages/BoardPage';
import { PostPage } from './pages/PostPage';
import { PostEditPage } from './pages/PostEditPage';
import { FindUsernamePage } from './pages/FindUsernamePage';
import { FindPasswordPage } from './pages/FindPasswordPage';
import { useAuthStore } from './store/authStore';
import { api } from './lib/axios';

export const App: React.FC = () => {
  const { setUser } = useAuthStore();

  useEffect(() => {
    api.get('/me')
      .then(res => setUser(res.data))
      .catch(() => setUser(null));
  }, [setUser]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RootLayout />}>
          <Route index element={<HomePage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="signup" element={<SignupPage />} />
          <Route path="find-username" element={<FindUsernamePage />} />
          <Route path="find-password" element={<FindPasswordPage />} />
          <Route path="me" element={<MyPage />} />
          <Route path="boards/:slug" element={<BoardPage />} />
          <Route path="boards/:slug/posts/new" element={<PostEditPage />} />
          <Route path="posts/:id" element={<PostPage />} />
          <Route path="posts/:id/edit" element={<PostEditPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};