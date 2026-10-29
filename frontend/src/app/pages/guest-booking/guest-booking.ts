import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { RoomService } from '../../core/services/room.service';
import { GuestBookingService } from '../../core/services/guest-booking.service';
import { Room } from '../../core/models/room';
import { BookingResponse } from '../../core/models/booking-response';
import { GuestBookingRequest } from '../../core/models/guest-booking';

export const MAX_GUESTS = 10;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-guest-booking',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './guest-booking.html',
  styleUrl: './guest-booking.scss',
})
export class GuestBooking implements OnInit {
  readonly maxGuests = MAX_GUESTS;

  rooms = signal<Room[]>([]);
  form = signal<GuestBookingRequest>({
    roomId: 0,
    checkInDate: '',
    checkOutDate: '',
    numGuests: 1,
    guestName: '',
    guestEmail: '',
    specialRequests: '',
  });

  submitting = signal(false);
  errorMessage = signal<string | null>(null);
  confirmation = signal<BookingResponse | null>(null);

  selectedRoom = computed(() => this.rooms().find((r) => r.id === this.form().roomId) ?? null);

  nights = computed(() => {
    const { checkInDate, checkOutDate } = this.form();
    if (!checkInDate || !checkOutDate) return 0;
    const diff = (Date.parse(checkOutDate) - Date.parse(checkInDate)) / 86_400_000;
    return diff > 0 ? Math.round(diff) : 0;
  });

  estimatedTotal = computed(() => {
    const room = this.selectedRoom();
    return room ? room.price * this.nights() : 0;
  });

  constructor(
    private roomService: RoomService,
    private guestBookingService: GuestBookingService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const roomId = Number(this.route.snapshot.queryParamMap.get('roomId'));
    if (roomId > 0) {
      this.update('roomId', roomId);
    }
    this.roomService.getAllRooms().subscribe({
      next: (rooms) => this.rooms.set(rooms.filter((r) => r.available || r.id === roomId)),
      error: () => this.errorMessage.set('Could not load rooms. Please try again later.'),
    });
  }

  update<K extends keyof GuestBookingRequest>(field: K, value: GuestBookingRequest[K]): void {
    this.form.update((f) => ({ ...f, [field]: value }));
  }

  validate(): string | null {
    const f = this.form();
    if (!f.roomId) return 'Please select a room.';
    if (!f.checkInDate || !f.checkOutDate) return 'Please choose check-in and check-out dates.';
    if (f.checkOutDate <= f.checkInDate) return 'Check-out date must be after check-in date.';
    if (!f.numGuests || f.numGuests < 1 || f.numGuests > MAX_GUESTS) {
      return `Number of guests must be between 1 and ${MAX_GUESTS}.`;
    }
    if (!f.guestName.trim()) return 'Please enter your name.';
    if (!EMAIL_PATTERN.test(f.guestEmail.trim())) return 'Please enter a valid email address.';
    return null;
  }

  submit(): void {
    this.errorMessage.set(null);
    const error = this.validate();
    if (error) {
      this.errorMessage.set(error);
      return;
    }

    const f = this.form();
    const request: GuestBookingRequest = {
      ...f,
      guestName: f.guestName.trim(),
      guestEmail: f.guestEmail.trim(),
      specialRequests: f.specialRequests?.trim() || undefined,
    };

    this.submitting.set(true);
    this.guestBookingService.createBooking(request).subscribe({
      next: (booking) => {
        this.submitting.set(false);
        this.confirmation.set(booking);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(err.error?.message || 'Booking failed. Please try again.');
      },
    });
  }

  bookAnother(): void {
    this.confirmation.set(null);
    this.form.update((f) => ({ ...f, checkInDate: '', checkOutDate: '', specialRequests: '' }));
  }
}
