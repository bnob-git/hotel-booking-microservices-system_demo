export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXPIRED';

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
}
