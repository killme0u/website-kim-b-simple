export {};

declare global {
  interface Window {
    turnstile?: {
      ready(callback: () => void): void;
      render(container: string | HTMLElement, options: {
        sitekey: string;
        callback?: (token: string) => void;
        'expired-callback'?: () => void;
        'error-callback'?: () => void;
        theme?: 'light' | 'dark' | 'auto';
      }): string;
      remove(widgetId: string): void;
      reset(widgetId?: string): void;
    };
  }
}
