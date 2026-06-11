<template>
  <main class="app-shell" :class="shellClasses">
    <aside class="side-panel" :class="{ 'collapsed': isSidebarCollapsed }">
      <div class="brand-header">
        <div class="brand-logo">
          <img src="/logo.png" alt="CampusLens" style="width: 100%; height: 100%; border-radius: 50%; object-fit: cover;" />
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
        <button class="primary-btn" :disabled="loading || jobPending" :class="{ 'is-loading': loading }">
          <span class="btn-text">{{ loading ? jobStatusLabel : '上传并检索' }}</span>
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
        <button :class="{ active: activeView === 'history' }" @click="openHistory">
          <span class="active-line"></span>
          个人历史
        </button>
        <button :class="{ active: activeView === 'checkins' }" @click="openCheckIns">
          <span class="active-line"></span>
          打卡留言
        </button>
        <button v-if="isAdmin" :class="{ active: activeView === 'admin' }" @click="openAdmin">
          <span class="active-line"></span>
          管理后台
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
          <p class="eyebrow">V3 构造阶段</p>
            <h2>{{ viewTitle }}</h2>
          </div>
        </div>
        <div class="top-bar-login">
          <template v-if="!currentUser">
            <button type="button" class="top-login-trigger-btn" @click="openAuth">
              登录/注册
            </button>
          </template>
          <template v-else>
            <div class="top-admin-status">
              <span class="admin-badge">{{ isAdmin ? 'Admin' : 'User' }}</span>
              <span class="admin-username">{{ currentUser.username }}</span>
              <button v-if="isAdmin" type="button" class="top-action-btn" @click="openAdmin">进入后台</button>
              <button type="button" class="top-action-btn logout" @click="handleLogout">退出</button>
            </div>
          </template>
        </div>
        <PreferencesDock
          :theme-label="themeLabel"
          :backdrop-label="backdropLabel"
          :language-label="languageLabel"
          :labels="preferenceLabels"
          @cycle-theme="cycleTheme"
          @cycle-backdrop="cycleBackdrop"
          @toggle-language="toggleLanguage"
        />
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
            <div v-if="jobState.status === 'queued' || jobState.status === 'processing'" class="job-progress" role="status">
              <div>
                <strong>{{ jobStatusLabel }}</strong>
                <span>任务 #{{ jobState.searchRecordId }} · 已尝试 {{ jobState.attemptCount }} 次</span>
              </div>
              <button v-if="!loading" type="button" class="ghost-btn" @click="resumeSearchJob">继续查询</button>
            </div>

            <div v-if="results.length" class="result-carousel-toolbar">
              <button type="button" @click="cycleResults(-1)">上一项</button>
              <span>{{ activeResultIndex + 1 }} / {{ results.length }}</span>
              <button type="button" @click="cycleResults(1)">下一项</button>
            </div>
            <article v-if="results.length" class="result-list result-carousel" @wheel.prevent="cycleResults($event.deltaY > 0 ? 1 : -1)">
              <div v-for="item in displayedResults" :key="item.landmarkId" class="result-card" :class="{ active: item.landmarkId === selectedId }" @click="selectLandmark(item.landmarkId)">
                <div class="rank">{{ item.rank }}</div>
                <div class="result-body">
                  <div class="result-heading">
                    <div>
                      <h3>{{ item.name }}</h3>
                      <p>{{ item.englishName }} · {{ item.locationText }}</p>
                    </div>
                    <strong>匹配分 {{ Math.round(item.score * 100) }}%</strong>
                  </div>
                  <p>{{ item.summary }}</p>
                  
                  <!-- 精密石墨拉丝匹配分条 -->
                  <div class="confidence-container">
                    <div class="confidence-bar-bg">
                      <div class="confidence-bar-fill" :style="{ width: Math.round(item.score * 100) + '%' }"></div>
                    </div>
                  </div>

                  <div class="result-actions">
                    <span class="result-meta">{{ confidenceLabel(item.confidenceLevel) }}<template v-if="item.mahalanobisDistance != null"> · D={{ Number(item.mahalanobisDistance).toFixed(2) }}</template></span>
                    <button @click.stop="openModal(item)">详情</button>
                    <button :disabled="!feedbackEligible" @click.stop="openFeedback(item)">反馈</button>
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
              <span>推荐打卡点</span><strong>正门口 / 广场旁</strong>
            </div>
            <button type="button" class="secondary-btn inline-action" @click="changeView('map')">查看地图位置</button>
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
                  <p>匹配分：{{ Math.round(item.score * 100) }}%<template v-if="item.mahalanobisDistance != null"> · 马氏距离：{{ Number(item.mahalanobisDistance).toFixed(2) }}</template></p>
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

        <AuthPanel
          v-else-if="activeView === 'auth'"
          key="auth"
          :mode="authMode"
          :form="authForm"
          :message="authMessage"
          :error="authError"
          @switch-mode="switchAuthMode"
          @submit="submitAuth"
          @update-form="updateAuthForm"
        />

        <HistoryView
          v-else-if="activeView === 'history'"
          key="history"
          :current-user="currentUser"
          :records="userSearchRecords"
          :loading="historyLoading"
          :labels="historyLabels"
          :fallback-image="demoLandmarks[0].imageUrl"
          @refresh="loadUserHistory"
          @open-feedback="openHistoryFeedback"
        />

        <CheckInBoard
          v-else-if="activeView === 'checkins'"
          key="checkins"
          :landmarks="landmarks"
          :items="checkIns"
          :labels="checkInLabels"
          @refresh="loadCheckIns"
          @create="createCheckIn"
          @like="toggleCheckInLike"
          @reply="replyCheckIn"
          @select-landmark="selectMapLandmark"
        />

        <AdminPanel
          v-else-if="activeView === 'admin'"
          key="admin"
          :is-admin="isAdmin"
          :search-records="adminSearchRecords"
          :feedback-records="adminFeedbackRecords"
          :selected-feedback-detail="selectedFeedbackDetail"
          @refresh="loadAdminData"
          @update-status="updateFeedbackStatus"
          @view-detail="loadFeedbackDetail"
        />
      </Transition>
    </section>

    <LandmarkModal
      :show="showModal"
      :landmark="modalLandmark"
      @close="closeModal"
      @jump-map="jumpModalToMap"
    />
    <InteractiveBackground />
    <button 
      type="button" 
      class="sidebar-toggle-btn"
      :class="isSidebarCollapsed ? 'collapsed' : 'expanded'"
      @click="isSidebarCollapsed = !isSidebarCollapsed"
      :title="isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
    >
      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="toggle-arrow"><polyline points="15 18 9 12 15 6"></polyline></svg>
    </button>

    <!-- 欢迎界面弹出层 -->
    <Transition name="welcome-fade">
      <div v-if="showWelcomeModal" class="welcome-overlay">
        <div class="welcome-card">
          <div class="welcome-bg-image" style="background-image: url('/welcome-bg.png');"></div>
          <div class="welcome-card-content">
            <div class="welcome-logo-badge">
              <img src="/logo.png" alt="CampusLens" style="width: 100%; height: 100%; border-radius: 50%; object-fit: cover;" />
            </div>
            <p class="welcome-eyebrow">CampusLens</p>
            <h2 class="welcome-title">欢迎使用 CampusLens 校园慧眼</h2>
            
            <div class="welcome-divider"></div>
            
            <div class="welcome-date-section">
              <p class="solar-date">{{ welcomeDate.solar }}</p>
              <p class="lunar-date">{{ welcomeDate.lunar }}</p>
            </div>
            
            <div class="welcome-poem-box">
              <p class="poem-text">
                <span style="color: var(--color-accent-light); opacity: 0.5; font-family: Georgia, serif; font-size: 1.2em; vertical-align: -0.1em; margin-right: 4px;">“</span>{{ welcomePoem }}<span style="color: var(--color-accent-light); opacity: 0.5; font-family: Georgia, serif; font-size: 1.2em; vertical-align: -0.2em; margin-left: 4px;">”</span>
              </p>
            </div>
            
            <button type="button" class="primary-btn welcome-enter-btn" @click="dismissWelcome">
              <span class="btn-text">进入系统</span>
              <span class="btn-shine"></span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AdminPanel from './components/AdminPanel.vue'
import AuthPanel from './components/AuthPanel.vue'
import CheckInBoard from './components/CheckInBoard.vue'
import HistoryView from './components/HistoryView.vue'
import LandmarkModal from './components/LandmarkModal.vue'
import InteractiveBackground from './components/InteractiveBackground.vue'
import PreferencesDock from './components/PreferencesDock.vue'
import { getLunarDateString } from './utils/lunar.js'

const isSidebarCollapsed = ref(false)

const showWelcomeModal = ref(false)
const welcomePoem = ref('')
const welcomeDate = ref({ solar: '', lunar: '' })

const poems = [
  "凡是过往，皆为序章。",
  "仰望星空，脚踏实地。",
  "生如夏花之绚烂，死如秋叶之静美。",
  "路漫漫其修远兮，吾将上下而求索。",
  "心有猛虎，细嗅蔷薇。",
  "明月装饰了你的窗子，你装饰了别人的梦。",
  "知者不惑，仁者不忧，勇者不惧。",
  "博学之，审问之，慎思之，明辨之，笃行之。",
  "独行快，众行远。",
  "星光不问赶路人，时光不负有心人。",
  "大漠孤烟直，长河落日圆。",
  "山海自有归期，风雨自有相逢。",
  "答案在未来的路上，而非过去的时光里。",
  "追风赶月莫停留，平芜尽处是春山。",
  "向野而生，踏歌而行。",
  "满怀希望，就会所向披靡。",
  "既然选择了远方，便只顾风雨兼程。",
  "纵有疾风起，人生不言弃。",
  "愿你在冷铁卷刃前，得以窥见天光。",
  "行而不辍，未来可期。",
  "心之所向，素履以往。"
]

const demoLandmarks = [
  { id: 1, code: 'L01', name: '图书馆', englishName: 'Library', type: '建筑', summary: '校园核心文化与学术中心，拥有独特的书页外立面结构。', description: '图书馆位于文雍广场北侧，是学生自习、借阅和课程资料检索的主要场所。建筑气势宏伟，外立面呈半开卷书页状，是校园最具标志性的文化地标。', locationText: '文雍广场北侧', mapX: 50.31, mapY: 59.33, imageUrl: 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?auto=format&fit=crop&w=1200&q=80' },
  { id: 2, code: 'L02', name: '学术大讲堂', englishName: 'Academic Auditorium', type: '建筑', summary: '举办大型学术报告与校园文娱盛典的多功能现代化场馆。', description: '学术大讲堂邻近东门，是学校举办大型学术报告、文化盛典及师生集中教学活动的主阵地。其弧形入口极具现代感与辨识度。', locationText: '东门附近', mapX: 62.16, mapY: 61.22, imageUrl: 'https://images.unsplash.com/photo-1492538368577-870624790c4a?auto=format&fit=crop&w=1200&q=80' },
  { id: 3, code: 'L03', name: '文雍广场', englishName: 'Wenyong Square', type: '广场', summary: '开阔宽广的标志性休闲广场，是校园人文景观的核心纽带。', description: '文雍广场坐落于图书馆南侧，是一座融绿化、喷泉与休闲步道于一体的开阔广场，为校园师生举行集会和课余小憩的重要集散地。', locationText: '图书馆南侧', mapX: 57.37, mapY: 63.56, imageUrl: 'https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=1200&q=80' },
  { id: 4, code: 'L04', name: '博学桥', englishName: 'Boxue Bridge', type: '桥梁', summary: '横跨韵湖的典雅观景石桥，连接南北主要功能园区。', description: '博学桥横跨在美丽的韵湖之上，将教学区与生活区优雅连通。桥身造型典雅，与湖面交相辉映，是备受师生喜爱的校园写意景观。', locationText: '韵湖沿线', mapX: 57.75, mapY: 46.17, imageUrl: 'https://images.unsplash.com/photo-1549880338-65ddcdfd017b?auto=format&fit=crop&w=1200&q=80' },
  { id: 5, code: 'L05', name: '琴湖及湖心岛', englishName: 'Qin Lake / Huxin Island', type: '湖区', summary: '环境幽雅的水域景观，湖水碧绿，岛上植被常青。', description: '琴湖及湖心岛位于文雍路东侧，水体清澈，绿化茂密。清晨和傍晚，这里烟波浩渺，是校园内最富有自然诗意和静谧之美的一隅。', locationText: '文雍路东侧', mapX: 67.32, mapY: 26.88, imageUrl: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80' },
  { id: 6, code: 'L06', name: '体育馆', englishName: 'Stadium', type: '场馆', summary: '配备多功能运动场地的现代化综合室内健身体育馆。', description: '体育馆位于文雍路西侧，是一座设施完善的现代化多功能场馆，服务全校体育教学、文体赛事和日常锻炼，极具动感的网架几何外形十分夺目。', locationText: '文雍路西侧', mapX: 43.88, mapY: 49.07, imageUrl: 'https://images.unsplash.com/photo-1577416412292-747c6607f055?auto=format&fit=crop&w=1200&q=80' },
  { id: 7, code: 'L07', name: '游泳馆', englishName: 'Natatorium', type: '场馆', summary: '配备先进循环系统的室内温水游泳馆。', description: '游泳馆位于体育馆北侧，配有标准泳道和水循环系统，是日常游泳教学、水上运动训练以及师生消暑运动的首选场馆。', locationText: '体育馆北侧', mapX: 45.39, mapY: 41.25, imageUrl: 'https://images.unsplash.com/photo-1519766304817-4f37bda74a27?auto=format&fit=crop&w=1200&q=80' },
  { id: 8, code: 'L08', name: '第一饭堂', englishName: 'The First Dining Hall', type: '生活服务', summary: '大众膳食生活服务中心，提供多风味特色餐饮。', description: '第一饭堂位于尚学路西侧，汇集了全国各地的特色美食与实惠膳食，是学生日常用餐和生活交流的主要生活服务场所。', locationText: '尚学路西侧', mapX: 33.8, mapY: 47.17, imageUrl: 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80' },
  { id: 9, code: 'L09', name: '第二饭堂', englishName: 'The Second Dining Hall', type: '生活服务', summary: '东区师生自选精致餐饮中心，兼备休闲社交空间。', description: '第二饭堂邻近东二门附近，与第一饭堂同属生活服务类建筑，内部设有现代化的自选餐厅与休闲卡座，主打精品小吃和社交就餐，为东区师生提供高品质膳食体验。', locationText: '东二门附近', mapX: 37.96, mapY: 21.84, imageUrl: 'https://images.unsplash.com/photo-1578474846511-04ba529f0b88?auto=format&fit=crop&w=1200&q=80' },
  { id: 10, code: 'L10', name: '中心酒店', englishName: 'Hotel', type: '建筑', summary: '校内接待与住宿场所，设施齐全服务高档。', description: '中心酒店位于北门内侧，主要用于校内接待和住宿服务，大厅宽敞，周边绿化环抱，为来访专家和宾客提供舒适静谧的居住环境。', locationText: '北门内侧', mapX: 62.28, mapY: 10.12, imageUrl: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80' }
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
const uploadIdempotencyKey = ref('')
const loading = ref(false)
const polling = ref(false)
const error = ref('')
const jobState = reactive({
  jobId: '',
  jobToken: '',
  searchRecordId: null,
  status: '',
  attemptCount: 0,
  startedAt: 0
})
const initialView = new URLSearchParams(window.location.search).get('view')
const availableViews = ['results', 'map', 'feedback', 'history', 'checkins', 'auth', 'admin']
const activeView = ref(availableViews.includes(initialView) ? initialView : 'results')
isSidebarCollapsed.value = (activeView.value === 'auth')
const feedbackMessage = ref('')
const authMessage = ref('')
const authError = ref(false)
const searchMeta = reactive({
  searchRecordId: 1,
  uploadImageUrl: '',
  lowConfidence: false,
  message: '尚未上传图片',
  status: ''
})
const feedback = reactive({
  searchRecordId: 1,
  predictedLandmarkId: 1,
  confirmedLandmarkId: 1,
  feedbackType: 'correct',
  comment: ''
})
const authMode = ref('login')
const authForm = reactive({
  username: '',
  password: '',
  email: ''
})
const currentUser = ref(loadStoredUser())
const guestId = ref(loadGuestId())
const adminSearchRecords = ref([])
const adminFeedbackRecords = ref([])
const selectedFeedbackDetail = ref(null)
const userSearchRecords = ref([])
const historyLoading = ref(false)
const jobStatusLabel = computed(() => ({
  queued: '任务排队中...',
  processing: '任务识别中...',
  failed: '检索失败'
}[jobState.status] || '提交任务中...'))
const jobPending = computed(() => Boolean(jobState.jobId) && ['queued', 'processing'].includes(jobState.status))
const feedbackEligible = computed(() => ['success', 'low_confidence'].includes(searchMeta.status))
const checkIns = ref([])
const activeResultIndex = ref(0)
const preferences = reactive(loadPreferences())

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
const isAdmin = computed(() => currentUser.value?.role === 'admin')
const shellClasses = computed(() => ({
  'sidebar-collapsed': isSidebarCollapsed.value,
  [`theme-${preferences.theme}`]: true,
  [`backdrop-${preferences.backdrop}`]: true
}))
const viewTitle = computed(() => ({
  results: t('图片检索结果', 'Image Search Results'),
  map: t('校园地图导览', 'Campus Map'),
  feedback: t('用户反馈纠错', 'Feedback'),
  history: t('个人历史记录', 'My History'),
  checkins: t('打卡留言板', 'Check-in Board'),
  auth: authMode.value === 'login' ? t('用户登录', 'Login') : t('用户注册', 'Register'),
  admin: t('管理员后台', 'Admin Console')
}[activeView.value]))
const displayedResults = computed(() => {
  if (!results.value.length) return []
  return results.value.map((_, index) => results.value[(activeResultIndex.value + index) % results.value.length])
})
const preferenceLabels = computed(() => ({
  theme: t('主题', 'Theme'),
  backdrop: t('背景', 'Backdrop'),
  language: t('语言', 'Language')
}))
const themeLabel = computed(() => ({ dark: '曜黑', light: '明亮', contrast: '高对比' }[preferences.theme]))
const backdropLabel = computed(() => ({ aurora: '极光', map: '地图', plain: '纯净' }[preferences.backdrop]))
const languageLabel = computed(() => preferences.language === 'zh' ? '中文' : 'EN')
const historyLabels = computed(() => ({
  title: t('个人历史记录', 'My Search History'),
  refresh: t('刷新', 'Refresh'),
  needLogin: t('需要登录', 'Login required'),
  needLoginDesc: t('登录后可查看自己的服务端检索记录。', 'Sign in to view server-side records.'),
  loading: t('正在读取历史记录...', 'Loading history...'),
  empty: t('暂无历史记录', 'No records'),
  emptyDesc: t('上传图片后，记录会出现在这里。', 'Upload an image and records will appear here.'),
  noCandidate: t('无候选', 'No candidate'),
  noMessage: t('无提示信息', 'No message'),
  score: t('匹配分', 'Score')
}))
const checkInLabels = computed(() => ({
  title: t('校园打卡留言板', 'Campus Check-in Board'),
  refresh: t('刷新', 'Refresh'),
  landmark: t('打卡地点', 'Landmark'),
  message: t('留言内容', 'Message'),
  placeholder: t('记录当前地标的观察、路线提醒或拍照建议', 'Share an observation, route note, or photo tip'),
  submit: t('发布打卡', 'Post'),
  empty: t('暂无留言', 'No posts yet'),
  emptyDesc: t('选择地标并发布第一条打卡留言。', 'Choose a landmark and post the first note.'),
  like: t('点赞', 'Like'),
  liked: t('已赞', 'Liked'),
  reply: t('回复', 'Reply'),
  replyPlaceholder: t('写一条一级回复', 'Write a reply'),
  replyCount: t('回复', 'Replies')
}))

function confidenceLabel(value) {
  return {
    high: '高匹配',
    medium: '中匹配',
    low: '低匹配'
  }[value] || '待核验'
}

onMounted(async () => {
  // 检查是否首次进入以弹出欢迎界面
  const dismissed = localStorage.getItem('campuslens.welcomeDismissed')
  if (!dismissed) {
    welcomePoem.value = poems[Math.floor(Math.random() * poems.length)]
    const today = new Date()
    const dateStr = today.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
    const weekdayStr = today.toLocaleDateString('zh-CN', { weekday: 'long' })
    welcomeDate.value.solar = `${dateStr}  ${weekdayStr}`
    welcomeDate.value.lunar = getLunarDateString(today)
    showWelcomeModal.value = true
  }

  try {
    const response = await fetch('/api/landmarks')
    if (response.ok) {
      const data = await response.json()
      landmarks.value = data.map((item, index) => ({
        ...item,
        imageUrl: imageForLandmark(item, index)
      }))
      results.value = landmarks.value.slice(0, 5).map((item, index) => ({
        ...item,
        rank: index + 1,
        landmarkId: item.id,
        landmarkCode: item.code,
        score: [0.92, 0.87, 0.81, 0.76, 0.71][index]
      }))
    }
  } catch {
    landmarks.value = demoLandmarks
  }

  restoreSearchJob()
})

function dismissWelcome() {
  localStorage.setItem('campuslens.welcomeDismissed', 'true')
  showWelcomeModal.value = false
}

function onFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
  uploadIdempotencyKey.value = selectedFile.value ? crypto.randomUUID() : ''
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
  form.append('guestId', guestId.value)
  try {
    if (!uploadIdempotencyKey.value) uploadIdempotencyKey.value = crypto.randomUUID()
    const response = await fetch('/api/search/upload', {
      method: 'POST',
      headers: {
        ...authHeaders(),
        'Idempotency-Key': uploadIdempotencyKey.value
      },
      body: form
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      if (response.status === 429) {
        const retryAfter = response.headers.get('Retry-After') || '5'
        throw new Error(`${body.message || '检索队列已满'}，请在 ${retryAfter} 秒后重试`)
      }
      if (response.status === 503) {
        throw new Error(body.message || '任务队列暂不可用，请稍后重试')
      }
      throw new Error(body.message || '检索请求失败')
    }
    const data = await response.json()
    uploadIdempotencyKey.value = ''
    Object.assign(jobState, {
      jobId: data.jobId,
      jobToken: data.jobToken,
      searchRecordId: data.searchRecordId,
      status: data.status,
      attemptCount: 0,
      startedAt: Date.now()
    })
    persistSearchJob()
    searchMeta.searchRecordId = data.searchRecordId
    searchMeta.status = data.status
    searchMeta.message = '任务已进入检索队列'
    navigateToView('results')
    await pollSearchJob()
  } catch (err) {
    error.value = err.message
    searchMeta.lowConfidence = true
    searchMeta.message = err.message
    loading.value = false
  }
}

function applySearchResult(data) {
    results.value = (data.results || []).map((item) => {
      const matched = landmarks.value.find(l => l.id === item.landmarkId)
      let imgUrl = item.coverImageUrl || item.cover_image_url || item.imageUrl
      if (!imgUrl || (!imgUrl.startsWith('http') && !imgUrl.startsWith('data:'))) {
        imgUrl = matched?.imageUrl || demoLandmarks[0].imageUrl
      }
      return {
        ...item,
        imageUrl: imgUrl,
        confidenceLevel: item.confidenceLevel || 'low',
        score: Math.max(0, Math.min(1, Number(item.score || 0)))
      }
    })
    saveGuestId(data.guestId)
    searchMeta.searchRecordId = data.searchRecordId
    searchMeta.uploadImageUrl = data.uploadImageUrl || ''
    searchMeta.lowConfidence = Boolean(data.lowConfidence)
    searchMeta.message = data.message || '检索完成'
    searchMeta.status = data.status
    feedback.searchRecordId = data.searchRecordId
    activeResultIndex.value = 0
    
    navigateToView('results')
    selectedId.value = results.value[0]?.landmarkId || 1
}

async function pollSearchJob() {
  if (polling.value) return
  polling.value = true
  try {
    while (jobState.jobId && ['queued', 'processing'].includes(jobState.status)) {
      const elapsed = Date.now() - jobState.startedAt
      const delay = elapsed < 10000 ? 1000 : elapsed < 30000 ? 2000 : 5000
      await new Promise(resolve => setTimeout(resolve, delay))
      const headers = { ...authHeaders() }
      if (!currentUser.value && jobState.jobToken) {
        headers['X-Search-Job-Token'] = jobState.jobToken
      }
      try {
        const response = await fetch(`/api/search/jobs/${jobState.jobId}`, { headers })
        if (!response.ok) {
          const body = await response.json().catch(() => ({}))
          throw new Error(body.message || '查询任务状态失败')
        }
        const data = await response.json()
        jobState.status = data.status
        searchMeta.status = data.status
        jobState.attemptCount = data.attemptCount || 0
        persistSearchJob()
        if (data.status === 'success' || data.status === 'low_confidence') {
          applySearchResult(data)
          clearSearchJob()
          loading.value = false
        } else if (data.status === 'failed') {
          error.value = data.message || '检索任务失败'
          searchMeta.message = error.value
          searchMeta.lowConfidence = true
          clearSearchJob()
          loading.value = false
        }
      } catch (err) {
        error.value = `${err.message}，任务仍在服务端运行，可刷新页面后继续查看`
        loading.value = false
        return
      }
    }
  } finally {
    polling.value = false
  }
}

async function resumeSearchJob() {
  if (!jobPending.value || loading.value) return
  error.value = ''
  loading.value = true
  await pollSearchJob()
}

function persistSearchJob() {
  localStorage.setItem('campuslens.pendingSearchJob', JSON.stringify(jobState))
}

function clearSearchJob() {
  localStorage.removeItem('campuslens.pendingSearchJob')
  jobState.jobId = ''
  jobState.jobToken = ''
}

function restoreSearchJob() {
  try {
    const saved = JSON.parse(localStorage.getItem('campuslens.pendingSearchJob') || 'null')
    if (saved?.jobId) {
      Object.assign(jobState, saved, { startedAt: saved.startedAt || Date.now() })
      loading.value = true
      pollSearchJob()
    }
  } catch {
    localStorage.removeItem('campuslens.pendingSearchJob')
  }
}

function selectLandmark(id) {
  selectedId.value = id
  navigateToView('results')
}

function selectMapLandmark(id) {
  selectedId.value = id
}

async function openModal(landmark) {
  selectedId.value = landmark.landmarkId || landmark.id
  modalLandmark.value = landmark
  showModal.value = true
  try {
    const response = await fetch(`/api/landmarks/${selectedId.value}`)
    if (response.ok) {
      const data = await response.json()
      modalLandmark.value = {
        ...data,
        imageUrl: imageForLandmark(data, landmarks.value.findIndex(item => item.id === data.id)) || landmark.imageUrl
      }
    }
  } catch {
    modalLandmark.value = landmark
  }
  window.addEventListener('keydown', handleEsc)
}

function jumpModalToMap() {
  closeModal()
  navigateToView('map')
}

function openFeedback(item) {
  if (!feedbackEligible.value) {
    error.value = '只有成功或低置信度检索任务允许提交反馈'
    return
  }
  feedback.predictedLandmarkId = item.landmarkId
  feedback.confirmedLandmarkId = item.landmarkId
  feedback.feedbackType = 'correct'
  feedback.comment = ''
  navigateToView('feedback')
}

async function submitFeedback() {
  feedbackMessage.value = ''
  if (!feedbackEligible.value) {
    feedbackMessage.value = '只有成功或低置信度检索任务允许提交反馈'
    return
  }
  const payload = { ...feedback, guestId: guestId.value }
  try {
    const response = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
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

function clearAuth() {
  authForm.username = ''
  authForm.password = ''
  authForm.email = ''
  authMessage.value = ''
  authError.value = false
}

function openAuth() {
  authMode.value = 'login'
  clearAuth()
  navigateToView('auth')
}

function switchAuthMode(mode) {
  authMode.value = mode
  clearAuth()
}

watch(activeView, (newView) => {
  if (newView === 'auth') {
    clearAuth()
    isSidebarCollapsed.value = true
  } else {
    isSidebarCollapsed.value = false
  }
})


function updateAuthForm(nextForm) {
  authForm.username = nextForm.username
  authForm.password = nextForm.password
  authForm.email = nextForm.email
}

async function submitAuth() {
  authMessage.value = ''
  authError.value = false
  try {
    const endpoint = authMode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const payload = {
      username: authForm.username,
      password: authForm.password
    }
    if (authMode.value === 'register' && authForm.email) {
      payload.email = authForm.email
    }
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.message || (authMode.value === 'login' ? '登录失败' : '注册失败'))
    }
    const data = await response.json()
    currentUser.value = data
    localStorage.setItem('campuslens.currentUser', JSON.stringify(data))
    authMessage.value = data.message
    authForm.password = ''
    if (data.admin) {
      await loadAdminData()
      navigateToView('admin')
      return
    }
    navigateToView('results')
  } catch (err) {
    authError.value = true
    authMessage.value = err.message
  }
}

function handleLogout() {
  currentUser.value = null
  localStorage.removeItem('campuslens.currentUser')
  authMessage.value = ''
  authError.value = false
  authForm.password = ''
  if (activeView.value === 'admin' || activeView.value === 'auth') {
    navigateToView('results')
  }
}

async function openAdmin() {
  if (isAdmin.value) {
    await loadAdminData()
    navigateToView('admin')
    return
  }
  openAuth()
}

async function loadAdminData() {
  const headers = authHeaders()
  const [recordsResponse, feedbackResponse] = await Promise.all([
    fetch('/api/admin/search-records', { headers }),
    fetch('/api/admin/feedback', { headers })
  ])
  if (recordsResponse.ok) {
    adminSearchRecords.value = await recordsResponse.json()
  }
  if (feedbackResponse.ok) {
    adminFeedbackRecords.value = await feedbackResponse.json()
  }
  if (selectedFeedbackDetail.value?.id) {
    await loadFeedbackDetail(selectedFeedbackDetail.value.id)
  }
}

async function updateFeedbackStatus(id, status) {
  const response = await fetch(`/api/admin/feedback/${id}/status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ status })
  })
  if (response.ok) {
    await loadAdminData()
    await loadFeedbackDetail(id)
  }
}

async function loadFeedbackDetail(id) {
  const response = await fetch(`/api/admin/feedback/${id}`, { headers: authHeaders() })
  if (response.ok) {
    selectedFeedbackDetail.value = await response.json()
  }
}

async function openHistory() {
  if (!currentUser.value) {
    openAuth()
    return
  }
  await loadUserHistory()
  navigateToView('history')
}

async function loadUserHistory() {
  if (!currentUser.value) return
  historyLoading.value = true
  try {
    const response = await fetch('/api/me/search-records?limit=20', { headers: authHeaders() })
    if (response.ok) {
      userSearchRecords.value = await response.json()
    }
  } finally {
    historyLoading.value = false
  }
}

function openHistoryFeedback(record, item) {
  if (!['success', 'low_confidence'].includes(record.status)) return
  searchMeta.searchRecordId = record.id
  searchMeta.status = record.status
  searchMeta.uploadImageUrl = record.uploadImageUrl || ''
  feedback.searchRecordId = record.id
  feedback.predictedLandmarkId = item.landmarkId
  feedback.confirmedLandmarkId = item.landmarkId
  feedback.feedbackType = 'correct'
  feedback.comment = ''
  results.value = record.topResults || []
  selectedId.value = item.landmarkId
  navigateToView('feedback')
}

async function openCheckIns() {
  await loadCheckIns()
  navigateToView('checkins')
}

async function loadCheckIns() {
  const response = await fetch(`/api/check-ins?limit=50&guestId=${encodeURIComponent(guestId.value)}`, { headers: authHeaders() })
  if (response.ok) {
    checkIns.value = await response.json()
  }
}

async function createCheckIn(payload) {
  const response = await fetch('/api/check-ins', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ ...payload, guestId: guestId.value })
  })
  if (response.ok) {
    await loadCheckIns()
  }
}

async function toggleCheckInLike(item) {
  const response = await fetch(`/api/check-ins/${item.id}/like?guestId=${encodeURIComponent(guestId.value)}`, {
    method: 'POST',
    headers: authHeaders()
  })
  if (response.ok) {
    await loadCheckIns()
  }
}

async function replyCheckIn(item, message) {
  const response = await fetch(`/api/check-ins/${item.id}/replies`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ message, guestId: guestId.value })
  })
  if (response.ok) {
    await loadCheckIns()
  }
}

function cycleResults(step) {
  if (!results.value.length) return
  activeResultIndex.value = (activeResultIndex.value + step + results.value.length) % results.value.length
  selectedId.value = results.value[activeResultIndex.value]?.landmarkId || selectedId.value
}

function cycleTheme() {
  const values = ['dark', 'light', 'contrast']
  preferences.theme = values[(values.indexOf(preferences.theme) + 1) % values.length]
  savePreferences()
}

function cycleBackdrop() {
  const values = ['aurora', 'map', 'plain']
  preferences.backdrop = values[(values.indexOf(preferences.backdrop) + 1) % values.length]
  savePreferences()
}

function toggleLanguage() {
  preferences.language = preferences.language === 'zh' ? 'en' : 'zh'
  savePreferences()
}

function t(zh, en) {
  return preferences.language === 'zh' ? zh : en
}

function imageForLandmark(item, index = 0) {
  const candidate = item?.coverImageUrl || item?.cover_image_url || item?.imageUrl || item?.images?.[0]?.imageUrl
  if (candidate && (candidate.startsWith('http') || candidate.startsWith('data:'))) {
    return candidate
  }
  const idx = index >= 0 ? index : 0
  return demoLandmarks[idx]?.imageUrl || demoLandmarks[0].imageUrl
}

function authHeaders() {
  return currentUser.value?.token ? { Authorization: `Bearer ${currentUser.value.token}` } : {}
}

function loadStoredUser() {
  try {
    const raw = localStorage.getItem('campuslens.currentUser')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function loadPreferences() {
  try {
    return {
      theme: 'dark',
      backdrop: 'aurora',
      language: 'zh',
      ...(JSON.parse(localStorage.getItem('campuslens.preferences') || '{}'))
    }
  } catch {
    return { theme: 'dark', backdrop: 'aurora', language: 'zh' }
  }
}

function savePreferences() {
  localStorage.setItem('campuslens.preferences', JSON.stringify(preferences))
}

function loadGuestId() {
  const key = 'campuslens.guestId'
  const existing = localStorage.getItem(key)
  if (/^guest#[1-9]\d*$/.test(existing || '')) {
    return existing
  }
  const generated = `guest#${Date.now()}${Math.floor(100 + Math.random() * 900)}`
  localStorage.setItem(key, generated)
  return generated
}

function saveGuestId(value) {
  if (/^guest#[1-9]\d*$/.test(value || '')) {
    guestId.value = value
    localStorage.setItem('campuslens.guestId', value)
  }
}
</script>
