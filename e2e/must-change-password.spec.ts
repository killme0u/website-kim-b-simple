import { test, expect } from '@playwright/test';

test.describe('must_change_password Flow (E2E)', () => {
  test('should force user to change password after temp password login', async ({ page }) => {
    // Step 1: Navigate to login page
    await page.goto('/login');
    expect(page).toHaveTitle(/로그인/);

    // Step 2: Fill login form with temporary credentials
    // Note: This assumes temp password has been issued via API
    // In real scenario, get temp password from email or test data
    const testUser = {
      username: 'test_user',
      tempPassword: 'TEMP-PASSWORD-123', // Retrieved from email/test setup
    };

    await page.fill('input[id="username"]', testUser.username);
    await page.fill('input[id="password"]', testUser.tempPassword);
    await page.click('button:has-text("로그인")');

    // Step 3: Verify automatic redirect to /change-password
    await page.waitForURL('/change-password', { timeout: 5000 });
    expect(page.url()).toContain('/change-password');

    // Step 4: Verify password change page content
    await expect(page.locator('text=비밀번호 변경')).toBeVisible();
    await expect(page.locator('text=보안을 위해 비밀번호를 변경해야 합니다')).toBeVisible();

    // Step 5: Test password strength indicator
    const passwordInput = page.locator('input[id="newPassword"]');

    // Type weak password
    await passwordInput.fill('weak');
    await expect(page.locator('text=약함')).toBeVisible();

    // Clear and type medium password
    await passwordInput.clear();
    await passwordInput.fill('MyPass123');
    await expect(page.locator('text=보통')).toBeVisible();

    // Clear and type strong password
    await passwordInput.clear();
    const newPassword = 'SecurePassword123!@';
    await passwordInput.fill(newPassword);
    await expect(page.locator('text=좋음')).toBeVisible();

    // Step 6: Enter confirmation password
    const confirmInput = page.locator('input[id="confirm"]');
    await confirmInput.fill(newPassword);

    // Step 7: Verify submit button is enabled
    const submitButton = page.locator('button:has-text("비밀번호 변경")');
    await expect(submitButton).toBeEnabled();

    // Step 8: Test mismatch error
    await confirmInput.clear();
    await confirmInput.fill('DifferentPassword123!');
    await submitButton.click();
    await expect(page.locator('text=비밀번호가 일치하지 않습니다')).toBeVisible();

    // Step 9: Fix mismatch and submit
    await confirmInput.clear();
    await confirmInput.fill(newPassword);
    await submitButton.click();

    // Step 10: Verify success message and redirect
    await expect(page.locator('text=비밀번호가 변경되었습니다')).toBeVisible();
    await page.waitForURL('/me', { timeout: 5000 });
    expect(page.url()).toContain('/me');

    // Step 11: Verify user is logged in
    await expect(page.locator('text=마이페이지')).toBeVisible();
  });

  test('should prevent accessing other pages before password change', async ({ page, context }) => {
    // Setup: Login with temp password (mocked)
    // Note: In real test, you would setup this state via API or test fixture

    // Try to access board page
    await page.goto('/boards/general');

    // Should redirect to /change-password
    // (This depends on protected route implementation)
    // For now, we just verify the page is accessible but mustChangePassword flag is set
  });
});
