import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * 依赖分组：只把「几乎每个页面都用到、且版本很少变」的基础运行时固定成独立块。
 * 其余依赖交给打包器按真实共享关系自动拆——把整包 antd 强行并成一块，
 * 会让首屏为了 shell 里用的几个组件下载整份 antd，反而抵消路由懒加载。
 */
function vendorChunk(id: string): string | undefined {
  if (!id.includes('node_modules')) return undefined;
  if (/node_modules\/(react|react-dom|scheduler|react-router)\//.test(id)) return 'vendor-react';
  if (/node_modules\/@tanstack\//.test(id)) return 'vendor-query';
  return undefined;
}

export default defineConfig({
  plugins: [react()],
  build: {
    // 页面本身按路由懒加载（见 src/app/routeRegistry.tsx），这里再把第三方依赖拆开：
    // 首屏只下入口 + shell 需要的块，antd / 图标这类大块独立缓存。
    rolldownOptions: {
      output: {
        manualChunks: vendorChunk,
      },
    },
    // 单块阈值留 600 kB：超过它说明有东西没拆开（正常块都在 100 kB 量级）。
    chunkSizeWarningLimit: 600,
  },
  server: {
    // 默认只监听回环地址，避免在宿主机直接 `pnpm dev` 时把开发服务器连同
    // /api 代理暴露到局域网。容器内由 compose 注入 VITE_DEV_HOST=0.0.0.0：
    // 宿主机的端口映射经 DNAT 指向容器网卡，容器内必须监听 0.0.0.0 才能被访问。
    host: process.env.VITE_DEV_HOST ?? '127.0.0.1',
    port: 5173,
    strictPort: true,
    // 启动后先预转换入口与它的静态依赖，避免首屏按需编译串成瀑布
    warmup: {
      clientFiles: ['./src/main.tsx', './src/app/routes.tsx', './src/app/AppShell.tsx'],
    },
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:9002',
      },
    },
  },
  // 打包产物本地预览：与 dev 共用同一套 /api 代理，用来验证构建后的行为。
  preview: {
    host: process.env.VITE_DEV_HOST ?? '127.0.0.1',
    port: 4173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:9002',
      },
    },
  },
});
