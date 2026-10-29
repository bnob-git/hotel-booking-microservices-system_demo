import { expect, test } from '@playwright/test';
import {
  API_URL,
  apiLogin,
  bookingIdForCode,
  loyaltyBalance,
  randomStay,
  registerUser,
  uiLogin,
  uniqueName,
  waitForEmail,
} from './support';

/**
 * Registered-guest journey: register → login → date search with promo → checkout with promo + loyalty points →
 * confirmation + email → loyalty dashboard → cancel with refund policy (points returned).
 */
test.describe.serial('registered guest journey', () => {
  const username = uniqueName('e2e');
  const password = 'Passw0rd!';
  const email = `${username}@example.com`;
  let token: string;
  let confirmationCode: string;

  test('register and log in', async ({ page, request }) => {
    await registerUser(request, username, password);
    await uiLogin(page, username, password);
    token = await apiLogin(request, username, password);
  });

  test('earns loyalty points from a first paid booking', async ({ request }) => {
    const stay = randomStay(2);
    const headers = { Authorization: `Bearer ${token}` };
    const avail = await request.get(`${API_URL}/rooms/availability`, { params: stay });
    const roomId = (await avail.json())[0].roomId;
    const created = await request.post(`${API_URL}/bookings`, {
      headers,
      data: { roomId, checkInDate: stay.checkIn, checkOutDate: stay.checkOut, numGuests: 1, contactEmail: email },
    });
    expect(created.ok(), await created.text()).toBeTruthy();
    const booking = await created.json();
    expect(booking.status).toBe('PENDING');
    const paid = await request.post(`${API_URL}/bookings/${booking.id}/pay`, { headers, data: { paymentMethod: 'tok_visa' } });
    expect((await paid.json()).status).toBe('CONFIRMED');
    await expect.poll(() => loyaltyBalance(request, token), { timeout: 30_000 }).toBeGreaterThanOrEqual(100);
  });

  test('searches by dates, then checks out with promo code and loyalty points', async ({ page }) => {
    await uiLogin(page, username, password);
    const stay = randomStay(3);

    await page.getByTestId('nav-search').click();
    await page.getByTestId('search-check-in').fill(stay.checkIn);
    await page.getByTestId('search-check-out').fill(stay.checkOut);
    await page.getByTestId('search-promo').fill('WELCOME10');
    await page.getByTestId('search-submit').click();

    const firstResult = page.locator('[data-testid^="search-result-"]:not([data-testid="search-result-count"])').first();
    await expect(firstResult).toBeVisible();
    await expect(firstResult.getByTestId('result-discount')).toBeVisible();
    await expect(firstResult.getByTestId('result-nightly')).toBeVisible();
    await firstResult.locator('[data-testid^="search-book-"]').click();

    await expect(page).toHaveURL(/\/checkout\?/);
    await expect(page.getByTestId('checkout-promo')).toContainText('WELCOME10');
    await page.getByTestId('checkout-continue').click();
    await page.getByTestId('checkout-email').fill(email);
    await expect(page.getByTestId('checkout-points-balance')).toContainText('Balance');
    await page.getByTestId('checkout-redeem-points').fill('100');
    await expect(page.getByTestId('checkout-points-preview')).toContainText('5.00');
    await page.getByTestId('checkout-place-hold').click();

    await expect(page.getByTestId('checkout-status')).toContainText(/pending/i);
    await expect(page.getByTestId('hold-countdown')).toBeVisible();
    await page.getByTestId('card-tok_visa').click();
    await page.getByTestId('checkout-pay').click();

    await expect(page.getByTestId('checkout-step-confirmed')).toBeVisible();
    await expect(page.getByTestId('confirmation-status')).toContainText(/confirmed/i);
    await expect(page.getByTestId('confirmation-promo-discount')).toContainText('WELCOME10');
    await expect(page.getByTestId('confirmation-loyalty-discount')).toContainText('100 points');
    confirmationCode = (await page.getByTestId('confirmation-code').innerText()).trim();
    expect(confirmationCode).toMatch(/^HB[A-Z2-9]{6}$/);
  });

  test('sends a confirmation email via Mailpit', async ({ request }) => {
    const message = await waitForEmail(request, `${confirmationCode} confirmed`);
    expect(message.To[0].Address).toBe(email);
  });

  test('shows earned and redeemed points on the loyalty dashboard', async ({ page }) => {
    await uiLogin(page, username, password);
    await page.getByTestId('nav-loyalty').click();
    await expect(page.getByTestId('loyalty-balance-value')).toBeVisible();
    await expect(page.getByTestId('loyalty-tier-badge')).toBeVisible();
    const types = page.getByTestId('loyalty-transaction-type');
    await expect(types.filter({ hasText: /earn/i }).first()).toBeVisible();
    await expect(types.filter({ hasText: /redeem/i }).first()).toBeVisible();
  });

  test('cancels the booking with the refund policy and returns the redeemed points', async ({ page, request }) => {
    const before = await loyaltyBalance(request, token);
    await uiLogin(page, username, password);
    await page.goto('/bookings');
    const id = await bookingIdForCode(page, confirmationCode);
    await expect(page.getByTestId(`booking-status-${id}`)).toContainText(/confirmed/i);

    await page.getByTestId(`booking-cancel-${id}`).click();
    await expect(page.getByTestId('cancel-panel')).toBeVisible();
    await expect(page.getByTestId('cancel-refund-percent')).toContainText('100');
    await page.getByTestId('cancel-confirm').click();

    await expect(page.getByTestId(`booking-status-${id}`)).toContainText(/cancelled/i);
    await expect(page.getByTestId(`booking-refund-${id}`)).toBeVisible();
    await waitForEmail(request, `${confirmationCode} cancelled`);
    // +100 redeemed points returned, earned points for this booking reversed
    await expect.poll(() => loyaltyBalance(request, token), { timeout: 30_000 }).not.toBe(before);
  });
});
