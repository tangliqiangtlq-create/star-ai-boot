'use client';

import Link from 'next/link';
import clsx from 'clsx';

interface VideoCardProps {
  id: string;
  title: string;
  coverUrl: string;
  episodeCount?: number;
  priceAmount: number;
  priceType: 'free' | 'paid' | 'member_only' | 'series' | 'creator_global';
  freeEpisodes?: number;
  previewSec?: number;
  creatorName?: string;
  blurOnLocked?: boolean;
}

const priceLabels: Record<VideoCardProps['priceType'], string> = {
  free: '免费',
  paid: '单集',
  member_only: 'VIP 专享',
  series: '剧集打包',
  creator_global: '全局解锁'
};

export function VideoCard({
  id,
  title,
  coverUrl,
  episodeCount,
  priceAmount,
  priceType,
  freeEpisodes,
  creatorName,
  blurOnLocked = true
}: VideoCardProps) {
  const isLocked = priceType === 'member_only';
  const blurLocked = isLocked && blurOnLocked;

  return (
    <Link href={`/video/${id}`} className="block group">
      <div className="relative aspect-[3/4] rounded-lg overflow-hidden bg-gray-900">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={coverUrl}
          alt={title}
          className={clsx(
            'w-full h-full object-cover transition-transform duration-300 group-hover:scale-105',
            blurLocked && 'blur-xl'
          )}
        />

        {isLocked && (
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="px-3 py-1 bg-black/60 rounded-full text-white text-xs font-bold">
              🔒VIP
            </span>
          </div>
        )}

        <div className="absolute top-2 left-2 flex gap-1">
          {priceType === 'free' && (
            <span className="px-2 py-0.5 bg-green-500 rounded text-xs text-white font-bold">免费</span>
          )}
          {priceAmount > 0 && !isLocked && (
            <span className="px-2 py-0.5 bg-brand-600 rounded text-xs text-white font-bold">
              ¥{priceAmount}
            </span>
          )}
        </div>

        {freeEpisodes && freeEpisodes > 0 && !isLocked && (
          <div className="absolute top-2 right-2">
            <span className="px-2 py-0.5 bg-blue-500 rounded text-xs text-white">
              前 {freeEpisodes} 集免费
            </span>
          </div>
        )}

        {episodeCount && (
          <div className="absolute bottom-2 right-2">
            <span className="px-2 py-0.5 bg-black/60 rounded text-xs text-white">
              共 {episodeCount} 集
            </span>
          </div>
        )}
      </div>

      <div className="mt-2">
        <h3 className="text-sm font-medium line-clamp-2 leading-tight">{title}</h3>
        {creatorName && (
          <p className="text-xs text-gray-500 mt-0.5">{creatorName}</p>
        )}
      </div>
    </Link>
  );
}
