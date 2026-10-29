import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Loyalty } from './loyalty';
import { LoyaltyService } from '../../core/services/loyalty.service';
import { LoyaltyAccount, PointsTransaction, TierInfo } from '../../core/models/loyalty';

describe('Loyalty Component', () => {
  let fixture: ComponentFixture<Loyalty>;
  let component: Loyalty;
  let loyaltyServiceMock: any;

  const tiers: TierInfo[] = [
    { tier: 'BRONZE', minLifetimePoints: 0, earnMultiplier: 1.0, perks: ['Member rates'] },
    { tier: 'SILVER', minLifetimePoints: 1000, earnMultiplier: 1.25, perks: ['Late checkout'] },
    { tier: 'GOLD', minLifetimePoints: 5000, earnMultiplier: 1.5, perks: ['Room upgrade'] },
    { tier: 'PLATINUM', minLifetimePoints: 15000, earnMultiplier: 2.0, perks: ['Suite upgrade'] },
  ];

  const account: LoyaltyAccount = {
    userId: 3,
    username: 'alice',
    pointsBalance: 2950,
    lifetimePoints: 3000,
    tier: 'SILVER',
    nextTier: 'GOLD',
    pointsToNextTier: 2000,
    pointValue: 0.05,
  };

  const transactions: PointsTransaction[] = [
    {
      id: 2,
      type: 'REVERSAL',
      points: -50,
      bookingId: 9,
      confirmationCode: 'HB000009',
      description: 'Reversed',
      createdAt: '2026-10-29T12:00:00',
    },
    {
      id: 1,
      type: 'EARN',
      points: 3000,
      bookingId: 8,
      confirmationCode: 'HB000008',
      description: 'Earned',
      createdAt: '2026-10-28T12:00:00',
    },
  ];

  const el = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const all = (testId: string): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`));

  const create = async (overrides: Partial<Record<string, any>> = {}) => {
    const real = new LoyaltyService(null as any);
    loyaltyServiceMock = {
      getMyAccount: jasmine.createSpy().and.returnValue(of(account)),
      getMyTransactions: jasmine.createSpy().and.returnValue(of(transactions)),
      getTiers: jasmine.createSpy().and.returnValue(of(tiers)),
      validateRedemption: jasmine
        .createSpy()
        .and.callFake((p: number, b: number) => real.validateRedemption(p, b)),
      discountFor: jasmine
        .createSpy()
        .and.callFake((p: number, v: number) => real.discountFor(p, v)),
      ...overrides,
    };

    await TestBed.configureTestingModule({
      imports: [Loyalty],
      providers: [{ provide: LoyaltyService, useValue: loyaltyServiceMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(Loyalty);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('should load account, transactions and tiers on init', async () => {
    await create();

    expect(loyaltyServiceMock.getMyAccount).toHaveBeenCalled();
    expect(loyaltyServiceMock.getMyTransactions).toHaveBeenCalled();
    expect(loyaltyServiceMock.getTiers).toHaveBeenCalled();
    expect(component.loading()).toBeFalse();
    expect(component.account()).toEqual(account);
  });

  it('should render balance, tier badge and next-tier hint', async () => {
    await create();

    expect(el('loyalty-balance')?.textContent).toContain('2,950');
    expect(el('loyalty-lifetime')?.textContent).toContain('3,000');
    expect(el('loyalty-balance-value')?.textContent).toContain('147.50');
    expect(el('loyalty-tier-badge')?.textContent?.trim()).toBe('Silver');
    expect(el('loyalty-next-tier')?.textContent).toContain('2,000 points to Gold');
    expect(el('loyalty-tier-progress')).toBeTruthy();
  });

  it('should compute progress between current and next tier thresholds', async () => {
    await create();
    // 3000 is halfway from SILVER (1000) to GOLD (5000)
    expect(component.progress()).toBe(50);
  });

  it('should show full progress and top-tier message for PLATINUM', async () => {
    await create({
      getMyAccount: jasmine
        .createSpy()
        .and.returnValue(
          of({
            ...account,
            tier: 'PLATINUM',
            nextTier: null,
            pointsToNextTier: 0,
            lifetimePoints: 20000,
          }),
        ),
    });

    expect(component.progress()).toBe(100);
    expect(el('loyalty-next-tier')?.textContent).toContain('top tier');
  });

  it('should render the tiers table and highlight the current tier', async () => {
    await create();

    expect(el('loyalty-tiers-table')).toBeTruthy();
    for (const t of tiers) {
      expect(el(`loyalty-tier-row-${t.tier}`)).toBeTruthy();
    }
    expect(el('loyalty-tier-row-SILVER')?.classList).toContain('current');
    expect(el('loyalty-tier-row-GOLD')?.classList).not.toContain('current');
    expect(el('loyalty-tier-row-GOLD')?.textContent).toContain('Room upgrade');
    expect(el('loyalty-tier-row-PLATINUM')?.textContent).toContain('×2');
  });

  it('should render transaction rows with signed points', async () => {
    await create();

    const rows = all('loyalty-transaction-row');
    expect(rows.length).toBe(2);
    expect(all('loyalty-transaction-type').map((e) => e.textContent?.trim())).toEqual([
      'REVERSAL',
      'EARN',
    ]);
    expect(all('loyalty-transaction-confirmation')[1].textContent).toContain('HB000008');
    const points = all('loyalty-transaction-points');
    expect(points[0].textContent?.trim()).toBe('-50');
    expect(points[0].classList).toContain('negative');
    expect(points[1].textContent?.trim()).toBe('+3,000');
    expect(points[1].classList).toContain('positive');
  });

  it('should show an empty state when there are no transactions', async () => {
    await create({ getMyTransactions: jasmine.createSpy().and.returnValue(of([])) });

    expect(el('loyalty-no-transactions')).toBeTruthy();
    expect(el('loyalty-transactions-table')).toBeNull();
  });

  it('should preview the euro value of a valid redemption', async () => {
    await create();

    component.updateRedeemPoints('200');
    fixture.detectChanges();

    expect(component.redeemError()).toBeNull();
    expect(component.redeemValue()).toBe(10);
    expect(el('loyalty-redeem-value')?.textContent).toContain('10.00');
    expect(el('loyalty-redeem-error')).toBeNull();
  });

  it('should show validation errors in the redemption preview', async () => {
    await create();

    component.updateRedeemPoints(150);
    fixture.detectChanges();
    expect(el('loyalty-redeem-error')?.textContent).toContain('multiples of 100');
    expect(component.redeemValue()).toBe(0);

    component.updateRedeemPoints(3000);
    fixture.detectChanges();
    expect(el('loyalty-redeem-error')?.textContent).toContain('Not enough points');

    component.updateRedeemPoints('');
    fixture.detectChanges();
    expect(component.redeemPoints()).toBeNull();
    expect(el('loyalty-redeem-error')).toBeNull();
  });

  it('should compute the maximum redeemable points', async () => {
    await create();
    expect(component.maxRedeemable()).toBe(2900);
  });

  it('should show an error when loading fails', async () => {
    spyOn(console, 'error');
    await create({
      getMyAccount: jasmine.createSpy().and.returnValue(throwError(() => new Error('boom'))),
    });

    expect(component.loading()).toBeFalse();
    expect(el('loyalty-error')?.textContent).toContain('Failed to load loyalty account');
    expect(el('loyalty-balance')).toBeNull();
  });
});
