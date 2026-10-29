import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { GuestBookingService } from './guest-booking.service';
import { jwtInterceptor } from '../interceptors/jwt.interceptor';
import { API } from '../config/api.config';

describe('GuestBookingService', () => {
  let service: GuestBookingService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('token');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(GuestBookingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem('token');
  });

  it('should POST a guest booking without an Authorization header', () => {
    const request = {
      roomId: 4,
      checkInDate: '2026-12-01',
      checkOutDate: '2026-12-04',
      numGuests: 2,
      guestName: 'Jane',
      guestEmail: 'jane@example.com',
    };

    service
      .createBooking(request)
      .subscribe((res) => expect(res.confirmationCode).toBe('HB7K2QXM'));

    const req = http.expectOne(API.GUEST_BOOKINGS);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ confirmationCode: 'HB7K2QXM' });
  });

  it('should GET lookup with trimmed code and email query params', () => {
    service.lookup(' HB7K2QXM ', ' jane@example.com ').subscribe((res) => expect(res.id).toBe(1));

    const req = http.expectOne((r) => r.url === `${API.GUEST_BOOKINGS}/lookup`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('code')).toBe('HB7K2QXM');
    expect(req.request.params.get('email')).toBe('jane@example.com');
    req.flush({ id: 1 });
  });

  it('should POST cancel with code and email', () => {
    service.cancel({ code: 'HB7K2QXM ', email: 'jane@example.com' }).subscribe();

    const req = http.expectOne(`${API.GUEST_BOOKINGS}/cancel`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'HB7K2QXM', email: 'jane@example.com' });
    req.flush({ status: 'CANCELLED' });
  });

  it('should still attach the token when a user happens to be logged in', () => {
    localStorage.setItem('token', 'abc');

    service.lookup('HB7K2QXM', 'jane@example.com').subscribe();

    const req = http.expectOne((r) => r.url === `${API.GUEST_BOOKINGS}/lookup`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
    req.flush({});
  });
});
