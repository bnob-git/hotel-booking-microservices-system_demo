import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { SearchService } from '../../core/services/search.service';
import { AvailableRoom } from '../../core/models/pricing';

export interface SearchCriteria {
  checkIn: string;
  checkOut: string;
  guests: number | null;
  type: string;
  minPrice: number | null;
  maxPrice: number | null;
  promoCode: string;
}

export type SortOrder = 'asc' | 'desc';

export const ROOM_TYPES = ['STANDARD', 'DELUXE', 'SUITE'];

function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function daysFromToday(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return isoDate(date);
}

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './search.html',
  styleUrl: './search.scss',
})
export class Search implements OnInit {
  readonly roomTypes = ROOM_TYPES;
  readonly today = daysFromToday(0);

  criteria: SearchCriteria = {
    checkIn: daysFromToday(1),
    checkOut: daysFromToday(3),
    guests: 1,
    type: '',
    minPrice: null,
    maxPrice: null,
    promoCode: '',
  };

  results = signal<AvailableRoom[]>([]);
  loading = signal(false);
  searched = signal(false);
  errorMessage = signal<string | null>(null);
  sortOrder = signal<SortOrder>('asc');

  // Criteria of the last successful search, used for the Book link
  private lastSearch: SearchCriteria | null = null;

  sortedResults = computed(() => {
    const direction = this.sortOrder() === 'asc' ? 1 : -1;
    return [...this.results()].sort(
      (a, b) => direction * (a.quote.totalPrice - b.quote.totalPrice) || a.roomId - b.roomId,
    );
  });

  promoMessage = computed(() => {
    const first = this.results()[0];
    if (!first || !first.quote.promoCode) {
      return null;
    }
    return { applied: first.quote.promoApplied, text: first.quote.promoMessage };
  });

  constructor(
    private searchService: SearchService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const type = params.get('type');
    if (type && ROOM_TYPES.includes(type.toUpperCase())) {
      this.criteria.type = type.toUpperCase();
    }
    this.criteria.checkIn = params.get('checkIn') ?? this.criteria.checkIn;
    this.criteria.checkOut = params.get('checkOut') ?? this.criteria.checkOut;
    const guests = Number(params.get('guests'));
    if (guests > 0) {
      this.criteria.guests = guests;
    }
    this.criteria.promoCode = params.get('promo') ?? '';

    if (params.get('checkIn') && params.get('checkOut')) {
      this.search();
    }
  }

  search(): void {
    this.errorMessage.set(null);

    const c = this.criteria;
    if (!c.checkIn || !c.checkOut) {
      this.errorMessage.set('Please select check-in and check-out dates.');
      return;
    }
    if (c.checkOut <= c.checkIn) {
      this.errorMessage.set('Check-out date must be after check-in date.');
      return;
    }
    if (c.minPrice != null && c.maxPrice != null && c.minPrice > c.maxPrice) {
      this.errorMessage.set('Minimum price must not exceed maximum price.');
      return;
    }

    const snapshot = { ...c, promoCode: c.promoCode.trim() };
    this.loading.set(true);
    this.searchService
      .searchAvailability({
        checkIn: snapshot.checkIn,
        checkOut: snapshot.checkOut,
        guests: snapshot.guests,
        type: snapshot.type || null,
        minPrice: snapshot.minPrice,
        maxPrice: snapshot.maxPrice,
        promoCode: snapshot.promoCode || null,
      })
      .subscribe({
        next: (rooms) => {
          this.results.set(rooms);
          this.lastSearch = snapshot;
          this.searched.set(true);
          this.loading.set(false);
        },
        error: (err) => {
          this.results.set([]);
          this.searched.set(true);
          this.loading.set(false);
          this.errorMessage.set(err?.error?.message || 'Search failed. Please try again.');
        },
      });
  }

  toggleSort(): void {
    this.sortOrder.update((order) => (order === 'asc' ? 'desc' : 'asc'));
  }

  book(room: AvailableRoom): void {
    const s = this.lastSearch ?? this.criteria;
    this.router.navigate(['/checkout'], {
      queryParams: {
        roomId: room.roomId,
        checkIn: s.checkIn,
        checkOut: s.checkOut,
        guests: s.guests ?? 1,
        promo: room.quote.promoApplied ? room.quote.promoCode : '',
      },
    });
  }

  seasonLabel(season: string): string {
    switch (season) {
      case 'PEAK':
        return 'Peak';
      case 'SUMMER':
        return 'Summer';
      default:
        return 'Standard';
    }
  }

  trackByRoom(index: number, room: AvailableRoom) {
    return room.roomId;
  }
}
