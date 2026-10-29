import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { GuestLookup } from './guest-lookup';
import { GuestBookingService } from '../../core/services/guest-booking.service';

describe('GuestLookup Component', () => {
  let fixture: ComponentFixture<GuestLookup>;
  let component: GuestLookup;
  let guestServiceMock: any;

  const booking = {
    id: 9,
    confirmationCode: 'HB7K2QXM',
    status: 'CONFIRMED',
    username: 'Jane Guest',
    roomName: 'Room 201',
    checkInDate: '2026-12-01',
    checkOutDate: '2026-12-04',
    numGuests: 2,
    totalPrice: 360,
  };

  async function setup(code: string | null = null) {
    guestServiceMock = {
      lookup: jasmine.createSpy().and.returnValue(of(booking)),
      cancel: jasmine.createSpy().and.returnValue(of({ ...booking, status: 'CANCELLED' })),
    };

    await TestBed.configureTestingModule({
      imports: [GuestLookup],
      providers: [
        provideRouter([]),
        { provide: GuestBookingService, useValue: guestServiceMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => code } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GuestLookup);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function el(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('should prefill the code from ?code=', async () => {
    await setup('hb7k2qxm');
    expect(component.code()).toBe('HB7K2QXM');
  });

  it('should require both code and email', async () => {
    await setup();
    component.code.set('HB7K2QXM');
    component.lookup();
    expect(component.errorMessage()).toBe('Please enter your confirmation code and email.');
    expect(guestServiceMock.lookup).not.toHaveBeenCalled();
  });

  it('should show booking details and status on a match', async () => {
    await setup('HB7K2QXM');
    component.email.set('jane@example.com');

    component.lookup();
    fixture.detectChanges();

    expect(guestServiceMock.lookup).toHaveBeenCalledWith('HB7K2QXM', 'jane@example.com');
    expect(el('lookup-result')).toBeTruthy();
    expect(el('lookup-confirmation-code')?.textContent?.trim()).toBe('HB7K2QXM');
    expect(el('lookup-status')?.textContent?.trim()).toBe('CONFIRMED');
    expect(el('lookup-guest-name')?.textContent?.trim()).toBe('Jane Guest');
    expect(el('lookup-room')?.textContent?.trim()).toBe('Room 201');
    expect(el('lookup-cancel')).toBeTruthy();
  });

  it('should show a generic not-found message on 404', async () => {
    await setup('HB7K2QXM');
    component.email.set('wrong@example.com');
    guestServiceMock.lookup.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    component.lookup();
    fixture.detectChanges();

    expect(el('lookup-error')?.textContent).toContain('No booking matches');
    expect(el('lookup-result')).toBeNull();
  });

  it('should show a rate-limit message on 429', async () => {
    await setup('HB7K2QXM');
    component.email.set('jane@example.com');
    guestServiceMock.lookup.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 429 })),
    );

    component.lookup();

    expect(component.errorMessage()).toContain('Too many attempts');
  });

  it('should cancel after confirmation and show CANCELLED without a cancel button', async () => {
    await setup('HB7K2QXM');
    component.email.set('jane@example.com');
    component.lookup();
    spyOn(window, 'confirm').and.returnValue(true);

    component.cancel();
    fixture.detectChanges();

    expect(guestServiceMock.cancel).toHaveBeenCalledWith({
      code: 'HB7K2QXM',
      email: 'jane@example.com',
    });
    expect(el('lookup-status')?.textContent?.trim()).toBe('CANCELLED');
    expect(el('lookup-success')).toBeTruthy();
    expect(el('lookup-cancel')).toBeNull();
  });

  it('should not cancel when the user declines', async () => {
    await setup('HB7K2QXM');
    component.email.set('jane@example.com');
    component.lookup();
    spyOn(window, 'confirm').and.returnValue(false);

    component.cancel();

    expect(guestServiceMock.cancel).not.toHaveBeenCalled();
  });

  it('should show the backend message when cancellation fails', async () => {
    await setup('HB7K2QXM');
    component.email.set('jane@example.com');
    component.lookup();
    spyOn(window, 'confirm').and.returnValue(true);
    guestServiceMock.cancel.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({ status: 409, error: { message: 'Booking cannot be cancelled' } }),
      ),
    );

    component.cancel();

    expect(component.errorMessage()).toBe('Booking cannot be cancelled');
    expect(component.cancelling()).toBeFalse();
  });

  it('should hide the cancel button for a cancelled booking', async () => {
    await setup('HB7K2QXM');
    component.booking.set({ ...booking, status: 'CANCELLED' } as any);
    fixture.detectChanges();
    expect(component.canCancel()).toBeFalse();
    expect(el('lookup-cancel')).toBeNull();
  });
});
