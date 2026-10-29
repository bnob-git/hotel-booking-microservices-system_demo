export interface BookingRequest {
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  numGuests?: number;
  specialRequests?: string;
  contactEmail?: string;
  promoCode?: string;
}
