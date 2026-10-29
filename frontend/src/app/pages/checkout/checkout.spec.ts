import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { Checkout } from './checkout';
import { BookingService } from '../../core/services/booking.service';
import { RoomService } from '../../core/services/room.service';
import { LoyaltyService } from '../../core/services/loyalty.service';
import { PricingService } from '../../core/services/pricing.service';
import { BookingResponse } from '../../core/models/booking-response';

describe('Checkout', () => {
  let fixture: ComponentFixture<Checkout>;
  let component: Checkout;
  let bookingService: jasmine.SpyObj<BookingService>;
  let roomService: jasmine.SpyObj<RoomService>;

  const pending: BookingResponse = {
    id: 42,
    roomId: 3,
    userId: 7,
    username: 'alice',
    roomName: 'Sea View',
    checkInDate: '2026-12-01',
    checkOutDate: '2026-12-04',
    status: 'PENDING',
    paymentStatus: 'UNPAID',
    totalPrice: 360,
    confirmationCode: 'HBQ7K2MX',
    holdExpiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
  };

  function setup(params: Record<string, string>) {
    bookingService = jasmine.createSpyObj<BookingService>('BookingService', [
      'createBooking',
      'payBooking',
      'getBookingById',
    ]);
    bookingService.getBookingById.and.returnValue(of(pending));
    roomService = jasmine.createSpyObj<RoomService>('RoomService', ['getRoomById']);
    roomService.getRoomById.and.returnValue(
      of({ id: 3, name: 'Sea View', type: 'DOUBLE', price: 120, available: true }),
    );

    TestBed.configureTestingModule({
      imports: [Checkout],
      providers: [
        provideRouter([]),
        { provide: BookingService, useValue: bookingService },
        { provide: RoomService, useValue: roomService },
        {
          provide: LoyaltyService,
          useValue: {
            getMyAccount: () =>
              of({ userId: 7, username: 'alice', pointsBalance: 1200, lifetimePoints: 1200, tier: 'SILVER', nextTier: 'GOLD', pointsToNextTier: 3800, pointValue: 0.05 }),
          },
        },
        { provide: PricingService, useValue: { getQuote: () => throwError(() => new Error('no quote')) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(params) } },
        },
      ],
    });

    fixture = TestBed.createComponent(Checkout);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const el = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  afterEach(() => fixture?.destroy());

  it('reads the stay from query params and shows an estimate', () => {
    setup({
      roomId: '3',
      checkIn: '2026-12-01',
      checkOut: '2026-12-04',
      guests: '2',
      promo: 'SUMMER10',
    });

    expect(roomService.getRoomById).toHaveBeenCalledWith(3);
    expect(component.nights()).toBe(3);
    expect(component.numGuests()).toBe(2);
    expect(el('checkout-room-name')?.textContent).toContain('Sea View');
    expect(el('checkout-total')?.textContent).toContain('360.00');
    expect(el('checkout-promo')?.textContent).toContain('SUMMER10');
  });

  it('blocks continuing without valid dates', () => {
    setup({ roomId: '3', checkIn: '2026-12-04', checkOut: '2026-12-04' });

    component.continueToDetails();

    expect(component.step()).toBe('review');
    expect(component.errorMessage()).toContain('valid check-in');
  });

  it('creates a pending hold with guest details and promo code, then shows the countdown', () => {
    setup({
      roomId: '3',
      checkIn: '2026-12-01',
      checkOut: '2026-12-04',
      guests: '2',
      promo: 'SUMMER10',
    });
    bookingService.createBooking.and.returnValue(of(pending));

    component.continueToDetails();
    component.contactEmail.set(' alice@example.com ');
    component.specialRequests.set('Late arrival');
    component.placeHold();
    fixture.detectChanges();

    expect(bookingService.createBooking).toHaveBeenCalledWith({
      roomId: 3,
      checkInDate: '2026-12-01',
      checkOutDate: '2026-12-04',
      numGuests: 2,
      contactEmail: 'alice@example.com',
      specialRequests: 'Late arrival',
      promoCode: 'SUMMER10',
    });
    expect(component.step()).toBe('payment');
    expect(el('hold-countdown')?.textContent).toMatch(/1[45]:\d\d/);
    expect(el('checkout-pay')).toBeTruthy();
  });

  it('confirms the booking after a successful payment', () => {
    setup({ roomId: '3', checkIn: '2026-12-01', checkOut: '2026-12-04' });
    bookingService.createBooking.and.returnValue(of(pending));
    bookingService.payBooking.and.returnValue(
      of({
        ...pending,
        status: 'CONFIRMED',
        paymentStatus: 'PAID',
        amountPaid: 360,
        paymentReference: 'mock_pi_1',
      }),
    );

    component.continueToDetails();
    component.placeHold();
    component.pay();
    fixture.detectChanges();

    expect(bookingService.payBooking).toHaveBeenCalledWith(42, 'tok_visa');
    expect(component.step()).toBe('confirmed');
    expect(el('confirmation-code')?.textContent).toContain('HBQ7K2MX');
    expect(el('confirmation-status')?.textContent).toContain('Confirmed');
    expect(el('confirmation-amount-paid')?.textContent).toContain('360.00');
  });

  it('stays on payment and shows the decline when the card is declined', () => {
    setup({ roomId: '3', checkIn: '2026-12-01', checkOut: '2026-12-04' });
    bookingService.createBooking.and.returnValue(of(pending));
    bookingService.payBooking.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 402,
            error: { status: 402, message: 'Your card was declined.' },
          }),
      ),
    );

    component.continueToDetails();
    component.placeHold();
    component.paymentMethod.set('tok_chargeDeclined');
    component.pay();
    fixture.detectChanges();

    expect(bookingService.payBooking).toHaveBeenCalledWith(42, 'tok_chargeDeclined');
    expect(component.step()).toBe('payment');
    expect(component.booking()?.paymentStatus).toBe('FAILED');
    expect(el('checkout-error')?.textContent).toContain('Your card was declined.');
  });

  it('disables payment once the hold has expired', () => {
    setup({ roomId: '3', checkIn: '2026-12-01', checkOut: '2026-12-04' });
    bookingService.createBooking.and.returnValue(
      of({ ...pending, holdExpiresAt: new Date(Date.now() - 1000).toISOString() }),
    );

    component.continueToDetails();
    component.placeHold();
    fixture.detectChanges();

    expect(component.holdExpired()).toBeTrue();
    expect(el('hold-expired')).toBeTruthy();
    expect((el('checkout-pay') as HTMLButtonElement).disabled).toBeTrue();
    component.pay();
    expect(bookingService.payBooking).not.toHaveBeenCalled();
  });

  it('resumes payment for an existing pending booking', () => {
    setup({ bookingId: '42' });

    expect(bookingService.getBookingById).toHaveBeenCalledWith(42);
    expect(component.step()).toBe('payment');
    expect(component.roomId()).toBe(3);
    expect(component.nights()).toBe(3);
  });

  it('start over returns to the bookings page for the room', () => {
    setup({ roomId: '3', checkIn: '2026-12-01', checkOut: '2026-12-04' });
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    component.startOver();

    expect(router.navigate).toHaveBeenCalledWith(['/bookings'], { queryParams: { roomId: 3 } });
  });

  it('sends redeemed loyalty points with the hold and previews the discount', () => {
    setup({ roomId: '3', checkIn: '2026-12-01', checkOut: '2026-12-04' });
    bookingService.createBooking.and.returnValue(of(pending));
    expect(component.pointsBalance()).toBe(1200);

    component.continueToDetails();
    component.redeemPoints.set(200);
    expect(component.pointsDiscountPreview()).toBe(10);
    expect(component.estimatedTotal()).toBe(350);
    component.placeHold();

    expect(bookingService.createBooking).toHaveBeenCalledWith(jasmine.objectContaining({ redeemPoints: 200 }));
  });

  it('rejects points that are not a multiple of 100', () => {
    setup({ roomId: '3', checkIn: '2026-12-01', checkOut: '2026-12-04' });
    component.continueToDetails();
    component.redeemPoints.set(150);
    component.placeHold();

    expect(bookingService.createBooking).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('multiples of 100');
  });
});
