import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ChangePasswordPage } from '../pages/ChangePasswordPage';
import { useAuthStore } from '../store/authStore';
import { BrowserRouter } from 'react-router-dom';

// Mock dependencies
vi.mock('../lib/axios');
vi.mock('../lib/session');
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => vi.fn(),
  };
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
    mutations: { retry: false },
  },
});

const Wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={queryClient}>
    <BrowserRouter>
      {children}
    </BrowserRouter>
  </QueryClientProvider>
);

describe('ChangePasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render password change form when mustChangePassword is true', () => {
    useAuthStore.setState({
      user: {
        id: 1,
        username: 'testuser',
        name: 'Test User',
        email: 'test@example.com',
        status: 'ACTIVE',
        role: 'USER',
        mustChangePassword: true,
      },
    });

    render(<ChangePasswordPage />, { wrapper: Wrapper });

    expect(screen.getByText('비밀번호 변경')).toBeInTheDocument();
    expect(screen.getByLabelText('새 비밀번호')).toBeInTheDocument();
    expect(screen.getByLabelText('새 비밀번호 확인')).toBeInTheDocument();
  });

  it('should show message when mustChangePassword is false', () => {
    useAuthStore.setState({
      user: {
        id: 1,
        username: 'testuser',
        name: 'Test User',
        email: 'test@example.com',
        status: 'ACTIVE',
        role: 'USER',
        mustChangePassword: false,
      },
    });

    render(<ChangePasswordPage />, { wrapper: Wrapper });

    expect(screen.getByText('비밀번호 변경이 필요하지 않습니다.')).toBeInTheDocument();
  });

  describe('Password Strength Display', () => {
    beforeEach(() => {
      useAuthStore.setState({
        user: {
          id: 1,
          username: 'testuser',
          name: 'Test User',
          email: 'test@example.com',
          status: 'ACTIVE',
          role: 'USER',
          mustChangePassword: true,
        },
      });
    });

    it('should display strength indicator for weak password', async () => {
      const user = userEvent.setup();
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const passwordInput = screen.getByLabelText('새 비밀번호');
      await user.type(passwordInput, 'pass');

      await waitFor(() => {
        expect(screen.getByText(/강도:/)).toBeInTheDocument();
        expect(screen.getByText('약함')).toBeInTheDocument();
      });
    });

    it('should display strength indicator for fair password', async () => {
      const user = userEvent.setup();
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const passwordInput = screen.getByLabelText('새 비밀번호');
      await user.type(passwordInput, 'MyPass123');

      await waitFor(() => {
        expect(screen.getByText('보통')).toBeInTheDocument();
      });
    });

    it('should display strength indicator for strong password', async () => {
      const user = userEvent.setup();
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const passwordInput = screen.getByLabelText('새 비밀번호');
      await user.type(passwordInput, 'MyNewPassword123!');

      await waitFor(() => {
        expect(screen.getByText('좋음')).toBeInTheDocument();
      });
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      useAuthStore.setState({
        user: {
          id: 1,
          username: 'testuser',
          name: 'Test User',
          email: 'test@example.com',
          status: 'ACTIVE',
          role: 'USER',
          mustChangePassword: true,
        },
      });
    });

    it('should disable submit button initially', () => {
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const submitButton = screen.getByRole('button', { name: /비밀번호 변경/ });
      expect(submitButton).toBeDisabled();
    });

    it('should disable submit button if passwords do not match', async () => {
      const user = userEvent.setup();
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const passwordInput = screen.getByLabelText('새 비밀번호');
      const confirmInput = screen.getByLabelText('새 비밀번호 확인');
      const submitButton = screen.getByRole('button', { name: /비밀번호 변경/ });

      await user.type(passwordInput, 'Password123!');
      await user.type(confirmInput, 'DifferentPassword123!');

      expect(submitButton).toBeDisabled();
    });

    it('should enable submit button when password is valid and matches', async () => {
      const user = userEvent.setup();
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const passwordInput = screen.getByLabelText('새 비밀번호');
      const confirmInput = screen.getByLabelText('새 비밀번호 확인');
      const submitButton = screen.getByRole('button', { name: /비밀번호 변경/ });

      await user.type(passwordInput, 'Password123!');
      await user.type(confirmInput, 'Password123!');

      await waitFor(() => {
        expect(submitButton).not.toBeDisabled();
      });
    });

    it('should show mismatch error when passwords do not match', async () => {
      const user = userEvent.setup();
      render(<ChangePasswordPage />, { wrapper: Wrapper });

      const passwordInput = screen.getByLabelText('새 비밀번호');
      const confirmInput = screen.getByLabelText('새 비밀번호 확인');
      const submitButton = screen.getByRole('button', { name: /비밀번호 변경/ });

      await user.type(passwordInput, 'Password123!');
      await user.type(confirmInput, 'Different123!');
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument();
      });
    });
  });
});
