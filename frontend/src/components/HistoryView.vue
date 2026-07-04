<template>
  <section class="history-page v3-panel">
    <div class="v3-page-head">
      <div>
        <p class="eyebrow">{{ labels.kicker }}</p>
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
        <div class="mini-rank-strip history-candidate-strip">
          <div
            v-for="item in record.topResults"
            :key="`${record.id}-${item.landmarkId}`"
            class="history-candidate"
          >
            <strong><span>#{{ item.rank }}</span>{{ item.name }}</strong>
            <div>
              <button type="button" @click="$emit('open-feedback', record, item)">{{ labels.feedback }}</button>
              <button type="button" @click="$emit('open-check-in', record, item)">{{ labels.checkIn }}</button>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
const props = defineProps({
  currentUser: { type: Object, default: null },
  records: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  labels: { type: Object, required: true },
  fallbackImage: { type: String, required: true },
  language: { type: String, default: 'zh' }
})

defineEmits(['refresh', 'open-feedback', 'open-check-in'])

function recordStatusLabel(status) {
  const isEn = props.language !== 'zh'
  return {
    success: isEn ? 'Success' : '成功',
    low_confidence: isEn ? 'Review Suggested' : '建议确认',
    empty_result: isEn ? 'No Result' : '未找到结果',
    algorithm_unavailable: isEn ? 'Service Unavailable' : '识别服务暂不可用'
  }[status] || status || '-'
}

function feedbackStatusLabel(status) {
  const isEn = props.language !== 'zh'
  return {
    pending: isEn ? 'Pending' : '反馈待处理',
    accepted: isEn ? 'Accepted' : '反馈已采纳',
    ignored: isEn ? 'Ignored' : '反馈已忽略'
  }[status] || (isEn ? 'No Feedback Submitted' : '未提交反馈')
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString(props.language === 'zh' ? 'zh-CN' : 'en-US', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false
  })
}
</script>
