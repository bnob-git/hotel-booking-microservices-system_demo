import { expect, test } from '@playwright/test';
import {
  ADMIN,
  API_URL,
  apiLogin,
  availableRoomIds,
  bookingIdForCode,
  isoDate,
  randomStay,
  registerUser,
  uiLogin,
  uniqueName,
  waitForEmail,
} from './support';

test.describe.serial('guest checkout without an account', () => {
  const email = `${uniqueName('guest')}@example.com`;
  let code: string;

  test('books as a guest', async ({ page, request }) => {
    const stay = randomStay(2);
    const roomId = (await availableRoomIds(request, stay.checkIn, stay.checkOut))[0];

    await page.goto('/guest/book');
    await page.getByTestId('guest-room-select').click();
    await page.getByTestId(`guest-room-option-${roomId}`).click();
    await page.getByTestId('guest-check-in').fill(stay.checkIn);
    await page.getByTestId('guest-check-out').fill(stay.checkOut);
    await page.getByTestId('guest-name').fill('Gina Guest');
    await page.getByTestId('guest-email').fill(email);
    await page.getByTestId('guest-booking-submit').click();

    await expect(page.getByTestId('guest-booking-confirmation')).toBeVisible();
    await expect(page.getByTestId('guest-booking-status')).toContainText(/confirmed/i);
    code = (await page.getByTestId('guest-confirmation-code').innerText()).trim();
    await waitForEmail(request, `${code} confirmed`);
  });

  test('rejects lookup with the wrong email', async ({ page }) => {
    await page.goto('/guest/lookup');
    await page.getByTestId('lookup-code').fill(code);
    await page.getByTestId('lookup-email').fill('someone-else@example.com');
    await page.getByTestId('lookup-submit').click();
    await expect(page.getByTestId('lookup-error')).toBeVisible();
  });

  test('looks up by confirmation code + email and cancels', async ({ page }) => {
    await page.goto('/guest/lookup');
    await page.getByTestId('lookup-code').fill(code);
    await page.getByTestId('lookup-email').fill(email.toUpperCase());
    await page.getByTestId('lookup-submit').click();
    await expect(page.getByTestId('lookup-result')).toBeVisible();
    await expect(page.getByTestId('lookup-status')).toContainText(/confirmed/i);

    page.once('dialog', (d) => d.accept());
    await page.getByTestId('lookup-cancel').click();
    await expect(page.getByTestId('lookup-status')).toContainText(/cancelled/i);
  });
});

test.describe.serial('booking status transitions', () => {
  test('PENDING → CONFIRMED → CHECKED_IN → COMPLETED', async ({ page, request }) => {
    const username = uniqueName('stay');
    await registerUser(request, username, 'Passw0rd!');
    const token = await apiLogin(request, username, 'Passw0rd!');
    const headers = { Authorization: `Bearer ${token}` };
    const checkIn = isoDate(0);
    const checkOut = isoDate(1);
    const roomId = (await availableRoomIds(request, checkIn, checkOut))[0];
    const created = await (
      await request.post(`${API_URL}/bookings`, {
        headers,
        data: { roomId, checkInDate: checkIn, checkOutDate: checkOut, numGuests: 1, contactEmail: `${username}@example.com` },
      })
    ).json();
    expect(created.status).toBe('PENDING');
    const paid = await (
      await request.post(`${API_URL}/bookings/${created.id}/pay`, { headers, data: { paymentMethod: 'tok_visa' } })
    ).json();
    expect(paid.status).toBe('CONFIRMED');

    await uiLogin(page, ADMIN.username, ADMIN.password);
    await page.goto('/bookings');
    const id = await bookingIdForCode(page, created.confirmationCode);
    expect(id).toBe(created.id);
    await page.getByTestId(`booking-check-in-${id}`).click();
    await expect(page.getByTestId(`booking-status-${id}`)).toContainText(/checked.in/i);
    await waitForEmail(request, `${created.confirmationCode}`);
    await page.getByTestId(`booking-complete-${id}`).click();
    await expect(page.getByTestId(`booking-status-${id}`)).toContainText(/completed/i);
  });
});
