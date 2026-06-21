<template>
  <section class="checkin-page">
    <article class="checkin-feed v3-panel">
      <div class="v3-page-head checkin-board-head">
        <div>
          <p class="eyebrow">{{ labels.kicker }}</p>
          <h3>{{ labels.title }}</h3>
          <p class="checkin-board-subtitle">{{ labels.boardSubtitle }}</p>
        </div>
        <button type="button" class="ghost-btn" @click="$emit('refresh')">{{ labels.refresh }}</button>
      </div>

      <div class="checkin-scroll-region">
        <div v-if="!items.length" class="empty-state compact checkin-empty-feed">
          <h3>{{ labels.empty }}</h3>
          <p>{{ labels.emptyDesc }}</p>
        </div>
        <div v-else class="checkin-list">
          <section v-for="item in items" :key="item.id" class="checkin-card">
            <header class="checkin-card-head">
              <button type="button" class="checkin-place" @click="$emit('select-landmark', item.landmarkId)">
                <span>{{ item.landmarkCode }}</span>{{ item.landmarkName }}
              </button>
              <time>{{ formatTime(item.createdAt) }}</time>
            </header>

            <p class="checkin-message">{{ item.message }}</p>

            <button
              v-if="item.sourceImageUrl"
              type="button"
              class="checkin-image-button"
              :aria-label="labels.enlargeImage"
              @click="openImage(item)"
            >
              <img class="checkin-public-image" :src="item.sourceImageUrl" :alt="item.landmarkName" />
              <span>{{ labels.viewImage }}</span>
            </button>

            <div class="checkin-meta">
              <span class="checkin-author">{{ item.displayName }}</span>
              <button type="button" :class="{ active: item.likedByMe }" @click="$emit('like', item)">
                {{ item.likedByMe ? labels.liked : labels.like }} · {{ item.likeCount }}
              </button>
              <span>{{ labels.replyCount }} · {{ item.replyCount }}</span>
            </div>

            <div class="reply-list" v-if="item.replies?.length">
              <div v-for="reply in item.replies" :key="reply.id">
                <strong>{{ reply.displayName }}</strong>
                <span>{{ reply.message }}</span>
              </div>
            </div>
            <form class="reply-form" @submit.prevent="submitReply(item)">
              <input v-model="replyDrafts[item.id]" maxlength="500" :placeholder="labels.replyPlaceholder" />
              <button type="submit">{{ labels.reply }}</button>
            </form>
          </section>
        </div>
      </div>
    </article>

    <Transition name="modal-fade">
      <div v-if="draft" class="modal-overlay checkin-compose-overlay" @click.self="cancelDraft">
        <form class="checkin-compose-modal" @submit.prevent="submit">
          <button type="button" class="modal-close" :title="labels.close" @click="cancelDraft">×</button>
          <div class="checkin-compose-head">
            <p class="eyebrow">{{ labels.composeKicker }}</p>
            <h3>{{ labels.composeTitle }}</h3>
            <p>{{ labels.sourceRecord }} #{{ draft.searchRecordId }}</p>
          </div>

          <div class="checkin-compose-grid">
            <div v-if="draft.sourceImageUrl" class="checkin-source-preview" :style="{ backgroundImage: `url(${draft.sourceImageUrl})` }"></div>
            <div class="checkin-form">
              <label>
                {{ labels.landmark }}
                <input :value="selectedLandmark ? `${selectedLandmark.code} ${selectedLandmark.name}` : draft.landmarkId" disabled />
              </label>
              <label>
                {{ labels.message }}
                <textarea v-model="form.message" rows="5" maxlength="500" :placeholder="labels.placeholder" autofocus></textarea>
              </label>
              <label class="checkin-public-toggle">
                <input v-model="form.publishImage" type="checkbox" />
                <span>{{ labels.publicPhoto }}</span>
              </label>
              <p v-if="createError" class="checkin-compose-error">{{ createError }}</p>
              <div class="checkin-compose-actions">
                <button type="button" class="ghost-btn" @click="cancelDraft">{{ labels.cancel }}</button>
                <button class="primary-btn" type="submit" :disabled="submitting || !form.message.trim()">
                  <span class="btn-text">{{ submitting ? labels.submitting : labels.submit }}</span>
                </button>
              </div>
            </div>
          </div>
        </form>
      </div>
    </Transition>

    <Transition name="modal-fade">
      <div v-if="lightboxItem" class="modal-overlay checkin-lightbox" @click.self="closeImage">
        <button type="button" class="modal-close" :title="labels.close" @click="closeImage">×</button>
        <figure>
          <img :src="lightboxItem.sourceImageUrl" :alt="lightboxItem.landmarkName" />
          <figcaption>
            <strong>{{ lightboxItem.landmarkCode }} {{ lightboxItem.landmarkName }}</strong>
            <span>{{ lightboxItem.displayName }} · {{ formatTime(lightboxItem.createdAt) }}</span>
          </figcaption>
        </figure>
      </div>
    </Transition>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

const props = defineProps({
  landmarks: { type: Array, default: () => [] },
  items: { type: Array, default: () => [] },
  draft: { type: Object, default: null },
  labels: { type: Object, required: true },
  language: { type: String, default: 'zh' },
  submitting: { type: Boolean, default: false },
  createError: { type: String, default: '' }
})

const emit = defineEmits(['create', 'like', 'reply', 'refresh', 'select-landmark', 'cancel-draft'])
const form = reactive({ message: '', publishImage: false })
const replyDrafts = reactive({})
const lightboxItem = ref(null)
const selectedLandmark = computed(() => props.landmarks.find(item => item.id === props.draft?.landmarkId))

watch(() => props.draft?.searchRecordId, () => {
  form.message = ''
  form.publishImage = false
})

function submit() {
  if (!props.draft || !form.message.trim() || props.submitting) return
  emit('create', {
    searchRecordId: props.draft.searchRecordId,
    landmarkId: props.draft.landmarkId,
    message: form.message.trim(),
    publishImage: form.publishImage
  })
}

function cancelDraft() {
  if (!props.submitting) emit('cancel-draft')
}

function openImage(item) {
  lightboxItem.value = item
}

function closeImage() {
  lightboxItem.value = null
}

function submitReply(item) {
  const message = (replyDrafts[item.id] || '').trim()
  if (!message) return
  emit('reply', item, message)
  replyDrafts[item.id] = ''
}

function handleEscape(event) {
  if (event.key !== 'Escape') return
  if (lightboxItem.value) closeImage()
  else if (props.draft) cancelDraft()
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString(props.language === 'zh' ? 'zh-CN' : 'en-US', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false
  })
}

onMounted(() => window.addEventListener('keydown', handleEscape))
onBeforeUnmount(() => window.removeEventListener('keydown', handleEscape))
</script>
