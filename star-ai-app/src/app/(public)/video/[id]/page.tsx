import { VideoPlayer } from '@/components/video/VideoPlayer';
import { PricingOptions } from '@/components/pricing/PricingOptions';

export default async function VideoDetailPage({ params }: { params: { id: string } }) {
  // 实际项目里: const detail = await videoApi.detail(params.id);
  const mockDetail = {
    id: params.id,
    title: '龙女入怀：雨夜孽缘',
    episodeNumber: 1,
    totalEpisodes: 16,
    description: '深山夜雨，垂死的龙女被凡人所救。一段跨越人妖界限的孽缘就此展开——报恩、纠缠、沉沦。',
    creatorId: '1001',
    creatorName: '@仙侠怪谈',
    creatorLevel: 3,
    revenueRate: 35,
    priceType: 'paid' as const,
    priceAmount: 15.0,
    previewSec: 30,
    playUrl: 'https://demo.starai.video/hls/demo.m3u8',
    coverUrl: '/demo/cover1.jpg',
    globalFreeEpisodes: 3,
    unlockOptions: [
      { type: 'episode' as const, label: '买这一集', price: 15, description: '永久解锁本集' },
      { type: 'series' as const, label: '解锁整部剧集', price: 199, description: '16 集, 省 ¥41', highlight: true },
      { type: 'creator_global' as const, label: '解锁 @仙侠怪谈 全部', price: 388, description: '该创作者所有内容' },
      { type: 'subscription' as const, label: '开通会员', price: 19.9, description: '月会员, 送 100 积分' }
    ]
  };

  return (
    <main className="min-h-screen pb-8">
      <VideoPlayer
        src={mockDetail.playUrl}
        poster={mockDetail.coverUrl}
        previewSec={mockDetail.previewSec}
        isPaid={mockDetail.priceType === 'paid'}
        className="aspect-video"
      />

      <div className="p-4">
        <div className="flex items-start justify-between gap-3 mb-4">
          <div>
            <h1 className="text-xl font-bold leading-tight">
              {mockDetail.title} · 第 {mockDetail.episodeNumber} 集 / 共 {mockDetail.totalEpisodes} 集
            </h1>
            <p className="text-xs text-gray-500 mt-1">
              {mockDetail.creatorName} · L{mockDetail.creatorLevel} · 分润 {mockDetail.revenueRate}% 💰
            </p>
          </div>
        </div>

        <p className="text-sm text-gray-600 dark:text-gray-300 mb-4">
          {mockDetail.description}
        </p>

        <div className="mb-2 text-sm font-semibold">选择解锁方式</div>
        <PricingOptions
          options={mockDetail.unlockOptions}
          globalFreeEpisodes={mockDetail.globalFreeEpisodes}
          currentEpisodeNumber={mockDetail.episodeNumber}
          onSelect={(opt) => console.log('Selected:', opt)}
        />

        <div className="mt-6">
          <h2 className="text-lg font-bold mb-3">选集 ({mockDetail.totalEpisodes} 集)</h2>
          <div className="grid grid-cols-4 sm:grid-cols-6 gap-2">
            {Array.from({ length: mockDetail.totalEpisodes }, (_, i) => {
              const ep = i + 1;
              const isFree = ep <= mockDetail.globalFreeEpisodes;
              const isCurrent = ep === mockDetail.episodeNumber;
              return (
                <a
                  key={ep}
                  href={`/video/${mockDetail.id}?ep=${ep}`}
                  className={`aspect-square rounded-lg flex items-center justify-center text-sm font-bold transition-all ${
                    isCurrent
                      ? 'bg-brand-600 text-white'
                      : isFree
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 hover:bg-blue-200'
                      : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400 hover:bg-gray-200'
                  }`}
                >
                  {isFree && !isCurrent ? '🆓' : ep}
                </a>
              );
            })}
          </div>
        </div>
      </div>
    </main>
  );
}
