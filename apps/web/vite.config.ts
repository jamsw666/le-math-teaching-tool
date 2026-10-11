import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Tauri / 离线壳：相对 base，避免 asset:// 下绝对路径 /assets 失效
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  base: './',
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // WebView2 ≈ Chromium；保持较新目标即可
    target: 'es2022',
    sourcemap: false,
  },
})
