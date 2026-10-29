import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { LoyaltyService } from './loyalty.service';
import { API } from '../config/api.config';

describe('LoyaltyService', () => {
  let service: LoyaltyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LoyaltyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should GET the current account', () => {
    service.getMyAccount().subscribe((a) => expect(a.pointsBalance).toBe(1200));

    const req = http.expectOne(`${API.LOYALTY}/me`);
    expect(req.request.method).toBe('GET');
    req.flush({ pointsBalance: 1200 });
  });

  it('should GET my transactions', () => {
    service.getMyTransactions().subscribe((t) => expect(t.length).toBe(1));

    const req = http.expectOne(`${API.LOYALTY}/me/transactions`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, type: 'EARN', points: 100 }]);
  });

  it('should GET the tiers', () => {
    service.getTiers().subscribe((t) => expect(t[0].tier).toBe('BRONZE'));

    const req = http.expectOne(`${API.LOYALTY}/tiers`);
    expect(req.request.method).toBe('GET');
    req.flush([{ tier: 'BRONZE', minLifetimePoints: 0, earnMultiplier: 1, perks: [] }]);
  });

  it('should POST a redemption', () => {
    service
      .redeem({ points: 200, bookingReference: 'HB7K2QXM' })
      .subscribe((r) => expect(r.discountAmount).toBe(10));

    const req = http.expectOne(`${API.LOYALTY}/redeem`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ points: 200, bookingReference: 'HB7K2QXM' });
    req.flush({ redemptionId: 1, pointsRedeemed: 200, discountAmount: 10, pointsBalance: 0 });
  });

  it('should convert points to euros (100 points = €5)', () => {
    expect(service.discountFor(100)).toBe(5);
    expect(service.discountFor(2500)).toBe(125);
    expect(service.discountFor(300, 0.1)).toBe(30);
    expect(service.discountFor(0)).toBe(0);
    expect(service.discountFor(-100)).toBe(0);
    expect(service.discountFor(NaN)).toBe(0);
  });

  it('should validate redemption rules', () => {
    expect(service.validateRedemption(200, 500)).toBeNull();
    expect(service.validateRedemption(500, 500)).toBeNull();
    expect(service.validateRedemption(0, 500)).toContain('positive');
    expect(service.validateRedemption(-100, 500)).toContain('positive');
    expect(service.validateRedemption(12.5, 500)).toContain('positive');
    expect(service.validateRedemption(150, 500)).toContain('multiples of 100');
    expect(service.validateRedemption(600, 500)).toBe('Not enough points');
  });
});
