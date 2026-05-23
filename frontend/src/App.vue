<template>
  <main class="app-shell">
    <aside class="side-panel">
      <div>
        <p class="eyebrow">CampusLens</p>
        <h1>校园慧眼</h1>
        <p class="intro">上传一张校园照片，查看 Top-5 候选地标、位置标注和反馈入口。</p>
      </div>

      <form class="upload-panel" @submit.prevent="submitSearch">
        <label class="file-drop" :class="{ active: selectedFile }">
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="onFileChange" />
          <span class="file-icon">+</span>
          <strong>{{ selectedFile ? selectedFile.name : '选择校园照片' }}</strong>
          <small>支持 JPG、PNG、WebP，建议不超过 8MB</small>
        </label>
        <button class="primary-btn" :disabled="loading">
          {{ loading ? '检索中...' : '上传并检索' }}
        </button>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>

      <nav class="module-list" aria-label="模块状态">
        <button :class="{ active: activeView === 'results' }" @click="activeView = 'results'">Top-5 结果</button>
        <button :class="{ active: activeView === 'map' }" @click="activeView = 'map'">地图导览</button>
        <button :class="{ active: activeView === 'feedback' }" @click="activeView = 'feedback'">反馈纠错</button>
      </nav>
    </aside>

    <section class="content-area">
      <header class="top-bar">
        <div>
          <p class="eyebrow">初始阶段原型</p>
          <h2>{{ viewTitle }}</h2>
        </div>
        <span class="status-pill">M1 / M2 / M4 / M5</span>
      </header>

      <section v-if="activeView === 'results'" class="results-layout">
        <article class="result-list">
          <div v-for="item in results" :key="item.landmarkId" class="result-card" @click="selectLandmark(item.landmarkId)">
            <div class="rank">{{ item.rank }}</div>
            <div class="result-body">
              <div class="result-heading">
                <div>
                  <h3>{{ item.name }}</h3>
                  <p>{{ item.englishName }} · {{ item.locationText }}</p>
                </div>
                <strong>{{ Math.round(item.score * 100) }}%</strong>
              </div>
              <p>{{ item.summary }}</p>
              <div class="result-actions">
                <button @click.stop="selectLandmark(item.landmarkId)">详情</button>
                <button @click.stop="openFeedback(item)">反馈</button>
              </div>
            </div>
          </div>
        </article>

        <article class="detail-panel" v-if="selectedLandmark">
          <p class="eyebrow">{{ selectedLandmark.code }}</p>
          <h3>{{ selectedLandmark.name }}</h3>
          <p class="detail-location">{{ selectedLandmark.locationText }}</p>
          <p>{{ selectedLandmark.description }}</p>
          <div class="detail-grid">
            <span>类型</span><strong>{{ selectedLandmark.type }}</strong>
            <span>地图坐标</span><strong>{{ selectedLandmark.mapX }}%, {{ selectedLandmark.mapY }}%</strong>
            <span>样本要求</span><strong>不少于 20 张</strong>
          </div>
        </article>
      </section>

      <section v-if="activeView === 'map'" class="map-layout">
        <div class="map-board">
          <img src="/campus-map.png" alt="校园平面图" />
          <button
            v-for="item in landmarks"
            :key="item.id"
            class="map-marker"
            :class="{ active: item.id === selectedId }"
            :style="{ left: item.mapX + '%', top: item.mapY + '%' }"
            :title="item.name"
            type="button"
            @click.stop="selectMapLandmark(item.id)"
          >
            {{ item.code.slice(1) }}
          </button>
        </div>
        <aside class="map-summary">
          <h3>静态地图标注</h3>
          <p>初始阶段采用校园平面图百分比坐标标注，后续可替换为更精确的 GIS 或室内导览数据。</p>
          <div class="legend-list">
            <button
              v-for="item in landmarks"
              :key="item.code"
              :class="{ active: item.id === selectedId }"
              type="button"
              @click="selectMapLandmark(item.id)"
            >
              <span>{{ item.code }}</span>{{ item.name }}
            </button>
          </div>
        </aside>
      </section>

      <section v-if="activeView === 'feedback'" class="feedback-page">
        <article class="feedback-card">
          <h3>识别反馈</h3>
          <p>用户可提交识别正确、识别错误或不确定，系统记录后用于后续统计和样本优化。</p>
          <form @submit.prevent="submitFeedback">
            <label>
              反馈类型
              <select v-model="feedback.feedbackType">
                <option value="correct">识别正确</option>
                <option value="wrong">识别错误</option>
                <option value="uncertain">不确定</option>
              </select>
            </label>
            <label>
              正确地标
              <select v-model.number="feedback.confirmedLandmarkId">
                <option :value="null">暂不选择</option>
                <option v-for="item in landmarks" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
            </label>
            <label>
              补充说明
              <textarea v-model="feedback.comment" rows="4" placeholder="例如：实际是学术大讲堂入口"></textarea>
            </label>
            <button class="primary-btn">提交反馈</button>
            <p v-if="feedbackMessage" class="success-text">{{ feedbackMessage }}</p>
          </form>
        </article>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const demoLandmarks = [
  { id: 1, code: 'L01', name: '图书馆', englishName: 'Library', type: '建筑', summary: '校园核心学习空间，建筑体量大、外立面辨识度高。', description: '图书馆位于文雍广场附近，是学生自习、借阅和课程资料检索的主要场所。', locationText: '文雍广场附近', mapX: 50.31, mapY: 59.33 },
  { id: 2, code: 'L02', name: '学术大讲堂', englishName: 'Academic Auditorium', type: '建筑', summary: '大型报告与答辩活动场所，适合作为答辩演示样本。', description: '学术大讲堂靠近东门，常用于学术报告、会议与集中教学活动。', locationText: '东门附近', mapX: 62.16, mapY: 61.22 },
  { id: 3, code: 'L03', name: '文雍广场', englishName: 'Wenyong Square', type: '广场', summary: '校园开放空间与人流汇聚点，便于地图静态标注。', description: '文雍广场位于图书馆前，是校园公共活动与通行的重要节点。', locationText: '图书馆前', mapX: 57.37, mapY: 63.56 },
  { id: 4, code: 'L04', name: '博学桥', englishName: 'Boxue Bridge', type: '桥梁', summary: '连接湖区两侧的桥梁景观，适合作为地标景观样本。', description: '博学桥位于韵湖沿线，桥体、湖面和周边道路共同形成稳定的视觉特征。', locationText: '韵湖沿线', mapX: 57.75, mapY: 46.17 },
  { id: 5, code: 'L05', name: '琴湖及湖心岛', englishName: 'Qin Lake / Huxin Island', type: '湖区', summary: '湖泊与岛屿组合景观，外观特征明显。', description: '琴湖及湖心岛位于文雍路东侧，水域、绿化和湖心岛轮廓适合进行地标图像检索。', locationText: '文雍路东侧', mapX: 67.32, mapY: 26.88 },
  { id: 6, code: 'L06', name: '体育馆', englishName: 'Stadium', type: '场馆', summary: '体育活动场馆，建筑边界清晰。', description: '体育馆位于文雍路西侧，服务课程教学、赛事活动和学生日常锻炼。', locationText: '文雍路西侧', mapX: 43.88, mapY: 49.07 },
  { id: 7, code: 'L07', name: '游泳馆', englishName: 'Natatorium', type: '场馆', summary: '运动场馆类地标，适合与体育馆形成区分样本。', description: '游泳馆位于体育馆北侧，是运动场馆类地标。', locationText: '体育馆北侧', mapX: 45.39, mapY: 41.25 },
  { id: 8, code: 'L08', name: '第一饭堂', englishName: 'The First Dining Hall', type: '生活服务', summary: '生活服务类建筑，面向学生日常场景。', description: '第一饭堂位于尚学路西侧，属于学生高频到达地点。', locationText: '尚学路西侧', mapX: 33.8, mapY: 47.17 },
  { id: 9, code: 'L09', name: '第二饭堂', englishName: 'The Second Dining Hall', type: '生活服务', summary: '生活服务类建筑，可与第一饭堂对比识别。', description: '第二饭堂位于东二门附近，与第一饭堂同属生活服务类建筑。', locationText: '东二门附近', mapX: 37.96, mapY: 21.84 },
  { id: 10, code: 'L10', name: '中心酒店', englishName: 'Hotel', type: '建筑', summary: '校内接待建筑，靠近北门且地图标注清晰。', description: '中心酒店位于北门内侧，主要用于校内接待和住宿服务。', locationText: '北门内侧', mapX: 62.28, mapY: 10.12 }
]

const landmarks = ref(demoLandmarks)
const results = ref(demoLandmarks.slice(0, 5).map((item, index) => ({
  ...item,
  rank: index + 1,
  landmarkId: item.id,
  landmarkCode: item.code,
  score: [0.92, 0.87, 0.81, 0.76, 0.71][index]
})))
const selectedId = ref(1)
const selectedFile = ref(null)
const loading = ref(false)
const error = ref('')
const initialView = new URLSearchParams(window.location.search).get('view')
const activeView = ref(['results', 'map', 'feedback'].includes(initialView) ? initialView : 'results')
const feedbackMessage = ref('')
const feedback = reactive({
  searchRecordId: 1,
  predictedLandmarkId: 1,
  confirmedLandmarkId: 1,
  feedbackType: 'correct',
  comment: ''
})

const selectedLandmark = computed(() => landmarks.value.find((item) => item.id === selectedId.value))
const viewTitle = computed(() => ({
  results: '图片检索结果',
  map: '校园地图导览',
  feedback: '用户反馈纠错'
}[activeView.value]))

onMounted(async () => {
  try {
    const response = await fetch('/api/landmarks')
    if (response.ok) {
      landmarks.value = await response.json()
    }
  } catch {
    landmarks.value = demoLandmarks
  }
})

function onFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
  error.value = ''
}

async function submitSearch() {
  if (!selectedFile.value) {
    error.value = '请先选择一张校园照片'
    return
  }
  loading.value = true
  error.value = ''
  const form = new FormData()
  form.append('file', selectedFile.value)
  try {
    const response = await fetch('/api/search/upload', { method: 'POST', body: form })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.message || '检索请求失败')
    }
    const data = await response.json()
    results.value = data.results
    feedback.searchRecordId = data.searchRecordId
    activeView.value = 'results'
    selectedId.value = data.results[0]?.landmarkId || 1
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function selectLandmark(id) {
  selectedId.value = id
  activeView.value = 'results'
}

function selectMapLandmark(id) {
  selectedId.value = id
}

function openFeedback(item) {
  feedback.predictedLandmarkId = item.landmarkId
  feedback.confirmedLandmarkId = item.landmarkId
  feedback.feedbackType = 'correct'
  feedback.comment = ''
  activeView.value = 'feedback'
}

async function submitFeedback() {
  feedbackMessage.value = ''
  const payload = { ...feedback }
  try {
    const response = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.message || '反馈提交失败')
    }
    const data = await response.json()
    feedbackMessage.value = `反馈已提交，记录编号：${data.feedbackId}`
  } catch (err) {
    feedbackMessage.value = err.message
  }
}
</script>
