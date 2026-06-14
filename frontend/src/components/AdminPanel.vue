<template>
  <section class="admin-page">
    <div v-if="!isAdmin" class="empty-state">
      <h3>{{ labels.needAdmin }}</h3>
      <p>{{ labels.needAdminDesc }}</p>
    </div>
    <div v-else class="admin-layout">
      <!-- 反馈统计卡片 -->
      <article v-if="stats" class="admin-card">
        <div class="admin-card-head">
          <div><p class="eyebrow">Stats</p><h3>{{ labels.feedbackStats || '反馈统计' }}</h3></div>
        </div>
        <div class="stats-row">
          <div class="stat-item"><span class="stat-num">{{ stats.totalCount }}</span><span class="stat-label">总反馈</span></div>
          <div class="stat-item"><span class="stat-num">{{ stats.accuracyRate }}%</span><span class="stat-label">准确率</span></div>
          <div class="stat-item"><span class="stat-num">{{ stats.pendingCount }}</span><span class="stat-label">待处理</span></div>
          <div class="stat-item"><span class="stat-num">{{ stats.acceptedCount }}</span><span class="stat-label">已采纳</span></div>
        </div>
        <div class="stats-bars">
          <div class="stats-bar-row"><span class="bar-label">正确</span><span class="bar-fill correct" :style="{ width: stats.correctCount / Math.max(stats.totalCount, 1) * 100 + '%' }"></span><span class="bar-num">{{ stats.correctCount }}</span></div>
          <div class="stats-bar-row"><span class="bar-label">错误</span><span class="bar-fill wrong" :style="{ width: stats.wrongCount / Math.max(stats.totalCount, 1) * 100 + '%' }"></span><span class="bar-num">{{ stats.wrongCount }}</span></div>
          <div class="stats-bar-row"><span class="bar-label">不确定</span><span class="bar-fill uncertain" :style="{ width: stats.uncertainCount / Math.max(stats.totalCount, 1) * 100 + '%' }"></span><span class="bar-num">{{ stats.uncertainCount }}</span></div>
        </div>
      </article>

      <article class="admin-card">
        <div class="admin-card-head">
          <div><p class="eyebrow">SAR Runtime</p><h3>{{ labels.algorithmRuntime }}</h3></div>
          <button type="button" :disabled="['building', 'switching'].includes(rebuildJob?.status)" @click="$emit('rebuild-index')">{{ labels.rebuildIndex }}</button>
        </div>
        <div class="detail-grid admin-detail-grid">
          <span>{{ labels.runtimeState }}</span><strong>{{ runtimeStatus?.status || '-' }}</strong>
          <span>{{ labels.baseModelVersion }}</span><strong>{{ runtimeStatus?.baseModelVersion || '-' }}</strong>
          <span>{{ labels.indexVersion }}</span><strong>{{ runtimeStatus?.indexVersion || '-' }}</strong>
          <span>{{ labels.sarStateVersion }}</span><strong>{{ runtimeStatus?.sarStateVersion || '-' }}</strong>
          <span>{{ labels.updateCount }}</span><strong>{{ runtimeStatus?.updateCount ?? 0 }}</strong>
          <span>{{ labels.lastResetReason }}</span><strong>{{ runtimeStatus?.lastResetReason || '-' }}</strong>
        </div>
        <p v-if="rebuildJob" class="admin-detail-note">Rebuild {{ rebuildJob.rebuildJobId }} · {{ rebuildJob.status }}<template v-if="rebuildJob.error"> · {{ rebuildJob.error }}</template></p>
      </article>
      <article class="admin-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">Search Records</p>
            <h3>{{ labels.searchRecords }}</h3>
          </div>
          <button type="button" @click="$emit('refresh')">{{ labels.refresh }}</button>
        </div>
        <div class="admin-table">
          <div class="admin-row admin-row-head">
            <span>{{ labels.id }}</span>
            <span>{{ labels.bestCandidate }}</span>
            <span>{{ labels.status }}</span>
            <span>{{ labels.visitor }}</span>
          </div>
          <div v-for="record in searchRecords" :key="record.id" class="admin-row">
            <span>#{{ record.id }}</span>
            <span>{{ record.bestLandmarkName || labels.noCandidate }}<small>{{ scoreLabel(record.bestScore) }}</small></span>
            <span :class="['status-badge', record.status]">{{ recordStatusLabel(record.status) }}</span>
            <span>{{ record.username || record.guestId }}</span>
          </div>
        </div>
      </article>

      <article class="admin-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">Feedback</p>
            <h3>{{ labels.feedbackProcess }}</h3>
          </div>
        </div>
        <div class="feedback-admin-list">
          <div v-for="item in feedbackRecords" :key="item.id" class="feedback-admin-item" :class="{ selected: selectedFeedbackDetail?.id === item.id }">
            <div>
              <strong>#{{ item.id }} {{ feedbackTypeLabel(item.feedbackType) }}</strong>
              <p>{{ labels.searchRecord }} #{{ item.searchRecordId }} · {{ labels.visitor }}：{{ item.username || 'guest' }} · {{ labels.predicted }}：{{ item.predictedLandmarkName || '-' }} · {{ labels.confirmed }}：{{ item.confirmedLandmarkName || '-' }}</p>
              <small>{{ item.comment || labels.noComment }}</small>
            </div>
            <div class="admin-actions">
              <span :class="['status-badge', item.status]">{{ feedbackStatusLabel(item.status) }}</span>
              <button type="button" @click="$emit('view-detail', item.id)">{{ labels.detail }}</button>
              <button type="button" @click="$emit('update-status', item.id, 'accepted')">{{ labels.accept }}</button>
              <button type="button" @click="$emit('update-status', item.id, 'ignored')">{{ labels.ignore }}</button>
            </div>
          </div>
        </div>
      </article>

      <article class="admin-card feedback-detail-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">Feedback Detail</p>
            <h3>{{ labels.feedbackDetail }}</h3>
          </div>
        </div>
        <div v-if="!selectedFeedbackDetail" class="empty-state compact">
          <h3>{{ labels.selectFeedback }}</h3>
          <p>{{ labels.selectFeedbackDesc }}</p>
        </div>
        <div v-else class="feedback-detail-body">
          <div class="feedback-image" :style="{ backgroundImage: `url(${selectedFeedbackDetail.uploadImageUrl || ''})` }"></div>
          <div class="detail-grid admin-detail-grid">
            <span>{{ labels.searchRecord }}</span><strong>#{{ selectedFeedbackDetail.searchRecordId }}</strong>
            <span>{{ labels.status }}</span><strong>{{ feedbackStatusLabel(selectedFeedbackDetail.status) }}</strong>
            <span>{{ labels.syncStatus }}</span><strong>{{ syncStatusLabel(selectedFeedbackDetail.correctionSample?.syncStatus) }}</strong>
            <span>{{ labels.advice }}</span><strong>{{ adviceLabel(selectedFeedbackDetail.correctionSample) }}</strong>
          </div>
          <div class="top-snapshot-list">
            <button v-for="item in selectedFeedbackDetail.topResults" :key="item.landmarkId" type="button">
              <span>#{{ item.rank }}</span>
              <strong>{{ item.landmarkCode }} {{ item.name }}</strong>
              <small>{{ Math.round(Number(item.score) * 100) }}%</small>
            </button>
          </div>
          <p class="admin-detail-note">{{ selectedFeedbackDetail.correctionSample?.reason || selectedFeedbackDetail.comment || labels.noCommentAdmin }}</p>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
const props = defineProps({
  isAdmin: { type: Boolean, required: true },
  searchRecords: { type: Array, default: () => [] },
  feedbackRecords: { type: Array, default: () => [] },
  selectedFeedbackDetail: { type: Object, default: null },
  runtimeStatus: { type: Object, default: null },
  rebuildJob: { type: Object, default: null },
  stats: { type: Object, default: null },
  labels: { type: Object, required: true }
})

defineEmits(['refresh', 'update-status', 'view-detail', 'rebuild-index'])

function recordStatusLabel(status) {
  return {
    success: props.labels.statusSuccess,
    low_confidence: props.labels.statusLowConfidence,
    empty_result: props.labels.statusEmptyResult,
    algorithm_unavailable: props.labels.statusAlgorithmUnavailable
  }[status] || status
}

function feedbackTypeLabel(value) {
  return {
    correct: props.labels.typeCorrect,
    wrong: props.labels.typeWrong,
    uncertain: props.labels.typeUncertain
  }[value] || value
}

function feedbackStatusLabel(value) {
  return {
    pending: props.labels.feedbackPending,
    accepted: props.labels.feedbackAccepted,
    ignored: props.labels.feedbackIgnored
  }[value] || value
}

function scoreLabel(value) {
  return value == null ? '' : `${props.labels.matchScore} ${Math.round(Number(value) * 100)}%`
}

function syncStatusLabel(value) {
  return {
    sync_pending: props.labels.syncPending,
    synced: props.labels.synced,
    sync_failed: props.labels.syncFailed,
    pending_index: props.labels.syncPending,
    published: props.labels.synced
  }[value] || props.labels.syncNone
}

function adviceLabel(sample) {
  if (!sample) return props.labels.advicePending
  if (sample.reviewScore == null) return props.labels.adviceWait
  return `${sample.suggestAccept ? props.labels.adviceAccept : props.labels.adviceReview} · ${Math.round(Number(sample.reviewScore) * 100)}%`
}
</script>

<style scoped>
.stats-row {
  display: flex; gap: 16px; margin-bottom: 20px;
}
.stat-item {
  flex: 1; text-align: center; background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06); border-radius: 8px; padding: 14px 10px;
}
.stat-num { display: block; font-size: 22px; font-weight: 700; color: #3b82f6; }
.stat-label { display: block; font-size: 11px; color: #94a3b8; margin-top: 4px; }
.stats-bars { display: flex; flex-direction: column; gap: 8px; }
.stats-bar-row { display: flex; align-items: center; gap: 10px; }
.bar-label { width: 40px; font-size: 11px; color: #94a3b8; text-align: right; }
.bar-num { width: 30px; font-size: 11px; color: #94a3b8; }
.bar-fill { height: 8px; border-radius: 4px; min-width: 2px; transition: width 0.3s; }
.bar-fill.correct { background: #10b981; }
.bar-fill.wrong { background: #ef4444; }
.bar-fill.uncertain { background: #f59e0b; }
</style>
