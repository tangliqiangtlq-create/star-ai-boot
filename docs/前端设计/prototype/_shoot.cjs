
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

async function main() {
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const page = await browser.newPage();
  
  const phoneShots = [
    { file: 'index.html', out: 'screenshots/用户端-首页Browse.png' },
    { file: 'video.html', out: 'screenshots/用户端-视频详情.png' },
    { file: 'subscription.html', out: 'screenshots/用户端-会员方案.png' },
    { file: 'creator.html', out: 'screenshots/创作者端-创作者中心.png' },
    { file: 'creator-page.html', out: 'screenshots/创作者端-创作者主页.png' },
  ];
  
  const adminShots = [
    { file: 'admin-review.html', out: 'screenshots/管理后台-审核中心.png', w: 1280, h: 900 },
    { file: 'admin-order.html', out: 'screenshots/管理后台-订单列表.png', w: 1280, h: 900 },
    { file: 'admin-dashboard.html', out: 'screenshots/管理后台-数据看板.png', w: 1280, h: 900 },
  ];
  
  os.makedirs('screenshots', { recursive: true });
  
  for (const s of phoneShots) {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('file://' + path.resolve(s.file));
    await page.waitForTimeout(600);
    await page.screenshot({ path: s.out, fullPage: true });
    console.log('  📱 ' + s.out);
  }
  
  for (const s of adminShots) {
    await page.setViewportSize({ width: s.w, height: s.h });
    await page.goto('file://' + path.resolve(s.file));
    await page.waitForTimeout(600);
    await page.screenshot({ path: s.out, fullPage: true });
    console.log('  🖥️  ' + s.out);
  }
  
  await browser.close();
  console.log('\n✅ 8 张截图完成');
}
main().catch(console.error);
