export type BookingStatus =
  'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';

export type PaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED' | 'PARTIALLY_REFUNDED' | 'FAILED';

export interface BookingResponse {
  id: number;
  checkInDate: string;
  checkOutDate: string;

  userId: number;
  roomId: number;

  username: string;
  roomName: string;

  status?: BookingStatus;
  totalPrice?: number;
  numGuests?: number;
  confirmationCode?: string;
  specialRequests?: string;
  contactEmail?: string;
  createdAt?: string;

  holdExpiresAt?: string;
  paymentStatus?: PaymentStatus;
  paymentReference?: string;
  amountPaid?: number;
  refundAmount?: number;
  cancelledAt?: string;
  cancellationReason?: string;
  checkedInAt?: string;
  completedAt?: string;
}
