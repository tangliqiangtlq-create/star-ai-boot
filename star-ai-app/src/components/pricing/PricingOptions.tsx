'use client';

import { UnlockOption } from '@/lib/api/stream';
import clsx from 'clsx';

interface PricingOptionsProps {
  options: UnlockOption[];
  onSelect: (option: UnlockOption) => void;
  hasActiveSubscription?: boolean;
  globalFreeEpisodes?: number;
  currentEpisodeNumber?: number;
}

const optionIcons: Record<UnlockOption['type'], string> = {
  episode: '💰',
  series: '📦',
  creator_global: '👤',
  subscription: '💎'
};

export function PricingOptions({
  options,
  onSelect,
  hasActiveSubscription,
  globalFreeEpisodes,
  currentEpisodeNumber
}: PricingOptionsProps) {
  if (hasActiveSubscription) {
    return (
      <div className="p-4 bg-green-50 dark:bg-green-900/30 rounded-xl border border-green-200 dark:border-green-800">
        <p className="text-green-700 dark:text-green-400 font-bold">
          ✅ 您已开通会员 · 可完整免费观看
        </p>
      </div>
    );
  }

  const isFreeEpisode =
    globalFreeEpisodes !== undefined &&
    currentEpisodeNumber !== undefined &&
    currentEpisodeNumber <= globalFreeEpisodes;

  return (
    <div className="space-y-2">
      {isFreeEpisode && (
        <div className="p-3 bg-blue-50 dark:bg-blue-900/30 rounded-xl border border-blue-200 dark:border-blue-800">
          <p className="text-blue-700 dark:text-blue-400 text-sm">
            🆓 前 {globalFreeEpisodes} 集免费 (平台方配置, 非会员也能看)
          </p>
        </div>
      )}

      {options.map((opt) => (
        <button
          key={opt.type}
          onClick={() => onSelect(opt)}
          className={clsx(
            'w-full p-3 rounded-xl border text-left transition-all',
            'hover:shadow-md hover:border-brand-400',
            opt.highlight
              ? 'bg-brand-50 dark:bg-brand-900/20 border-brand-300 dark:border-brand-700 ring-2 ring-brand-400'
              : 'bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-800'
          )}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-2xl">{optionIcons[opt.type]}</span>
              <div>
                <p className="font-semibold">{opt.label}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400">{opt.description}</p>
              </div>
            </div>
            <div className="text-right">
              {opt.price === 0 ? (
                <span className="text-green-600 font-bold">免费</span>
              ) : (
                <span className="text-lg font-bold text-brand-600">¥{opt.price}</span>
              )}
            </div>
          </div>
        </button>
      ))}
    </div>
  );
}
