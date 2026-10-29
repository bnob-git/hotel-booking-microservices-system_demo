import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../config/api.config';

import {
  LoyaltyAccount,
  PointsTransaction,
  RedeemRequest,
  RedemptionResponse,
  TierInfo,
} from '../models/loyalty';

/** Matches CONTRACTS.md Track C: 100 points = €5, redeemable in multiples of 100. */
export const DEFAULT_POINT_VALUE = 0.05;
export const REDEMPTION_INCREMENT = 100;

@Injectable({
  providedIn: 'root',
})
export class LoyaltyService {
  private apiUrl = API.LOYALTY;

  constructor(private http: HttpClient) {}

  getMyAccount(): Observable<LoyaltyAccount> {
    return this.http.get<LoyaltyAccount>(`${this.apiUrl}/me`);
  }

  getMyTransactions(): Observable<PointsTransaction[]> {
    return this.http.get<PointsTransaction[]>(`${this.apiUrl}/me/transactions`);
  }

  getTiers(): Observable<TierInfo[]> {
    return this.http.get<TierInfo[]>(`${this.apiUrl}/tiers`);
  }

  redeem(request: RedeemRequest): Observable<RedemptionResponse> {
    return this.http.post<RedemptionResponse>(`${this.apiUrl}/redeem`, request);
  }

  /** Client-side mirror of the server redemption rules, used for the preview only. */
  validateRedemption(points: number, balance: number): string | null {
    if (!Number.isInteger(points) || points <= 0) {
      return 'Enter a positive number of points';
    }
    if (points % REDEMPTION_INCREMENT !== 0) {
      return `Points must be redeemed in multiples of ${REDEMPTION_INCREMENT}`;
    }
    if (points > balance) {
      return 'Not enough points';
    }
    return null;
  }

  discountFor(points: number, pointValue: number = DEFAULT_POINT_VALUE): number {
    if (!Number.isFinite(points) || points <= 0) {
      return 0;
    }
    return Math.round(points * pointValue * 100) / 100;
  }
}
