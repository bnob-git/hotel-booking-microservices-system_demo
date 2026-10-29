import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { BookingService } from '../../core/services/booking.service';
import { Booking } from '../../core/models/booking';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RoomService } from '../../core/services/room.service';
import { UserService } from '../../core/services/user.service';
import { Room } from '../../core/models/room';
import { BookingResponse } from '../../core/models/booking-response';
import { BookingRequest } from '../../core/models/booking-request';
import { CancellationQuote } from '../../core/models/cancellation-quote';
import { BookingStatusBadge } from '../../shared/components/booking-status-badge/booking-status-badge';

@Component({
  selector: 'app-bookings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    BookingStatusBadge,
  ],
  templateUrl: './bookings.html',
  styleUrl: './bookings.scss',
})
export class Bookings implements OnInit {
  bookings = signal<BookingResponse[]>([]);
  loading = signal(true);

  loadError = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Room details
  room = signal<Room | null>(null);

  // Booking form state
  // Using signals instead of Angular forms to demonstrate fine-grained reactive state management
  newBooking = signal<BookingRequest>({
    roomId: 0,
    checkInDate: '',
    checkOutDate: '',
  });

  // Soft-cancel dialog state (refund preview from the cancellation quote)
  cancelQuote = signal<CancellationQuote | null>(null);
  cancelReason = signal('');
  actionInProgress = signal<number | null>(null);

  private router = inject(Router);

  constructor(
    private bookingService: BookingService,
    private roomService: RoomService,
    private route: ActivatedRoute,
    public userService: UserService,
  ) {}

  ngOnInit(): void {
    const roomId = Number(this.route.snapshot.queryParamMap.get('roomId'));

    if (roomId) {
      this.updateBooking({ roomId });

      // Fetch full room details to display on the booking page and check availability
      this.roomService.getRoomById(roomId).subscribe({
        next: (room) => this.room.set(room),
        error: () => console.warn('Room not found'),
      });
    }

    this.loadBookings();
  }

  // Helper method
  updateBooking(partial: Partial<BookingRequest>) {
    this.newBooking.update((b) => ({ ...b, ...partial }));
  }

  // ================= LOAD =================

  private loadBookings(): void {
    this.loading.set(true);
    this.loadError.set('');

    const user = this.userService.getCurrentUser();

    if (!user) {
      this.loadError.set('User not logged in');
      this.bookings.set([]);
      this.loading.set(false);
      return;
    }

    // Choose API call based on role
    const bookings$ = this.userService.isAdmin()
      ? this.bookingService.getAllBookings()
      : this.bookingService.getMyBookings();

    bookings$.subscribe({
      next: (data) => {
        this.bookings.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.loadError.set('Failed to load bookings');
        this.bookings.set([]);
        this.loading.set(false);
      },
    });
  }

  // ================= FORM UPDATES =================

  updateCheckInDate(date: string) {
    this.updateBooking({ checkInDate: date });
  }

  updateCheckOutDate(date: string) {
    this.updateBooking({ checkOutDate: date });
  }

  // ================= CHECKOUT =================

  goToCheckout(): void {
    const booking = this.newBooking();

    if (booking.roomId <= 0 || !booking.checkInDate || !booking.checkOutDate) {
      this.errorMessage.set('Please fill in all fields');
      this.successMessage.set(null);
      return;
    }

    this.errorMessage.set(null);
    this.router.navigate(['/checkout'], {
      queryParams: {
        roomId: booking.roomId,
        checkIn: booking.checkInDate,
        checkOut: booking.checkOutDate,
      },
    });
  }

  resumePayment(booking: BookingResponse): void {
    this.router.navigate(['/checkout'], { queryParams: { bookingId: booking.id } });
  }

  // ================= CREATE =================

  createBooking(): void {
    const booking = this.newBooking();

    if (booking.roomId <= 0 || !booking.checkInDate || !booking.checkOutDate) {
      this.errorMessage.set('Please fill in all fields');
      this.successMessage.set(null);
      return;
    }

    this.bookingService.createBooking(booking).subscribe({
      next: (created) => {
        this.bookings.set([...this.bookings(), created]);
        this.errorMessage.set(null);
        this.successMessage.set('Booking created successfully');

        // reset dates only (room stays)
        this.newBooking.update((b) => ({
          ...b,
          checkInDate: '',
          checkOutDate: '',
        }));
      },
      error: (err) => {
        this.successMessage.set(null);

        const message = err.error?.message ?? 'Booking failed';

        this.errorMessage.set(message);
      },
    });
  }

  // ================= DELETE =================

  deleteBooking(id: number): void {
    this.bookingService.deleteBooking(id).subscribe({
      next: () => {
        this.bookings.set(this.bookings().filter((b) => b.id !== id));
      },
    });
  }

  // ================= SOFT CANCEL =================

  canCancel(booking: BookingResponse): boolean {
    return booking.status === 'PENDING' || booking.status === 'CONFIRMED';
  }

  openCancel(booking: BookingResponse): void {
    this.actionInProgress.set(booking.id);
    this.errorMessage.set(null);
    this.bookingService.getCancellationQuote(booking.id).subscribe({
      next: (quote) => {
        this.actionInProgress.set(null);
        this.cancelReason.set('');
        this.cancelQuote.set(quote);
      },
      error: (err) => this.actionFailed(err, 'Could not load the cancellation quote'),
    });
  }

  closeCancel(): void {
    this.cancelQuote.set(null);
  }

  confirmCancel(): void {
    const quote = this.cancelQuote();
    if (!quote) {
      return;
    }
    this.actionInProgress.set(quote.bookingId);
    const reason = this.cancelReason().trim() || undefined;
    this.bookingService.cancelBooking(quote.bookingId, reason).subscribe({
      next: (cancelled) => {
        this.cancelQuote.set(null);
        this.replaceBooking(cancelled);
        this.successMessage.set(`Booking #${cancelled.id} cancelled`);
      },
      error: (err) => this.actionFailed(err, 'Cancellation failed'),
    });
  }

  // ================= ADMIN LIFECYCLE =================

  checkIn(booking: BookingResponse): void {
    this.actionInProgress.set(booking.id);
    this.bookingService.checkInBooking(booking.id).subscribe({
      next: (updated) => this.replaceBooking(updated),
      error: (err) => this.actionFailed(err, 'Check-in failed'),
    });
  }

  complete(booking: BookingResponse): void {
    this.actionInProgress.set(booking.id);
    this.bookingService.completeBooking(booking.id).subscribe({
      next: (updated) => this.replaceBooking(updated),
      error: (err) => this.actionFailed(err, 'Could not complete booking'),
    });
  }

  private replaceBooking(updated: BookingResponse): void {
    this.actionInProgress.set(null);
    this.errorMessage.set(null);
    this.bookings.update((list) => list.map((b) => (b.id === updated.id ? updated : b)));
  }

  private actionFailed(err: any, fallback: string): void {
    this.actionInProgress.set(null);
    this.successMessage.set(null);
    this.errorMessage.set(err?.error?.message ?? fallback);
  }

  trackById(index: number, booking: BookingResponse) {
    return booking.id;
  }
}
