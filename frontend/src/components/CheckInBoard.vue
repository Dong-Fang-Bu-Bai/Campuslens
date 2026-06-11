<template>
  <section class="checkin-page">
    <article class="checkin-compose v3-panel">
      <div class="v3-page-head">
        <div>
          <p class="eyebrow">Check-in Board</p>
          <h3>{{ labels.title }}</h3>
        </div>
        <button type="button" class="ghost-btn" @click="$emit('refresh')">{{ labels.refresh }}</button>
      </div>
      <form class="checkin-form" @submit.prevent="submit">
        <label>
          {{ labels.landmark }}
          <select v-model.number="form.landmarkId">
            <option v-for="item in landmarks" :key="item.id" :value="item.id">{{ item.code }} {{ item.name }}</option>
          </select>
        </label>
        <label>
          {{ labels.message }}
          <textarea v-model="form.message" rows="4" maxlength="500" :placeholder="labels.placeholder"></textarea>
        </label>
        <button class="primary-btn" type="submit">
          <span class="btn-text">{{ labels.submit }}</span>
        </button>
      </form>
    </article>

    <article class="checkin-feed v3-panel">
      <div v-if="!items.length" class="empty-state compact">
        <h3>{{ labels.empty }}</h3>
        <p>{{ labels.emptyDesc }}</p>
      </div>
      <div v-else class="checkin-list">
        <section v-for="item in items" :key="item.id" class="checkin-card">
          <div class="checkin-card-head">
            <button type="button" class="checkin-place" @click="$emit('select-landmark', item.landmarkId)">
              <span>{{ item.landmarkCode }}</span>{{ item.landmarkName }}
            </button>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
          <p>{{ item.message }}</p>
          <div class="checkin-meta">
            <span>{{ item.displayName }}</span>
            <button type="button" :class="{ active: item.likedByMe }" @click="$emit('like', item)">
              {{ item.likedByMe ? labels.liked : labels.like }} · {{ item.likeCount }}
            </button>
            <span>{{ labels.replyCount }} {{ item.replyCount }}</span>
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
    </article>
  </section>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  landmarks: { type: Array, default: () => [] },
  items: { type: Array, default: () => [] },
  labels: { type: Object, required: true }
})

const emit = defineEmits(['create', 'like', 'reply', 'refresh', 'select-landmark'])

const form = reactive({
  landmarkId: props.landmarks[0]?.id || 1,
  message: ''
})
const replyDrafts = reactive({})

watch(() => props.landmarks, (next) => {
  if (!form.landmarkId && next.length) {
    form.landmarkId = next[0].id
  }
}, { immediate: true })

function submit() {
  if (!form.message.trim()) return
  emit('create', { landmarkId: form.landmarkId, message: form.message.trim() })
  form.message = ''
}

function submitReply(item) {
  const message = (replyDrafts[item.id] || '').trim()
  if (!message) return
  emit('reply', item, message)
  replyDrafts[item.id] = ''
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>
