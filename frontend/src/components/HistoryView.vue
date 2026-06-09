<template>
  <section class="history-page v3-panel">
    <div class="v3-page-head">
      <div>
        <p class="eyebrow">My Records</p>
        <h3>{{ labels.title }}</h3>
      </div>
      <button type="button" class="ghost-btn" @click="$emit('refresh')">{{ labels.refresh }}</button>
    </div>

    <div v-if="!currentUser" class="empty-state compact">
      <h3>{{ labels.needLogin }}</h3>
      <p>{{ labels.needLoginDesc }}</p>
    </div>

    <div v-else-if="loading" class="v3-loading">{{ labels.loading }}</div>

    <div v-else-if="!records.length" class="empty-state compact">
      <h3>{{ labels.empty }}</h3>
      <p>{{ labels.emptyDesc }}</p>
    </div>

    <div v-else class="timeline-list">
      <article v-for="record in records" :key="record.id" class="timeline-card">
        <div class="timeline-main">
          <div class="timeline-thumb" :style="{ backgroundImage: `url(${record.uploadImageUrl || fallbackImage})` }"></div>
          <div>
            <div class="timeline-title-row">
              <strong>#{{ record.id }} {{ record.bestLandmarkName || labels.noCandidate }}</strong>
              <span :class="['status-badge', record.status]">{{ recordStatusLabel(record.status) }}</span>
            </div>
            <p>{{ record.message || labels.noMessage }}</p>
            <div class="history-chip-row">
              <span>{{ formatTime(record.createdAt) }}</span>
              <span v-if="record.bestScore != null">{{ labels.score }} {{ Math.round(Number(record.bestScore) * 100) }}%</span>
              <span>{{ feedbackStatusLabel(record.feedbackStatus) }}</span>
            </div>
          </div>
        </div>
        <div class="mini-rank-strip">
          <button
            v-for="item in record.topResults"
            :key="`${record.id}-${item.landmarkId}`"
            type="button"
            @click="$emit('open-feedback', record, item)"
          >
            <span>#{{ item.rank }}</span>
            {{ item.name }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({
  currentUser: { type: Object, default: null },
  records: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  labels: { type: Object, required: true },
  fallbackImage: { type: String, required: true }
})

defineEmits(['refresh', 'open-feedback'])

function recordStatusLabel(status) {
  return {
    success: '成功',
    low_confidence: '低匹配',
    empty_result: '空结果',
    algorithm_unavailable: '算法不可用'
  }[status] || status || '-'
}

function feedbackStatusLabel(status) {
  return {
    pending: '反馈待处理',
    accepted: '反馈已采纳',
    ignored: '反馈已忽略'
  }[status] || '未反馈'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>
