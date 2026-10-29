export interface GuestBookingRequest {
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  numGuests: number;
  guestName: string;
  guestEmail: string;
  specialRequests?: string;
}

export interface GuestCancelRequest {
  code: string;
  email: string;
}
