import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { BookingService } from './booking.service';
import { API } from '../config/api.config';

describe('BookingService (payments & lifecycle)', () => {
  let service: BookingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a booking passing the promo code through', () => {
    service
      .createBooking({
        roomId: 1,
        checkInDate: '2026-12-01',
        checkOutDate: '2026-12-03',
        promoCode: 'SUMMER10',
      })
      .subscribe((b) => expect(b.status).toBe('PENDING'));

    const req = http.expectOne(API.BOOKINGS);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.promoCode).toBe('SUMMER10');
    req.flush({ id: 5, status: 'PENDING' });
  });

  it('pays a booking with the selected payment method', () => {
    service.payBooking(5, 'tok_visa').subscribe((b) => expect(b.paymentStatus).toBe('PAID'));

    const req = http.expectOne(`${API.BOOKINGS}/5/pay`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ paymentMethod: 'tok_visa' });
    req.flush({ id: 5, status: 'CONFIRMED', paymentStatus: 'PAID' });
  });

  it('soft cancels with an optional reason', () => {
    service.cancelBooking(5, 'Plans changed').subscribe();
    const withReason = http.expectOne(`${API.BOOKINGS}/5/cancel`);
    expect(withReason.request.method).toBe('POST');
    expect(withReason.request.body).toEqual({ reason: 'Plans changed' });
    withReason.flush({ id: 5, status: 'CANCELLED' });

    service.cancelBooking(6).subscribe();
    const withoutReason = http.expectOne(`${API.BOOKINGS}/6/cancel`);
    expect(withoutReason.request.body).toEqual({});
    withoutReason.flush({ id: 6, status: 'CANCELLED' });
  });

  it('loads the cancellation quote', () => {
    service.getCancellationQuote(5).subscribe((q) => expect(q.refundPercent).toBe(50));

    const req = http.expectOne(`${API.BOOKINGS}/5/cancellation-quote`);
    expect(req.request.method).toBe('GET');
    req.flush({ bookingId: 5, refundPercent: 50 });
  });

  it('checks in and completes bookings', () => {
    service.checkInBooking(5).subscribe();
    const checkIn = http.expectOne(`${API.BOOKINGS}/5/check-in`);
    expect(checkIn.request.method).toBe('POST');
    checkIn.flush({ id: 5, status: 'CHECKED_IN' });

    service.completeBooking(5).subscribe();
    const complete = http.expectOne(`${API.BOOKINGS}/5/complete`);
    expect(complete.request.method).toBe('POST');
    complete.flush({ id: 5, status: 'COMPLETED' });
  });
});
