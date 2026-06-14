<template>
  <main class="app-shell" :class="shellClasses">
    <aside class="side-panel" :class="{ 'collapsed': isSidebarCollapsed }">
      <div class="brand-header">
        <div class="brand-logo">
          <img src="/logo.png" alt="CampusLens" style="width: 100%; height: 100%; border-radius: 50%; object-fit: cover;" />
        </div>
        <div>
          <p class="eyebrow">CampusLens</p>
          <h1>{{ t('校园慧眼', 'CampusLens') }}</h1>
        </div>
      </div>
      <p class="intro">{{ t('上传校园照片，查看 Top-5 地标、位置标注和反馈入口。', 'Upload campus photos to view Top-5 landmarks, map markers, and feedback.') }}</p>

      <form class="upload-panel" @submit.prevent="submitSearch">
        <label class="file-drop" :class="{ active: selectedFile }">
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="onFileChange" />
          <span class="file-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
          </span>
          <strong>{{ selectedFile ? selectedFile.name : t('选择校园照片', 'Choose Campus Photo') }}</strong>
          <small>{{ t('支持 JPG、PNG、WebP，不超过 8MB', 'Supports JPG, PNG, WebP up to 8MB') }}</small>
        </label>
        <label class="sar-mode-toggle">
          <input v-model="sarMode" type="checkbox" />
          <span><strong>{{ t('SAR 深度思考', 'SAR Deep Thinking') }}</strong><small>{{ t('持续适应测试域，耗时更长', 'Persistent adaptation; slower') }}</small></span>
        </label>
        <button class="primary-btn" :disabled="loading || jobPending" :class="{ 'is-loading': loading }">
          <span class="btn-text">{{ loading ? jobStatusLabel : t('上传并检索', 'Upload & Search') }}</span>
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
          {{ t('Top-5 结果', 'Top-5 Results') }}
        </button>
        <button :class="{ active: activeView === 'map' }" @click="changeView('map')">
          <span class="active-line"></span>
          {{ t('地图导览', 'Map Tour') }}
        </button>
        <button :class="{ active: activeView === 'feedback' }" @click="changeView('feedback')">
          <span class="active-line"></span>
          {{ t('反馈纠错', 'Feedback') }}
        </button>
        <button :class="{ active: activeView === 'history' }" @click="openHistory">
          <span class="active-line"></span>
          {{ t('个人历史', 'My History') }}
        </button>
        <button :class="{ active: activeView === 'checkins' }" @click="openCheckIns">
          <span class="active-line"></span>
          {{ t('打卡留言', 'Check-in Board') }}
        </button>
        <button v-if="isAdmin" :class="{ active: activeView === 'admin' }" @click="openAdmin">
          <span class="active-line"></span>
          {{ t('管理后台', 'Admin Panel') }}
        </button>
      </nav>
    </aside>

    <section class="content-area">
      <header class="top-bar">
        <div class="navigator-header">
          <div class="history-navigator">
            <button type="button" @click="goBack" :disabled="historyIndex === 0" :title="t('返回上一步', 'Go Back')">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
            </button>
            <button type="button" @click="goForward" :disabled="historyIndex === viewHistory.length - 1" :title="t('进入下一步', 'Go Forward')">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
            </button>
          </div>
          <div>
            <p class="eyebrow">V3 {{ t('构造阶段', 'Release v3') }}</p>
            <h2>{{ viewTitle }}</h2>
          </div>
        </div>
        <div class="top-actions-container">
          <div class="top-bar-login">
            <template v-if="!currentUser">
              <button type="button" class="top-login-trigger-btn" @click="openAuth">
                {{ t('登录/注册', 'Login / Register') }}
              </button>
            </template>
            <template v-else>
              <div class="top-admin-status">
                <span class="admin-badge">{{ isAdmin ? 'Admin' : 'User' }}</span>
                <span class="admin-username">{{ currentUser.username }}</span>
                <button v-if="isAdmin" type="button" class="top-action-btn" @click="openAdmin">{{ t('进入后台', 'Console') }}</button>
                <button type="button" class="top-action-btn logout" @click="handleLogout">{{ t('退出', 'Logout') }}</button>
              </div>
            </template>
          </div>
          <PreferencesDock
            :theme-label="themeLabel"
            :language-label="languageLabel"
            :labels="preferenceLabels"
            @cycle-theme="cycleTheme"
            @toggle-language="toggleLanguage"
          />
        </div>
      </header>

      <Transition name="page-fade" mode="out-in">
        <section v-if="activeView === 'results'" class="results-layout" key="results">
          <div class="results-main">
            <div class="ai-analytical-header">
              <div class="stat-card">
                <span class="stat-label">{{ t('检索记录', 'Record ID') }}</span>
                <span class="stat-value">#{{ searchMeta.searchRecordId }}</span>
                <span class="stat-desc">{{ searchMeta.uploadImageUrl ? t('已保存上传图片', 'Uploaded image saved') : t('等待上传校园照片', 'Awaiting campus photo') }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">{{ t('候选数量', 'Candidates') }}</span>
                <span class="stat-value">{{ results.length }}</span>
                <span class="stat-desc">{{ t('后端返回的 Top-5 候选地标', 'Top-5 landmark recommendations') }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">{{ t('检索状态', 'Search Status') }}</span>
                <span class="stat-value">{{ searchMeta.lowConfidence ? t('需核验', 'Verify') : t('可展示', 'Available') }}</span>
                <span class="stat-desc">{{ searchMeta.message }}</span>
              </div>
            </div>

            <p v-if="searchMeta.lowConfidence" class="warning-text">
              {{ searchMeta.message }}
            </p>
            <p v-if="searchMeta.modelVersion" class="sar-result-meta">
              {{ t('SAR更新', 'SAR update') }}：{{ searchMeta.sarApplied ? t('已参与', 'Applied') : t('未参与', 'Skipped') }} ·
              {{ t('信任等级', 'Trust') }}：{{ searchMeta.trustLevel || '-' }} · {{ searchMeta.modelVersion }}
            </p>
            <div v-if="jobState.status === 'queued' || jobState.status === 'processing'" class="job-progress" role="status">
              <div>
                <strong>{{ jobStatusLabel }}</strong>
                <span>{{ t('任务', 'Task') }} #{{ jobState.searchRecordId }} · {{ t('已尝试', 'Attempt') }} {{ jobState.attemptCount }} {{ t('次', 'times') }}</span>
              </div>
              <button v-if="!loading" type="button" class="ghost-btn" @click="resumeSearchJob">{{ t('继续查询', 'Resume') }}</button>
            </div>


            <div 
              v-if="results.length" 
              class="result-carousel-container" 
              @wheel.prevent="cycleResults($event.deltaY > 0 ? 1 : -1)"
              @mousedown="handleDragStart"
              @mouseup="handleDragEnd"
              @mouseleave="handleDragEnd"
              @touchstart="handleTouchStart"
              @touchend="handleTouchEnd"
            >
              <div 
                v-for="(item, index) in localizedResults" 
                :key="item.landmarkId" 
                class="result-card-wrapper" 
                :style="getCardStyle(index)"
                @click="selectLandmarkFromCarousel(item.landmarkId, index)"
              >
                <div class="result-card" :class="{ active: item.landmarkId === selectedId }">
                  <div class="rank">{{ item.rank }}</div>
                  <div class="result-body">
                    <div class="result-heading">
                      <div>
                        <h3>{{ item.name }}</h3>
                        <p>{{ item.englishName }} · {{ item.locationText }}</p>
                      </div>
                      <strong>{{ t('匹配分', 'Score') }} {{ Math.round(item.score * 100) }}%</strong>
                    </div>
                    <p>{{ item.summary }}</p>
                    
                    <div class="confidence-container">
                      <div class="confidence-bar-bg">
                        <div class="confidence-bar-fill" :style="{ width: Math.round(item.score * 100) + '%' }"></div>
                      </div>
                    </div>

                    <div class="result-actions">
                      <span class="result-meta">{{ confidenceLabel(item.confidenceLevel) }}<template v-if="item.mahalanobisDistance != null"> · D={{ Number(item.mahalanobisDistance).toFixed(2) }}</template></span>
                      <button @click.stop="openModal(item)">{{ t('详情', 'Detail') }}</button>
                      <button :disabled="!feedbackEligible" @click.stop="openFeedback(item)">{{ t('反馈', 'Feedback') }}</button>
                    </div>
                  </div>
                </div>
                <div v-if="((index - activeResultIndex + results.length) % results.length) !== 0" class="carousel-click-shield"></div>
              </div>
            </div>
            <article v-else class="empty-state">
              <h3>{{ t('暂无检索结果', 'No Search Results') }}</h3>
              <p>{{ t('请上传 JPG、PNG 或 WebP 图片后重新检索。', 'Please upload a JPG, PNG, or WebP photo to search.') }}</p>
            </article>
          </div>

          <article class="detail-panel" v-if="localizedSelectedLandmark">
            <p class="eyebrow">{{ localizedSelectedLandmark.code }}</p>
            <h3>{{ localizedSelectedLandmark.name }}</h3>
            <p class="detail-location">{{ localizedSelectedLandmark.locationText }}</p>
            <p>{{ localizedSelectedLandmark.description }}</p>
            <div class="detail-grid">
              <span>{{ t('类型', 'Type') }}</span><strong>{{ localizedSelectedLandmark.type }}</strong>
              <span>{{ t('地图坐标', 'Map Coordinates') }}</span><strong>{{ localizedSelectedLandmark.mapX }}%, {{ localizedSelectedLandmark.mapY }}%</strong>
              <span>{{ t('推荐打卡点', 'Recommended Spot') }}</span><strong>{{ t('正门口 / 广场旁', 'Main Entrance / Plaza') }}</strong>
            </div>
            <button type="button" class="secondary-btn inline-action" @click="changeView('map')">{{ t('查看地图位置', 'Show on Map') }}</button>
          </article>
        </section>

        <section v-else-if="activeView === 'map'" class="map-layout" key="map">
          <div class="map-board">
            <div class="map-controls">
              <button type="button" @click="zoomIn" :title="t('放大', 'Zoom In')">
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              </button>
              <span class="zoom-indicator">{{ Math.round(zoomScale * 100) }}%</span>
              <button type="button" @click="zoomOut" :title="t('缩小', 'Zoom Out')">
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line></svg>
              </button>
              <button type="button" @click="zoomReset" :title="t('重置', 'Reset')">
                <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M23 4v6h-6"></path><path d="M1 20v-6h6"></path><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
              </button>
            </div>
            
            <div 
              class="map-viewport" 
              :class="{ 'is-zoomed': zoomScale > 1.0 }"
              ref="mapViewport"
              @mousedown="startDrag"
              @mousemove="onDrag"
              @mouseup="endDrag"
              @mouseleave="endDrag"
            >
              <div 
                class="map-container"
                :style="{ transform: `scale(${zoomScale})` }"
              >
                <img src="/campus-map.png" alt="校园平面图" class="map-img" />
                <button
                  v-for="item in localizedLandmarks"
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
            <h3>{{ t('静态地图标注', 'Static Map Annotation') }}</h3>
            <p>{{ t('基于校园平面图百分比坐标标注，后续可替换为更精确的 GIS 或室内导览数据。', 'Marked based on the coordinates of the campus map. These can be replaced with more precise GIS or indoor navigation data in the future.') }}</p>
            <div class="legend-list">
              <button
                v-for="item in localizedLandmarks"
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
            <aside class="history-panel">
              <h3>{{ t('本次检索记录', 'Current Search Record') }}</h3>
              <p class="panel-desc">{{ t('反馈将绑定当前 searchRecordId 和候选地标编号，第二周保留 pending 状态。', 'Feedback will be bound to searchRecordId and candidate code, remaining in pending status for review.') }}</p>
              
              <div class="history-list">
                <div class="history-item">
                  <div class="history-meta">
                    <span class="history-time">{{ t('检索记录', 'Search Record') }} #{{ feedback.searchRecordId }}</span>
                    <span class="history-status active">{{ t('待反馈', 'Pending') }}</span>
                  </div>
                  <strong>{{ localizedSelectedLandmark?.code }} {{ localizedSelectedLandmark?.name }}</strong>
                  <p>{{ t('预测地标 ID', 'Predicted ID') }}：{{ feedback.predictedLandmarkId }} · {{ t('反馈类型', 'Feedback Type') }}：{{ feedbackTypeLabel(feedback.feedbackType) }}</p>
                </div>
                <div v-for="item in localizedResults.slice(0, 3)" :key="item.landmarkCode" class="history-item">
                  <div class="history-meta">
                    <span class="history-time">{{ t('候选', 'Candidate') }} #{{ item.rank }}</span>
                    <span class="history-status" :class="{ active: item.confidenceLevel !== 'low', wrong: item.confidenceLevel === 'low' }">{{ confidenceLabel(item.confidenceLevel) }}</span>
                  </div>
                  <strong>{{ item.landmarkCode }} {{ item.name }}</strong>
                  <p>{{ t('匹配分', 'Score') }}：{{ Math.round(item.score * 100) }}%<template v-if="item.mahalanobisDistance != null"> · {{ t('马氏距离', 'M-dist') }}：{{ Number(item.mahalanobisDistance).toFixed(2) }}</template></p>
                </div>
              </div>
            </aside>

            <article class="feedback-card">
              <h3>{{ t('提交反馈纠错', 'Submit Feedback') }}</h3>
              <p>{{ t('当前阶段记录反馈入口和字段衔接，审核、采纳和统计在后续迭代完善。', 'In this phase, we record the feedback and match the fields. Audit, adoption, and analytics will be added in future updates.') }}</p>
              <form @submit.prevent="submitFeedback">
                <label>
                  {{ t('反馈类型', 'Feedback Type') }}
                  <select v-model="feedback.feedbackType">
                    <option value="correct">{{ t('识别正确', 'Correct') }}</option>
                    <option value="wrong">{{ t('识别错误', 'Incorrect') }}</option>
                    <option value="uncertain">{{ t('不确定', 'Uncertain') }}</option>
                  </select>
                </label>
                <label>
                  {{ t('正确地标', 'Correct Landmark') }}
                  <select v-model.number="feedback.confirmedLandmarkId">
                    <option :value="null">{{ t('暂不选择', 'Not selected') }}</option>
                    <option v-for="item in localizedLandmarks" :key="item.id" :value="item.id">{{ item.name }}</option>
                  </select>
                </label>
                <label>
                  {{ t('补充说明', 'Additional Info') }}
                  <textarea v-model="feedback.comment" rows="4" :placeholder="t('例如：实际是学术大讲堂入口', 'e.g. Actually the entrance of Academic Auditorium')"></textarea>
                </label>
                <button class="primary-btn" :disabled="feedbackSubmitting" :class="{ 'is-loading': feedbackSubmitting }">
                  <span class="btn-text">{{ feedbackSubmitting ? t('提交中...', 'Submitting...') : t('提交反馈', 'Submit') }}</span>
                  <span class="btn-shine"></span>
                </button>
                <p v-if="feedbackMessage" class="success-text">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                  {{ feedbackMessage }}
                </p>
                <p v-if="feedbackError" class="error-text">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
                  {{ feedbackError }}
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
          :labels="authLabels"
          @switch-mode="switchAuthMode"
          @submit="submitAuth"
          @update-form="updateAuthForm"
        />

        <HistoryView
          v-else-if="activeView === 'history'"
          key="history"
          :current-user="currentUser"
          :records="localizedUserSearchRecords"
          :loading="historyLoading"
          :labels="historyLabels"
          :fallback-image="demoLandmarks[0].imageUrl"
          :language="preferences.language"
          @refresh="loadUserHistory"
          @open-feedback="openHistoryFeedback"
        />

        <CheckInBoard
          v-else-if="activeView === 'checkins'"
          key="checkins"
          :landmarks="localizedLandmarks"
          :items="localizedCheckIns"
          :labels="checkInLabels"
          :language="preferences.language"
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
          :search-records="localizedAdminSearchRecords"
          :feedback-records="localizedAdminFeedbackRecords"
          :selected-feedback-detail="localizedAdminFeedbackDetail"
          :runtime-status="adminRuntimeStatus"
          :rebuild-job="adminRebuildJob"
          :stats="adminStats"
          :labels="adminLabels"
          @refresh="loadAdminData"
          @update-status="updateFeedbackStatus"
          @view-detail="loadFeedbackDetail"
          @rebuild-index="startIndexRebuild"
        />
      </Transition>
    </section>

    <LandmarkModal
      :show="showModal"
      :landmark="localizedModalLandmark"
      :labels="modalLabels"
      @close="closeModal"
      @jump-map="jumpModalToMap"
    />
    <InteractiveBackground :theme="preferences.theme" />
    <button 
      type="button" 
      class="sidebar-toggle-btn"
      :class="isSidebarCollapsed ? 'collapsed' : 'expanded'"
      @click="isSidebarCollapsed = !isSidebarCollapsed"
      :title="isSidebarCollapsed ? t('展开侧边栏', 'Expand Sidebar') : t('收起侧边栏', 'Collapse Sidebar')"
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
            <h2 class="welcome-title">{{ t('欢迎使用 CampusLens 校园慧眼', 'Welcome to CampusLens') }}</h2>
            
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
              <span class="btn-text">{{ t('进入系统', 'Enter System') }}</span>
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
  { 
    id: 1, 
    code: 'L01', 
    name: '图书馆', 
    englishName: 'Library', 
    type: '建筑', 
    englishType: 'Building',
    summary: '校园核心文化与学术中心，拥有独特的书页外立面结构。', 
    englishSummary: 'The campus cultural and academic center, featuring a unique facade shaped like open book pages.',
    description: '图书馆位于文雍广场北侧，是学生自习、借阅和课程资料检索的主要场所。建筑气势宏伟，外立面呈半开卷书页状，是校园最具标志性的文化地标。', 
    englishDescription: 'Located north of Wenyong Square, the library is the main venue for self-study, borrowing, and information retrieval. The magnificent building features a unique facade shaped like open book pages, making it the most iconic cultural landmark on campus.',
    locationText: '文雍广场北侧', 
    englishLocation: 'North side of Wenyong Square',
    mapX: 50.31, 
    mapY: 59.33, 
    imageUrl: 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 2, 
    code: 'L02', 
    name: '学术大讲堂', 
    englishName: 'Academic Auditorium', 
    type: '建筑', 
    englishType: 'Building',
    summary: '举办大型学术报告与校园文娱盛典的多功能现代化场馆。', 
    englishSummary: 'A multifunctional modern venue for large academic reports and campus cultural celebrations.',
    description: '学术大讲堂邻近东门，是学校举办大型学术报告、文化盛典及师生集中教学活动的主阵地。其弧形入口极具现代感与辨识度。', 
    englishDescription: 'Situated near the East Gate, the Academic Auditorium is the primary venue for hosting academic lectures, grand cultural celebrations, and joint teaching sessions. Its curved entrance design is modern and highly recognizable.',
    locationText: '东门附近', 
    englishLocation: 'Near the East Gate',
    mapX: 62.16, 
    mapY: 61.22, 
    imageUrl: 'https://images.unsplash.com/photo-1492538368577-870624790c4a?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 3, 
    code: 'L03', 
    name: '文雍广场', 
    englishName: 'Wenyong Square', 
    type: '广场', 
    englishType: 'Square',
    summary: '开阔宽广的标志性休闲广场，是校园人文景观的核心纽带。', 
    englishSummary: 'A spacious and iconic recreational square, serving as the core hub of the campus cultural landscape.',
    description: '文雍广场坐落于图书馆南侧，是一座融绿化、喷泉与休闲步道于一体的开阔广场，为校园师生举行集会和课余小憩的重要集散地。', 
    englishDescription: 'Located south of the library, Wenyong Square is a spacious open area integrating green spaces, fountains, and leisure paths, serving as a primary gathering and resting place for faculty and students.',
    locationText: '图书馆南侧', 
    englishLocation: 'South side of the Library',
    mapX: 57.37, 
    mapY: 63.56, 
    imageUrl: 'https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 4, 
    code: 'L04', 
    name: '博学桥', 
    englishName: 'Boxue Bridge', 
    type: '桥梁', 
    englishType: 'Bridge',
    summary: '横跨韵湖的典雅观景石桥，连接南北主要功能园区。', 
    englishSummary: 'An elegant stone viewing bridge across Yun Lake, connecting the main northern and southern campus sectors.',
    description: '博学桥横跨在美丽的韵湖之上，将教学区与生活区优雅连通。桥身造型典雅，与湖面交相辉映，是备受师生喜爱的校园写意景观。', 
    englishDescription: 'Spanning the beautiful Yun Lake, Boxue Bridge elegantly links the teaching and residential areas. With its graceful design reflecting on the water, it is a highly popular picturesque landscape on campus.',
    locationText: '韵湖沿线', 
    englishLocation: 'Along Yun Lake',
    mapX: 57.75, 
    mapY: 46.17, 
    imageUrl: 'https://images.unsplash.com/photo-1549880338-65ddcdfd017b?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 5, 
    code: 'L05', 
    name: '琴湖及湖心岛', 
    englishName: 'Qin Lake / Huxin Island', 
    type: '湖区', 
    englishType: 'Lake Area',
    summary: '环境幽雅的水域景观，湖水碧绿，岛上植被常青。', 
    englishSummary: 'A serene lake area featuring emerald waters and evergreen island vegetation.',
    description: '琴湖及湖心岛位于文雍路东侧，水体清澈，绿化茂密。清晨和傍晚，这里烟波浩渺，是校园内最富有自然诗意和静谧之美的一隅。', 
    englishDescription: 'Located east of Wenyong Road, Qin Lake and Huxin Island feature crystal-clear water and dense greenery. With misty views at dawn and dusk, it is the most poetic and tranquil spot on campus.',
    locationText: '文雍路东侧', 
    englishLocation: 'East side of Wenyong Road',
    mapX: 67.32, 
    mapY: 26.88, 
    imageUrl: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 6, 
    code: 'L06', 
    name: '体育馆', 
    englishName: 'Stadium', 
    type: '场馆', 
    englishType: 'Gymnasium',
    summary: '配备多功能运动场地的现代化综合室内健身体育馆。', 
    englishSummary: 'A modern comprehensive indoor sports hall equipped with multifunctional fields.',
    description: '体育馆位于文雍路西侧，是一座设施完善的现代化多功能场馆，服务全校体育教学、文体赛事和日常锻炼，极极具动感的网架几何外形十分夺目。', 
    englishDescription: 'Located west of Wenyong Road, the Gymnasium is a well-equipped modern facility for physical education, sports events, and daily exercises. Its dynamic space frame geometry makes it highly striking.',
    locationText: '文雍路西侧', 
    englishLocation: 'West side of Wenyong Road',
    mapX: 43.88, 
    mapY: 49.07, 
    imageUrl: 'https://images.unsplash.com/photo-1577416412292-747c6607f055?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 7, 
    code: 'L07', 
    name: '游泳馆', 
    englishName: 'Natatorium', 
    type: '场馆', 
    englishType: 'Natatorium',
    summary: '配备先进循环系统的室内温水游泳馆。', 
    englishSummary: 'An indoor heated natatorium equipped with advanced water circulation systems.',
    description: '游泳馆位于体育馆北侧，配有标准泳道 and 水循环系统，是日常游泳教学、水上运动训练以及师生消暑运动的首选场馆。', 
    englishDescription: 'Located north of the Gymnasium, the Natatorium features standard lanes and water circulation systems, making it the preferred place for swimming classes, water training, and cooling off.',
    locationText: '体育馆北侧', 
    englishLocation: 'North side of the Gymnasium',
    mapX: 45.39, 
    mapY: 41.25, 
    imageUrl: 'https://images.unsplash.com/photo-1519766304817-4f37bda74a27?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 8, 
    code: 'L08', 
    name: '第一饭堂', 
    englishName: 'The First Dining Hall', 
    type: '生活服务', 
    englishType: 'Dining Services',
    summary: '大众膳食生活服务中心，提供多风味特色餐饮。', 
    englishSummary: 'A public dining service center offering various regional and local cuisines.',
    description: '第一饭堂位于尚学路西侧，汇集了全国各地的特色美食与实惠膳食，是学生日常用餐和生活交流的主要生活服务场所。', 
    englishDescription: 'Situated west of Shangxue Road, the First Dining Hall offers diverse local delicacies and affordable meals, serving as a primary hub for students\' daily dining and social activities.',
    locationText: '尚学路西侧', 
    englishLocation: 'West side of Shangxue Road',
    mapX: 33.8, 
    mapY: 47.17, 
    imageUrl: 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 9, 
    code: 'L09', 
    name: '第二饭堂', 
    englishName: 'The Second Dining Hall', 
    type: '生活服务', 
    englishType: 'Dining Services',
    summary: '东区师生自选精致餐饮中心，兼备休闲社交空间。', 
    englishSummary: 'A delicate cafeteria for East Campus students, combining dining and social spaces.',
    description: '第二饭堂邻近东二门附近，与第一饭堂同属生活服务类建筑，内部设有现代化的自选餐厅与休闲卡座，主打精品小吃和社交就餐，为东区师生提供高品质膳食体验。', 
    englishDescription: 'Located near the Second East Gate, the Second Dining Hall features self-serve counters and cozy seating. It focuses on premium snacks and social dining, providing high-quality meals for students and staff.',
    locationText: '东二门附近', 
    englishLocation: 'Near the Second East Gate',
    mapX: 37.96, 
    mapY: 21.84, 
    imageUrl: 'https://images.unsplash.com/photo-1578474846511-04ba529f0b88?auto=format&fit=crop&w=1200&q=80' 
  },
  { 
    id: 10, 
    code: 'L10', 
    name: '中心酒店', 
    englishName: 'Hotel', 
    type: '建筑', 
    englishType: 'Hotel',
    summary: '校内接待与住宿场所，设施齐全服务高档。', 
    englishSummary: 'A well-equipped guest house and hotel inside the campus.',
    description: '中心酒店位于北门内侧，主要用于校内接待和住宿服务，大厅宽敞，周边绿化环抱，为来访专家和宾客提供舒适静谧的居住环境。', 
    englishDescription: 'Located just inside the North Gate, the Center Hotel is used for university receptions and accommodation. It features a spacious lobby and green surroundings, offering visiting experts a quiet and comfortable stay.',
    locationText: '北门内侧', 
    englishLocation: 'Inside the North Gate',
    mapX: 62.28, 
    mapY: 10.12, 
    imageUrl: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80' 
  }
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
const sarMode = ref(false)
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
const feedbackError = ref('')
const feedbackSubmitting = ref(false)
const authMessage = ref('')
const authError = ref(false)
const searchMeta = reactive({
  searchRecordId: 1,
  uploadImageUrl: '',
  lowConfidence: false,
  message: '尚未上传图片',
  status: '',
  sarApplied: false,
  trustLevel: '',
  modelVersion: ''
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
const guestClientToken = loadGuestClientToken()
const guestId = ref(loadStoredGuestId())
let guestIdentityPromise = null
let guestIdentityVerified = false
const adminSearchRecords = ref([])
const adminFeedbackRecords = ref([])
const adminRuntimeStatus = ref(null)
const adminStats = ref(null)
const adminRebuildJob = ref(null)
const selectedFeedbackDetail = ref(null)
const userSearchRecords = ref([])
const historyLoading = ref(false)
const jobStatusLabel = computed(() => ({
  queued: t('任务排队中...', 'Job queued...'),
  processing: t('任务识别中...', 'Analyzing image...'),
  failed: t('检索失败', 'Search failed')
}[jobState.status] || t('提交任务中...', 'Submitting task...')))
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

function t(zh, en) {
  return preferences.language === 'zh' ? zh : en
}

function localizedLandmark(l) {
  if (!l) return null
  return {
    ...l,
    name: t(l.name, l.englishName || l.name),
    type: t(l.type, l.englishType || l.type),
    summary: t(l.summary, l.englishSummary || l.summary),
    description: t(l.description, l.englishDescription || l.description),
    locationText: t(l.locationText, l.englishLocation || l.locationText)
  }
}

const localizedLandmarks = computed(() => landmarks.value.map(localizedLandmark))

const localizedSelectedLandmark = computed(() => {
  const item = selectedLandmark.value
  return item ? localizedLandmark(item) : null
})

const localizedModalLandmark = computed(() => {
  return modalLandmark.value ? localizedLandmark(modalLandmark.value) : null
})

const localizedResults = computed(() => {
  if (!results.value.length) return []
  return results.value.map(item => {
    const matched = localizedLandmarks.value.find(l => l.id === item.landmarkId)
    return {
      ...item,
      name: matched?.name || item.name,
      englishName: matched?.englishName || item.englishName,
      locationText: matched?.locationText || item.locationText,
      summary: matched?.summary || item.summary,
      description: matched?.description || item.description,
      type: matched?.type || item.type
    }
  })
})

const displayedResults = computed(() => {
  if (!localizedResults.value.length) return []
  return localizedResults.value.map((_, index) => localizedResults.value[(activeResultIndex.value + index) % localizedResults.value.length])
})

const localizedAdminSearchRecords = computed(() => {
  return adminSearchRecords.value.map(record => {
    const matched = localizedLandmarks.value.find(l => l.name === record.bestLandmarkName || l.englishName === record.bestLandmarkName)
    return {
      ...record,
      bestLandmarkName: matched ? matched.name : record.bestLandmarkName
    }
  })
})

const localizedAdminFeedbackRecords = computed(() => {
  return adminFeedbackRecords.value.map(item => {
    const predMatched = localizedLandmarks.value.find(l => l.id === item.predictedLandmarkId)
    const confMatched = localizedLandmarks.value.find(l => l.id === item.confirmedLandmarkId)
    return {
      ...item,
      predictedLandmarkName: predMatched ? predMatched.name : item.predictedLandmarkName,
      confirmedLandmarkName: confMatched ? confMatched.name : item.confirmedLandmarkName
    }
  })
})

const localizedAdminFeedbackDetail = computed(() => {
  const detail = selectedFeedbackDetail.value
  if (!detail) return null
  return {
    ...detail,
    topResults: (detail.topResults || []).map(item => {
      const matched = localizedLandmarks.value.find(l => l.id === item.landmarkId)
      return {
        ...item,
        name: matched ? matched.name : item.name
      }
    })
  }
})

const localizedUserSearchRecords = computed(() => {
  return userSearchRecords.value.map(record => {
    const bestMatched = localizedLandmarks.value.find(l => l.id === record.bestLandmarkId || l.name === record.bestLandmarkName || l.englishName === record.bestLandmarkName)
    return {
      ...record,
      bestLandmarkName: bestMatched ? bestMatched.name : record.bestLandmarkName,
      topResults: (record.topResults || []).map(item => {
        const matched = localizedLandmarks.value.find(l => l.id === item.landmarkId)
        return {
          ...item,
          name: matched ? matched.name : item.name
        }
      })
    }
  })
})

const localizedCheckIns = computed(() => {
  return checkIns.value.map(item => {
    const matched = localizedLandmarks.value.find(l => l.id === item.landmarkId)
    return {
      ...item,
      landmarkName: matched ? matched.name : item.landmarkName
    }
  })
})

function navigateToView(view) {
  if (activeView.value === view) return
  if (historyIndex.value < viewHistory.value.length - 1) {
    viewHistory.value = viewHistory.value.slice(0, historyIndex.value + 1)
  }
  viewHistory.value.push(view)
  historyIndex.value = viewHistory.value.length - 1
  activeView.value = view
}

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

function startDrag(e) {
  if (zoomScale.value <= 1.0) return
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

const preferenceLabels = computed(() => ({
  theme: t('主题', 'Theme'),
  backdrop: t('背景', 'Backdrop'),
  language: t('语言', 'Language')
}))

const themeLabel = computed(() => ({ dark: '曜黑', light: '落日', contrast: '幻夜' }[preferences.theme]))
const languageLabel = computed(() => preferences.language === 'zh' ? '中文' : 'EN')

const historyLabels = computed(() => ({
  title: t('个人历史记录', 'My Search History'),
  refresh: t('刷新', 'Refresh'),
  needLogin: t('需要登录', 'Login Required'),
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

const authLabels = computed(() => ({
  subtitle: t('校园地标智能检索与导览系统', 'Intelligent Landmark Retrieval & Tour System'),
  login: t('登录', 'Login'),
  register: t('注册', 'Register'),
  username: t('用户名', 'Username'),
  password: t('密码', 'Password'),
  email: t('邮箱', 'Email'),
  emailPlaceholder: t('选填，用于绑定', 'Optional, for binding'),
  submitLogin: t('登录', 'Sign In'),
  submitRegister: t('注册并登录', 'Register & Sign In')
}))

const modalLabels = computed(() => ({
  closeTitle: t('关闭 (Esc)', 'Close (Esc)'),
  introTitle: t('地标实景详细介绍', 'Detailed Landmark Introduction'),
  gridTitle: t('深度解析指标', 'Analytical Metrics'),
  typeLabel: t('地标类型', 'Landmark Type'),
  mapCoords: t('地图百分比坐标', 'Map Percentage Coords'),
  recommendIndex: t('推荐打卡指数', 'Recommend Rating'),
  verifyStatus: t('验证状态', 'Verification Status'),
  verifiedText: t('已校验并收录', 'Verified & Cataloged'),
  showMap: t('在地图中查看', 'View on Map')
}))

const adminLabels = computed(() => ({
  needAdmin: t('需要管理员登录', 'Admin Login Required'),
  needAdminDesc: t('请通过右上角“登录/注册”进入登录页，管理员账号登录成功后会自动进入后台。', 'Please sign in with an administrator account via the button at the top right to access the console.'),
  searchRecords: t('检索记录', 'Search Records'),
  refresh: t('刷新', 'Refresh'),
  id: t('ID', 'ID'),
  bestCandidate: t('最高候选', 'Best Candidate'),
  status: t('状态', 'Status'),
  visitor: t('游客', 'User/Guest'),
  noCandidate: t('无候选', 'No candidate'),
  feedbackProcess: t('反馈处理', 'Feedback Processing'),
  predicted: t('预测', 'Predicted'),
  confirmed: t('确认', 'Confirmed'),
  detail: t('详情', 'Detail'),
  accept: t('采纳', 'Accept'),
  ignore: t('忽略', 'Ignore'),
  feedbackDetail: t('反馈详情', 'Feedback Detail'),
  selectFeedback: t('请选择反馈记录', 'Please select a feedback record'),
  selectFeedbackDesc: t('详情会展示上传图片、Top-5 快照、算法采纳建议和校正样本同步状态。', 'The detail view displays the uploaded image, Top-5 candidates snapshot, algorithm recommendations, and synchronization status.'),
  searchRecord: t('检索记录', 'Search Record'),
  syncStatus: t('校正同步', 'Correction Sync'),
  advice: t('采纳建议', 'Recommendation'),
  noComment: t('无补充说明', 'No description provided'),
  noCommentAdmin: t('暂无补充说明', 'No comments yet'),
  matchScore: t('匹配分', 'Score'),
  statusSuccess: t('成功', 'Success'),
  statusLowConfidence: t('低匹配', 'Low Match'),
  statusEmptyResult: t('空结果', 'Empty'),
  statusAlgorithmUnavailable: t('算法不可用', 'Algorithm Offline'),
  feedbackPending: t('待处理', 'Pending'),
  feedbackAccepted: t('已采纳', 'Accepted'),
  feedbackIgnored: t('已忽略', 'Ignored'),
  typeCorrect: t('识别正确', 'Correct'),
  typeWrong: t('识别错误', 'Incorrect'),
  typeUncertain: t('不确定', 'Uncertain'),
  syncPending: t('同步中', 'Syncing'),
  synced: t('已同步', 'Synced'),
  syncFailed: t('同步失败', 'Sync Failed'),
  syncNone: t('未生成', 'Not Generated'),
  advicePending: t('待生成', 'Pending'),
  adviceWait: t('等待算法建议', 'Awaiting advice'),
  adviceAccept: t('建议采纳', 'Recommend Accept'),
  adviceReview: t('建议复核', 'Recommend Review'),
  algorithmRuntime: t('算法运行状态', 'Algorithm Runtime'),
  rebuildIndex: t('重建索引', 'Rebuild Index'),
  baseModelVersion: t('基准模型', 'Base Model'),
  indexVersion: t('索引版本', 'Index Version'),
  sarStateVersion: t('SAR 状态', 'SAR State'),
  updateCount: t('更新次数', 'Updates'),
  lastResetReason: t('最近回退', 'Last Reset'),
  runtimeState: t('运行状态', 'Runtime State')
}))

function confidenceLabel(value) {
  return {
    high: t('高匹配', 'High Confidence'),
    medium: t('中匹配', 'Medium Confidence'),
    low: t('低匹配', 'Low Confidence')
  }[value] || t('待核验', 'To Verify')
}

onMounted(async () => {
  try {
    await ensureGuestIdentity()
  } catch (err) {
    error.value = err.message
  }
  const dismissed = localStorage.getItem('campuslens.welcomeDismissed')
  if (!dismissed) {
    welcomePoem.value = poems[Math.floor(Math.random() * poems.length)]
    const today = new Date()
    const lang = preferences.language
    if (lang === 'zh') {
      const dateStr = today.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
      const weekdayStr = today.toLocaleDateString('zh-CN', { weekday: 'long' })
      welcomeDate.value.solar = `${dateStr}  ${weekdayStr}`
      welcomeDate.value.lunar = getLunarDateString(today)
    } else {
      const dateStr = today.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
      const weekdayStr = today.toLocaleDateString('en-US', { weekday: 'long' })
      welcomeDate.value.solar = `${dateStr}, ${weekdayStr}`
      welcomeDate.value.lunar = 'Lunar calendar only supports Chinese'
    }
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

function createRequestId() {
  const webCrypto = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined
  if (typeof webCrypto?.randomUUID === 'function') return webCrypto.randomUUID()
  const bytes = new Uint8Array(16)
  if (typeof webCrypto?.getRandomValues === 'function') {
    webCrypto.getRandomValues(bytes)
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256)
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function onFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
  uploadIdempotencyKey.value = selectedFile.value ? createRequestId() : ''
  error.value = ''
}

async function submitSearch() {
  if (!selectedFile.value) {
    error.value = t('请先选择一张校园照片', 'Please select a campus photo first')
    return
  }
  loading.value = true
  error.value = ''
  try {
    await ensureGuestIdentity()
    const form = new FormData()
    form.append('file', selectedFile.value)
    form.append('guestId', guestId.value)
    form.append('sarMode', String(sarMode.value))
    if (!uploadIdempotencyKey.value) uploadIdempotencyKey.value = createRequestId()
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
        throw new Error(t(`${body.message || '检索队列已满'}，请在 ${retryAfter} 秒后重试`, `${body.message || 'Queue full'}, please retry in ${retryAfter} seconds`))
      }
      if (response.status === 503) {
        throw new Error(body.message || t('任务队列暂不可用，请稍后重试', 'Task queue unavailable, please retry later'))
      }
      throw new Error(body.message || t('检索请求失败', 'Search request failed'))
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
    searchMeta.message = t('任务已进入检索队列', 'Job entered retrieval queue')
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
    searchMeta.message = data.message || t('检索完成', 'Search completed')
    searchMeta.status = data.status
    searchMeta.sarApplied = Boolean(data.sarApplied)
    searchMeta.trustLevel = data.trustLevel || ''
    searchMeta.modelVersion = data.modelVersion || ''
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
          throw new Error(body.message || t('查询任务状态失败', 'Failed to retrieve job status'))
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
          error.value = data.message || t('检索任务失败', 'Search job failed')
          searchMeta.message = error.value
          searchMeta.lowConfidence = true
          clearSearchJob()
          loading.value = false
        }
      } catch (err) {
        error.value = t(`${err.message}，任务仍在服务端运行，可刷新页面后继续查看`, `${err.message}. Task is still running on the server; you can refresh to view progress later.`)
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
  const index = results.value.findIndex((item) => item.landmarkId === id || item.id === id)
  if (index !== -1) {
    activeResultIndex.value = index
  }
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
    error.value = t('只有成功或低置信度检索任务允许提交反馈', 'Only successful or low-confidence search jobs allow feedback')
    return
  }
  feedback.predictedLandmarkId = item.landmarkId
  feedback.confirmedLandmarkId = item.landmarkId
  feedback.feedbackType = 'correct'
  feedback.comment = ''
  navigateToView('feedback')
}

function resetFeedbackForm() {
  feedback.comment = ''
  feedback.feedbackType = 'correct'
  feedback.confirmedLandmarkId = feedback.predictedLandmarkId
}

async function submitFeedback() {
  feedbackMessage.value = ''
  feedbackError.value = ''
  if (feedback.feedbackType === 'wrong' && !feedback.confirmedLandmarkId) {
    feedbackError.value = t('识别错误反馈请选择正确地标', 'Please select the correct landmark for wrong feedback')
    return
  }
  if (!feedbackEligible.value) {
    feedbackError.value = t('只有成功或低置信度检索任务允许提交反馈', 'Only successful or low-confidence search jobs allow feedback')
    return
  }
  feedbackSubmitting.value = true
  try {
    await ensureGuestIdentity()
    const payload = { ...feedback, guestId: guestId.value }
    const response = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(payload)
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.message || t('反馈提交失败', 'Feedback submission failed'))
    }
    const data = await response.json()
    feedbackMessage.value = t(`反馈已提交，记录编号：${data.feedbackId}`, `Feedback submitted. Record ID: ${data.feedbackId}`)
    resetFeedbackForm()
  } catch (err) {
    feedbackError.value = err.message
  } finally {
    feedbackSubmitting.value = false
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
      throw new Error(body.message || (authMode.value === 'login' ? t('登录失败', 'Login failed') : t('注册失败', 'Registration failed')))
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
  const [recordsResponse, feedbackResponse, runtimeResponse, statsResponse] = await Promise.all([
    fetch('/api/admin/search-records', { headers }),
    fetch('/api/admin/feedback', { headers }),
    fetch('/api/admin/algorithm/runtime', { headers }),
    fetch('/api/admin/feedback/stats', { headers })
  ])
  if (recordsResponse.ok) {
    adminSearchRecords.value = await recordsResponse.json()
  }
  if (feedbackResponse.ok) {
    adminFeedbackRecords.value = await feedbackResponse.json()
  }
  if (runtimeResponse.ok) adminRuntimeStatus.value = await runtimeResponse.json()
  if (statsResponse.ok) adminStats.value = await statsResponse.json()
  if (selectedFeedbackDetail.value?.id) {
    await loadFeedbackDetail(selectedFeedbackDetail.value.id)
  }
}

async function startIndexRebuild() {
  const response = await fetch('/api/admin/index/rebuild', { method: 'POST', headers: authHeaders() })
  if (!response.ok) return
  adminRebuildJob.value = await response.json()
  const jobId = adminRebuildJob.value.rebuildJobId
  while (['accepted', 'building', 'switching'].includes(adminRebuildJob.value.status)) {
    await new Promise(resolve => setTimeout(resolve, 1500))
    const statusResponse = await fetch(`/api/admin/index/rebuild/${jobId}`, { headers: authHeaders() })
    if (!statusResponse.ok) break
    adminRebuildJob.value = await statusResponse.json()
  }
  await loadAdminData()
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
  await ensureGuestIdentity()
  const response = await fetch(`/api/check-ins?limit=50&guestId=${encodeURIComponent(guestId.value)}`, { headers: authHeaders() })
  if (response.ok) {
    checkIns.value = await response.json()
  }
}

async function createCheckIn(payload) {
  await ensureGuestIdentity()
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
  await ensureGuestIdentity()
  const response = await fetch(`/api/check-ins/${item.id}/like?guestId=${encodeURIComponent(guestId.value)}`, {
    method: 'POST',
    headers: authHeaders()
  })
  if (response.ok) {
    await loadCheckIns()
  }
}

async function replyCheckIn(item, message) {
  await ensureGuestIdentity()
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

function toggleLanguage() {
  preferences.language = preferences.language === 'zh' ? 'en' : 'zh'
  savePreferences()
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
    const parsed = JSON.parse(localStorage.getItem('campuslens.preferences') || '{}')
    return {
      theme: parsed.theme || 'dark',
      backdrop: 'aurora',
      language: parsed.language || 'zh'
    }
  } catch {
    return { theme: 'dark', backdrop: 'aurora', language: 'zh' }
  }
}

function savePreferences() {
  localStorage.setItem('campuslens.preferences', JSON.stringify(preferences))
}

function loadGuestClientToken() {
  const key = 'campuslens.guestClientToken.v2'
  const existing = localStorage.getItem(key)
  if (existing && existing.length >= 16) return existing
  const generated = createRequestId()
  localStorage.setItem(key, generated)
  return generated
}

function loadStoredGuestId() {
  const value = localStorage.getItem('campuslens.guestId.v2')
  return /^guest#[1-9]\d*$/.test(value || '') ? value : ''
}

async function ensureGuestIdentity() {
  if (guestIdentityVerified && /^guest#[1-9]\d*$/.test(guestId.value || '')) return guestId.value
  if (!guestIdentityPromise) {
    guestIdentityPromise = fetch('/api/guests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ clientToken: guestClientToken })
    }).then(async response => {
      const body = await response.json().catch(() => ({}))
      if (!response.ok || !/^guest#[1-9]\d*$/.test(body.guestId || '')) {
        throw new Error(body.message || t('游客身份初始化失败', 'Failed to initialize guest identity'))
      }
      saveGuestId(body.guestId)
      guestIdentityVerified = true
      return body.guestId
    }).finally(() => {
      guestIdentityPromise = null
    })
  }
  return guestIdentityPromise
}

function saveGuestId(value) {
  if (/^guest#[1-9]\d*$/.test(value || '')) {
    guestId.value = value
    localStorage.setItem('campuslens.guestId.v2', value)
  }
}

function feedbackTypeLabel(value) {
  return {
    correct: t('识别正确', 'Correct'),
    wrong: t('识别错误', 'Incorrect'),
    uncertain: t('不确定', 'Uncertain')
  }[value] || value
}

function getCardStyle(index) {
  const len = results.value.length
  if (len === 0) return {}
  
  let diff = index - activeResultIndex.value
  if (diff < 0) diff += len

  // diff 为 0 是最顶端焦点卡片
  // diff 为 1, 2, 3, 4 依次往下层叠
  
  // 垂直位移：焦点为 0，第二张偏移 145px，第三张偏移 275px，后续卡片设为 400px
  const translateY = diff === 0 ? 0 : diff === 1 ? 130 : diff === 2 ? 250 : 380
  
  // 绕 X 轴倾角：层层向前倾斜，表现下沉式阶梯斜面
  const rotateX = diff * -7 

  // 缩放率：第一张稍微放大，后面逐层变小
  const scale = diff === 0 ? 1.02 : 1 - diff * 0.08 
  
  // 透明度：焦点为 1，第 2 张 0.65，第 3 张 0.3，后面完全隐藏
  const opacity = diff === 0 ? 1.0 : diff === 1 ? 0.65 : diff === 2 ? 0.3 : 0.0

  const zIndex = 10 - diff

  return {
    transform: `translateY(${translateY}px) scale(${scale})`,
    opacity: opacity,
    zIndex: zIndex,
    pointerEvents: 'auto'
  }
}
  


function selectLandmarkFromCarousel(id, index) {
  if (selectedId.value === id) return
  activeResultIndex.value = index
  selectedId.value = id
}

// 叠瓦选项卡鼠标拖拽滑动识别
let carouselDragStartY = 0
let carouselDragStartX = 0
let isCarouselDragging = false

function handleDragStart(e) {
  if (e.button !== 0) return // 仅响应左键
  isCarouselDragging = true
  carouselDragStartY = e.clientY
  carouselDragStartX = e.clientX
}

function handleDragEnd(e) {
  if (!isCarouselDragging) return
  isCarouselDragging = false
  const deltaY = e.clientY - carouselDragStartY
  const deltaX = e.clientX - carouselDragStartX
  const threshold = 50 // 滑动判定阈值（像素）
  
  if (Math.abs(deltaY) > Math.abs(deltaX)) {
    // 纵向滑动
    if (Math.abs(deltaY) > threshold) {
      if (deltaY < 0) {
        cycleResults(1) // 向上滑动：展示下一张
      } else {
        cycleResults(-1) // 向下滑动：展示上一张
      }
    }
  } else {
    // 横向滑动
    if (Math.abs(deltaX) > threshold) {
      if (deltaX < 0) {
        cycleResults(1) // 向左滑动：展示下一张
      } else {
        cycleResults(-1) // 向右滑动：展示上一张
      }
    }
  }
}

// 叠瓦选项卡手机触摸屏滑动识别
let touchStartY = 0
let touchStartX = 0

function handleTouchStart(e) {
  if (!e.touches.length) return
  touchStartY = e.touches[0].clientY
  touchStartX = e.touches[0].clientX
}

function handleTouchEnd(e) {
  if (!e.changedTouches.length) return
  const deltaY = e.changedTouches[0].clientY - touchStartY
  const deltaX = e.changedTouches[0].clientX - touchStartX
  const threshold = 40 // 手机端稍微灵敏一点
  
  if (Math.abs(deltaY) > Math.abs(deltaX)) {
    // 纵向滑动
    if (Math.abs(deltaY) > threshold) {
      if (deltaY < 0) {
        cycleResults(1)
      } else {
        cycleResults(-1)
      }
    }
  } else {
    // 横向滑动
    if (Math.abs(deltaX) > threshold) {
      if (deltaX < 0) {
        cycleResults(1)
      } else {
        cycleResults(-1)
      }
    }
  }
}
</script>
