<template>
  <div class="crop-overlay" role="dialog" aria-modal="true" :aria-label="labels.title" @click.self="$emit('cancel')">
    <section class="crop-dialog">
      <header>
        <div><p class="eyebrow">{{ labels.kicker }}</p><h3>{{ labels.title }}</h3></div>
        <button type="button" class="crop-close" :aria-label="labels.cancel" @click="$emit('cancel')">×</button>
      </header>
      <p class="crop-hint">{{ labels.hint }}</p>
      <div
        ref="stage"
        class="crop-stage"
        @pointerdown="startDrag"
        @pointermove="drag"
        @pointerup="endDrag"
        @pointercancel="endDrag"
      >
        <img
          ref="image"
          :src="sourceUrl"
          alt=""
          draggable="false"
          :style="imageStyle"
          @load="onImageLoad"
        />
        <div class="crop-guide"></div>
      </div>
      <label class="zoom-control">
        <span>{{ labels.zoom }}</span>
        <input v-model.number="zoom" type="range" min="1" max="3" step="0.01" @input="clampOffsets" />
      </label>
      <p v-if="message" :class="error ? 'crop-message error' : 'crop-message success'">{{ message }}</p>
      <footer>
        <button type="button" class="crop-secondary" :disabled="submitting" @click="$emit('cancel')">{{ labels.cancel }}</button>
        <button type="button" class="crop-primary" :disabled="submitting || !imageReady" @click="save">{{ submitting ? labels.saving : labels.save }}</button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  file: { type: File, required: true },
  labels: { type: Object, required: true },
  submitting: { type: Boolean, default: false },
  message: { type: String, default: '' },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['cancel', 'save'])
const stage = ref(null)
const image = ref(null)
const sourceUrl = ref('')
const naturalWidth = ref(1)
const naturalHeight = ref(1)
const stageSize = ref(300)
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const imageReady = ref(false)
let dragging = false
let lastX = 0
let lastY = 0
let resizeObserver = null

const baseScale = computed(() => Math.max(stageSize.value / naturalWidth.value, stageSize.value / naturalHeight.value))
const scale = computed(() => baseScale.value * zoom.value)
const imageStyle = computed(() => ({
  width: `${naturalWidth.value * scale.value}px`,
  height: `${naturalHeight.value * scale.value}px`,
  transform: `translate(-50%, -50%) translate(${offsetX.value}px, ${offsetY.value}px)`
}))

watch(() => props.file, loadFile, { immediate: true })

async function loadFile(file) {
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value)
  imageReady.value = false
  zoom.value = 1
  offsetX.value = 0
  offsetY.value = 0
  sourceUrl.value = URL.createObjectURL(file)
  await nextTick()
  observeStage()
}

function observeStage() {
  resizeObserver?.disconnect()
  if (!stage.value) return
  const update = () => {
    stageSize.value = stage.value.clientWidth || 300
    clampOffsets()
  }
  update()
  resizeObserver = new ResizeObserver(update)
  resizeObserver.observe(stage.value)
}

function onImageLoad() {
  naturalWidth.value = image.value.naturalWidth
  naturalHeight.value = image.value.naturalHeight
  imageReady.value = true
  clampOffsets()
}

function limits() {
  return {
    x: Math.max(0, (naturalWidth.value * scale.value - stageSize.value) / 2),
    y: Math.max(0, (naturalHeight.value * scale.value - stageSize.value) / 2)
  }
}

function clampOffsets() {
  const limit = limits()
  offsetX.value = Math.max(-limit.x, Math.min(limit.x, offsetX.value))
  offsetY.value = Math.max(-limit.y, Math.min(limit.y, offsetY.value))
}

function startDrag(event) {
  if (!imageReady.value) return
  dragging = true
  lastX = event.clientX
  lastY = event.clientY
  stage.value.setPointerCapture(event.pointerId)
}

function drag(event) {
  if (!dragging) return
  offsetX.value += event.clientX - lastX
  offsetY.value += event.clientY - lastY
  lastX = event.clientX
  lastY = event.clientY
  clampOffsets()
}

function endDrag(event) {
  dragging = false
  if (stage.value?.hasPointerCapture(event.pointerId)) stage.value.releasePointerCapture(event.pointerId)
}

function save() {
  const outputSize = 512
  const sourceSize = stageSize.value / scale.value
  const sourceX = (naturalWidth.value - sourceSize) / 2 - offsetX.value / scale.value
  const sourceY = (naturalHeight.value - sourceSize) / 2 - offsetY.value / scale.value
  const canvas = document.createElement('canvas')
  canvas.width = outputSize
  canvas.height = outputSize
  const context = canvas.getContext('2d')
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(image.value, sourceX, sourceY, sourceSize, sourceSize, 0, 0, outputSize, outputSize)
  const mime = props.file.type === 'image/png' ? 'image/png' : 'image/jpeg'
  canvas.toBlob(blob => {
    if (!blob) return
    const extension = mime === 'image/png' ? 'png' : 'jpg'
    emit('save', new File([blob], `avatar.${extension}`, { type: mime }))
  }, mime, .92)
}

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (sourceUrl.value) URL.revokeObjectURL(sourceUrl.value)
})
</script>

<style scoped>
.crop-overlay { position: fixed; inset: 0; z-index: 500; display: grid; place-items: center; padding: 22px; background: rgba(2,6,23,.76); backdrop-filter: blur(12px); }
.crop-dialog { width: min(480px, 100%); padding: 24px; border: 1px solid var(--border-light); border-radius: 22px; background: color-mix(in srgb, var(--bg-card) 97%, transparent); box-shadow: 0 28px 80px rgba(0,0,0,.5); }
.crop-dialog header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.crop-dialog h3 { margin: 3px 0 0; color: var(--text-primary); font-size: 22px; }
.crop-close { width: 38px; height: 38px; border: 1px solid var(--border-light); border-radius: 50%; background: rgba(255,255,255,.04); color: var(--text-primary); font-size: 24px; cursor: pointer; }
.crop-hint { margin: 14px 0 18px; color: var(--text-secondary); font-size: 12.5px; line-height: 1.6; }
.crop-stage { position: relative; width: min(300px, calc(100vw - 92px)); aspect-ratio: 1; margin: 0 auto; overflow: hidden; border: 3px solid rgba(var(--bg-particle-click-rgb), .58); border-radius: 50%; background: repeating-conic-gradient(rgba(148,163,184,.12) 0 25%, rgba(15,23,42,.2) 0 50%) 50% / 20px 20px; cursor: grab; touch-action: none; box-shadow: 0 0 0 10px rgba(var(--bg-particle-click-rgb), .07); }
.crop-stage:active { cursor: grabbing; }
.crop-stage img { position: absolute; left: 50%; top: 50%; max-width: none; user-select: none; pointer-events: none; }
.crop-guide { position: absolute; inset: 12%; border: 1px dashed rgba(255,255,255,.48); border-radius: 50%; pointer-events: none; }
.zoom-control { display: grid; grid-template-columns: auto minmax(0,1fr); align-items: center; gap: 14px; margin-top: 24px; color: var(--text-secondary); font-size: 12px; font-weight: 800; }
.zoom-control input { accent-color: var(--color-accent); }
.crop-message { margin: 12px 0 0; font-size: 12px; }
.crop-message.error { color: #fda4af; }
.crop-message.success { color: #34d399; }
.crop-dialog footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.crop-dialog footer button { min-height: 42px; padding: 0 18px; border-radius: 11px; font-weight: 900; cursor: pointer; }
.crop-secondary { border: 1px solid var(--border-light); background: rgba(255,255,255,.04); color: var(--text-primary); }
.crop-primary { border: 0; background: var(--color-accent); color: #fff; }
.crop-dialog footer button:disabled { cursor: wait; opacity: .6; }
@media (max-width: 520px) { .crop-overlay { align-items: end; padding: 0; } .crop-dialog { padding: 22px 18px max(22px, env(safe-area-inset-bottom)); border-radius: 22px 22px 0 0; } .crop-dialog footer button { flex: 1; } }
</style>
