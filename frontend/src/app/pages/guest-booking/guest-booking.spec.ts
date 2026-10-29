import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { GuestBooking } from './guest-booking';
import { RoomService } from '../../core/services/room.service';
import { GuestBookingService } from '../../core/services/guest-booking.service';

describe('GuestBooking Component', () => {
  let fixture: ComponentFixture<GuestBooking>;
  let component: GuestBooking;
  let roomServiceMock: any;
  let guestServiceMock: any;

  const rooms = [
    { id: 4, name: 'Room 201', type: 'DOUBLE', price: 120, available: true },
    { id: 5, name: 'Room 202', type: 'SINGLE', price: 80, available: false },
  ];

  async function setup(roomId: string | null = null) {
    roomServiceMock = { getAllRooms: jasmine.createSpy().and.returnValue(of(rooms)) };
    guestServiceMock = { createBooking: jasmine.createSpy() };

    await TestBed.configureTestingModule({
      imports: [GuestBooking],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: RoomService, useValue: roomServiceMock },
        { provide: GuestBookingService, useValue: guestServiceMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => roomId } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GuestBooking);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillValidForm() {
    component.update('roomId', 4);
    component.update('checkInDate', '2026-12-01');
    component.update('checkOutDate', '2026-12-04');
    component.update('numGuests', 2);
    component.update('guestName', ' Jane Guest ');
    component.update('guestEmail', ' jane@example.com ');
    component.update('specialRequests', '  ');
  }

  function el(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('should load only available rooms', async () => {
    await setup();
    expect(component.rooms().map((r) => r.id)).toEqual([4]);
    expect(el('guest-booking-form')).toBeTruthy();
    expect(el('guest-name')).toBeTruthy();
    expect(el('guest-email')).toBeTruthy();
    expect(el('guest-booking-submit')).toBeTruthy();
  });

  it('should preselect the room from ?roomId=', async () => {
    await setup('4');
    expect(component.form().roomId).toBe(4);
    expect(component.selectedRoom()?.name).toBe('Room 201');
  });

  it('should compute nights and estimated total', async () => {
    await setup();
    fillValidForm();
    expect(component.nights()).toBe(3);
    expect(component.estimatedTotal()).toBe(360);
  });

  it('should show validation errors without calling the API', async () => {
    await setup();
    component.submit();
    expect(component.errorMessage()).toBe('Please select a room.');

    fillValidForm();
    component.update('checkOutDate', '2026-12-01');
    component.submit();
    expect(component.errorMessage()).toBe('Check-out date must be after check-in date.');

    fillValidForm();
    component.update('numGuests', 11);
    component.submit();
    expect(component.errorMessage()).toContain('between 1 and 10');

    fillValidForm();
    component.update('guestName', ' ');
    component.submit();
    expect(component.errorMessage()).toBe('Please enter your name.');

    fillValidForm();
    component.update('guestEmail', 'nope');
    component.submit();
    expect(component.errorMessage()).toBe('Please enter a valid email address.');

    fixture.detectChanges();
    expect(el('guest-booking-error')?.textContent).toContain('valid email');
    expect(guestServiceMock.createBooking).not.toHaveBeenCalled();
  });

  it('should create a booking and show the confirmation code', async () => {
    await setup();
    guestServiceMock.createBooking.and.returnValue(
      of({
        id: 9,
        confirmationCode: 'HB7K2QXM',
        status: 'CONFIRMED',
        roomName: 'Room 201',
        contactEmail: 'jane@example.com',
        checkInDate: '2026-12-01',
        checkOutDate: '2026-12-04',
        numGuests: 2,
        totalPrice: 360,
      }),
    );
    fillValidForm();

    component.submit();
    fixture.detectChanges();

    expect(guestServiceMock.createBooking).toHaveBeenCalledWith({
      roomId: 4,
      checkInDate: '2026-12-01',
      checkOutDate: '2026-12-04',
      numGuests: 2,
      guestName: 'Jane Guest',
      guestEmail: 'jane@example.com',
      specialRequests: undefined,
    });
    expect(el('guest-confirmation-code')?.textContent?.trim()).toBe('HB7K2QXM');
    expect(el('guest-booking-status')?.textContent?.trim()).toBe('CONFIRMED');
    expect(el('guest-manage-booking')?.getAttribute('href')).toBe('/guest/lookup?code=HB7K2QXM');
    expect(el('guest-booking-form')).toBeNull();
  });

  it('should show the backend error message on failure', async () => {
    await setup();
    guestServiceMock.createBooking.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Room already booked for these dates' },
          }),
      ),
    );
    fillValidForm();

    component.submit();

    expect(component.errorMessage()).toBe('Room already booked for these dates');
    expect(component.submitting()).toBeFalse();
    expect(component.confirmation()).toBeNull();
  });

  it('should reset to the form when booking another stay', async () => {
    await setup();
    component.confirmation.set({ confirmationCode: 'HB7K2QXM' } as any);
    component.bookAnother();
    expect(component.confirmation()).toBeNull();
    expect(component.form().checkInDate).toBe('');
  });
});
