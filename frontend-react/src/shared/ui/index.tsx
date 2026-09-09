import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode, Ref, TextareaHTMLAttributes } from 'react';
import { Button as CossButton } from '@/components/ui/button';
import { Card as CossCard } from '@/components/ui/card';
import { Input as CossInput } from '@/components/ui/input';
import { Textarea as CossTextarea } from '@/components/ui/textarea';
import { Field as CossField, FieldLabel } from '@/components/ui/field';
import { Dialog as CossDialog, DialogPopup, DialogHeader, DialogTitle, DialogDescription, DialogPanel } from '@/components/ui/dialog';
import { Alert as CossAlert } from '@/components/ui/alert';

// React 19부터 ref는 일반 prop이므로 forwardRef 래핑이 필요하지 않다.

type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md';

const BUTTON_VARIANTS = {
  primary: 'default',
  secondary: 'secondary',
  outline: 'outline',
  ghost: 'ghost',
  danger: 'destructive',
} as const;

const BUTTON_SIZES = {
  sm: 'sm',
  md: 'default',
} as const;

export function Button({
  className = '',
  variant = 'primary',
  size = 'md',
  ref,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  ref?: Ref<HTMLButtonElement>;
}) {
  return (
    <CossButton
      ref={ref}
      variant={BUTTON_VARIANTS[variant]}
      size={BUTTON_SIZES[size]}
      className={className}
      {...props}
    />
  );
}

export function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <CossCard className={className} {...props} />;
}

export function Input({
  className = '',
  ref,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> }) {
  return <CossInput ref={ref} className={className} {...props} />;
}

export function Textarea({
  className = '',
  ref,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { ref?: Ref<HTMLTextAreaElement> }) {
  return <CossTextarea ref={ref} className={className} {...props} />;
}

export function Field({
  label,
  htmlFor,
  hint,
  children,
}: {
  label: string;
  htmlFor?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <CossField className="block space-y-1.5 w-full">
      <FieldLabel htmlFor={htmlFor} className="text-sm font-medium text-slate-700">{label}</FieldLabel>
      {children}
      {hint && <span className="block text-xs text-slate-500">{hint}</span>}
    </CossField>
  );
}

export function Dialog({
  open,
  title,
  description,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <CossDialog open={open} onOpenChange={(nextOpen: boolean) => { if (!nextOpen) onClose(); }}>
      <DialogPopup className="w-[calc(100%-2rem)] max-w-md rounded-2xl border border-slate-200 shadow-2xl">
        <DialogHeader className="border-b border-slate-200 px-6 py-4">
          <DialogTitle className="text-lg font-semibold text-slate-900">{title}</DialogTitle>
          {description && <DialogDescription className="mt-1 text-sm text-slate-500">{description}</DialogDescription>}
        </DialogHeader>
        <DialogPanel className="px-6 py-5">{children}</DialogPanel>
      </DialogPopup>
    </CossDialog>
  );
}

export function Alert({ tone = 'info', children }: { tone?: 'error' | 'info'; children: ReactNode }) {
  return (
    <CossAlert variant={tone === 'error' ? 'error' : 'info'} className="px-3 py-2 text-sm rounded-lg">
      {children}
    </CossAlert>
  );
}
