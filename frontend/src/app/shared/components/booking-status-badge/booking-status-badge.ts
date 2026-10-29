import { Component, computed, input } from '@angular/core';

const LABELS: Record<string, string> = {
  PENDING: 'Pending payment',
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  UNPAID: 'Unpaid',
  PAID: 'Paid',
  REFUNDED: 'Refunded',
  PARTIALLY_REFUNDED: 'Partially refunded',
  FAILED: 'Payment failed',
};

/** Coloured pill for a booking status or payment status value. */
@Component({
  selector: 'app-booking-status-badge',
  standalone: true,
  template: `<span
    class="badge"
    [class]="'badge badge--' + cssValue()"
    [attr.data-status]="value()"
    >{{ label() }}</span
  >`,
  styles: `
    .badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.02em;
      background: #eceff1;
      color: #37474f;
    }
    .badge--pending,
    .badge--unpaid {
      background: #fff3e0;
      color: #e65100;
    }
    .badge--confirmed,
    .badge--paid {
      background: #e8f5e9;
      color: #1b5e20;
    }
    .badge--checked_in {
      background: #e3f2fd;
      color: #0d47a1;
    }
    .badge--completed {
      background: #ede7f6;
      color: #4527a0;
    }
    .badge--cancelled,
    .badge--failed {
      background: #ffebee;
      color: #b71c1c;
    }
    .badge--refunded,
    .badge--partially_refunded {
      background: #e0f7fa;
      color: #006064;
    }
  `,
})
export class BookingStatusBadge {
  value = input<string | null | undefined>();

  cssValue = computed(() => (this.value() ?? 'unknown').toLowerCase());
  label = computed(() => {
    const v = this.value();
    return v ? (LABELS[v] ?? v) : '—';
  });
}
