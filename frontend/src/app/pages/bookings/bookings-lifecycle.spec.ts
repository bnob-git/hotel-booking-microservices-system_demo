import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Bookings } from './bookings';
import { BookingService } from '../../core/services/booking.service';
import { RoomService } from '../../core/services/room.service';
import { UserService } from '../../core/services/user.service';
import { BookingResponse } from '../../core/models/booking-response';

describe('Bookings page (payments & lifecycle)', () => {
  let fixture: ComponentFixture<Bookings>;
  let component: Bookings;
  let bookingService: any;
  let isAdmin = false;

  const base: BookingResponse = {
    id: 1,
    roomId: 3,
    userId: 7,
    username: 'alice',
    roomName: 'Sea View',
    checkInDate: '2026-12-01',
    checkOutDate: '2026-12-04',
    status: 'CONFIRMED',
    paymentStatus: 'PAID',
    totalPrice: 360,
    amountPaid: 360,
    confirmationCode: 'HBQ7K2MX',
  };

  function setup(bookings: BookingResponse[]) {
    bookingService = {
      getAllBookings: jasmine.createSpy().and.returnValue(of(bookings)),
      getMyBookings: jasmine.createSpy().and.returnValue(of(bookings)),
      getCancellationQuote: jasmine.createSpy().and.returnValue(
        of({
          bookingId: 1,
          status: 'CONFIRMED',
          cancellable: true,
          daysBeforeCheckIn: 3,
          refundPercent: 50,
          amountPaid: 360,
          refundAmount: 180,
          cancellationFee: 180,
          currency: 'EUR',
          policy: '50% refund: cancelled 2 to 6 days before check-in',
        }),
      ),
      cancelBooking: jasmine
        .createSpy()
        .and.returnValue(
          of({
            ...base,
            status: 'CANCELLED',
            paymentStatus: 'PARTIALLY_REFUNDED',
            refundAmount: 180,
          }),
        ),
      checkInBooking: jasmine.createSpy().and.returnValue(of({ ...base, status: 'CHECKED_IN' })),
      completeBooking: jasmine
        .createSpy()
        .and.returnValue(of({ ...base, id: 2, status: 'COMPLETED' })),
    };

    TestBed.configureTestingModule({
      imports: [Bookings],
      providers: [
        provideRouter([]),
        { provide: BookingService, useValue: bookingService },
        { provide: RoomService, useValue: { getRoomById: () => of({ id: 3 }) } },
        {
          provide: UserService,
          useValue: {
            getCurrentUser: () => ({ id: 7, username: 'alice', role: isAdmin ? 'ADMIN' : 'USER' }),
            isAdmin: () => isAdmin,
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ roomId: '3' }) } },
        },
      ],
    });
    fixture = TestBed.createComponent(Bookings);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const el = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  beforeEach(() => (isAdmin = false));

  it('routes the room + dates selection into checkout', () => {
    setup([]);
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    component.updateCheckInDate('2026-12-01');
    component.updateCheckOutDate('2026-12-04');
    component.goToCheckout();

    expect(router.navigate).toHaveBeenCalledWith(['/checkout'], {
      queryParams: { roomId: 3, checkIn: '2026-12-01', checkOut: '2026-12-04' },
    });
  });

  it('validates dates before going to checkout', () => {
    setup([]);
    component.goToCheckout();
    expect(component.errorMessage()).toBe('Please fill in all fields');
  });

  it('shows status and payment badges for each booking', () => {
    setup([base, { ...base, id: 2, status: 'PENDING', paymentStatus: 'UNPAID' }]);

    expect(el('booking-status-1')?.textContent).toContain('Confirmed');
    expect(el('booking-payment-status-1')?.textContent).toContain('Paid');
    expect(el('booking-status-2')?.textContent).toContain('Pending payment');
    expect(el('booking-pay-2')).toBeTruthy();
    expect(el('booking-code-1')?.textContent).toContain('HBQ7K2MX');
  });

  it('previews the refund and soft cancels with a reason', () => {
    setup([base]);

    component.openCancel(base);
    fixture.detectChanges();
    expect(el('cancel-refund-amount')?.textContent).toContain('180.00');
    expect(el('cancel-refund-percent')?.textContent).toBe('50');

    component.cancelReason.set('Plans changed');
    component.confirmCancel();
    fixture.detectChanges();

    expect(bookingService.cancelBooking).toHaveBeenCalledWith(1, 'Plans changed');
    expect(component.cancelQuote()).toBeNull();
    expect(component.bookings()[0].status).toBe('CANCELLED');
    expect(el('booking-status-1')?.textContent).toContain('Cancelled');
    expect(el('booking-cancel-1')).toBeNull();
  });

  it('hides admin actions from regular users', () => {
    setup([base]);
    expect(el('booking-check-in-1')).toBeNull();
  });

  it('lets admins check in and complete bookings', () => {
    isAdmin = true;
    setup([base, { ...base, id: 2, status: 'CHECKED_IN' }]);

    expect(el('booking-check-in-1')).toBeTruthy();
    expect(el('booking-complete-2')).toBeTruthy();

    component.checkIn(component.bookings()[0]);
    component.complete(component.bookings()[1]);
    fixture.detectChanges();

    expect(bookingService.checkInBooking).toHaveBeenCalledWith(1);
    expect(bookingService.completeBooking).toHaveBeenCalledWith(2);
    expect(component.bookings().map((b) => b.status)).toEqual(['CHECKED_IN', 'COMPLETED']);
  });
});
