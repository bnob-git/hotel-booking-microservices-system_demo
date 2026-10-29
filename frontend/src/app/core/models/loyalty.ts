export type LoyaltyTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';

export type PointsTransactionType = 'EARN' | 'REDEEM' | 'REVERSAL' | 'ADJUST';

export interface LoyaltyAccount {
  userId: number;
  username: string;
  pointsBalance: number;
  lifetimePoints: number;
  tier: LoyaltyTier;
  nextTier: LoyaltyTier | null;
  pointsToNextTier: number;
  pointValue: number;
}

export interface PointsTransaction {
  id: number;
  type: PointsTransactionType;
  points: number;
  bookingId: number | null;
  confirmationCode: string | null;
  description: string | null;
  createdAt: string;
}

export interface TierInfo {
  tier: LoyaltyTier;
  minLifetimePoints: number;
  earnMultiplier: number;
  perks: string[];
}

export interface RedeemRequest {
  points: number;
  bookingReference?: string;
}

export interface RedemptionResponse {
  redemptionId: number;
  pointsRedeemed: number;
  discountAmount: number;
  pointsBalance: number;
}
