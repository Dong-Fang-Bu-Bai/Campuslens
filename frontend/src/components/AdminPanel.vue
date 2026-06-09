<template>
  <section class="admin-page">
    <div v-if="!isAdmin" class="empty-state">
      <h3>需要管理员登录</h3>
      <p>请通过右上角“登录/注册”进入登录页，管理员账号登录成功后会自动进入后台。</p>
    </div>
    <div v-else class="admin-layout">
      <article class="admin-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">Search Records</p>
            <h3>检索记录</h3>
          </div>
          <button type="button" @click="$emit('refresh')">刷新</button>
        </div>
        <div class="admin-table">
          <div class="admin-row admin-row-head">
            <span>ID</span>
            <span>最高候选</span>
            <span>状态</span>
            <span>游客</span>
          </div>
          <div v-for="record in searchRecords" :key="record.id" class="admin-row">
            <span>#{{ record.id }}</span>
            <span>{{ record.bestLandmarkName || '无候选' }}<small>{{ scoreLabel(record.bestScore) }}</small></span>
            <span :class="['status-badge', record.status]">{{ recordStatusLabel(record.status) }}</span>
            <span>{{ record.username || record.guestId }}</span>
          </div>
        </div>
      </article>

      <article class="admin-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">Feedback</p>
            <h3>反馈处理</h3>
          </div>
        </div>
        <div class="feedback-admin-list">
          <div v-for="item in feedbackRecords" :key="item.id" class="feedback-admin-item" :class="{ selected: selectedFeedbackDetail?.id === item.id }">
            <div>
              <strong>#{{ item.id }} {{ feedbackTypeLabel(item.feedbackType) }}</strong>
              <p>检索 #{{ item.searchRecordId }} · 用户：{{ item.username || 'guest' }} · 预测：{{ item.predictedLandmarkName || '-' }} · 确认：{{ item.confirmedLandmarkName || '-' }}</p>
              <small>{{ item.comment || '无补充说明' }}</small>
            </div>
            <div class="admin-actions">
              <span :class="['status-badge', item.status]">{{ feedbackStatusLabel(item.status) }}</span>
              <button type="button" @click="$emit('view-detail', item.id)">详情</button>
              <button type="button" @click="$emit('update-status', item.id, 'accepted')">采纳</button>
              <button type="button" @click="$emit('update-status', item.id, 'ignored')">忽略</button>
            </div>
          </div>
        </div>
      </article>

      <article class="admin-card feedback-detail-card">
        <div class="admin-card-head">
          <div>
            <p class="eyebrow">Feedback Detail</p>
            <h3>反馈详情</h3>
          </div>
        </div>
        <div v-if="!selectedFeedbackDetail" class="empty-state compact">
          <h3>请选择反馈记录</h3>
          <p>详情会展示上传图片、Top-5 快照、算法采纳建议和校正样本同步状态。</p>
        </div>
        <div v-else class="feedback-detail-body">
          <div class="feedback-image" :style="{ backgroundImage: `url(${selectedFeedbackDetail.uploadImageUrl || ''})` }"></div>
          <div class="detail-grid admin-detail-grid">
            <span>检索记录</span><strong>#{{ selectedFeedbackDetail.searchRecordId }}</strong>
            <span>反馈状态</span><strong>{{ feedbackStatusLabel(selectedFeedbackDetail.status) }}</strong>
            <span>校正同步</span><strong>{{ syncStatusLabel(selectedFeedbackDetail.correctionSample?.syncStatus) }}</strong>
            <span>采纳建议</span><strong>{{ adviceLabel(selectedFeedbackDetail.correctionSample) }}</strong>
          </div>
          <div class="top-snapshot-list">
            <button v-for="item in selectedFeedbackDetail.topResults" :key="item.landmarkId" type="button">
              <span>#{{ item.rank }}</span>
              <strong>{{ item.landmarkCode }} {{ item.name }}</strong>
              <small>{{ Math.round(Number(item.score) * 100) }}%</small>
            </button>
          </div>
          <p class="admin-detail-note">{{ selectedFeedbackDetail.correctionSample?.reason || selectedFeedbackDetail.comment || '暂无补充说明' }}</p>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({
  isAdmin: { type: Boolean, required: true },
  searchRecords: { type: Array, default: () => [] },
  feedbackRecords: { type: Array, default: () => [] },
  selectedFeedbackDetail: { type: Object, default: null }
})

defineEmits(['refresh', 'update-status', 'view-detail'])

function recordStatusLabel(status) {
  return {
    success: '成功',
    low_confidence: '低匹配',
    empty_result: '空结果',
    algorithm_unavailable: '算法不可用'
  }[status] || status
}

function feedbackTypeLabel(value) {
  return {
    correct: '识别正确',
    wrong: '识别错误',
    uncertain: '不确定'
  }[value] || value
}

function feedbackStatusLabel(value) {
  return {
    pending: '待处理',
    accepted: '已采纳',
    ignored: '已忽略'
  }[value] || value
}

function scoreLabel(value) {
  return value == null ? '' : `匹配分 ${Math.round(Number(value) * 100)}%`
}

function syncStatusLabel(value) {
  return {
    sync_pending: '同步中',
    synced: '已同步',
    sync_failed: '同步失败'
  }[value] || '未生成'
}

function adviceLabel(sample) {
  if (!sample) return '待生成'
  if (sample.reviewScore == null) return '等待算法建议'
  return `${sample.suggestAccept ? '建议采纳' : '建议复核'} · ${Math.round(Number(sample.reviewScore) * 100)}%`
}
</script>
