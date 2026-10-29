import { APIRequestContext, Page, expect } from '@playwright/test';

export const API_URL = process.env['API_URL'] ?? 'http://localhost:8080/api';
export const MAILPIT_URL = process.env['MAILPIT_URL'] ?? 'http://localhost:8025';
export const ADMIN = { username: process.env['E2E_ADMIN_USER'] ?? 'admin', password: process.env['E2E_ADMIN_PASS'] ?? 'admin123' };

export const isoDate = (daysFromToday: number): string => {
  const d = new Date();
  d.setUTCHours(12, 0, 0, 0);
  d.setUTCDate(d.getUTCDate() + daysFromToday);
  return d.toISOString().slice(0, 10);
};

/** A random far-future stay so repeated runs against the same stack don't collide on availability. */
export const randomStay = (nights = 3): { checkIn: string; checkOut: string } => {
  const start = 40 + Math.floor(Math.random() * 280);
  return { checkIn: isoDate(start), checkOut: isoDate(start + nights) };
};

export const uniqueName = (prefix: string): string => `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

export async function apiLogin(request: APIRequestContext, username: string, password: string): Promise<string> {
  const res = await request.post(`${API_URL}/auth/login`, { data: { username, password } });
  expect(res.ok(), `login ${username}`).toBeTruthy();
  return (await res.json()).token;
}

/** Account creation is an admin capability in this system (POST /api/auth/register requires ROLE_ADMIN). */
export async function registerUser(request: APIRequestContext, username: string, password: string): Promise<void> {
  const adminToken = await apiLogin(request, ADMIN.username, ADMIN.password);
  const res = await request.post(`${API_URL}/auth/register`, {
    data: { username, password },
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  expect(res.status(), 'register user').toBe(200);
}

export async function uiLogin(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name=username]', username);
  await page.fill('input[name=password]', password);
  await page.click('button[type=submit]');
  await expect(page).not.toHaveURL(/\/login/);
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
}

export async function uiLogout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Logout' }).click();
}

export async function availableRoomIds(request: APIRequestContext, checkIn: string, checkOut: string): Promise<number[]> {
  const res = await request.get(`${API_URL}/rooms/availability`, { params: { checkIn, checkOut } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).map((r: { roomId: number }) => r.roomId);
}

export async function loyaltyBalance(request: APIRequestContext, token: string): Promise<number> {
  const res = await request.get(`${API_URL}/loyalty/me`, { headers: { Authorization: `Bearer ${token}` } });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).pointsBalance;
}

export interface MailpitMessage {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

/** Polls Mailpit until an email whose subject contains `text` arrives. */
export async function waitForEmail(request: APIRequestContext, text: string, timeoutMs = 30_000): Promise<MailpitMessage> {
  let found: MailpitMessage | undefined;
  await expect
    .poll(
      async () => {
        const res = await request.get(`${MAILPIT_URL}/api/v1/messages`, { params: { limit: '200' } });
        const body = await res.json();
        found = (body.messages as MailpitMessage[]).find((m) => m.Subject.includes(text));
        return !!found;
      },
      { timeout: timeoutMs, message: `email containing "${text}"` },
    )
    .toBe(true);
  return found!;
}

export async function bookingIdForCode(page: Page, code: string): Promise<number> {
  const codeEl = page.locator('[data-testid^="booking-code-"]', { hasText: code });
  await expect(codeEl).toBeVisible();
  const testId = (await codeEl.getAttribute('data-testid'))!;
  return Number(testId.replace('booking-code-', ''));
}
