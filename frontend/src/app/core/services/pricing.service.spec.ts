import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PricingService } from './pricing.service';
import { API } from '../config/api.config';

describe('PricingService', () => {
  let service: PricingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PricingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the quote request', () => {
    const body = { roomId: 1, checkInDate: '2026-11-10', checkOutDate: '2026-11-12', promoCode: 'FLAT25' };

    service.getQuote(body).subscribe((quote) => expect(quote.totalPrice).toBe(95));

    const req = http.expectOne(`${API.PRICING}/quote`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ totalPrice: 95 });
  });
});
