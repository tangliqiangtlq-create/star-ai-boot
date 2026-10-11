/** @type {import('@capacitor/cli').CapacitorConfig} */
const config = {
  appId: 'com.starai.video',
  appName: 'Star AI',
  webDir: 'out',
  server: {
    androidScheme: 'https',
    allowNavigation: ['*.starai.video', '*.minio.io', '*.amazonaws.com', '*.stripe.com']
  },
  plugins: {
    SplashScreen: { launchShowDuration: 2000 },
    Preferences: { enableShmPrebuilt: false }
  },
  android: { allowMixedContent: true },
  ios: {
    contentInset: 'automatic',
    backgroundColor: '#0a0a0a'
  }
};

module.exports = config;
