import Link from 'next/link';

const mockPlans = [
  {
    id: 'monthly',
    planCode: 'monthly',
    planName: '月会员',
    durationDays: 30,
    priceAmount: 19.9,
    giftPoints: 100,
    giftPointsBonus: 0,
    freeEpisodeExtra: 2,
    previewSecExtra: 30,
    tag: null
  },
  {
    id: 'quarterly',
    planCode: 'quarterly',
    planName: '季会员',
    durationDays: 90,
    priceAmount: 49.9,
    giftPoints: 300,
    giftPointsBonus: 0,
    freeEpisodeExtra: 3,
    previewSecExtra: 45,
    tag: null
  },
  {
    id: 'yearly',
    planCode: 'yearly',
    planName: '年会员',
    durationDays: 365,
    priceAmount: 168.0,
    giftPoints: 1200,
    giftPointsBonus: 200,
    freeEpisodeExtra: 5,
    previewSecExtra: -1,
    tag: '🔥 最划算'
  }
];

const benefitIcons = [
  { icon: '🍿', title: '完整畅看', desc: '全部 VIP 内容' },
  { icon: '🆓', title: '会员多几集', desc: '年会员 +5 集' },
  { icon: '👁️', title: '长试看', desc: '年会员完整' },
  { icon: '🎁', title: '充值送积分', desc: '年会员 +1400' }
];

export default function SubscriptionPlansPage() {
  return (
    <main className="min-h-screen">
      <section className="px-4 pt-6 pb-8 bg-gradient-to-b from-brand-50 to-transparent dark:from-brand-900/20">
        <h1 className="text-2xl font-bold text-center">💎 开通会员</h1>
        <p className="text-center text-gray-500 mt-2">畅看全部 VIP 内容 · 专属权益</p>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-6 max-w-2xl mx-auto">
          {benefitIcons.map((b) => (
            <div key={b.title} className="bg-white dark:bg-gray-900 rounded-xl p-3 text-center shadow-sm">
              <div className="text-2xl">{b.icon}</div>
              <div className="text-sm font-bold mt-1">{b.title}</div>
              <div className="text-xs text-gray-500">{b.desc}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="px-4 pb-8">
        <div className="max-w-3xl mx-auto grid md:grid-cols-3 gap-4">
          {mockPlans.map((plan) => (
            <div
              key={plan.id}
              className={`relative rounded-2xl p-5 border transition-all ${
                plan.planCode === 'yearly'
                  ? 'border-brand-400 bg-brand-50 dark:bg-brand-900/20 ring-2 ring-brand-400'
                  : 'border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900'
              }`}
            >
              {plan.tag && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 bg-brand-600 text-white text-xs font-bold rounded-full">
                  {plan.tag}
                </div>
              )}

              <div className="text-center">
                <h3 className="text-lg font-bold">{plan.planName}</h3>
                <div className="mt-2">
                  <span className="text-3xl font-bold text-brand-600">¥{plan.priceAmount}</span>
                  <span className="text-sm text-gray-500"> / {plan.durationDays} 天</span>
                </div>
              </div>

              <ul className="mt-4 space-y-2 text-sm">
                <li className="flex items-center gap-2">
                  <span>🎁</span> 送 {plan.giftPoints + plan.giftPointsBonus} 积分
                  {plan.giftPointsBonus > 0 && <span className="text-xs text-brand-500">+{plan.giftPointsBonus} bonus</span>}
                </li>
                <li className="flex items-center gap-2">
                  <span>🆓</span> 会员多 {plan.freeEpisodeExtra} 集免费
                </li>
                <li className="flex items-center gap-2">
                  <span>👁️</span>
                  {plan.previewSecExtra === -1 ? '完整免费看付费视频' : `试看多 +${plan.previewSecExtra}s`}
                </li>
              </ul>

              <button
                className={`w-full mt-5 py-3 rounded-xl font-bold transition-colors ${
                  plan.planCode === 'yearly'
                    ? 'bg-brand-600 text-white hover:bg-brand-700'
                    : 'bg-gray-900 dark:bg-white text-white dark:text-gray-900 hover:opacity-90'
                }`}
              >
                立即开通
              </button>
            </div>
          ))}
        </div>

        <div className="text-center text-xs text-gray-500 mt-6">
          🔄 自动续费 · 随时可取消 · 支持微信 / 支付宝 / Stripe
        </div>
        <div className="text-center mt-3">
          <Link href="/" className="text-brand-500 text-sm">返回首页 →</Link>
        </div>
      </section>
    </main>
  );
}
