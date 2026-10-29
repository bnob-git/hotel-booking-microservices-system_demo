export type Season = 'PEAK' | 'SUMMER' | 'STANDARD' | string;

export interface NightlyRate {
  date: string;
  baseRate: number;
  multiplier: number;
  rate: number;
  season: Season;
}

export interface PriceQuote {
  roomId: number;
  nights: number;
  currency: string;
  baseAmount: number;
  seasonalAdjustment: number;
  promoCode: string | null;
  promoDiscount: number;
  promoApplied: boolean;
  promoMessage: string | null;
  totalPrice: number;
  nightly: NightlyRate[];
}

export interface PriceQuoteRequest {
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  promoCode?: string | null;
}

export interface AvailableRoom {
  roomId: number;
  name: string;
  type: string;
  nightlyBaseRate: number;
  maxGuests?: number | null;
  nights: number;
  quote: PriceQuote;
}

export interface AvailabilitySearch {
  checkIn: string;
  checkOut: string;
  guests?: number | null;
  type?: string | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  promoCode?: string | null;
}
