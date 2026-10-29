import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SearchService } from './search.service';
import { API } from '../config/api.config';

describe('SearchService', () => {
  let service: SearchService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SearchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends dates and all provided filters as query params', () => {
    service
      .searchAvailability({
        checkIn: '2026-12-20',
        checkOut: '2026-12-22',
        guests: 2,
        type: 'DELUXE',
        minPrice: 50,
        maxPrice: 150,
        promoCode: ' WELCOME10 ',
      })
      .subscribe((rooms) => expect(rooms.length).toBe(0));

    const req = http.expectOne((r) => r.url === API.AVAILABILITY);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('checkIn')).toBe('2026-12-20');
    expect(req.request.params.get('checkOut')).toBe('2026-12-22');
    expect(req.request.params.get('guests')).toBe('2');
    expect(req.request.params.get('type')).toBe('DELUXE');
    expect(req.request.params.get('minPrice')).toBe('50');
    expect(req.request.params.get('maxPrice')).toBe('150');
    expect(req.request.params.get('promoCode')).toBe('WELCOME10');
    req.flush([]);
  });

  it('omits empty optional filters', () => {
    service
      .searchAvailability({ checkIn: '2026-11-10', checkOut: '2026-11-12', type: '', guests: null, promoCode: '  ' })
      .subscribe();

    const req = http.expectOne((r) => r.url === API.AVAILABILITY);
    expect(req.request.params.keys().sort()).toEqual(['checkIn', 'checkOut']);
    req.flush([]);
  });
});
