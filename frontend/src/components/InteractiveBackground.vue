<template>
  <div class="interactive-bg-wrapper">
    <canvas ref="canvasRef"></canvas>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const canvasRef = ref(null)
let animationFrameId = null

// 粒子类定义
class Particle {
  constructor(width, height, isClickParticle = false, clickX = 0, clickY = 0) {
    this.isClickParticle = isClickParticle
    
    if (isClickParticle) {
      this.x = clickX
      this.y = clickY
      // 点击爆发时向随机角度散射
      const angle = Math.random() * Math.PI * 2
      const speed = Math.random() * 2.5 + 1.2
      this.vx = Math.cos(angle) * speed
      this.vy = Math.sin(angle) * speed
      this.radius = Math.random() * 1.5 + 0.8
      this.alpha = 1.0
      this.decay = Math.random() * 0.015 + 0.012
    } else {
      this.x = Math.random() * width
      this.y = Math.random() * height
      this.vx = (Math.random() - 0.5) * 0.4
      this.vy = (Math.random() - 0.5) * 0.4
      this.radius = Math.random() * 1.5 + 0.8
      this.alpha = Math.random() * 0.4 + 0.45
    }
  }

  update(width, height, mouse) {
    if (this.isClickParticle) {
      this.x += this.vx
      this.y += this.vy
      this.alpha -= this.decay
    } else {
      // 鼠标吸引效果
      if (mouse.x !== null && mouse.y !== null) {
        const dx = mouse.x - this.x
        const dy = mouse.y - this.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        const limitDist = 180
        if (dist < limitDist) {
          const force = (limitDist - dist) / limitDist
          this.x += (dx / dist) * force * 0.5
          this.y += (dy / dist) * force * 0.5
        }
      }

      this.x += this.vx
      this.y += this.vy

      // 边缘碰撞反弹
      if (this.x < 0 || this.x > width) this.vx = -this.vx
      if (this.y < 0 || this.y > height) this.vy = -this.vy
    }
  }

  draw(ctx) {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2)
    if (this.isClickParticle) {
      // 蓝色星尘爆破
      ctx.fillStyle = `rgba(59, 130, 246, ${this.alpha})`
    } else {
      // 常驻粒子
      ctx.fillStyle = `rgba(255, 255, 255, ${this.alpha})`
    }
    ctx.fill()
  }
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  
  let width = canvas.width = window.innerWidth
  let height = canvas.height = window.innerHeight

  const particles = []
  const maxNormalParticles = Math.min(80, Math.floor((width * height) / 18000))
  const mouse = { x: null, y: null }

  // 初始化常驻粒子
  for (let i = 0; i < maxNormalParticles; i++) {
    particles.push(new Particle(width, height))
  }

  // 监听窗口大小变化
  const handleResize = () => {
    width = canvas.width = window.innerWidth
    height = canvas.height = window.innerHeight
    
    // 重新校正粒子总数
    const newMax = Math.min(80, Math.floor((width * height) / 18000))
    const currentNormals = particles.filter(p => !p.isClickParticle).length
    if (currentNormals < newMax) {
      const diff = newMax - currentNormals
      for (let i = 0; i < diff; i++) {
        particles.push(new Particle(width, height))
      }
    }
  }

  // 监听鼠标移动
  const handleMouseMove = (e) => {
    // 如果鼠标位于地图区域内部，重置鼠标位置以防吸附和跟随连线
    if (e.target && e.target.closest('.map-board')) {
      mouse.x = null
      mouse.y = null
      return
    }
    mouse.x = e.clientX
    mouse.y = e.clientY
  }

  // 监听鼠标离开
  const handleMouseLeave = () => {
    mouse.x = null
    mouse.y = null
  }

  // 监听鼠标点击爆破
  const handleMouseDown = (e) => {
    // 仅响应鼠标左键点击
    if (e.button !== 0) return
    
    // 如果在地图区域内部点击，不触发爆发粒子
    if (e.target && e.target.closest('.map-board')) {
      return
    }

    const burstCount = 12
    for (let i = 0; i < burstCount; i++) {
      particles.push(new Particle(width, height, true, e.clientX, e.clientY))
    }
  }

  window.addEventListener('resize', handleResize)
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseleave', handleMouseLeave)
  window.addEventListener('mousedown', handleMouseDown)

  // 渲染帧循环
  const render = () => {
    ctx.clearRect(0, 0, width, height)

    // 1. 更新并绘制所有粒子
    for (let i = particles.length - 1; i >= 0; i--) {
      const p = particles[i]
      p.update(width, height, mouse)
      
      // 移除消散完毕的点击粒子
      if (p.isClickParticle && p.alpha <= 0) {
        particles.splice(i, 1)
        continue
      }
      
      p.draw(ctx)
    }

    // 2. 粒子连线逻辑
    const maxLinkDist = 110
    for (let i = 0; i < particles.length; i++) {
      const p1 = particles[i]
      if (p1.isClickParticle) continue // 点击的爆裂粒子不进行连线

      for (let j = i + 1; j < particles.length; j++) {
        const p2 = particles[j]
        if (p2.isClickParticle) continue

        const dx = p1.x - p2.x
        const dy = p1.y - p2.y
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < maxLinkDist) {
          const alpha = (1 - dist / maxLinkDist) * 0.38 // 再次提升连线明度
          ctx.beginPath()
          ctx.moveTo(p1.x, p1.y)
          ctx.lineTo(p2.x, p2.y)
          ctx.strokeStyle = `rgba(240, 249, 255, ${alpha})` // 超高亮蓝白连线
          ctx.lineWidth = 1.3
          ctx.stroke()
        }
      }

      // 3. 粒子与鼠标连线逻辑
      if (mouse.x !== null && mouse.y !== null) {
        const dx = p1.x - mouse.x
        const dy = p1.y - mouse.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        const mouseLinkDist = 150

        if (dist < mouseLinkDist) {
          const alpha = (1 - dist / mouseLinkDist) * 0.48
          ctx.beginPath()
          ctx.moveTo(p1.x, p1.y)
          ctx.lineTo(mouse.x, mouse.y)
          ctx.strokeStyle = `rgba(224, 242, 254, ${alpha})` // 更加醒目的天空蓝白连线
          ctx.lineWidth = 1.5
          ctx.stroke()
        }
      }
    }

    animationFrameId = requestAnimationFrame(render)
  }

  // 启动绘制循环
  render()

  onUnmounted(() => {
    cancelAnimationFrame(animationFrameId)
    window.removeEventListener('resize', handleResize)
    window.removeEventListener('mousemove', handleMouseMove)
    window.removeEventListener('mouseleave', handleMouseLeave)
    window.removeEventListener('mousedown', handleMouseDown)
  })
})
</script>

<style scoped>
.interactive-bg-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 1;
  pointer-events: none;
  background-color: transparent;
  overflow: hidden;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
  background: transparent;
}
</style>
