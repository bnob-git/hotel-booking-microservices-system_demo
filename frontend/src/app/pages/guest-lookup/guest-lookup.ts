import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { GuestBookingService } from '../../core/services/guest-booking.service';
import { BookingResponse } from '../../core/models/booking-response';

const CANCELLABLE = ['PENDING', 'CONFIRMED'];

@Component({
  selector: 'app-guest-lookup',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './guest-lookup.html',
  styleUrl: './guest-lookup.scss',
})
export class GuestLookup implements OnInit {
  code = signal('');
  email = signal('');

  loading = signal(false);
  cancelling = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  booking = signal<BookingResponse | null>(null);

  canCancel = computed(() => {
    const b = this.booking();
    return !!b && !!b.status && CANCELLABLE.includes(b.status);
  });

  constructor(
    private guestBookingService: GuestBookingService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) {
      this.code.set(code.toUpperCase());
    }
  }

  lookup(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    if (!this.code().trim() || !this.email().trim()) {
      this.errorMessage.set('Please enter your confirmation code and email.');
      return;
    }

    this.loading.set(true);
    this.guestBookingService.lookup(this.code(), this.email()).subscribe({
      next: (booking) => {
        this.loading.set(false);
        this.booking.set(booking);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.booking.set(null);
        this.errorMessage.set(this.messageFor(err));
      },
    });
  }

  cancel(): void {
    if (!this.canCancel() || !confirm('Cancel this booking?')) {
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.cancelling.set(true);
    this.guestBookingService.cancel({ code: this.code(), email: this.email() }).subscribe({
      next: (booking) => {
        this.cancelling.set(false);
        this.booking.set(booking);
        this.successMessage.set(
          'Your booking has been cancelled. A confirmation email is on its way.',
        );
      },
      error: (err: HttpErrorResponse) => {
        this.cancelling.set(false);
        this.errorMessage.set(err.error?.message || 'Cancellation failed. Please try again.');
      },
    });
  }

  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 404) {
      return 'No booking matches that confirmation code and email.';
    }
    if (err.status === 429) {
      return 'Too many attempts. Please wait a minute and try again.';
    }
    return err.error?.message || 'Lookup failed. Please try again.';
  }
}
