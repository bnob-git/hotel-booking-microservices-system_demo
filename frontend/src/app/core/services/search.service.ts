import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../config/api.config';
import { AvailabilitySearch, AvailableRoom } from '../models/pricing';

@Injectable({
  providedIn: 'root',
})
export class SearchService {
  private apiUrl = API.AVAILABILITY;

  constructor(private http: HttpClient) {}

  searchAvailability(search: AvailabilitySearch): Observable<AvailableRoom[]> {
    let params = new HttpParams().set('checkIn', search.checkIn).set('checkOut', search.checkOut);
    const optional: [string, unknown][] = [
      ['guests', search.guests],
      ['type', search.type],
      ['minPrice', search.minPrice],
      ['maxPrice', search.maxPrice],
      ['promoCode', search.promoCode?.trim()],
    ];
    for (const [key, value] of optional) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<AvailableRoom[]>(this.apiUrl, { params });
  }
}
