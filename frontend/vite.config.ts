import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { copyFileSync } from 'fs'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'copy-netlify-toml',
      closeBundle() {
        // Copy netlify.toml to dist folder after build
        copyFileSync(
          resolve(__dirname, 'netlify.toml'),
          resolve(__dirname, 'dist', 'netlify.toml')
        )
      }
    }
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
