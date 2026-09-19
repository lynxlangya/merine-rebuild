import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    // 默认只监听回环地址，避免在宿主机直接 `pnpm dev` 时把开发服务器连同
    // /api 代理暴露到局域网。容器内由 compose 注入 VITE_DEV_HOST=0.0.0.0：
    // 宿主机的端口映射经 DNAT 指向容器网卡，容器内必须监听 0.0.0.0 才能被访问。
    host: process.env.VITE_DEV_HOST ?? '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:9002',
      },
    },
  },
});
