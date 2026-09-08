import React from 'react';
import { Button as CossButton } from '@/components/ui/button';
import { Card as CossCard } from '@/components/ui/card';
import { Input as CossInput } from '@/components/ui/input';
import { Textarea as CossTextarea } from '@/components/ui/textarea';
import { Field as CossField, FieldLabel } from '@/components/ui/field';
import { Dialog as CossDialog, DialogPopup, DialogHeader, DialogTitle, DialogDescription, DialogPanel } from '@/components/ui/dialog';
import { Alert as CossAlert } from '@/components/ui/alert';

type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';

export const Button = React.forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: 'sm' | 'md';
}>(({ className = '', variant = 'primary', size = 'md', ...props }, ref) => {
  const vMap: Record<string, any> = {
    primary: 'default',
    secondary: 'secondary',
    outline: 'outline',
    ghost: 'ghost',
    danger: 'destructive',
  };
  const sMap: Record<string, any> = {
    sm: 'sm',
    md: 'default',
  };
  return <CossButton ref={ref} variant={vMap[variant] || 'default'} size={sMap[size] || 'default'} className={className} {...props} />;
});
Button.displayName = 'Button';

export const Card: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({ className = '', ...props }) => (
  <CossCard className={className} {...props} />
);

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...props }, ref) => (
    <CossInput ref={ref} className={className} {...props} />
  )
);
Input.displayName = 'Input';

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...props }, ref) => (
    <CossTextarea ref={ref} className={className} {...props} />
  )
);
Textarea.displayName = 'Textarea';

export const Field: React.FC<{
  label: string;
  htmlFor?: string;
  hint?: string;
  children: React.ReactNode;
}> = ({ label, htmlFor, hint, children }) => (
  <CossField className="block space-y-1.5 w-full">
    <FieldLabel htmlFor={htmlFor} className="text-sm font-medium text-slate-700">{label}</FieldLabel>
    {children}
    {hint && <span className="block text-xs text-slate-500">{hint}</span>}
  </CossField>
);

export const Dialog: React.FC<{
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: React.ReactNode;
}> = ({ open, title, description, onClose, children }) => {
  return (
    <CossDialog open={open} onOpenChange={(v: boolean) => { if (!v) onClose(); }}>
      <DialogPopup className="w-[calc(100%-2rem)] max-w-md rounded-2xl border border-slate-200 shadow-2xl">
        <DialogHeader className="border-b border-slate-200 px-6 py-4">
          <DialogTitle className="text-lg font-semibold text-slate-900">{title}</DialogTitle>
          {description && <DialogDescription className="mt-1 text-sm text-slate-500">{description}</DialogDescription>}
        </DialogHeader>
        <DialogPanel className="px-6 py-5">{children}</DialogPanel>
      </DialogPopup>
    </CossDialog>
  );
};

export const Alert: React.FC<{ tone?: 'error' | 'info'; children: React.ReactNode }> = ({
  tone = 'info',
  children,
}) => {
  // map tone to valid VariantProps for Alert
  const v = tone === 'error' ? 'error' : 'info';
  return (
    <CossAlert variant={v} className="px-3 py-2 text-sm rounded-lg">
      {children}
    </CossAlert>
  );
};
