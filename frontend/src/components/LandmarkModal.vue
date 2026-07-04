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
          <div class="detail-actions">
            <button type="button" class="secondary-btn inline-action" @click="$emit('jump-map')">
              <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M1 6v14l6-4 6 4 6-4 6 4V6l-6-4-6 4-6-4z"></path><line x1="7" y1="2" x2="7" y2="16"></line><line x1="13" y1="8" x2="13" y2="22"></line></svg>
              <span>{{ labels.showMap }}</span>
            </button>
            <a
              v-if="navigationUrl"
              class="secondary-btn inline-action navigation-btn"
              :href="navigationUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="3 11 22 2 13 21 11 13 3 11"></polygon></svg>
              <span>{{ labels.navigate }}</span>
            </a>
            <a
              v-if="markerUrl"
              class="secondary-btn inline-action navigation-btn marker-btn"
              :href="markerUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
              <span>{{ labels.showLocation }}</span>
            </a>
          </div>
          <p v-if="!navigationUrl" class="navigation-unavailable">{{ labels.navigationUnavailable }}</p>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  show: { type: Boolean, required: true },
  landmark: { type: Object, default: null },
  navigationUrl: { type: String, default: null },
  markerUrl: { type: String, default: null },
  labels: { type: Object, required: true }
})

defineEmits(['close', 'jump-map'])
</script>
