<template>
  <Transition name="modal-fade">
    <div v-if="show && landmark" class="modal-overlay" @click.self="$emit('close')">
      <div class="modal-content">
        <button class="modal-close" @click="$emit('close')" title="关闭 (Esc)">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>

        <div class="modal-hero">
          <img :src="landmark.imageUrl" :alt="landmark.name" />
          <div class="modal-hero-overlay">
            <span class="modal-code">{{ landmark.code }}</span>
            <h2>{{ landmark.name }}</h2>
            <p>{{ landmark.englishName }} · {{ landmark.locationText }}</p>
          </div>
        </div>

        <div class="modal-body">
          <div class="modal-section">
            <h4>地标实景详细介绍</h4>
            <p>{{ landmark.description }}</p>
          </div>

          <div class="modal-section">
            <h4>深度解析指标</h4>
            <div class="modal-grid">
              <div class="grid-item">
                <span>地标类型</span>
                <strong>{{ landmark.type }}</strong>
              </div>
              <div class="grid-item">
                <span>地图百分比坐标</span>
                <strong>X: {{ landmark.mapX }}%, Y: {{ landmark.mapY }}%</strong>
              </div>
              <div class="grid-item">
                <span>推荐打卡指数</span>
                <strong style="color: #fbbf24;">★★★★★</strong>
              </div>
              <div class="grid-item">
                <span>验证状态</span>
                <strong style="color: #10b981;">已校验并收录</strong>
              </div>
            </div>
          </div>
          <button type="button" class="secondary-btn inline-action" @click="$emit('jump-map')">在地图中查看</button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  show: { type: Boolean, required: true },
  landmark: { type: Object, default: null }
})

defineEmits(['close', 'jump-map'])
</script>
