import { BookingStatus } from './booking-response';

export interface CancellationQuote {
  bookingId: number;
  status: BookingStatus;
  cancellable: boolean;
  daysBeforeCheckIn: number;
  refundPercent: number;
  amountPaid: number;
  refundAmount: number;
  cancellationFee: number;
  currency: string;
  policy: string;
}
