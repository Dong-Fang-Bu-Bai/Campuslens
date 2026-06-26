<template>
  <section class="checkin-page">
    <article class="checkin-feed v3-panel" :class="{ 'is-detail': selectedItem }">
      <div class="v3-page-head checkin-board-head">
        <template v-if="selectedItem">
          <button type="button" class="checkin-back-btn" @click="$emit('close-post')">← {{ labels.backToBoard }}</button>
          <div class="checkin-detail-heading">
            <p class="eyebrow">{{ labels.detailKicker }}</p>
            <h3>{{ labels.detailTitle }}</h3>
          </div>
        </template>
        <template v-else>
          <div>
            <p class="eyebrow">{{ labels.kicker }}</p>
            <h3>{{ labels.title }}</h3>
            <p class="checkin-board-subtitle">{{ labels.boardSubtitle }}</p>
          </div>
          <button type="button" class="ghost-btn" @click="$emit('refresh')">{{ labels.refresh }}</button>
        </template>
      </div>

      <div class="checkin-scroll-region">
        <p v-if="interactionError && !selectedItem" class="checkin-interaction-error board-error" role="alert">{{ interactionError }}</p>
        <div v-if="detailLoading" class="v3-loading">{{ labels.loadingDetail }}</div>

        <div v-else-if="selectedItem" class="checkin-thread-page">
          <section class="checkin-card checkin-thread-post">
            <header class="checkin-card-head">
              <button type="button" class="checkin-place" @click="$emit('select-landmark', selectedItem.landmarkId)">
                <span>{{ selectedItem.landmarkCode }}</span>{{ selectedItem.landmarkName }}
              </button>
              <time>{{ formatTime(selectedItem.createdAt) }}</time>
            </header>
            <p class="checkin-message">{{ selectedItem.message }}</p>
            <button
              v-if="selectedItem.sourceImageUrl"
              type="button"
              class="checkin-image-button detail-image"
              :aria-label="labels.enlargeImage"
              @click="openImage(selectedItem)"
            >
              <img class="checkin-public-image" :src="selectedItem.sourceImageUrl" :alt="selectedItem.landmarkName" />
              <span>{{ labels.viewImage }}</span>
            </button>
            <div class="checkin-meta">
              <span class="checkin-author">{{ selectedItem.displayName }}</span>
              <button
                type="button"
                class="engagement-btn like-btn"
                :class="{ active: selectedItem.likedByMe }"
                :disabled="postLikePendingIds.includes(selectedItem.id)"
                @click="$emit('like', selectedItem)"
              >
                <span aria-hidden="true">♥</span>
                {{ postLikePendingIds.includes(selectedItem.id) ? labels.liking : (selectedItem.likedByMe ? labels.liked : labels.like) }}
                <strong>{{ selectedItem.likeCount }}</strong>
              </button>
              <span class="engagement-count"><span aria-hidden="true">●</span>{{ labels.replyCount }} <strong>{{ selectedItem.replyCount }}</strong></span>
            </div>
          </section>

          <section class="thread-replies" aria-live="polite">
            <div class="thread-section-title">
              <h4>{{ labels.discussion }}</h4>
              <span>{{ selectedItem.replyCount }} {{ labels.repliesUnit }}</span>
            </div>
            <div v-if="!flatReplies.length" class="thread-empty">{{ labels.noReplies }}</div>
            <article
              v-for="entry in flatReplies"
              :key="entry.reply.id"
              class="thread-reply"
              :style="{
                '--reply-indent': `${entry.depth * 26}px`,
                '--reply-indent-mobile': `${entry.depth * 12}px`
              }"
            >
              <header>
                <strong>{{ entry.reply.displayName }}</strong>
                <time>{{ formatTime(entry.reply.createdAt) }}</time>
              </header>
              <p>{{ entry.reply.message }}</p>
              <footer>
                <button
                  type="button"
                  class="engagement-btn like-btn"
                  :class="{ active: entry.reply.likedByMe }"
                  :disabled="replyLikePendingIds.includes(entry.reply.id)"
                  @click="$emit('reply-like', entry.reply)"
                >
                  <span aria-hidden="true">♥</span>
                  {{ replyLikePendingIds.includes(entry.reply.id) ? labels.liking : (entry.reply.likedByMe ? labels.liked : labels.like) }}
                  <strong>{{ entry.reply.likeCount }}</strong>
                </button>
                <button type="button" class="engagement-btn reply-btn" @click="selectReplyTarget(entry.reply)">
                  <span aria-hidden="true">↩</span>{{ labels.reply }}
                </button>
                <span class="reply-count-badge"><span aria-hidden="true">●</span>{{ entry.reply.replyCount }}</span>
              </footer>
            </article>
          </section>

          <form class="thread-reply-composer" @submit.prevent="submitDetailReply">
            <p v-if="interactionError" class="checkin-interaction-error" role="alert">{{ interactionError }}</p>
            <div v-if="replyTarget" class="reply-target">
              <span>{{ labels.replyingTo }} @{{ replyTarget.displayName }}</span>
              <button type="button" @click="replyTarget = null">{{ labels.cancelReply }}</button>
            </div>
            <div class="reply-form">
              <input ref="replyInput" v-model="replyMessage" maxlength="500" :placeholder="replyTarget ? labels.nestedReplyPlaceholder : labels.replyPlaceholder" />
              <button type="submit" :disabled="replySubmitting || !replyMessage.trim()">
                {{ replySubmitting ? labels.replySubmitting : labels.reply }}
              </button>
            </div>
          </form>
        </div>

        <div v-else-if="!items.length" class="empty-state compact checkin-empty-feed">
          <h3>{{ labels.empty }}</h3>
          <p>{{ labels.emptyDesc }}</p>
        </div>
        <div v-else class="checkin-list">
          <section
            v-for="item in items"
            :key="item.id"
            class="checkin-card checkin-card-summary"
            role="button"
            tabindex="0"
            @click="$emit('open-post', item)"
            @keydown.enter="$emit('open-post', item)"
          >
            <header class="checkin-card-head">
              <button type="button" class="checkin-place" @click.stop="$emit('select-landmark', item.landmarkId)">
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
              @click.stop="openImage(item)"
            >
              <img class="checkin-public-image" :src="item.sourceImageUrl" :alt="item.landmarkName" />
              <span>{{ labels.viewImage }}</span>
            </button>
            <div class="checkin-meta">
              <span class="checkin-author">{{ item.displayName }}</span>
              <button
                type="button"
                class="engagement-btn like-btn"
                :class="{ active: item.likedByMe }"
                :disabled="postLikePendingIds.includes(item.id)"
                @click.stop="$emit('like', item)"
              >
                <span aria-hidden="true">♥</span>
                {{ postLikePendingIds.includes(item.id) ? labels.liking : (item.likedByMe ? labels.liked : labels.like) }}
                <strong>{{ item.likeCount }}</strong>
              </button>
              <button type="button" class="engagement-btn reply-btn" @click.stop="$emit('open-post', item)">
                <span aria-hidden="true">●</span>{{ labels.replyCount }} <strong>{{ item.replyCount }}</strong>
              </button>
              <span class="open-thread-hint">{{ labels.openDiscussion }} →</span>
            </div>
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

const props = defineProps({
  landmarks: { type: Array, default: () => [] },
  items: { type: Array, default: () => [] },
  selectedItem: { type: Object, default: null },
  detailLoading: { type: Boolean, default: false },
  interactionError: { type: String, default: '' },
  replySubmitting: { type: Boolean, default: false },
  postLikePendingIds: { type: Array, default: () => [] },
  replyLikePendingIds: { type: Array, default: () => [] },
  draft: { type: Object, default: null },
  labels: { type: Object, required: true },
  language: { type: String, default: 'zh' },
  submitting: { type: Boolean, default: false },
  createError: { type: String, default: '' }
})

const emit = defineEmits([
  'create', 'like', 'reply', 'reply-like', 'refresh', 'select-landmark',
  'open-post', 'close-post', 'cancel-draft'
])
const form = reactive({ message: '', publishImage: false })
const lightboxItem = ref(null)
const replyInput = ref(null)
const replyMessage = ref('')
const replyTarget = ref(null)
const selectedLandmark = computed(() => props.landmarks.find(item => item.id === props.draft?.landmarkId))
const flatReplies = computed(() => flattenReplies(props.selectedItem?.replies || []))

watch(() => props.draft?.searchRecordId, () => {
  form.message = ''
  form.publishImage = false
})

watch(() => props.selectedItem?.id, () => {
  replyMessage.value = ''
  replyTarget.value = null
})

watch(() => props.replySubmitting, (submitting, wasSubmitting) => {
  if (wasSubmitting && !submitting && !props.interactionError) {
    replyMessage.value = ''
    replyTarget.value = null
  }
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

function flattenReplies(replies, depth = 0) {
  return replies.flatMap(reply => [
    { reply, depth: Math.min(depth, 3) },
    ...flattenReplies(reply.replies || [], depth + 1)
  ])
}

function selectReplyTarget(reply) {
  replyTarget.value = reply
  nextTick(() => replyInput.value?.focus())
}

function submitDetailReply() {
  const message = replyMessage.value.trim()
  if (!props.selectedItem || !message) return
  emit('reply', props.selectedItem, {
    message,
    parentReplyId: replyTarget.value?.id || null
  })
}

function handleEscape(event) {
  if (event.key !== 'Escape') return
  if (lightboxItem.value) closeImage()
  else if (props.draft) cancelDraft()
  else if (props.selectedItem) emit('close-post')
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
