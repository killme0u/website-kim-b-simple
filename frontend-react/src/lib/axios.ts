import axios from 'axios';

export const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // Necessary if backend utilizes Cookie session or CSRF
});

// For intercepting responses, especially 401s globally if necessary
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // We can handle global 401 redirect here if we want, or leave it to TanStack Query config
    return Promise.reject(error);
  }
);