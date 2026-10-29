import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../config/api.config';
import { PriceQuote, PriceQuoteRequest } from '../models/pricing';

@Injectable({
  providedIn: 'root',
})
export class PricingService {
  private apiUrl = API.PRICING;

  constructor(private http: HttpClient) {}

  getQuote(request: PriceQuoteRequest): Observable<PriceQuote> {
    return this.http.post<PriceQuote>(`${this.apiUrl}/quote`, request);
  }
}
