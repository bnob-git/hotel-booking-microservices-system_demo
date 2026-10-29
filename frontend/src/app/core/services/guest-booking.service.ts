import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../config/api.config';

import { BookingResponse } from '../models/booking-response';
import { GuestBookingRequest, GuestCancelRequest } from '../models/guest-booking';

/** Public guest checkout endpoints (no login / JWT required). */
@Injectable({
  providedIn: 'root',
})
export class GuestBookingService {
  private apiUrl = API.GUEST_BOOKINGS;

  constructor(private http: HttpClient) {}

  createBooking(request: GuestBookingRequest): Observable<BookingResponse> {
    return this.http.post<BookingResponse>(this.apiUrl, request);
  }

  lookup(code: string, email: string): Observable<BookingResponse> {
    const params = new HttpParams().set('code', code.trim()).set('email', email.trim());
    return this.http.get<BookingResponse>(`${this.apiUrl}/lookup`, { params });
  }

  cancel(request: GuestCancelRequest): Observable<BookingResponse> {
    return this.http.post<BookingResponse>(`${this.apiUrl}/cancel`, {
      code: request.code.trim(),
      email: request.email.trim(),
    });
  }
}
