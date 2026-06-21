<template>
  <section class="admin-page">
    <div v-if="!isAdmin" class="empty-state">
      <h3>{{ labels.needAdmin }}</h3>
      <p>{{ labels.needAdminDesc }}</p>
    </div>
    <div v-else class="admin-layout">
      <article class="admin-card">
        <div class="admin-card-head">
          <div><p class="eyebrow">{{ labels.runtimeKicker }}</p><h3>{{ labels.algorithmRuntime }}</h3></div>
          <button type="button" :disabled="['building', 'switching'].includes(rebuildJob?.status)" @click="$emit('rebuild-index')">{{ labels.rebuildIndex }}</button>
        </div>
        <div class="detail-grid admin-detail-grid">
          <span>{{ labels.runtimeState }}</span><strong>{{ runtimeStatusLabel(runtimeStatus?.status) }}</strong>
          <span>{{ labels.baseModelVersion }}</span><strong>{{ runtimeStatus?.baseModelVersion || '-' }}</strong>
          <span>{{ labels.indexVersion }}</span><strong>{{ runtimeStatus?.indexVersion || '-' }}</strong>
          <span>{{ labels.sarStateVersion }}</span><strong>{{ runtimeStatus?.sarStateVersion || '-' }}</strong>
          <span>{{ labels.updateCount }}</span><strong>{{ runtimeStatus?.updateCount ?? 0 }}</strong>
          <span>{{ labels.lastResetReason }}</span><strong>{{ runtimeStatus?.lastResetReason || '-' }}</strong>
        </div>
        <p v-if="rebuildJob" class="admin-detail-note">{{ labels.rebuildTask }} #{{ rebuildJob.rebuildJobId }} · {{ rebuildStatusLabel(rebuildJob.status) }}<template v-if="rebuildJob.error"> · {{ rebuildJob.error }}</template></p>
      </article>
      <article class="admin-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">{{ labels.recordsKicker }}</p>
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
            <span>{{ record.username || record.guestId || labels.anonymousVisitor }}</span>
          </div>
        </div>
      </article>

      <article class="admin-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">{{ labels.feedbackKicker }}</p>
            <h3>{{ labels.feedbackProcess }}</h3>
          </div>
        </div>
        <div class="feedback-admin-list">
          <div v-for="item in feedbackRecords" :key="item.id" class="feedback-admin-item" :class="{ selected: selectedFeedbackDetail?.id === item.id }">
            <div>
              <strong>#{{ item.id }} {{ feedbackTypeLabel(item.feedbackType) }}</strong>
              <p>{{ labels.searchRecord }} #{{ item.searchRecordId }} · {{ labels.visitor }}：{{ item.username || labels.anonymousVisitor }} · {{ labels.predicted }}：{{ item.predictedLandmarkName || '-' }} · {{ labels.confirmed }}：{{ item.confirmedLandmarkName || '-' }}</p>
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
            <p class="eyebrow">{{ labels.detailKicker }}</p>
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

function runtimeStatusLabel(status) {
  return {
    ok: props.labels.runtimeHealthy,
    healthy: props.labels.runtimeHealthy,
    ready: props.labels.runtimeHealthy,
    running: props.labels.runtimeHealthy,
    degraded: props.labels.runtimeDegraded,
    unavailable: props.labels.runtimeUnavailable,
    offline: props.labels.runtimeUnavailable
  }[status] || status || '-'
}

function rebuildStatusLabel(status) {
  return {
    queued: props.labels.rebuildQueued,
    building: props.labels.rebuildBuilding,
    validating: props.labels.rebuildValidating,
    switching: props.labels.rebuildPublishing,
    published: props.labels.rebuildComplete,
    completed: props.labels.rebuildComplete,
    success: props.labels.rebuildComplete,
    failed: props.labels.rebuildFailed
  }[status] || status || '-'
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
