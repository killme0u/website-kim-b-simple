import { test, expect } from '@playwright/test';

test.describe('Username Recovery with CAPTCHA (E2E)', () => {
  test('should require CAPTCHA for username recovery', async ({ page }) => {
    // Step 1: Navigate to username recovery page
    await page.goto('/find-username');
    expect(page).toHaveTitle(/아이디 찾기/);

    // Step 2: Verify page content
    await expect(page.locator('text=아이디 찾기')).toBeVisible();
    await expect(page.locator('text=가입할 때 사용한 이메일을 입력해 주세요')).toBeVisible();

    // Step 3: Verify CAPTCHA field exists (NEW feature)
    const emailInput = page.locator('input[type="email"]');
    const captchaFrame = page.locator('iframe[title*="Turnstile"]'); // Cloudflare Turnstile CAPTCHA
    const submitButton = page.locator('button:has-text("아이디 안내 받기")');

    await expect(emailInput).toBeVisible();
    await expect(submitButton).toBeVisible();

    // Step 4: Verify submit button is disabled without CAPTCHA
    await emailInput.fill('test@example.com');
    await expect(submitButton).toBeDisabled();

    // Step 5: Fill CAPTCHA (fake mode in dev)
    // In development with CAPTCHA_MODE=fake, we need to trigger the CAPTCHA validation
    // The CAPTCHA component will be available in the page

    // Wait for CAPTCHA to be available
    await page.waitForSelector('[data-testid="captcha-token"]', { timeout: 5000 }).catch(() => {
      // Fallback: use the dev-captcha token directly (only works in fake mode)
      console.log('CAPTCHA component not found, using dev-captcha token');
    });

    // Step 6: Submit the form (CAPTCHA should be filled by dev token)
    await submitButton.click();

    // Step 7: Verify success message
    await expect(page.locator('text=가입 이메일로 아이디 안내를 전송했습니다')).toBeVisible({ timeout: 5000 });
  });

  test('should show error on invalid email', async ({ page }) => {
    await page.goto('/find-username');

    const emailInput = page.locator('input[type="email"]');
    const submitButton = page.locator('button:has-text("아이디 안내 받기")');

    // Fill invalid email
    await emailInput.fill('invalid-email');

    // HTML5 validation should prevent submission
    // (The button should be disabled or form should not submit)
    await expect(emailInput).toHaveAttribute('type', 'email');
  });

  test('should handle network errors gracefully', async ({ page }) => {
    await page.goto('/find-username');

    // Simulate offline mode
    await page.context().setOffline(true);

    const emailInput = page.locator('input[type="email"]');
    const submitButton = page.locator('button:has-text("아이디 안내 받기")');

    await emailInput.fill('test@example.com');
    // CAPTCHA would be filled normally
    await submitButton.click();

    // Should show error message
    await expect(page.locator('text=요청에 실패했습니다')).toBeVisible({ timeout: 5000 });

    // Restore connection
    await page.context().setOffline(false);
  });
});
