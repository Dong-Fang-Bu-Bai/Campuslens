import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import os from 'os'
import fs from 'fs'
import path from 'path'

// 自定义插件：重写 Vite 的默认地址打印逻辑，提供极简标注且防止重复
function networkLabelPlugin() {
  return {
    name: 'vite-plugin-network-label',
    configureServer(server) {
      // 覆盖默认的 printUrls 方法
      server.printUrls = () => {
        const address = server.httpServer?.address()
        if (address && typeof address === 'object') {
          const port = address.port
          const interfaces = os.networkInterfaces()
          
          console.log(`\n  \x1b[32m➜\x1b[0m  \x1b[1mLocal:      \x1b[0mhttps://localhost:${port}/`)
          
          const paddedLabels = {
            'WLAN': 'WLAN:       ',
            'VPN': 'VPN:        ',
            'WSL': 'WSL:        ',
            '以太网': '以太网:     ',
            '虚拟机': '虚拟机:     ',
            '局域网': '局域网:     '
          }
          
          for (const name of Object.keys(interfaces)) {
            for (const net of interfaces[name]) {
              if (net.family === 'IPv4' && !net.internal) {
                let label = '局域网'
                if (name.includes('WLAN') || name.includes('Wi-Fi') || name.includes('Wireless')) {
                  label = 'WLAN'
                } else if (name.includes('Ethernet') && !name.includes('vEthernet') && !name.includes('VirtualBox') && !name.includes('VMware')) {
                  label = '以太网'
                } else if (name.includes('VMware') || name.includes('VMnet') || name.includes('VirtualBox')) {
                  label = '虚拟机'
                } else if (name.includes('WSL') || name.includes('vEthernet')) {
                  label = 'WSL'
                } else if (name.includes('mihomo') || name.includes('clash') || name.includes('TAP')) {
                  label = 'VPN'
                }
                
                const prefix = paddedLabels[label] || `${label}: `
                console.log(`  \x1b[32m➜\x1b[0m  \x1b[1m${prefix}\x1b[0mhttps://${net.address}:${port}/`)
              }
            }
          }
          console.log('')
        }
      }
    }
  }
}

const certPath = path.resolve('certs', 'campuslens-dev.p12')

export default defineConfig({
  plugins: [vue(), networkLabelPlugin()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    https: {
      pfx: fs.readFileSync(certPath),
      passphrase: 'campuslens-dev'
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})


