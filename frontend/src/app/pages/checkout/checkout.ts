import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';

import { BookingService } from '../../core/services/booking.service';
import { RoomService } from '../../core/services/room.service';
import { LoyaltyService } from '../../core/services/loyalty.service';
import { PricingService } from '../../core/services/pricing.service';
import { PriceQuote } from '../../core/models/pricing';
import { Room } from '../../core/models/room';
import { BookingResponse } from '../../core/models/booking-response';
import { BookingRequest } from '../../core/models/booking-request';
import { BookingStatusBadge } from '../../shared/components/booking-status-badge/booking-status-badge';

export type CheckoutStep = 'review' | 'details' | 'payment' | 'confirmed';

export interface MockCard {
  token: string;
  label: string;
  hint: string;
}

export const MOCK_CARDS: MockCard[] = [
  { token: 'tok_visa', label: 'Visa •••• 4242', hint: 'Payment succeeds' },
  { token: 'tok_mastercard', label: 'Mastercard •••• 4444', hint: 'Payment succeeds' },
  { token: 'tok_chargeDeclined', label: 'Visa •••• 0002', hint: 'Card is declined' },
  { token: 'tok_insufficientFunds', label: 'Visa •••• 9995', hint: 'Insufficient funds' },
];

const DAY_MS = 24 * 60 * 60 * 1000;

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    BookingStatusBadge,
  ],
  templateUrl: './checkout.html',
  styleUrl: './checkout.scss',
})
export class Checkout implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private bookingService = inject(BookingService);
  private roomService = inject(RoomService);
  private loyaltyService = inject(LoyaltyService);
  private pricingService = inject(PricingService);
  private destroyRef = inject(DestroyRef);

  readonly cards = MOCK_CARDS;

  step = signal<CheckoutStep>('review');
  room = signal<Room | null>(null);
  booking = signal<BookingResponse | null>(null);

  roomId = signal(0);
  checkIn = signal('');
  checkOut = signal('');
  promoCode = signal<string | null>(null);

  numGuests = signal(1);
  redeemPoints = signal(0);
  pointsBalance = signal<number | null>(null);
  pointValue = signal(0.05);
  pointsDiscountPreview = computed(() => (Number(this.redeemPoints()) || 0) * this.pointValue());
  contactEmail = signal('');
  specialRequests = signal('');
  paymentMethod = signal(MOCK_CARDS[0].token);

  submitting = signal(false);
  errorMessage = signal<string | null>(null);
  now = signal(Date.now());

  nights = computed(() => {
    const inDate = Date.parse(this.checkIn());
    const outDate = Date.parse(this.checkOut());
    if (Number.isNaN(inDate) || Number.isNaN(outDate)) {
      return 0;
    }
    return Math.max(0, Math.round((outDate - inDate) / DAY_MS));
  });

  quote = signal<PriceQuote | null>(null);

  estimatedTotal = computed(() => {
    const quoted = this.quote()?.totalPrice;
    const base = quoted ?? (this.room()?.price ?? 0) * this.nights();
    return Math.max(0, base - this.pointsDiscountPreview());
  });

  stayValid = computed(() => this.roomId() > 0 && this.nights() > 0);

  holdRemainingMs = computed(() => {
    const expiresAt = this.booking()?.holdExpiresAt;
    return expiresAt ? Math.max(0, Date.parse(expiresAt) - this.now()) : 0;
  });

  holdExpired = computed(
    () =>
      this.booking()?.status === 'EXPIRED' ||
      (this.booking()?.status === 'PENDING' && this.holdRemainingMs() <= 0),
  );

  holdCountdown = computed(() => {
    const totalSeconds = Math.ceil(this.holdRemainingMs() / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.roomId.set(Number(params.get('roomId')) || 0);
    this.checkIn.set(params.get('checkIn') ?? '');
    this.checkOut.set(params.get('checkOut') ?? '');
    this.promoCode.set(params.get('promo') || null);
    const guests = Number(params.get('guests'));
    if (guests > 0) {
      this.numGuests.set(guests);
    }

    this.loyaltyService.getMyAccount().subscribe({
      next: (account) => {
        this.pointsBalance.set(account.pointsBalance);
        this.pointValue.set(account.pointValue ?? 0.05);
      },
      error: () => this.pointsBalance.set(null),
    });

    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => clearInterval(timer));

    const bookingId = Number(params.get('bookingId'));
    if (bookingId) {
      this.resumeBooking(bookingId);
    } else if (this.roomId()) {
      this.loadRoom(this.roomId());
      this.loadQuote();
    }
  }

  private loadQuote(): void {
    if (!this.stayValid()) {
      return;
    }
    this.pricingService
      .getQuote({
        roomId: this.roomId(),
        checkInDate: this.checkIn(),
        checkOutDate: this.checkOut(),
        promoCode: this.promoCode(),
      })
      .subscribe({
        next: (q) => this.quote.set(q),
        error: () => this.quote.set(null),
      });
  }

  // ================= STEPS =================

  continueToDetails(): void {
    if (!this.stayValid()) {
      this.errorMessage.set('Please choose a room and valid check-in / check-out dates');
      return;
    }
    this.errorMessage.set(null);
    this.step.set('details');
  }

  backToReview(): void {
    this.errorMessage.set(null);
    this.step.set('review');
  }

  placeHold(): void {
    const guests = Number(this.numGuests());
    if (!guests || guests < 1) {
      this.errorMessage.set('Please enter the number of guests');
      return;
    }

    const request: BookingRequest = {
      roomId: this.roomId(),
      checkInDate: this.checkIn(),
      checkOutDate: this.checkOut(),
      numGuests: guests,
      contactEmail: this.contactEmail().trim() || undefined,
      specialRequests: this.specialRequests().trim() || undefined,
    };
    if (this.promoCode()) {
      request.promoCode = this.promoCode()!;
    }
    const points = Number(this.redeemPoints()) || 0;
    if (points > 0) {
      if (points % 100 !== 0) {
        this.errorMessage.set('Points must be redeemed in multiples of 100');
        return;
      }
      request.redeemPoints = points;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.bookingService.createBooking(request).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.now.set(Date.now());
        this.booking.set(created);
        this.step.set('payment');
      },
      error: (err) => this.fail(err, 'Could not place the booking'),
    });
  }

  pay(): void {
    const current = this.booking();
    if (!current || this.holdExpired()) {
      this.errorMessage.set('Your hold has expired. Please start a new booking.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.bookingService.payBooking(current.id, this.paymentMethod()).subscribe({
      next: (paid) => {
        this.submitting.set(false);
        this.booking.set(paid);
        this.step.set('confirmed');
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 402) {
          this.booking.update((b) => (b ? { ...b, paymentStatus: 'FAILED' } : b));
        }
        this.fail(err, 'Payment failed');
      },
    });
  }

  startOver(): void {
    this.router.navigate(['/bookings'], { queryParams: { roomId: this.roomId() || null } });
  }

  // ================= HELPERS =================

  private loadRoom(roomId: number): void {
    this.roomService.getRoomById(roomId).subscribe({
      next: (room) => this.room.set(room),
      error: () => this.errorMessage.set('Room not found'),
    });
  }

  private resumeBooking(bookingId: number): void {
    this.bookingService.getBookingById(bookingId).subscribe({
      next: (existing) => {
        this.booking.set(existing);
        this.roomId.set(existing.roomId);
        this.checkIn.set(existing.checkInDate);
        this.checkOut.set(existing.checkOutDate);
        this.numGuests.set(existing.numGuests ?? 1);
        this.loadRoom(existing.roomId);
        this.step.set(existing.status === 'PENDING' ? 'payment' : 'confirmed');
      },
      error: (err) => this.fail(err, 'Booking not found'),
    });
  }

  private fail(err: HttpErrorResponse, fallback: string): void {
    this.submitting.set(false);
    this.errorMessage.set(err?.error?.message ?? fallback);
  }
}
