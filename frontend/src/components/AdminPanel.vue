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
          <div v-for="item in feedbackRecords" :key="item.id" class="feedback-admin-item">
            <div>
              <strong>#{{ item.id }} {{ feedbackTypeLabel(item.feedbackType) }}</strong>
              <p>检索 #{{ item.searchRecordId }} · 用户：{{ item.username || 'guest' }} · 预测：{{ item.predictedLandmarkName || '-' }} · 确认：{{ item.confirmedLandmarkName || '-' }}</p>
              <small>{{ item.comment || '无补充说明' }}</small>
            </div>
            <div class="admin-actions">
              <span :class="['status-badge', item.status]">{{ feedbackStatusLabel(item.status) }}</span>
              <button type="button" @click="$emit('update-status', item.id, 'accepted')">采纳</button>
              <button type="button" @click="$emit('update-status', item.id, 'ignored')">忽略</button>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({
  isAdmin: { type: Boolean, required: true },
  searchRecords: { type: Array, default: () => [] },
  feedbackRecords: { type: Array, default: () => [] }
})

defineEmits(['refresh', 'update-status'])

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
</script>
