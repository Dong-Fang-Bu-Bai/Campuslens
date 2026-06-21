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
      <p class="intro">{{ t('上传一张校园照片，快速识别地标并查看位置与介绍。', 'Upload a campus photo to identify landmarks and explore their locations and stories.') }}</p>

      <form class="upload-panel" @submit.prevent="submitSearch">
        <label class="file-drop" :class="{ active: selectedFile }">
          <input type="file" accept="image/jpeg,image/png,image/webp" @change="onFileChange" />
          <span class="file-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
          </span>
          <strong>{{ selectedFile ? selectedFile.name : t('选择校园照片', 'Choose Campus Photo') }}</strong>
          <small>{{ t('支持 JPG、PNG 或 WebP，单张不超过 8 MB', 'JPG, PNG, or WebP · up to 8 MB') }}</small>
        </label>
        <label class="sar-mode-toggle">
          <input v-model="sarMode" type="checkbox" />
          <span><strong>{{ t('增强识别', 'Enhanced Recognition') }}</strong><small>{{ t('适合复杂角度或光线，处理时间稍长', 'Better for challenging angles or lighting; may take longer') }}</small></span>
        </label>
        <button class="primary-btn" :disabled="loading || jobPending" :class="{ 'is-loading': loading }">
          <span class="btn-text">{{ loading ? jobStatusLabel : t('上传并识别', 'Upload & Identify') }}</span>
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
          {{ t('识别结果', 'Results') }}
        </button>
        <button :class="{ active: activeView === 'map' }" @click="changeView('map')">
          <span class="active-line"></span>
          {{ t('校园地图', 'Campus Map') }}
        </button>
        <button :class="{ active: activeView === 'feedback' }" @click="changeView('feedback')">
          <span class="active-line"></span>
          {{ t('结果反馈', 'Feedback') }}
        </button>
        <button :class="{ active: activeView === 'history' }" @click="openHistory">
          <span class="active-line"></span>
          {{ t('识别记录', 'History') }}
        </button>
        <button :class="{ active: activeView === 'checkins' }" @click="openCheckIns">
          <span class="active-line"></span>
          {{ t('校园留言', 'Campus Moments') }}
        </button>
        <button v-if="isAdmin" :class="{ active: activeView === 'admin' }" @click="openAdmin">
          <span class="active-line"></span>
          {{ t('管理中心', 'Admin Center') }}
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
            <p class="eyebrow">Campus Landmark Intelligence</p>
            <h2>{{ viewTitle }}</h2>
          </div>
        </div>
        <div class="top-actions-container">
          <div class="top-bar-login">
            <template v-if="!currentUser">
              <button type="button" class="top-login-trigger-btn" @click="openAuth">
                {{ t('登录 / 注册', 'Login / Register') }}
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
                <span class="stat-label">{{ t('本次识别', 'Current Search') }}</span>
                <span class="stat-value">{{ searchMeta.searchRecordId ? `#${searchMeta.searchRecordId}` : '—' }}</span>
                <span class="stat-desc">{{ searchMeta.uploadImageUrl ? t('照片已安全接收', 'Photo received') : t('等待上传校园照片', 'Waiting for a campus photo') }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">{{ t('候选数量', 'Candidates') }}</span>
                <span class="stat-value">{{ results.length }}</span>
                <span class="stat-desc">{{ t('按相似程度排列候选地标', 'Landmark candidates ranked by similarity') }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">{{ t('识别状态', 'Recognition Status') }}</span>
                <span class="stat-value">{{ !searchMeta.status ? t('待开始', 'Ready') : searchMeta.lowConfidence ? t('建议确认', 'Review') : t('识别完成', 'Complete') }}</span>
                <span class="stat-desc">{{ searchMeta.message || t('上传照片后即可开始识别', 'Upload a photo to begin recognition') }}</span>
              </div>
            </div>

            <p v-if="searchMeta.lowConfidence" class="warning-text">
              {{ searchMeta.message }}
            </p>
            <p v-if="searchMeta.trustLevel || searchMeta.sarApplied" class="sar-result-meta">
              {{ t('增强识别', 'Enhanced recognition') }}：{{ searchMeta.sarApplied ? t('已启用', 'Enabled') : t('未启用', 'Off') }} ·
              {{ t('结果可信度', 'Result confidence') }}：{{ searchMeta.trustLevel || '-' }}
            </p>
            <div v-if="jobState.status === 'queued' || jobState.status === 'processing'" class="job-progress" role="status">
              <div>
                <strong>{{ jobStatusLabel }}</strong>
                <span>{{ t('正在处理照片', 'Processing your photo') }}<template v-if="jobState.attemptCount > 1"> · {{ t(`第 ${jobState.attemptCount} 次尝试`, `Attempt ${jobState.attemptCount}`) }}</template></span>
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
                      <button :disabled="!checkInEligible" @click.stop="beginCheckIn(searchMeta, item)">{{ t('打卡', 'Check in') }}</button>
                    </div>
                  </div>
                </div>
                <div v-if="((index - activeResultIndex + results.length) % results.length) !== 0" class="carousel-click-shield"></div>
              </div>
            </div>
            <article v-else class="empty-state">
              <h3>{{ t('等待识别校园地标', 'Ready to Identify a Landmark') }}</h3>
              <p>{{ t('从左侧选择一张校园照片，识别完成后将在这里展示候选地标。', 'Choose a campus photo on the left. Landmark candidates will appear here when recognition is complete.') }}</p>
            </article>
          </div>

          <article class="detail-panel" v-if="localizedSelectedLandmark && results.length">
            <p class="eyebrow">{{ localizedSelectedLandmark.code }}</p>
            <h3>{{ localizedSelectedLandmark.name }}</h3>
            <p class="detail-location">{{ localizedSelectedLandmark.locationText }}</p>
            <p>{{ localizedSelectedLandmark.description }}</p>
            <div class="detail-grid">
              <span>{{ t('类型', 'Type') }}</span><strong>{{ localizedSelectedLandmark.type }}</strong>
              <span>{{ t('地图坐标', 'Map Coordinates') }}</span><strong>{{ localizedSelectedLandmark.mapX }}%, {{ localizedSelectedLandmark.mapY }}%</strong>
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
            <h3>{{ t('校园地标分布', 'Campus Landmarks') }}</h3>
            <p>{{ t('点击地图标记或下方地标名称，即可查看位置与详细介绍。', 'Select a map marker or landmark name to explore its location and details.') }}</p>
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
          <div v-if="feedbackEligible" class="feedback-layout">
            <aside class="history-panel">
              <h3>{{ t('本次识别', 'Current Search') }}</h3>
              <p class="panel-desc">{{ t('你的反馈会与本次识别结果一并保存，便于后续审核与改进。', 'Your feedback will be saved with this search for review and future improvements.') }}</p>
              
              <div class="history-list">
                <div class="history-item">
                  <div class="history-meta">
                    <span class="history-time">{{ t('识别编号', 'Search') }} #{{ feedback.searchRecordId }}</span>
                    <span class="history-status active">{{ t('等待提交', 'Ready to submit') }}</span>
                  </div>
                  <strong>{{ localizedSelectedLandmark?.code }} {{ localizedSelectedLandmark?.name }}</strong>
                  <p>{{ t('当前选择', 'Selected landmark') }}：{{ localizedSelectedLandmark?.name || '-' }} · {{ t('反馈类型', 'Feedback type') }}：{{ feedbackTypeLabel(feedback.feedbackType) }}</p>
                </div>
                <div v-for="item in localizedResults.slice(0, 3)" :key="item.landmarkCode" class="history-item">
                  <div class="history-meta">
                    <span class="history-time">{{ t('候选', 'Candidate') }} #{{ item.rank }}</span>
                    <span class="history-status" :class="{ active: item.confidenceLevel !== 'low', wrong: item.confidenceLevel === 'low' }">{{ confidenceLabel(item.confidenceLevel) }}</span>
                  </div>
                  <strong>{{ item.landmarkCode }} {{ item.name }}</strong>
                  <p>{{ t('匹配分', 'Score') }}：{{ Math.round(item.score * 100) }}%</p>
                </div>
              </div>
            </aside>

            <article class="feedback-card">
              <h3>{{ t('提交反馈纠错', 'Submit Feedback') }}</h3>
              <p>{{ t('请告诉我们识别结果是否准确。反馈经审核后将用于持续提升识别效果。', 'Tell us whether the result is accurate. Reviewed feedback helps improve future searches.') }}</p>
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
                  {{ t('实际地标', 'Actual Landmark') }}
                  <select v-model.number="feedback.confirmedLandmarkId">
                    <option :value="null">{{ t('暂不选择', 'Not selected') }}</option>
                    <option v-for="item in localizedLandmarks" :key="item.id" :value="item.id">{{ item.name }}</option>
                  </select>
                </label>
                <label>
                  {{ t('补充说明', 'Additional Info') }}
                  <textarea v-model="feedback.comment" rows="4" :placeholder="t('可补充拍摄位置、角度或实际地标信息', 'Add the location, angle, or correct landmark if helpful')"></textarea>
                </label>
                <button class="primary-btn">
                  <span class="btn-text">{{ t('提交反馈', 'Submit') }}</span>
                </button>
                <p v-if="feedbackMessage" :class="feedbackError ? 'error-text' : 'success-text'">
                  <svg v-if="!feedbackError" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
                  {{ feedbackMessage }}
                </p>
              </form>
            </article>
          </div>
          <div v-else class="empty-state feedback-empty-state">
            <h3>{{ t('还没有可反馈的识别结果', 'No result available for feedback') }}</h3>
            <p>{{ t('请先上传校园照片并完成识别，再从候选地标进入反馈页面。', 'Upload a campus photo and complete a search, then open feedback from a landmark candidate.') }}</p>
            <button type="button" class="secondary-btn inline-action" @click="changeView('results')">{{ t('返回识别页面', 'Go to Search') }}</button>
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
          @open-check-in="openHistoryCheckIn"
        />

        <CheckInBoard
          v-else-if="activeView === 'checkins'"
          key="checkins"
          :landmarks="localizedLandmarks"
          :items="localizedCheckIns"
          :draft="checkInDraft"
          :labels="checkInLabels"
          :language="preferences.language"
          :submitting="checkInSubmitting"
          :create-error="checkInCreateError"
          @refresh="loadCheckIns"
          @create="createCheckIn"
          @like="toggleCheckInLike"
          @reply="replyCheckIn"
          @select-landmark="selectMapLandmark"
          @cancel-draft="cancelCheckInDraft"
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
            <h2 class="welcome-title">{{ t('欢迎来到校园慧眼', 'Welcome to CampusLens') }}</h2>
            
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
              <span class="btn-text">{{ t('开始探索', 'Start Exploring') }}</span>
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
    description: '体育馆位于文雍路西侧，是一座设施完善的现代化多功能场馆，服务全校体育教学、文体赛事和日常锻炼，富有动感的网架结构具有很高辨识度。',
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
    description: '游泳馆位于体育馆北侧，配有标准泳道与先进水循环系统，是开展游泳教学、水上运动训练和日常锻炼的重要场馆。',
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
    summary: '面向师生的综合餐饮空间，提供多种风味与实惠餐食。',
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
    summary: '服务东区师生的现代餐饮空间，兼具用餐与休闲功能。',
    englishSummary: 'A modern dining hall for the East Campus community, combining meals with comfortable social spaces.',
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
    summary: '校内接待与住宿场所，环境舒适，配套设施完善。',
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
const results = ref([])
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
const feedbackError = ref(false)
const authMessage = ref('')
const authError = ref(false)
const searchMeta = reactive({
  searchRecordId: null,
  uploadImageUrl: '',
  lowConfidence: false,
  message: '',
  status: '',
  sarApplied: false,
  trustLevel: '',
  modelVersion: ''
})
const feedback = reactive({
  searchRecordId: null,
  predictedLandmarkId: null,
  confirmedLandmarkId: null,
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
const adminRebuildJob = ref(null)
const selectedFeedbackDetail = ref(null)
const userSearchRecords = ref([])
const historyLoading = ref(false)
const jobStatusLabel = computed(() => ({
  queued: t('正在等待识别...', 'Waiting to begin...'),
  processing: t('正在识别照片...', 'Analyzing your photo...'),
  failed: t('识别失败', 'Recognition failed')
}[jobState.status] || t('正在提交照片...', 'Submitting your photo...')))
const jobPending = computed(() => Boolean(jobState.jobId) && ['queued', 'processing'].includes(jobState.status))
const feedbackEligible = computed(() => ['success', 'low_confidence'].includes(searchMeta.status))
const checkInEligible = computed(() => feedbackEligible.value && Boolean(searchMeta.searchRecordId))
const checkIns = ref([])
const checkInDraft = ref(null)
const checkInSubmitting = ref(false)
const checkInCreateError = ref('')
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
  'view-checkins': activeView.value === 'checkins',
  [`theme-${preferences.theme}`]: true,
  [`backdrop-${preferences.backdrop}`]: true
}))

const viewTitle = computed(() => ({
  results: t('校园地标识别', 'Landmark Search'),
  map: t('校园地图导览', 'Campus Map'),
  feedback: t('识别结果反馈', 'Search Feedback'),
  history: t('识别记录', 'Search History'),
  checkins: t('校园留言', 'Campus Moments'),
  auth: authMode.value === 'login' ? t('用户登录', 'Login') : t('用户注册', 'Register'),
  admin: t('管理中心', 'Admin Center')
}[activeView.value]))

const preferenceLabels = computed(() => ({
  theme: t('主题', 'Theme'),
  backdrop: t('背景', 'Backdrop'),
  language: t('语言', 'Language')
}))

const themeLabel = computed(() => ({
  dark: t('曜黑', 'Obsidian'),
  light: t('落日', 'Sunset'),
  contrast: t('幻夜', 'Nebula')
}[preferences.theme]))
const languageLabel = computed(() => preferences.language === 'zh' ? '中文' : 'EN')

const historyLabels = computed(() => ({
  kicker: t('识别足迹', 'Search History'),
  title: t('个人历史记录', 'My Search History'),
  refresh: t('刷新', 'Refresh'),
  needLogin: t('需要登录', 'Login Required'),
  needLoginDesc: t('登录后即可查看和继续使用你的识别记录。', 'Sign in to view and continue from your previous searches.'),
  loading: t('正在加载识别记录...', 'Loading your search history...'),
  empty: t('还没有识别记录', 'No search history yet'),
  emptyDesc: t('上传第一张校园照片，识别结果会保存在这里。', 'Upload your first campus photo and the result will appear here.'),
  noCandidate: t('暂未识别到地标', 'No landmark identified'),
  noMessage: t('识别已完成', 'Search completed'),
  score: t('匹配分', 'Score'),
  feedback: t('反馈', 'Feedback'),
  checkIn: t('打卡', 'Check in')
}))

const checkInLabels = computed(() => ({
  kicker: t('校园动态', 'Campus Moments'),
  composeKicker: t('分享此刻', 'Share a Moment'),
  title: t('校园打卡留言板', 'Campus Check-in Board'),
  boardSubtitle: t('真实识图记录留下的校园观察与交流', 'Campus notes and conversations backed by real searches'),
  refresh: t('刷新', 'Refresh'),
  landmark: t('打卡地点', 'Landmark'),
  sourceRecord: t('关联识别', 'Linked search'),
  publicPhoto: t('在公共留言板展示本次上传照片', 'Show this uploaded photo on the public board'),
  composeTitle: t('发布识图打卡', 'Publish Search Check-in'),
  cancel: t('取消', 'Cancel'),
  close: t('关闭', 'Close'),
  submitting: t('发布中...', 'Publishing...'),
  viewImage: t('查看大图', 'View image'),
  enlargeImage: t('点击放大打卡图片', 'Enlarge check-in image'),
  message: t('留言内容', 'Message'),
  placeholder: t('记录当前地标的观察、路线提醒或拍照建议', 'Share an observation, route note, or photo tip'),
  submit: t('发布打卡', 'Post'),
  empty: t('暂无留言', 'No posts yet'),
  emptyDesc: t('完成一次校园地标识别后，即可从候选结果分享打卡留言。', 'Complete a landmark search, then share a check-in from one of the results.'),
  like: t('点赞', 'Like'),
  liked: t('已赞', 'Liked'),
  reply: t('回复', 'Reply'),
  replyPlaceholder: t('写一条一级回复', 'Write a reply'),
  replyCount: t('回复', 'Replies')
}))

const authLabels = computed(() => ({
  subtitle: t('校园地标智能识别与导览', 'Intelligent Campus Landmark Recognition and Guide'),
  login: t('登录', 'Login'),
  register: t('注册', 'Register'),
  username: t('用户名', 'Username'),
  password: t('密码', 'Password'),
  email: t('邮箱', 'Email'),
  emailPlaceholder: t('选填，用于完善账户信息', 'Optional, for your account profile'),
  submitLogin: t('登录', 'Sign In'),
  submitRegister: t('注册并登录', 'Register & Sign In')
}))

const modalLabels = computed(() => ({
  closeTitle: t('关闭 (Esc)', 'Close (Esc)'),
  introTitle: t('地标介绍', 'About this landmark'),
  gridTitle: t('地标信息', 'Landmark details'),
  typeLabel: t('地标类型', 'Landmark Type'),
  mapCoords: t('校园地图位置', 'Campus map position'),
  verifyStatus: t('资料状态', 'Information status'),
  verifiedText: t('已收录', 'Available'),
  showMap: t('在地图中查看', 'View on Map')
}))

const adminLabels = computed(() => ({
  runtimeKicker: t('识别服务', 'Recognition Service'),
  recordsKicker: t('访问记录', 'Search Activity'),
  feedbackKicker: t('用户反馈', 'User Feedback'),
  detailKicker: t('审核详情', 'Review Details'),
  needAdmin: t('需要管理员登录', 'Admin Login Required'),
  needAdminDesc: t('请通过右上角“登录/注册”进入登录页，管理员账号登录成功后会自动进入后台。', 'Please sign in with an administrator account via the button at the top right to access the console.'),
  searchRecords: t('检索记录', 'Search Records'),
  refresh: t('刷新', 'Refresh'),
  id: t('编号', 'ID'),
  bestCandidate: t('首选结果', 'Top Result'),
  status: t('状态', 'Status'),
  visitor: t('访问用户', 'Visitor'),
  noCandidate: t('未识别到地标', 'No landmark identified'),
  feedbackProcess: t('反馈处理', 'Feedback Processing'),
  predicted: t('预测', 'Predicted'),
  confirmed: t('确认', 'Confirmed'),
  detail: t('详情', 'Detail'),
  accept: t('采纳', 'Accept'),
  ignore: t('忽略', 'Ignore'),
  feedbackDetail: t('反馈详情', 'Feedback Detail'),
  selectFeedback: t('请选择反馈记录', 'Please select a feedback record'),
  selectFeedbackDesc: t('选择一条反馈后，可查看原图、候选结果、处理建议和更新进度。', 'Select feedback to review the image, candidate results, recommendation, and update progress.'),
  searchRecord: t('检索记录', 'Search Record'),
  syncStatus: t('更新进度', 'Update Progress'),
  advice: t('处理建议', 'Recommendation'),
  noComment: t('无补充说明', 'No description provided'),
  noCommentAdmin: t('暂无补充说明', 'No comments yet'),
  matchScore: t('匹配分', 'Score'),
  statusSuccess: t('成功', 'Success'),
  statusLowConfidence: t('低匹配', 'Low Match'),
  statusEmptyResult: t('未找到结果', 'No Result'),
  statusAlgorithmUnavailable: t('识别服务暂不可用', 'Service Unavailable'),
  feedbackPending: t('待处理', 'Pending'),
  feedbackAccepted: t('已采纳', 'Accepted'),
  feedbackIgnored: t('已忽略', 'Ignored'),
  typeCorrect: t('识别正确', 'Correct'),
  typeWrong: t('识别错误', 'Incorrect'),
  typeUncertain: t('不确定', 'Uncertain'),
  syncPending: t('等待更新', 'Update Pending'),
  synced: t('已更新', 'Updated'),
  syncFailed: t('更新失败', 'Update Failed'),
  syncNone: t('尚未开始', 'Not Started'),
  advicePending: t('等待处理', 'Awaiting Review'),
  adviceWait: t('正在生成建议', 'Preparing Recommendation'),
  adviceAccept: t('建议采纳', 'Recommend Accept'),
  adviceReview: t('建议复核', 'Recommend Review'),
  algorithmRuntime: t('识别服务状态', 'Recognition Service'),
  rebuildIndex: t('更新识别库', 'Update Search Index'),
  baseModelVersion: t('基础模型版本', 'Base Model Version'),
  indexVersion: t('识别库版本', 'Search Index Version'),
  sarStateVersion: t('增强识别状态', 'Enhanced Recognition State'),
  updateCount: t('累计更新', 'Total Updates'),
  lastResetReason: t('最近恢复原因', 'Last Recovery Reason'),
  runtimeState: t('当前状态', 'Current Status'),
  rebuildTask: t('识别库更新', 'Index Update'),
  anonymousVisitor: t('未登录访客', 'Guest Visitor'),
  runtimeHealthy: t('运行正常', 'Healthy'),
  runtimeDegraded: t('部分功能受限', 'Degraded'),
  runtimeUnavailable: t('暂不可用', 'Unavailable'),
  rebuildQueued: t('等待开始', 'Queued'),
  rebuildBuilding: t('正在生成识别库', 'Building'),
  rebuildValidating: t('正在校验', 'Validating'),
  rebuildPublishing: t('正在发布', 'Publishing'),
  rebuildComplete: t('更新完成', 'Complete'),
  rebuildFailed: t('更新失败', 'Failed')
}))

function confidenceLabel(value) {
  return {
    high: t('高匹配', 'High Confidence'),
    medium: t('中匹配', 'Medium Confidence'),
    low: t('低匹配', 'Low Confidence')
  }[value] || t('待核验', 'To Verify')
}

function searchFailureMessage(errorCode) {
  return {
    algorithm_unavailable: t('识别服务暂时不可用，请稍后重试', 'Recognition is temporarily unavailable. Please try again later.'),
    queue_timeout: t('等待时间过长，请重新提交照片', 'The wait took too long. Please submit the photo again.'),
    job_timeout: t('本次识别超时，请重新尝试', 'This search timed out. Please try again.'),
    upload_missing: t('上传照片已失效，请重新选择照片', 'The uploaded photo is no longer available. Please choose it again.')
  }[errorCode] || t('本次识别未能完成，请重新尝试', 'The search could not be completed. Please try again.')
}

onMounted(async () => {
  try {
    await ensureGuestIdentity()
  } catch (err) {
    error.value = err.message
  }
  const dismissed = localStorage.getItem('campuslens.welcomeDismissed')
  if (!dismissed) {
    const today = new Date()
    const lang = preferences.language
    if (lang === 'zh') {
      welcomePoem.value = poems[Math.floor(Math.random() * poems.length)]
      const dateStr = today.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
      const weekdayStr = today.toLocaleDateString('zh-CN', { weekday: 'long' })
      welcomeDate.value.solar = `${dateStr}  ${weekdayStr}`
      welcomeDate.value.lunar = getLunarDateString(today)
    } else {
      welcomePoem.value = 'Every corner of campus has a story worth discovering.'
      const dateStr = today.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
      const weekdayStr = today.toLocaleDateString('en-US', { weekday: 'long' })
      welcomeDate.value.solar = `${dateStr}, ${weekdayStr}`
      welcomeDate.value.lunar = 'A new day to explore the campus'
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
      if (response.status === 429) {
        const retryAfter = response.headers.get('Retry-After') || '5'
        throw new Error(t(`当前使用人数较多，请在 ${retryAfter} 秒后重试`, `The service is busy. Please try again in ${retryAfter} seconds.`))
      }
      if (response.status === 503) {
        throw new Error(t('识别服务暂时不可用，请稍后重试', 'Recognition is temporarily unavailable. Please try again later.'))
      }
      throw new Error(t('照片提交失败，请检查网络后重试', 'Could not submit the photo. Check your connection and try again.'))
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
    searchMeta.message = t('照片已提交，正在识别地标', 'Photo submitted. Identifying landmarks...')
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
    searchMeta.message = data.lowConfidence
      ? t('识别完成，建议结合照片确认候选结果', 'Search complete. Please review the candidates against your photo.')
      : t('识别完成，已为你整理候选地标', 'Search complete. Your landmark candidates are ready.')
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
          throw new Error(t('暂时无法获取识别进度', 'Unable to retrieve search progress right now.'))
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
          error.value = searchFailureMessage(data.errorCode)
          searchMeta.message = error.value
          searchMeta.lowConfidence = true
          clearSearchJob()
          loading.value = false
        }
      } catch (err) {
        error.value = t(`${err.message}。识别仍在继续，可稍后刷新查看结果`, `${err.message} The search is still running; refresh later to view the result.`)
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
    error.value = t('只有已完成的识别结果可以提交反馈', 'Feedback is available after a search is completed.')
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
  feedbackError.value = false
  if (!feedbackEligible.value) {
    feedbackMessage.value = t('只有已完成的识别结果可以提交反馈', 'Feedback is available after a search is completed.')
    feedbackError.value = true
    return
  }
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
    await response.json()
    feedbackMessage.value = t('反馈提交成功，感谢你帮助我们改进识别效果', 'Feedback submitted. Thank you for helping improve recognition quality.')
  } catch (err) {
    feedbackMessage.value = err.message
    feedbackError.value = true
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
  const [recordsResponse, feedbackResponse, runtimeResponse] = await Promise.all([
    fetch('/api/admin/search-records', { headers }),
    fetch('/api/admin/feedback', { headers }),
    fetch('/api/admin/algorithm/runtime', { headers })
  ])
  if (recordsResponse.ok) {
    adminSearchRecords.value = await recordsResponse.json()
  }
  if (feedbackResponse.ok) {
    adminFeedbackRecords.value = await feedbackResponse.json()
  }
  if (runtimeResponse.ok) adminRuntimeStatus.value = await runtimeResponse.json()
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

function beginCheckIn(record, item) {
  if (!['success', 'low_confidence'].includes(record.status) || !record.searchRecordId) return
  checkInDraft.value = {
    searchRecordId: record.searchRecordId,
    landmarkId: item.landmarkId,
    sourceImageUrl: record.uploadImageUrl || ''
  }
  checkInCreateError.value = ''
  loadCheckIns().then(() => navigateToView('checkins'))
}

function cancelCheckInDraft() {
  if (checkInSubmitting.value) return
  checkInDraft.value = null
  checkInCreateError.value = ''
}

function openHistoryCheckIn(record, item) {
  beginCheckIn({
    searchRecordId: record.id,
    status: record.status,
    uploadImageUrl: record.uploadImageUrl
  }, item)
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
  checkInSubmitting.value = true
  checkInCreateError.value = ''
  try {
    await ensureGuestIdentity()
    const response = await fetch('/api/check-ins', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ ...payload, guestId: guestId.value })
    })
    if (response.ok) {
      checkInDraft.value = null
      await loadCheckIns()
    } else {
      const body = await response.json().catch(() => ({}))
      checkInCreateError.value = body.message || t('打卡发布失败', 'Failed to publish check-in')
    }
  } catch (err) {
    checkInCreateError.value = err.message || t('打卡发布失败', 'Failed to publish check-in')
  } finally {
    checkInSubmitting.value = false
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
        throw new Error(body.message || t('暂时无法建立访客会话，请确认服务已启动后刷新页面', 'Could not start a guest session. Make sure the service is running, then refresh the page.'))
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
