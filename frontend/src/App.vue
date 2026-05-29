<template>
  <main class="app-shell">
    <aside class="side-panel">
      <div class="brand-header">
        <div class="brand-logo">
          <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path><circle cx="12" cy="13" r="4"></circle></svg>
        </div>
        <div>
          <p class="eyebrow">CampusLens</p>
          <h1>校园慧眼</h1>
        </div>
      </div>
      <p class="intro">上传校园照片，查看 Top-5 地标、位置标注和反馈入口。</p>

      <form class="upload-panel" @submit.prevent="submitSearch">
        <label class="file-drop" :class="{ active: selectedFile }">
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="onFileChange" />
          <span class="file-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
          </span>
          <strong>{{ selectedFile ? selectedFile.name : '选择校园照片' }}</strong>
          <small>支持 JPG、PNG、WebP，不超过 8MB</small>
        </label>
        <button class="primary-btn" :disabled="loading" :class="{ 'is-loading': loading }">
          <span class="btn-text">{{ loading ? '算法检索中...' : '上传并检索' }}</span>
          <span class="btn-shine"></span>
        </button>
        <p v-if="error" class="error-text">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
          {{ error }}
        </p>
      </form>

      <nav class="module-list" aria-label="模块状态">
        <button :class="{ active: activeView === 'results' }" @click="changeView('results')">
          <span class="active-line"></span>
          Top-5 结果
        </button>
        <button :class="{ active: activeView === 'map' }" @click="changeView('map')">
          <span class="active-line"></span>
          地图导览
        </button>
        <button :class="{ active: activeView === 'feedback' }" @click="changeView('feedback')">
          <span class="active-line"></span>
          反馈纠错
        </button>
      </nav>
    </aside>

    <section class="content-area">
      <header class="top-bar">
        <!-- SPA 极简历史后退前进导航栏 -->
        <div class="navigator-header">
          <div class="history-navigator">
            <button type="button" @click="goBack" :disabled="historyIndex === 0" title="返回上一步">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
            </button>
            <button type="button" @click="goForward" :disabled="historyIndex === viewHistory.length - 1" title="进入下一步">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
            </button>
          </div>
          <div>
          <p class="eyebrow">V1 联调版本</p>
            <h2>{{ viewTitle }}</h2>
          </div>
        </div>
        <span class="status-pill">M1 / M2 / M4 / M5</span>
      </header>

      <!-- 引入 Vue 极致渐入垂直淡滑过渡 -->
      <Transition name="page-fade" mode="out-in">
        <section v-if="activeView === 'results'" class="results-layout" key="results">
          <div class="results-main">
            <!-- 本次检索状态概览 -->
            <div class="ai-analytical-header">
              <div class="stat-card">
                <span class="stat-label">检索记录</span>
                <span class="stat-value">#{{ searchMeta.searchRecordId }}</span>
                <span class="stat-desc">{{ searchMeta.uploadImageUrl ? '已保存上传图片' : '等待上传校园照片' }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">候选数量</span>
                <span class="stat-value">{{ results.length }}</span>
                <span class="stat-desc">后端返回的 Top-5 候选地标</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">检索状态</span>
                <span class="stat-value">{{ searchMeta.lowConfidence ? '需核验' : '可展示' }}</span>
                <span class="stat-desc">{{ searchMeta.message }}</span>
              </div>
            </div>

            <p v-if="searchMeta.lowConfidence" class="warning-text">
              {{ searchMeta.message }}
            </p>

            <article v-if="results.length" class="result-list">
              <div v-for="item in results" :key="item.landmarkId" class="result-card" :class="{ active: item.landmarkId === selectedId }" @click="selectLandmark(item.landmarkId)">
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
                  
                  <!-- 精密石墨拉丝置信度条 -->
                  <div class="confidence-container">
                    <div class="confidence-bar-bg">
                      <div class="confidence-bar-fill" :style="{ width: Math.round(item.score * 100) + '%' }"></div>
                    </div>
                  </div>

                  <div class="result-actions">
                    <span class="result-meta">{{ confidenceLabel(item.confidenceLevel) }}<template v-if="item.mahalanobisDistance != null"> · D={{ Number(item.mahalanobisDistance).toFixed(2) }}</template></span>
                    <button @click.stop="openModal(item)">详情</button>
                    <button @click.stop="openFeedback(item)">反馈</button>
                  </div>
                </div>
              </div>
            </article>
            <article v-else class="empty-state">
              <h3>暂无检索结果</h3>
              <p>请上传 JPG、PNG 或 WebP 图片后重新检索。</p>
            </article>
          </div>

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

        <section v-else-if="activeView === 'map'" class="map-layout" key="map">
          <div class="map-board">
            <!-- 悬浮 macOS 亚克力控制条 -->
            <div class="map-controls">
              <button type="button" @click="zoomIn" title="放大">
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              </button>
              <span class="zoom-indicator">{{ Math.round(zoomScale * 100) }}%</span>
              <button type="button" @click="zoomOut" title="缩小">
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              </button>
              <button type="button" @click="zoomReset" title="重置">
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M23 4v6h-6"></path><path d="M1 20v-6h6"></path><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
              </button>
            </div>
            
            <!-- 超出可拖动滚动的视口 -->
            <div 
              class="map-viewport" 
              :class="{ 'is-zoomed': zoomScale > 1.0 }"
              ref="mapViewport"
              @mousedown="startDrag"
              @mousemove="onDrag"
              @mouseup="endDrag"
              @mouseleave="endDrag"
            >
              <!-- 定位与图片完全绑定的容器 -->
              <div 
                class="map-container"
                :style="{ transform: `scale(${zoomScale})` }"
              >
                <img src="/campus-map.png" alt="校园平面图" class="map-img" />
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
                  <span>{{ item.code.slice(1) }}</span>
                </button>
              </div>
            </div>
          </div>
          <aside class="map-summary">
            <h3>静态地图标注</h3>
            <p>基于校园平面图百分比坐标标注，后续可替换为更精确的 GIS 或室内导览数据。</p>
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

        <section v-else-if="activeView === 'feedback'" class="feedback-page" key="feedback">
          <div class="feedback-layout">
            <!-- 左侧：高水准近期历史识别记录追踪大盘 -->
            <aside class="history-panel">
              <h3>本次检索记录</h3>
              <p class="panel-desc">反馈将绑定当前 searchRecordId 和候选地标编号，第二周保留 pending 状态。</p>
              
              <div class="history-list">
                <div class="history-item">
                  <div class="history-meta">
                    <span class="history-time">SearchRecord #{{ feedback.searchRecordId }}</span>
                    <span class="history-status active">待反馈</span>
                  </div>
                  <strong>{{ selectedLandmark?.code }} {{ selectedLandmark?.name }}</strong>
                  <p>预测地标 ID：{{ feedback.predictedLandmarkId }} · 反馈类型：{{ feedback.feedbackType }}</p>
                </div>
                <div v-for="item in results.slice(0, 3)" :key="item.landmarkCode" class="history-item">
                  <div class="history-meta">
                    <span class="history-time">候选 #{{ item.rank }}</span>
                    <span class="history-status" :class="{ active: item.confidenceLevel !== 'low', wrong: item.confidenceLevel === 'low' }">{{ confidenceLabel(item.confidenceLevel) }}</span>
                  </div>
                  <strong>{{ item.landmarkCode }} {{ item.name }}</strong>
                  <p>置信度：{{ Math.round(item.score * 100) }}%<template v-if="item.mahalanobisDistance != null"> · 马氏距离：{{ Number(item.mahalanobisDistance).toFixed(2) }}</template></p>
                </div>
              </div>
            </aside>

            <!-- 右侧：反馈提交表单 -->
            <article class="feedback-card">
              <h3>提交反馈纠错</h3>
              <p>当前阶段记录反馈入口和字段衔接，审核、采纳和统计在后续迭代完善。</p>
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
                <button class="primary-btn">
                  <span class="btn-text">提交反馈</span>
                </button>
                <p v-if="feedbackMessage" class="success-text">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                  {{ feedbackMessage }}
                </p>
              </form>
            </article>
          </div>
        </section>
      </Transition>
    </section>

    <!-- 地标详情弹窗 -->
    <Transition name="modal-fade">
      <div v-if="showModal && modalLandmark" class="modal-overlay" @click.self="closeModal">
        <div class="modal-content">
          <button class="modal-close" @click="closeModal" title="关闭 (Esc)">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
          </button>
          
          <!-- 高精实拍底图 -->
          <div class="modal-hero">
            <img :src="modalLandmark.imageUrl" :alt="modalLandmark.name" />
            <div class="modal-hero-overlay">
              <span class="modal-code">{{ modalLandmark.code }}</span>
              <h2>{{ modalLandmark.name }}</h2>
              <p>{{ modalLandmark.englishName }} · {{ modalLandmark.locationText }}</p>
            </div>
          </div>
          
          <div class="modal-body">
            <div class="modal-section">
              <h4>地标实景详细介绍</h4>
              <p>{{ modalLandmark.description }}</p>
            </div>
            
            <div class="modal-section">
              <h4>深度解析指标</h4>
              <div class="modal-grid">
                <div class="grid-item">
                  <span>地标类型</span>
                  <strong>{{ modalLandmark.type }}</strong>
                </div>
                <div class="grid-item">
                  <span>地图百分比坐标</span>
                  <strong>X: {{ modalLandmark.mapX }}%, Y: {{ modalLandmark.mapY }}%</strong>
                </div>
                <div class="grid-item">
                  <span>样本容量限制</span>
                  <strong>不少于 20 张</strong>
                </div>
                <div class="grid-item">
                  <span>验证状态</span>
                  <strong style="color: #10b981;">已校验并收录</strong>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const demoLandmarks = [
  { id: 1, code: 'L01', name: '图书馆', englishName: 'Library', type: '建筑', summary: '校园核心学习空间，建筑体量大、外立面辨识度高。', description: '图书馆位于文雍广场附近，是学生自习、借阅 and 课程资料检索的主要场所。', locationText: '文雍广场附近', mapX: 50.31, mapY: 59.33, imageUrl: 'https://images.unsplash.com/photo-1507842217343-583bb7270b66?auto=format&fit=crop&w=800&q=80' },
  { id: 2, code: 'L02', name: '学术大讲堂', englishName: 'Academic Auditorium', type: '建筑', summary: '大型报告与答辩 activity 场所，适合作为答辩演示样本。', description: '学术大讲堂靠近东门，常用于学术报告、会议与集中教学活动。', locationText: '东门附近', mapX: 62.16, mapY: 61.22, imageUrl: 'https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=800&q=80' },
  { id: 3, code: 'L03', name: '文雍广场', englishName: 'Wenyong Square', type: '广场', summary: '校园开放空间与人流汇聚点，便于地图静态标注。', description: '文雍广场位于图书馆前，是校园公共活动与通行的重要节点。', locationText: '图书馆前', mapX: 57.37, mapY: 63.56, imageUrl: 'https://images.unsplash.com/photo-1541339907198-e08756dedf3f?auto=format&fit=crop&w=800&q=80' },
  { id: 4, code: 'L04', name: '博学桥', englishName: 'Boxue Bridge', type: '桥梁', summary: '连接湖区两侧的桥梁景观，适合作为地标景观样本。', description: '博学桥位于韵湖沿线，桥体、湖面和周边道路共同形成稳定的视觉特征。', locationText: '韵湖沿线', mapX: 57.75, mapY: 46.17, imageUrl: 'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?auto=format&fit=crop&w=800&q=80' },
  { id: 5, code: 'L05', name: '琴湖及湖心岛', englishName: 'Qin Lake / Huxin Island', type: '湖区', summary: '湖泊与岛屿组合景观，外观特征明显。', description: '琴湖及湖心岛位于文雍路东侧，水域、绿化和湖心岛轮廓适合进行地标图像检索。', locationText: '文雍路东侧', mapX: 67.32, mapY: 26.88, imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=800&q=80' },
  { id: 6, code: 'L06', name: '体育馆', englishName: 'Stadium', type: '场馆', summary: '体育活动场馆，建筑边界清晰。', description: '体育馆位于文雍路西侧，服务课程教学、赛事活动和学生日常锻炼。', locationText: '文雍路西侧', mapX: 43.88, mapY: 49.07, imageUrl: 'https://images.unsplash.com/photo-1544698310-74ea9d1c8258?auto=format&fit=crop&w=800&q=80' },
  { id: 7, code: 'L07', name: '游泳馆', englishName: 'Natatorium', type: '场馆', summary: '运动场馆类地标，适合与体育馆形成区分样本。', description: '游泳馆位于体育馆北侧，是运动场馆类地标。', locationText: '体育馆北侧', mapX: 45.39, mapY: 41.25, imageUrl: 'https://images.unsplash.com/photo-1576013551627-0cc20b96c2a7?auto=format&fit=crop&w=800&q=80' },
  { id: 8, code: 'L08', name: '第一饭堂', englishName: 'The First Dining Hall', type: '生活服务', summary: '生活服务类建筑，面向学生日常场景。', description: '第一饭堂位于尚学路西侧，属于学生高频到达地点。', locationText: '尚学路西侧', mapX: 33.8, mapY: 47.17, imageUrl: 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=800&q=80' },
  { id: 9, code: 'L09', name: '第二饭堂', englishName: 'The Second Dining Hall', type: '生活服务', summary: '生活服务类建筑，可与第一饭堂对比识别。', description: '第二饭堂位于东二门附近，与第一饭堂同属生活服务类建筑。', locationText: '东二门附近', mapX: 37.96, mapY: 21.84, imageUrl: 'https://images.unsplash.com/photo-1578474846511-04ba529f0b88?auto=format&fit=crop&w=800&q=80' },
  { id: 10, code: 'L10', name: '中心酒店', englishName: 'Hotel', type: '建筑', summary: '校内接待建筑，靠近北门且地图标注清晰。', description: '中心酒店位于北门内侧，主要用于校内接待和住宿服务。', locationText: '北门内侧', mapX: 62.28, mapY: 10.12, imageUrl: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800&q=80' }
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
const searchMeta = reactive({
  searchRecordId: 1,
  uploadImageUrl: '',
  lowConfidence: false,
  message: '尚未上传图片'
})
const feedback = reactive({
  searchRecordId: 1,
  predictedLandmarkId: 1,
  confirmedLandmarkId: 1,
  feedbackType: 'correct',
  comment: ''
})

// 地图缩放与拖拽状态
const zoomScale = ref(1.0)
const maxZoom = 3.0
const minZoom = 0.5
const isDragging = ref(false)
const startX = ref(0)
const startY = ref(0)
const scrollLeft = ref(0)
const scrollTop = ref(0)
const mapViewport = ref(null)

// 详情弹窗状态
const showModal = ref(false)
const modalLandmark = ref(null)

// SPA 全局视图历史栈状态机
const viewHistory = ref(['results'])
const historyIndex = ref(0)

function navigateToView(view) {
  if (activeView.value === view) return
  // 如果当前指针不在栈顶（说明发生了后退），清除指针后面的历史
  if (historyIndex.value < viewHistory.value.length - 1) {
    viewHistory.value = viewHistory.value.slice(0, historyIndex.value + 1)
  }
  viewHistory.value.push(view)
  historyIndex.value = viewHistory.value.length - 1
  activeView.value = view
}

// 供侧边栏切换和上传成功后跳转的拦截器
function changeView(view) {
  navigateToView(view)
}

function goBack() {
  if (historyIndex.value > 0) {
    historyIndex.value--
    activeView.value = viewHistory.value[historyIndex.value]
  }
}

function goForward() {
  if (historyIndex.value < viewHistory.value.length - 1) {
    historyIndex.value++
    activeView.value = viewHistory.value[historyIndex.value]
  }
}

function openModal(landmark) {
  modalLandmark.value = landmark
  showModal.value = true
  window.addEventListener('keydown', handleEsc)
}

function closeModal() {
  showModal.value = false
  window.removeEventListener('keydown', handleEsc)
}

function handleEsc(e) {
  if (e.key === 'Escape') {
    closeModal()
  }
}

function zoomIn() {
  if (zoomScale.value < maxZoom) {
    zoomScale.value = parseFloat((zoomScale.value + 0.25).toFixed(2))
  }
}

function zoomOut() {
  if (zoomScale.value > minZoom) {
    zoomScale.value = parseFloat((zoomScale.value - 0.25).toFixed(2))
  }
}

function zoomReset() {
  zoomScale.value = 1.0
  if (mapViewport.value) {
    mapViewport.value.scrollLeft = 0
    mapViewport.value.scrollTop = 0
  }
}

// 鼠标拖动漫游平移 (仅当有缩放时生效)
function startDrag(e) {
  if (zoomScale.value <= 1.0) return // 未缩放时不响应拖拽
  if (e.target.closest('.map-marker') || e.target.closest('.map-controls')) return
  isDragging.value = true
  startX.value = e.pageX - mapViewport.value.offsetLeft
  startY.value = e.pageY - mapViewport.value.offsetTop
  scrollLeft.value = mapViewport.value.scrollLeft
  scrollTop.value = mapViewport.value.scrollTop
  mapViewport.value.style.cursor = 'grabbing'
  mapViewport.value.style.userSelect = 'none'
}

function onDrag(e) {
  if (!isDragging.value) return
  e.preventDefault()
  const x = e.pageX - mapViewport.value.offsetLeft
  const y = e.pageY - mapViewport.value.offsetTop
  const walkX = (x - startX.value) * 1.5
  const walkY = (y - startY.value) * 1.5
  mapViewport.value.scrollLeft = scrollLeft.value - walkX
  mapViewport.value.scrollTop = scrollTop.value - walkY
}

function endDrag() {
  isDragging.value = false
  if (mapViewport.value && zoomScale.value > 1.0) {
    mapViewport.value.style.cursor = 'grab'
    mapViewport.value.style.removeProperty('user-select')
  }
}

const selectedLandmark = computed(() => landmarks.value.find((item) => item.id === selectedId.value))
const viewTitle = computed(() => ({
  results: '图片检索结果',
  map: '校园地图导览',
  feedback: '用户反馈纠错'
}[activeView.value]))

function confidenceLabel(value) {
  return {
    high: '高置信度',
    medium: '中置信度',
    low: '低置信度'
  }[value] || '待核验'
}

onMounted(async () => {
  try {
    const response = await fetch('/api/landmarks')
    if (response.ok) {
      const data = await response.json()
      // 如果后端发来地标，我们把 Unsplash 照片补全
      landmarks.value = data.map((item, index) => ({
        ...item,
        imageUrl: demoLandmarks[index]?.imageUrl || demoLandmarks[0].imageUrl
      }))
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
    results.value = data.results.map((item) => ({
      ...item,
      imageUrl: demoLandmarks.find(l => l.id === item.landmarkId)?.imageUrl || demoLandmarks[0].imageUrl,
      confidenceLevel: item.confidenceLevel || 'low',
      score: Math.max(0, Math.min(1, Number(item.score || 0)))
    }))
    searchMeta.searchRecordId = data.searchRecordId
    searchMeta.uploadImageUrl = data.uploadImageUrl || ''
    searchMeta.lowConfidence = Boolean(data.lowConfidence)
    searchMeta.message = data.message || '检索完成'
    feedback.searchRecordId = data.searchRecordId
    
    // 自动推送历史
    navigateToView('results')
    selectedId.value = results.value[0]?.landmarkId || 1
  } catch (err) {
    error.value = err.message
    searchMeta.lowConfidence = true
    searchMeta.message = err.message
  } finally {
    loading.value = false
  }
}

function selectLandmark(id) {
  selectedId.value = id
  navigateToView('results')
}

function selectMapLandmark(id) {
  selectedId.value = id
}

function openFeedback(item) {
  feedback.predictedLandmarkId = item.landmarkId
  feedback.confirmedLandmarkId = item.landmarkId
  feedback.feedbackType = 'correct'
  feedback.comment = ''
  navigateToView('feedback')
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
