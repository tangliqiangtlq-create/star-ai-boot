import { AuthGuard } from '@/components/auth/AuthGuard';

const mockCreator = {
  nickname: '@仙侠怪谈',
  level: 3,
  pricingEnabled: true,
  autoPublishPrice: true,
  priceAmountMin: 1,
  priceAmountMax: 199,
  seriesPriceMin: 20,
  seriesPriceMax: 1000,
  globalUnlockPriceMin: 300,
  globalUnlockPriceMax: 2000,
  currentEpisodePrice: 15,
  currentSeriesPrice: 199,
  globalUnlockEnabled: true,
  globalUnlockPrice: 388
};

const mockEarnings = {
  totalIncome: 12485.0,
  withdrawable: 3210.0,
  frozen: 200.0,
  thisMonth: 2100.0,
  subscriptionContribution: 12.5,
  subscriptionRank: 3
};

export default function CreatorDashboardPage() {
  return (
    <AuthGuard requireCreator>
      <main className="min-h-screen p-4 space-y-6">
        <header className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold">🎬 创作者中心</h1>
            <p className="text-sm text-gray-500">
              {mockCreator.nickname} · L{mockCreator.level}
            </p>
          </div>
        </header>

        <section>
          <h2 className="font-bold mb-3">💰 我的收益</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
              <p className="text-xs text-gray-500">累计收入</p>
              <p className="text-xl font-bold">¥{mockEarnings.totalIncome.toLocaleString()}</p>
              <p className="text-xs text-green-600 mt-1">本月 +¥{mockEarnings.thisMonth}</p>
            </div>
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
              <p className="text-xs text-gray-500">可提现</p>
              <p className="text-xl font-bold text-brand-600">¥{mockEarnings.withdrawable}</p>
              <button className="text-xs text-brand-600 mt-1">申请提现 →</button>
            </div>
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
              <p className="text-xs text-gray-500">冻结中</p>
              <p className="text-xl font-bold text-orange-500">¥{mockEarnings.frozen}</p>
              <p className="text-xs text-gray-400 mt-1">1 笔订单</p>
            </div>
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
              <p className="text-xs text-gray-500">订阅池贡献</p>
              <p className="text-xl font-bold">{mockEarnings.subscriptionContribution}%</p>
              <p className="text-xs text-gray-400 mt-1">月第 {mockEarnings.subscriptionRank} 位</p>
            </div>
          </div>
        </section>

        <section>
          <h2 className="font-bold mb-3">⚙️ 三级定价 (创作者自助定价)</h2>
          <div className="space-y-3">
            <PricingRow
              label="单集定价"
              value={mockCreator.currentEpisodePrice}
              min={mockCreator.priceAmountMin}
              max={mockCreator.priceAmountMax}
              unit="¥"
              autoPublish={mockCreator.autoPublishPrice}
            />
            <PricingRow
              label="剧集打包价"
              value={mockCreator.currentSeriesPrice}
              min={mockCreator.seriesPriceMin}
              max={mockCreator.seriesPriceMax}
              unit="¥"
              autoPublish={mockCreator.autoPublishPrice}
            />
            <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
              <div className="flex items-center justify-between mb-2">
                <span className="font-semibold">👤 全局解锁</span>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input type="checkbox" defaultChecked className="sr-only peer" />
                  <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-600"></div>
                </label>
              </div>
              <p className="text-xs text-gray-500">
                区间: ¥{mockCreator.globalUnlockPriceMin} - ¥{mockCreator.globalUnlockPriceMax}
              </p>
              <div className="mt-2 flex items-center gap-2">
                <span className="text-sm">价格:</span>
                <input
                  type="number"
                  defaultValue={mockCreator.globalUnlockPrice}
                  className="w-32 px-2 py-1 border rounded bg-white dark:bg-gray-800"
                />
                <span className="text-xs text-green-600">✅ 自动生效</span>
              </div>
              <p className="text-xs text-gray-400 mt-2">
                开启后, 用户可以付一笔钱永久解锁你所有视频 (含未来新发布的)
              </p>
            </div>
          </div>
          <button className="mt-3 w-full py-2 bg-brand-600 text-white rounded-xl font-bold">
            保存定价
          </button>
        </section>

        <section>
          <h2 className="font-bold mb-3">📋 定价区间参考 (平台方配置)</h2>
          <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4 text-sm">
            <p>
              你的等级 <strong>L{mockCreator.level}</strong> 区间:
            </p>
            <ul className="mt-2 space-y-1 text-xs">
              <li>单集: ¥{mockCreator.priceAmountMin} - ¥{mockCreator.priceAmountMax}</li>
              <li>剧集: ¥{mockCreator.seriesPriceMin} - ¥{mockCreator.seriesPriceMax}</li>
              <li>全局: ¥{mockCreator.globalUnlockPriceMin} - ¥{mockCreator.globalUnlockPriceMax}</li>
            </ul>
            <p className="text-xs text-blue-600 mt-2">
              💡 想调更高? 升级到 L4 可谈 50%+ 抽成, 联系运营
            </p>
          </div>
        </section>
      </main>
    </AuthGuard>
  );
}

function PricingRow({
  label,
  value,
  min,
  max,
  unit,
  autoPublish
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  unit: string;
  autoPublish: boolean;
}) {
  return (
    <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
      <div className="flex items-center justify-between mb-1">
        <span className="font-semibold">{label}</span>
        <span className={`text-xs ${autoPublish ? 'text-green-600' : 'text-orange-500'}`}>
          {autoPublish ? '✅ 自动生效' : '⏳ 待审核'}
        </span>
      </div>
      <p className="text-xs text-gray-500 mb-2">区间: {unit}{min} - {unit}{max}</p>
      <div className="flex items-center gap-2">
        <span className="text-sm">{unit}</span>
        <input
          type="number"
          defaultValue={value}
          min={min}
          max={max}
          className="w-32 px-2 py-1 border rounded bg-white dark:bg-gray-800"
        />
      </div>
    </div>
  );
}
