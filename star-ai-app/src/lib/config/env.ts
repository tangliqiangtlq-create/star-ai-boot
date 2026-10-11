const DEFAULTS = {
  API_BASE: 'http://localhost:8080/jeecg-boot',
  TOKEN_KEY: 'X-Access-Token',
  REFRESH_INTERVAL: 300000
};

export const env = {
  API_BASE: process.env.NEXT_PUBLIC_API_BASE || DEFAULTS.API_BASE,
  APP_REGION: process.env.NEXT_PUBLIC_REGION || 'cn',
  TOKEN_KEY: DEFAULTS.TOKEN_KEY,
  IS_CAPACITOR: typeof document !== 'undefined' && !!document.querySelector('meta[name="capacitor"]'),
  IS_IOS: typeof navigator !== 'undefined' && /iPhone|iPad|iPod/.test(navigator.userAgent),
  IS_ANDROID: typeof navigator !== 'undefined' && /Android/.test(navigator.userAgent)
};
