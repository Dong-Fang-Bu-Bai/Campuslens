<template>
  <Transition name="modal-fade">
    <div v-if="show && landmark" class="modal-overlay" @click.self="$emit('close')">
      <div class="modal-content">
        <button class="modal-close" @click="$emit('close')" :title="labels.closeTitle">
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
            <h4>{{ labels.introTitle }}</h4>
            <p>{{ landmark.description }}</p>
          </div>

          <div class="modal-section">
            <h4>{{ labels.gridTitle }}</h4>
            <div class="modal-grid">
              <div class="grid-item">
                <span>{{ labels.typeLabel }}</span>
                <strong>{{ landmark.type }}</strong>
              </div>
              <div class="grid-item">
                <span>{{ labels.mapCoords }}</span>
                <strong>{{ landmark.mapX }}% · {{ landmark.mapY }}%</strong>
              </div>
              <div class="grid-item">
                <span>{{ labels.verifyStatus }}</span>
                <strong style="color: #10b981;">{{ labels.verifiedText }}</strong>
              </div>
            </div>
          </div>
          <button type="button" class="secondary-btn inline-action" @click="$emit('jump-map')">{{ labels.showMap }}</button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  show: { type: Boolean, required: true },
  landmark: { type: Object, default: null },
  labels: { type: Object, required: true }
})

defineEmits(['close', 'jump-map'])
</script>
