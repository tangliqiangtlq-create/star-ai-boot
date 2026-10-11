import os

ROOT = '/Users/luohuan/Documents/trae_projects/mp-'

FILES_ADMIN = {
    'star-ai-admin/package.json': """{
  "name": "star-ai-admin",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "ant-design-vue": "^4.2.5",
    "@ant-design/icons-vue": "^7.0.1",
    "axios": "^1.7.7",
    "pinia": "^2.2.6",
    "pinia-plugin-persistedstate": "^4.1.2",
    "vue": "^3.5.12",
    "vue-router": "^4.4.5"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.1.4",
    "typescript": "~5.6.3",
    "vite": "^5.4.10",
    "vue-tsc": "^2.1.10",
    "@types/node": "^22.8.1",
    "less": "^4.2.0"
  }
}
""",
}

for path, content in FILES_ADMIN.items():
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        f.write(content)
    print(f'✅ {path}')
print('Done!')
