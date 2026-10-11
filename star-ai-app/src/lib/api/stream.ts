import api from './client';

export interface Video {
  id: string;
  title: string;
  coverUrl: string;
  episodeNumber?: number;
  collectionId?: string;
  collectionTitle?: string;
  creatorId: string;
  creatorName: string;
  priceType: 'free' | 'paid' | 'member_only';
  priceAmount: number;
  previewSec: number;
  durationSec: number;
  playCount: number;
  tags?: string[];
}

export interface Series {
  id: string;
  title: string;
  coverUrl: string;
  episodeCount: number;
  totalDurationSec: number;
  priceType: 'free' | 'paid' | 'member_only';
  priceAmount: number;
  episodeUnitPrice?: number;
  creatorId: string;
  creatorName: string;
}

export interface Creator {
  id: string;
  nickname: string;
  avatarUrl: string;
  level: number;
  revenueRate: number;
  subscriptionRevenueRate: number;
  totalPlayCount: number;
  followerCount: number;
  globalUnlockEnabled: boolean;
  globalUnlockPrice: number;
  totalEarnings: number;
}

export interface SubscriptionPlan {
  id: string;
  planCode: string;
  planName: string;
  durationDays: number;
  priceAmount: number;
  currency: string;
  region: string;
  giftPoints: number;
  giftPointsBonus: number;
  freeEpisodeExtra: number;
  previewSecExtra: number;
}

export interface VideoDetail extends Video {
  description: string;
  hdPlayUrl?: string;
  playUrl?: string;
  collection?: Series;
  unlockOptions: UnlockOption[];
  isPurchased: boolean;
  isMemberActive: boolean;
  canFreeWatch: boolean;
  globalFreeEpisodes: number;
}

export interface UnlockOption {
  type: 'episode' | 'series' | 'creator_global' | 'subscription';
  label: string;
  price: number;
  description: string;
  highlight?: boolean;
}

export const videoApi = {
  list: (params?: Record<string, unknown>) => api.get('/stream/videos', { params }),
  detail: (id: string) => api.get(`/stream/videos/${id}`),
  browse: (params?: Record<string, unknown>) => api.get('/stream/series', { params })
};

export const creatorApi = {
  detail: (id: string) => api.get(`/stream/creators/${id}`),
  pricingBounds: () => api.get('/stream/creators/pricing-bounds')
};

export const subscriptionApi = {
  plans: (region?: string) => api.get('/stream/subscription/plans', { params: { region } }),
  mySubscription: () => api.get('/stream/subscription/mine')
};
