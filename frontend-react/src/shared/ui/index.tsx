import React, { forwardRef, useEffect, useRef } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';

const buttonStyles: Record<ButtonVariant, string> = {
  primary: 'bg-slate-900 text-white hover:bg-slate-700',
  secondary: 'bg-indigo-600 text-white hover:bg-indigo-500',
  outline: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
  ghost: 'text-slate-600 hover:bg-slate-100',
  danger: 'bg-rose-600 text-white hover:bg-rose-500',
};

export const Button = forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: 'sm' | 'md';
}>(({ className = '', variant = 'primary', size = 'md', ...props }, ref) => (
  <button
    ref={ref}
    className={`inline-flex items-center justify-center rounded-lg font-semibold transition focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${
      size === 'sm' ? 'px-3 py-1.5 text-sm' : 'px-4 py-2 text-sm'
    } ${buttonStyles[variant]} ${className}`}
    {...props}
  />
));
Button.displayName = 'Button';

export const Card: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({ className = '', ...props }) => (
  <section className={`rounded-2xl border border-slate-200 bg-white shadow-sm ${className}`} {...props} />
);

export const Input = forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...props }, ref) => (
    <input
      ref={ref}
      className={`w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-slate-100 ${className}`}
      {...props}
    />
  ),
);
Input.displayName = 'Input';

export const Textarea = forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...props }, ref) => (
    <textarea
      ref={ref}
      className={`w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-slate-100 ${className}`}
      {...props}
    />
  ),
);
Textarea.displayName = 'Textarea';

export const Field: React.FC<{
  label: string;
  htmlFor?: string;
  hint?: string;
  children: React.ReactNode;
}> = ({ label, htmlFor, hint, children }) => (
  <label className="block space-y-1.5" htmlFor={htmlFor}>
    <span className="text-sm font-medium text-slate-700">{label}</span>
    {children}
    {hint && <span className="block text-xs text-slate-500">{hint}</span>}
  </label>
);

export const Dialog: React.FC<{
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: React.ReactNode;
}> = ({ open, title, description, onClose, children }) => {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      onCancel={onClose}
      onClose={onClose}
      className="w-[calc(100%-2rem)] max-w-md rounded-2xl border border-slate-200 p-0 shadow-2xl backdrop:bg-slate-950/40"
    >
      <div className="border-b border-slate-200 px-6 py-4">
        <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      <div className="px-6 py-5">{children}</div>
    </dialog>
  );
};

export const Alert: React.FC<{ tone?: 'error' | 'info'; children: React.ReactNode }> = ({
  tone = 'info',
  children,
}) => (
  <div
    role="alert"
    className={`rounded-lg border px-3 py-2 text-sm ${
      tone === 'error'
        ? 'border-rose-200 bg-rose-50 text-rose-700'
        : 'border-indigo-200 bg-indigo-50 text-indigo-700'
    }`}
  >
    {children}
  </div>
);
