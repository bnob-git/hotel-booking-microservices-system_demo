import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';

import { Search } from './search';
import { SearchService } from '../../core/services/search.service';
import { AvailableRoom } from '../../core/models/pricing';

function room(id: number, total: number, overrides: Partial<AvailableRoom['quote']> = {}): AvailableRoom {
  return {
    roomId: id,
    name: `Room ${id}`,
    type: 'DELUXE',
    nightlyBaseRate: 100,
    maxGuests: 3,
    nights: 2,
    quote: {
      roomId: id,
      nights: 2,
      currency: 'EUR',
      baseAmount: 200,
      seasonalAdjustment: 25,
      promoCode: null,
      promoDiscount: 0,
      promoApplied: false,
      promoMessage: null,
      totalPrice: total,
      nightly: [
        { date: '2026-12-14', baseRate: 100, multiplier: 1, rate: 100, season: 'STANDARD' },
        { date: '2026-12-15', baseRate: 100, multiplier: 1.25, rate: 125, season: 'PEAK' },
      ],
      ...overrides,
    },
  };
}

describe('Search Component', () => {
  let fixture: ComponentFixture<Search>;
  let component: Search;
  let searchServiceMock: any;
  let routerMock: any;
  let queryParams: Record<string, string>;

  async function create() {
    await TestBed.configureTestingModule({
      imports: [Search],
      providers: [
        { provide: SearchService, useValue: searchServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Search);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const el = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  beforeEach(() => {
    queryParams = {};
    searchServiceMock = {
      searchAvailability: jasmine.createSpy().and.returnValue(of([room(1, 250), room(2, 180)])),
    };
    routerMock = { navigate: jasmine.createSpy() };
  });

  it('renders the form with data-testids and does not search without query dates', async () => {
    await create();

    for (const id of [
      'search-check-in',
      'search-check-out',
      'search-guests',
      'search-type',
      'search-min-price',
      'search-max-price',
      'search-promo',
      'search-submit',
    ]) {
      expect(el(id)).withContext(id).not.toBeNull();
    }
    expect(searchServiceMock.searchAvailability).not.toHaveBeenCalled();
  });

  it('prefills from query params and searches automatically', async () => {
    queryParams = { checkIn: '2026-12-14', checkOut: '2026-12-16', guests: '2', type: 'deluxe', promo: 'WELCOME10' };

    await create();

    expect(searchServiceMock.searchAvailability).toHaveBeenCalledWith({
      checkIn: '2026-12-14',
      checkOut: '2026-12-16',
      guests: 2,
      type: 'DELUXE',
      minPrice: null,
      maxPrice: null,
      promoCode: 'WELCOME10',
    });
  });

  it('shows results sorted by total price with breakdown and season badges', async () => {
    await create();
    component.search();
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('[data-testid^="search-result-"]:not([data-testid="search-result-count"])');
    expect(cards.length).toBe(2);
    expect(cards[0].getAttribute('data-testid')).toBe('search-result-2');
    expect(el('search-result-count')?.textContent).toContain('2 room(s)');
    expect(cards[0].querySelector('[data-testid="result-total"]').textContent).toContain('180.00');
    expect(cards[0].querySelector('[data-testid="result-base"]').textContent).toContain('200.00');
    expect(cards[0].querySelector('[data-testid="result-seasonal"]').textContent).toContain('25.00');
    expect(cards[0].querySelector('[data-testid="season-badge-PEAK"]').textContent).toContain('Peak');

    component.toggleSort();
    fixture.detectChanges();
    expect(component.sortedResults().map((r) => r.roomId)).toEqual([1, 2]);
  });

  it('passes filters to the service', async () => {
    await create();
    component.criteria = {
      checkIn: '2026-11-10',
      checkOut: '2026-11-12',
      guests: 3,
      type: 'SUITE',
      minPrice: 50,
      maxPrice: 300,
      promoCode: ' flat25 ',
    };

    component.search();

    expect(searchServiceMock.searchAvailability).toHaveBeenCalledWith({
      checkIn: '2026-11-10',
      checkOut: '2026-11-12',
      guests: 3,
      type: 'SUITE',
      minPrice: 50,
      maxPrice: 300,
      promoCode: 'flat25',
    });
  });

  it('shows promo message for rejected promo', async () => {
    searchServiceMock.searchAvailability.and.returnValue(
      of([room(1, 225, { promoCode: 'EXPIRED20', promoMessage: 'Promo code EXPIRED20 has expired' })]),
    );
    await create();
    component.search();
    fixture.detectChanges();

    const message = el('search-promo-message');
    expect(message?.textContent).toContain('has expired');
    expect(message?.classList).toContain('rejected');
  });

  it('validates dates client side', async () => {
    await create();
    component.criteria.checkIn = '2026-11-12';
    component.criteria.checkOut = '2026-11-12';

    component.search();
    fixture.detectChanges();

    expect(searchServiceMock.searchAvailability).not.toHaveBeenCalled();
    expect(el('search-error')?.textContent).toContain('Check-out date must be after check-in date');
  });

  it('shows backend error message', async () => {
    searchServiceMock.searchAvailability.and.returnValue(
      throwError(() => ({ error: { message: 'Booking cannot start in the past' } })),
    );
    await create();
    component.search();
    fixture.detectChanges();

    expect(el('search-error')?.textContent).toContain('Booking cannot start in the past');
  });

  it('shows empty state when nothing is available', async () => {
    searchServiceMock.searchAvailability.and.returnValue(of([]));
    await create();
    component.search();
    fixture.detectChanges();

    expect(el('search-no-results')).not.toBeNull();
  });

  it('Book navigates to checkout with the searched stay and applied promo', async () => {
    searchServiceMock.searchAvailability.and.returnValue(
      of([room(7, 202.5, { promoCode: 'WELCOME10', promoApplied: true, promoDiscount: 22.5 })]),
    );
    await create();
    component.criteria = {
      checkIn: '2026-12-14',
      checkOut: '2026-12-16',
      guests: 2,
      type: '',
      minPrice: null,
      maxPrice: null,
      promoCode: 'welcome10',
    };
    component.search();
    fixture.detectChanges();

    (el('search-book-7') as HTMLButtonElement).click();

    expect(routerMock.navigate).toHaveBeenCalledWith(['/checkout'], {
      queryParams: { roomId: 7, checkIn: '2026-12-14', checkOut: '2026-12-16', guests: 2, promo: 'WELCOME10' },
    });
  });
});
