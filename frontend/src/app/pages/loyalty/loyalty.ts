import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import {
  DEFAULT_POINT_VALUE,
  LoyaltyService,
  REDEMPTION_INCREMENT,
} from '../../core/services/loyalty.service';
import { LoyaltyAccount, PointsTransaction, TierInfo } from '../../core/models/loyalty';

@Component({
  selector: 'app-loyalty',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './loyalty.html',
  styleUrl: './loyalty.scss',
})
export class Loyalty implements OnInit {
  account = signal<LoyaltyAccount | null>(null);
  transactions = signal<PointsTransaction[]>([]);
  tiers = signal<TierInfo[]>([]);
  loading = signal(true);
  loadError = signal<string | null>(null);

  redeemPoints = signal<number | null>(null);
  readonly redemptionIncrement = REDEMPTION_INCREMENT;

  /** Percentage of the way from the current tier threshold to the next one. */
  progress = computed(() => {
    const acc = this.account();
    if (!acc) return 0;
    if (!acc.nextTier) return 100;

    const current = this.tiers().find((t) => t.tier === acc.tier)?.minLifetimePoints ?? 0;
    const next =
      this.tiers().find((t) => t.tier === acc.nextTier)?.minLifetimePoints ??
      acc.lifetimePoints + acc.pointsToNextTier;
    const span = next - current;
    if (span <= 0) return 100;

    return Math.min(100, Math.max(0, Math.round(((acc.lifetimePoints - current) / span) * 100)));
  });

  redeemError = computed(() => {
    const points = this.redeemPoints();
    if (points === null) return null;
    return this.loyaltyService.validateRedemption(points, this.account()?.pointsBalance ?? 0);
  });

  redeemValue = computed(() => {
    const points = this.redeemPoints();
    if (points === null || this.redeemError()) return 0;
    return this.loyaltyService.discountFor(
      points,
      this.account()?.pointValue ?? DEFAULT_POINT_VALUE,
    );
  });

  maxRedeemable = computed(() => {
    const balance = this.account()?.pointsBalance ?? 0;
    return Math.floor(balance / REDEMPTION_INCREMENT) * REDEMPTION_INCREMENT;
  });

  constructor(private loyaltyService: LoyaltyService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);

    forkJoin({
      account: this.loyaltyService.getMyAccount(),
      transactions: this.loyaltyService.getMyTransactions(),
      tiers: this.loyaltyService.getTiers(),
    }).subscribe({
      next: ({ account, transactions, tiers }) => {
        this.account.set(account);
        this.transactions.set(transactions);
        this.tiers.set(tiers);
        this.loading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.loadError.set('Failed to load loyalty account');
        this.loading.set(false);
      },
    });
  }

  updateRedeemPoints(value: number | string | null): void {
    if (value === null || value === '') {
      this.redeemPoints.set(null);
      return;
    }
    this.redeemPoints.set(Number(value));
  }

  isCurrentTier(tier: TierInfo): boolean {
    return this.account()?.tier === tier.tier;
  }

  tierLabel(tier: string | null | undefined): string {
    if (!tier) return '';
    return tier.charAt(0) + tier.slice(1).toLowerCase();
  }

  trackById(index: number, tx: PointsTransaction) {
    return tx.id;
  }
}
