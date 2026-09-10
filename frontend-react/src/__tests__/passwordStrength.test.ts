import { describe, it, expect } from 'vitest';

type PasswordStrength = 'weak' | 'fair' | 'good' | 'strong';

function calculatePasswordStrength(password: string): PasswordStrength {
  if (!password) return 'weak';

  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (password.length >= 16) score++;
  if (/[a-z]/.test(password)) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[!@#$%^&*()_+\-=\[\]{};:'",.<>?/\\|`~]/.test(password)) score++;

  if (score <= 2) return 'weak';
  if (score <= 4) return 'fair';
  if (score <= 5) return 'good';
  return 'strong';
}

describe('Password Strength Calculator', () => {
  describe('weak passwords', () => {
    it('should return "weak" for empty password', () => {
      expect(calculatePasswordStrength('')).toBe('weak');
    });

    it('should return "weak" for short passwords', () => {
      expect(calculatePasswordStrength('pass')).toBe('weak');
      expect(calculatePasswordStrength('1234')).toBe('weak');
    });

    it('should return "weak" for single character type with 8+ chars', () => {
      expect(calculatePasswordStrength('aaaaaaaa')).toBe('weak');
      expect(calculatePasswordStrength('12345678')).toBe('weak');
    });
  });

  describe('fair passwords', () => {
    it('should return "fair" for 8-11 chars with mixed types', () => {
      expect(calculatePasswordStrength('MyPass123')).toBe('fair');
      expect(calculatePasswordStrength('TestPass1')).toBe('fair');
    });

    it('should return "fair" for 12+ chars with lowercase only', () => {
      expect(calculatePasswordStrength('passwordabcdef')).toBe('fair');
    });
  });

  describe('good passwords', () => {
    it('should return "good" for 12+ chars with multiple types', () => {
      expect(calculatePasswordStrength('MyNewPassword123')).toBe('good');
      expect(calculatePasswordStrength('SecurePass123!')).toBe('good');
    });

    it('should return "good" for 16+ chars with basic diversity', () => {
      expect(calculatePasswordStrength('MyPassword12345a')).toBe('good');
    });
  });

  describe('strong passwords', () => {
    it('should return "strong" for 16+ chars with high diversity', () => {
      expect(calculatePasswordStrength('ComplexPassword12345@#')).toBe('strong');
      expect(calculatePasswordStrength('SuperSecurePass123!@#')).toBe('strong');
    });

    it('should return "strong" for all character types', () => {
      expect(calculatePasswordStrength('MyP@ssw0rd1234567')).toBe('strong');
    });
  });

  describe('edge cases', () => {
    it('should handle special characters correctly', () => {
      expect(calculatePasswordStrength('Pass!@#$%^&*()')).toBe('strong');
    });

    it('should handle unicode characters', () => {
      expect(calculatePasswordStrength('패스워드Password123!')).toBe('strong');
    });

    it('should handle spaces', () => {
      expect(calculatePasswordStrength('My Password 123!')).toBe('strong');
    });
  });
});
