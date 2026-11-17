<script setup>
import { computed, ref } from 'vue'
import EvaluationPanel from './EvaluationPanel.vue'

const userId = 'demo-user'
const cityLabels = {
  hangzhou: '杭州',
  '杭州市': '杭州',
}
const cityApiValues = { '杭州': '杭州市', hangzhou: '杭州市', '杭州市': '杭州市' }
const amenityLabels = {
  breakfast: '含早餐',
  gym: '健身房',
  laundry: '洗衣房',
  parking: '停车场',
  luggage_storage: '行李寄存',
  robot_service: '机器人服务',
  restaurant: '餐厅',
  taxi_service: '叫车服务',
  meeting_room: '会议室',
  accessibility: '无障碍设施',
  hair_dryer: '吹风机',
  towels: '毛巾',
}
const stateLabels = {
  CONNECTING: '连接中',
  COLLECTING_REQUIREMENTS: '收集需求',
  SEARCHING: '搜索中',
  OBSERVING_RESULTS: '分析结果',
  CLARIFICATION_REQUIRED: '需要补充信息',
  RECOMMENDED: '已生成推荐',
  ERROR: '发生错误',
}
const observationStatusLabels = {
  SUCCESS: '成功',
  PARTIAL: '部分完成',
  ERROR: '失败',
  NO_INVENTORY: '无库存',
  NO_FILTER_MATCH: '无筛选匹配',
  NO_HARD_CONSTRAINT_MATCH: '无硬条件匹配',
  NO_RESULTS: '无结果',
  NO_CANDIDATES: '无候选酒店',
  NO_MATCHES: '无符合项',
  MATCHES: '符合条件',
  AMAP_MCP: '高德路线已返回',
  UNAVAILABLE: '不可用',
  VERIFIED: '已复核',
  REJECTED: '复核未通过',
  NOT_APPLICABLE: '不适用',
  AWAITING_USER_CONFIRMATION: '等待用户确认',
  CANDIDATES_AVAILABLE: '存在可订候选',
  HARD_FILTERED: '已完成硬条件过滤',
}
const observationToolLabels = {
  requirement_analysis: '需求分析',
  memory_fetch: '长期偏好读取',
  hotel_list: '酒店与房型查询',
  distance_calculation: '高德路线查询',
  reviews_extraction: '酒店评价提取',
  review_matching: '酒店评价匹配',
  multiple_ranking: '候选排序',
  recommendation_validation: '最终推荐复核',
  condition_relax: '条件放宽确认',
  recommendation_duration: '本轮处理耗时',
  'spring-ai-alibaba-react-agent': 'ReAct 智能体',
}
const translations = {
  zh: {
    discover: '发现',
    home: '首页',
    recommendation: '推荐',
    matchScore: '匹配分',
    recommendationNav: '推荐',
    staticDataset: '静态酒店数据',
    tagline: '用心发现理想酒店',
    homeEyebrow: '开启下一段旅程',
    homeTitle: '美好旅程',
    homeTitleEmphasis: '从理想住宿开始。',
    homeDescription: '选择目的地和日期，查找符合行程安排的酒店与房型。',
    destination: '目的地',
    destinationPlaceholder: '你想去哪里？',
    checkIn: '入住日期',
    checkOut: '退房日期',
    chooseDate: '请选择日期',
    search: '搜索酒店',
    searching: '正在搜索…',
    searchResults: '搜索结果',
    hotels: '家酒店',
    searchingRooms: '正在查询可订房间…',
    noHotels: '这些日期暂无可订酒店，请尝试更换目的地或日期。',
    rating: '分',
    perNight: ' / 晚',
    viewRooms: '查看酒店详情',
    roomOptions: '可选房型',
    breakfastIncluded: '含早餐',
    breakfastExcluded: '不含早餐',
    noRoomInfo: '暂无房型信息。',
    homePrompt: '准备去哪里？',
    homePromptDescription: '选择目的地和日期，查看当前模拟数据中的可订酒店。',
    recommendDescription: '告诉我们你的需求，智能助手会为你搜索、比较并不断优化推荐。',
    assistantLabel: '酒店智能推荐助手',
    companion: '专属旅行助手',
    chatTitle: '一起找到理想住宿',
    historyTitle: '会话历史',
    historyDetail: '记录详情',
    selectHistory: '选择一条会话记录查看详情。',
    continueConversation: '继续对话',
    newSession: '新建会话',
    untitledSession: '新会话',
    noHistory: '暂无会话记录。',
    deleteSession: '删除会话',
    confirmDelete: '确定删除这条会话及其消息吗？此操作无法撤销，已提取的长期偏好仍会保留。',
    historyLoading: '正在加载会话…',
    loading: '正在查询房态并匹配你的偏好',
    describeNeeds: '描述酒店需求',
    example: '例如：杭州，2026-11-20 至 2026-11-21，预算 1000，必须有吹风机',
    dateHelp: '请提供目的地、入住和退房日期（日期格式：YYYY-MM-DD）。',
    send: '发送',
    sendNeeds: '发送需求',
    observations: '助手观察结果',
    shortlist: '精选推荐',
    bestMatches: '最适合你的酒店',
    mustHaves: '所有推荐都会优先满足你的必要条件。',
    recommendationsPrompt: '告诉我们目的地和出行日期后，精选酒店将显示在这里。',
    recommendation: '推荐',
    hotelRating: '酒店评分',
    sourcePrice: '来源房价（币种未注明）',
    details: '酒店详情',
    detailSubtitle: '酒店信息、设施、房型库存与评价',
    back: '返回',
    hotelId: '酒店编号',
    city: '城市',
    province: '省份',
    district: '区县',
    address: '地址',
    phone: '联系电话',
    ratingValue: '评分',
    price: '参考房价',
    breakfast: '早餐',
    amenities: '设施',
    keywords: '关键词',
    longitude: '经度',
    latitude: '纬度',
    openingYear: '开业年份',
    renovationYear: '装修年份',
    image: '图片地址',
    gym: '健身房',
    laundry: '洗衣服务',
    parking: '停车场',
    luggageStorage: '行李寄存',
    robotService: '机器人服务',
    restaurant: '餐厅',
    taxiService: '叫车服务',
    meetingRoom: '会议室',
    accessibility: '无障碍设施',
    roomId: '房型编号',
    roomName: '房型名称',
    availableFrom: '库存开始日期',
    availableTo: '库存结束日期',
    hairDryer: '吹风机',
    towels: '毛巾',
    reviews: '酒店评价',
    reviewId: '评价编号',
    reviewRating: '评价评分',
    reviewDate: '评价时间',
    reviewContent: '评价内容',
    previousPage: '上一页',
    nextPage: '下一页',
    noReviews: '暂无评价记录。',
    noRoomInfo: '暂无房型信息。',
    route: '路线信息',
    routeUnavailable: '路线信息暂不可用。',
    unknown: '未提供',
    yourRating: '你的评分',
    shareExperience: '有什么值得分享？',
    saveReview: '保存评价',
    writeReview: '撰写入住评价',
    currentSearch: '当前搜索条件',
    maxPerNight: '每晚最高',
    footer: '静态酒店数据 · 不提供预订或支付',
    requiredFields: '请填写目的地、入住日期和退房日期。',
    invalidDateRange: '退房日期必须晚于入住日期。',
    reviewRequired: '请填写评价内容后再保存。',
    feedbackSaved: '感谢反馈！评价已保存，并可用于改进此演示用户后续收到的推荐。',
    connectionError: '无法连接到服务，请检查后端是否已启动。',
    genericError: '操作失败，请稍后重试。',
    pageTitle: '酒店筛选助手',
  },
}
document.documentElement.lang = 'zh-CN'
document.title = translations.zh.pageTitle
const activePage = ref('home')
const sessionId = ref('')
const historySessions = ref([])
const historySelection = ref(null)
const historyLoaded = ref(false)
const historyBusy = ref(false)
const feedbackBusy = ref(false)
const homeCity = ref('杭州市')
const checkIn = ref('')
const checkOut = ref('')
const hotelResults = ref([])
const homeSearched = ref(false)
const homeLoading = ref(false)
const hotelDetail = ref(null)
const detailLoading = ref(false)
const detailReturnPage = ref('home')
const reviewPage = ref(1)
const draft = ref('')
const messages = ref([])
const recommendations = ref([])
const observations = ref([])
const evaluation = ref(null)
const requirements = ref(null)
const state = ref('CONNECTING')
const loading = ref(false)
const error = ref('')
const feedback = ref({})
const showFeedback = ref(null)
const checkInPicker = ref(null)
const checkOutPicker = ref(null)
const sessionBusy = computed(() => loading.value || historyBusy.value || feedbackBusy.value)
const canSend = computed(() => Boolean(sessionId.value) && !sessionBusy.value && draft.value.trim().length > 0)
const reviewPageCount = computed(() => Math.ceil((hotelDetail.value?.reviews?.length || 0) / 3))
const paginatedReviews = computed(() => {
  const start = (reviewPage.value - 1) * 3
  return hotelDetail.value?.reviews?.slice(start, start + 3) || []
})

function t(key) {
  return translations.zh[key]
}

function cityLabel(city) {
  const key = city?.trim().toLowerCase()
  return cityLabels[key] || city
}

function cityApiValue(city) {
  const value = city.trim()
  return cityApiValues[value] || cityApiValues[value.toLowerCase()] || value
}

function amenityLabel(amenity) {
  const key = amenity.trim().toLowerCase()
  return amenityLabels[key] || amenity.trim()
}

function breakfastLabel(value) {
  if (value === true) return t('breakfastIncluded')
  if (value === false) return t('breakfastExcluded')
  return t('unknown')
}

function booleanLabel(value) {
  if (value === true) return '是'
  if (value === false) return '否'
  return t('unknown')
}

function displayFields(fields) {
  return fields.map(([label, value]) => [
    label,
    value === null || value === undefined || value === '' ? t('unknown') : value,
  ])
}

function hotelFields(detail) {
  const hotel = detail.hotel
  return displayFields([
    [t('hotelId'), hotel.id],
    [t('city'), cityLabel(hotel.city)],
    [t('province'), hotel.province],
    [t('district'), hotel.district],
    [t('address'), hotel.address],
    [t('phone'), hotel.phone],
    [t('ratingValue'), hotel.rating],
    [t('amenities'), detail.amenities?.map(amenityLabel).join('、')],
    [t('openingYear'), hotel.openingYear],
    [t('renovationYear'), hotel.renovationYear],
    [t('gym'), booleanLabel(hotel.gym)],
    [t('laundry'), booleanLabel(hotel.laundry)],
    [t('parking'), booleanLabel(hotel.parking)],
    [t('luggageStorage'), booleanLabel(hotel.luggageStorage)],
    [t('robotService'), booleanLabel(hotel.robotService)],
    [t('restaurant'), booleanLabel(hotel.restaurant)],
    [t('taxiService'), booleanLabel(hotel.taxiService)],
    [t('meetingRoom'), booleanLabel(hotel.meetingRoom)],
    [t('accessibility'), booleanLabel(hotel.accessibility)],
  ])
}

function roomFields(room) {
  return displayFields([
    [t('price'), room.pricePerNight],
    [t('breakfast'), breakfastLabel(room.breakfast)],
  ])
}

function reviewFields(review) {
  return displayFields([
    [t('reviewId'), review.reviewId],
    [t('reviewRating'), review.rating],
    [t('reviewDate'), review.createdAt?.slice(0, 10)],
    [t('reviewContent'), review.content],
  ])
}

function routeModeLabel(mode) {
  const labels = { walking: '步行', driving: '驾车', transit: '公共交通', cycling: '骑行' }
  return labels[mode] || mode
}

function routeDistance(meters) {
  return `${(meters / 1000).toFixed(1)} 公里`
}

function routeDuration(seconds) {
  const minutes = Math.round(seconds / 60)
  return minutes >= 60
    ? `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
    : `${minutes} 分钟`
}

function stateLabel(value) {
  return stateLabels[value] || value
}

function toolLabel(value) {
  return observationToolLabels[value] || value
}

function statusLabel(value) {
  return observationStatusLabels[value] || value
}

function errorMessage(cause) {
  if (cause instanceof TypeError && cause.message === 'Failed to fetch') {
    return t('connectionError')
  }
  return cause.message || t('genericError')
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(body.message || `请求失败（${response.status}）`)
  }
  return body
}

function resetConversation() {
  sessionId.value = ''
  messages.value = []
  recommendations.value = []
  observations.value = []
  evaluation.value = null
  requirements.value = null
  state.value = 'CONNECTING'
  draft.value = ''
  showFeedback.value = null
  feedback.value = {}
}

function attachHotelCards(conversationMessages, reply) {
  const result = conversationMessages.map(message => ({ ...message }))
  const appliedRelaxations = reply.requirements?.appliedRelaxations || []
  if (!reply.hotelCards?.length && !appliedRelaxations.length) return result
  for (let index = result.length - 1; index >= 0; index -= 1) {
    if (result[index].role === 'agent' && result[index].text === reply.message) {
      result[index].hotelCards = reply.hotelCards || []
      result[index].appliedRelaxations = appliedRelaxations
      break
    }
  }
  return result
}

function relaxationLabel(condition) {
  const [kind, amenity] = condition.split(':', 2)
  if (kind === 'priorityAmenity' || kind === 'generalAmenity') {
    return `${kind === 'priorityAmenity' ? '优先' : '一般'}设施：${amenityLabel(amenity || '')}`
  }
  return ({
    budget: '每晚最高预算',
    amenity: '必需设施',
    dates: '入住日期',
    city: '城市',
    priorityPrice: '优先每晚最高价格',
    generalPrice: '一般每晚最高价格',
    priorityMinPrice: '优先每晚最低价格',
    generalMinPrice: '一般每晚最低价格',
  })[kind] || condition
}

function applyConversation(reply, conversationMessages) {
  resetConversation()
  sessionId.value = reply.sessionId
  state.value = reply.state
  requirements.value = reply.requirements
  recommendations.value = reply.recommendations || []
  observations.value = reply.observations || []
  evaluation.value = reply.evaluation || null
  messages.value = attachHotelCards(conversationMessages, reply)
}

async function loadHistory() {
  historySessions.value = await request(`/api/agent/sessions?${new URLSearchParams({ userId })}`)
  historyLoaded.value = true
}

async function createSession() {
  if (sessionBusy.value) return
  historyBusy.value = true
  error.value = ''
  try {
    const reply = await request('/api/agent/sessions', {
      method: 'POST',
      body: JSON.stringify({ userId }),
    })
    applyConversation(reply, [{ role: 'agent', text: reply.message }])
    await loadHistory()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    historyBusy.value = false
  }
}

async function selectHistory(id) {
  if (sessionBusy.value) return
  historyBusy.value = true
  error.value = ''
  try {
    const history = await request(`/api/agent/sessions/${encodeURIComponent(id)}?${new URLSearchParams({ userId })}`)
    history.messages = attachHotelCards(history.messages, history.latestReply)
    historySelection.value = history
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    historyBusy.value = false
  }
}

function continueConversation() {
  if (!historySelection.value || sessionBusy.value) return
  if (sessionId.value !== historySelection.value.latestReply.sessionId) {
    applyConversation(historySelection.value.latestReply, historySelection.value.messages)
  }
  activePage.value = 'recommendation'
  error.value = ''
}

async function deleteSession(id) {
  if (sessionBusy.value || !window.confirm(t('confirmDelete'))) return
  historyBusy.value = true
  error.value = ''
  try {
    await request(`/api/agent/sessions/${encodeURIComponent(id)}?${new URLSearchParams({ userId })}`, { method: 'DELETE' })
    if (sessionId.value === id) resetConversation()
    if (historySelection.value?.latestReply.sessionId === id) historySelection.value = null
    await loadHistory()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    historyBusy.value = false
  }
}

async function openPage(page) {
  if (sessionBusy.value) return
  activePage.value = page
  error.value = ''
  if ((page === 'history' || (page === 'recommendation' && !sessionId.value)) && !sessionBusy.value) {
    historyBusy.value = true
    try {
      await loadHistory()
      if (page === 'history' && historySelection.value) {
        const selectedId = historySelection.value.latestReply.sessionId
        historySelection.value = historySessions.value.some(item => item.sessionId === selectedId)
          ? await request(`/api/agent/sessions/${encodeURIComponent(selectedId)}?${new URLSearchParams({ userId })}`)
          : null
      } else if (page === 'recommendation' && historySessions.value.length) {
        const history = await request(`/api/agent/sessions/${encodeURIComponent(historySessions.value[0].sessionId)}?${new URLSearchParams({ userId })}`)
        applyConversation(history.latestReply, history.messages)
      }
    } catch (cause) {
      historyLoaded.value = false
      error.value = errorMessage(cause)
    } finally {
      historyBusy.value = false
    }
    if (page === 'recommendation' && activePage.value === page && historyLoaded.value && !historySessions.value.length) await createSession()
  }
}

async function openHotelDetails(hotelId, returnPage) {
  if (detailLoading.value) return
  detailLoading.value = true
  error.value = ''
  try {
    hotelDetail.value = await request(`/api/hotels/${hotelId}`)
    reviewPage.value = 1
    detailReturnPage.value = returnPage
    activePage.value = 'hotel-detail'
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    detailLoading.value = false
  }
}

function changeReviewPage(page) {
  reviewPage.value = Math.min(Math.max(page, 1), reviewPageCount.value)
}

function closeHotelDetails() {
  activePage.value = detailReturnPage.value
  error.value = ''
}

async function searchHotels() {
  if (homeLoading.value) return
  if (!homeCity.value.trim() || !checkIn.value || !checkOut.value) {
    error.value = t('requiredFields')
    return
  }
  if (checkOut.value <= checkIn.value) {
    error.value = t('invalidDateRange')
    return
  }

  homeLoading.value = true
  error.value = ''
  homeSearched.value = true
  try {
    const params = new URLSearchParams({
      city: cityApiValue(homeCity.value),
      checkIn: checkIn.value,
      checkOut: checkOut.value,
    })
    hotelResults.value = await request(`/api/hotels?${params}`)
  } catch (cause) {
    hotelResults.value = []
    error.value = errorMessage(cause)
  } finally {
    homeLoading.value = false
  }
}

async function sendMessage() {
  const text = draft.value.trim()
  if (!text || sessionBusy.value || !sessionId.value) return

  messages.value.push({ role: 'user', text })
  draft.value = ''
  loading.value = true
  error.value = ''
  let replied = false
  try {
    const reply = await request(`/api/agent/sessions/${sessionId.value}/messages`, {
      method: 'POST',
      body: JSON.stringify({ userId, text }),
    })
    state.value = reply.state
    requirements.value = reply.requirements
    recommendations.value = reply.recommendations || []
    observations.value = reply.observations || []
    evaluation.value = reply.evaluation || null
    messages.value.push({
      role: 'agent',
      text: reply.message,
      hotelCards: reply.hotelCards || [],
      appliedRelaxations: reply.requirements?.appliedRelaxations || [],
    })
    replied = true
    await loadHistory()
  } catch (cause) {
    if (!replied) {
      messages.value.pop()
      draft.value = text
      try {
        const history = await request(`/api/agent/sessions/${encodeURIComponent(sessionId.value)}?${new URLSearchParams({ userId })}`)
        evaluation.value = history.latestReply.evaluation || null
      } catch (refreshCause) {
        evaluation.value = null
        error.value = `${errorMessage(cause)}；评估结果刷新失败：${errorMessage(refreshCause)}`
        return
      }
    }
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function submitFeedback(hotelId) {
  if (sessionBusy.value) return
  const entry = feedback.value[hotelId] || {}
  if (!entry.comment?.trim()) {
    error.value = t('reviewRequired')
    return
  }
  feedbackBusy.value = true
  try {
    await request(`/api/agent/sessions/${sessionId.value}/feedback`, {
      method: 'POST',
      body: JSON.stringify({
        userId,
        hotelId,
        rating: Number(entry.rating || 5),
        comment: entry.comment.trim(),
      }),
    })
    showFeedback.value = null
    feedback.value[hotelId] = { rating: 5, comment: '' }
    messages.value.push({ role: 'agent', text: '感谢反馈！评价已保存，并可用于改进此演示用户后续收到的推荐。' })
    error.value = ''
    await loadHistory()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    feedbackBusy.value = false
  }
}

function submitOnEnter(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

function openDatePicker(which) {
  const input = which === 'checkIn' ? checkInPicker.value : checkOutPicker.value
  if (typeof input.showPicker === 'function') {
    input.showPicker()
  } else {
    input.click()
  }
}

function nextDate(date) {
  const next = new Date(`${date}T00:00:00Z`)
  next.setUTCDate(next.getUTCDate() + 1)
  return next.toISOString().slice(0, 10)
}

function initializeCheckOut() {
  if (checkIn.value) checkOut.value = nextDate(checkIn.value)
}

function displayText(text) {
  return text
}

</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#" :aria-label="t('home')" @click.prevent="openPage('home')">
        <span class="brand-mark">住</span>
        <span>住选</span>
      </a>
      <p class="sidebar-label">{{ t('discover') }}</p>
      <nav class="sidebar-nav" :aria-label="t('discover')">
        <button :class="{ active: activePage === 'home' }" type="button" :disabled="sessionBusy" @click="openPage('home')">
          <span class="nav-icon" aria-hidden="true">⌂</span>
          <span>{{ t('home') }}</span>
        </button>
        <button :class="{ active: activePage === 'recommendation' }" type="button" :disabled="sessionBusy" @click="openPage('recommendation')">
          <span class="nav-icon sparkle-icon" aria-hidden="true">✳</span>
          <span>{{ t('recommendationNav') }}</span>
          <span class="nav-badge">AI</span>
        </button>
        <button :class="{ active: activePage === 'history' }" type="button" :disabled="sessionBusy" @click="openPage('history')">
          <span class="nav-icon" aria-hidden="true">◷</span>
          <span>{{ t('historyTitle') }}</span>
        </button>
      </nav>
      <div class="sidebar-footer">
        <span class="status-dot"></span>
        <span>{{ t('staticDataset') }}</span>
      </div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div class="breadcrumb">酒店筛选助手 <span>/</span> {{ activePage === 'home' ? t('home') : activePage === 'history' ? t('historyTitle') : activePage === 'hotel-detail' ? t('details') : t('recommendationNav') }}</div>
        <div class="topbar-meta">
          <span class="status-dot"></span>
          <span>{{ t('tagline') }}</span>
        </div>
      </header>

      <section v-if="activePage === 'home'" class="home-page">
        <section class="hero">
          <p class="eyebrow">{{ t('homeEyebrow') }}</p>
          <h1>{{ t('homeTitle') }}<br /><em>{{ t('homeTitleEmphasis') }}</em></h1>
          <p class="hero-copy">{{ t('homeDescription') }}</p>
        </section>

        <form class="hotel-search-form" @submit.prevent="searchHotels">
          <label class="search-field city-field">
            <span>{{ t('destination') }}</span>
            <input v-model="homeCity" list="hotel-cities" :placeholder="t('destinationPlaceholder')" required />
            <datalist id="hotel-cities">
              <option value="杭州市" />
            </datalist>
          </label>
          <div class="search-field">
            <span id="check-in-label">{{ t('checkIn') }}</span>
            <button class="date-display" :class="{ placeholder: !checkIn }" type="button" aria-labelledby="check-in-label" @click="openDatePicker('checkIn')">
              {{ checkIn || t('chooseDate') }}<span aria-hidden="true">▣</span>
            </button>
            <input ref="checkInPicker" v-model="checkIn" class="native-date-picker" type="date" tabindex="-1" aria-hidden="true" @change="initializeCheckOut" />
          </div>
          <div class="search-field">
            <span id="check-out-label">{{ t('checkOut') }}</span>
            <button class="date-display" :class="{ placeholder: !checkOut }" type="button" aria-labelledby="check-out-label" @click="openDatePicker('checkOut')">
              {{ checkOut || t('chooseDate') }}<span aria-hidden="true">▣</span>
            </button>
            <input ref="checkOutPicker" v-model="checkOut" class="native-date-picker" type="date" :min="checkIn ? nextDate(checkIn) : undefined" tabindex="-1" aria-hidden="true" />
          </div>
          <button class="search-button" type="submit" :disabled="homeLoading">
            <span aria-hidden="true">⌕</span>
            {{ homeLoading ? t('searching') : t('search') }}
          </button>
        </form>

        <div v-if="error" class="error-banner home-error" role="alert">{{ error }}</div>

        <section v-if="homeSearched" class="hotel-results">
          <div class="results-heading home-results-heading">
            <div>
              <p class="eyebrow">{{ t('searchResults') }}</p>
              <h2>{{ cityLabel(homeCity) }}的酒店</h2>
            </div>
            <span class="result-count">
              {{ hotelResults.length }} {{ t('hotels') }}
            </span>
          </div>

          <div v-if="homeLoading" class="home-empty-state">{{ t('searchingRooms') }}</div>
          <div v-else-if="!hotelResults.length" class="home-empty-state">
            {{ t('noHotels') }}
          </div>

          <article v-for="hotel in hotelResults" :key="hotel.id" class="browse-hotel-card">
            <div class="hotel-thumb">
              <img v-if="hotel.image" :src="hotel.image" :alt="`${hotel.name}酒店图片`" />
              <span v-else>{{ t('unknown') }}</span>
            </div>
            <div class="browse-hotel-info">
              <div class="browse-hotel-title">
                <div>
                  <h3>{{ hotel.name }}</h3>
                  <p class="hotel-location">{{ hotel.address }} · {{ cityLabel(hotel.city) }}</p>
                </div>
                <span class="rating-chip">★ {{ hotel.rating == null ? t('unknown') : hotel.rating.toFixed(1) }} {{ t('rating') }}</span>
              </div>
              <div class="browse-hotel-bottom">
                <div class="browse-amenities">
                  <span v-for="amenity in (hotel.amenities || '').split(',').filter(Boolean).slice(0, 3)" :key="amenity">{{ amenityLabel(amenity) }}</span>
                </div>
                <div class="browse-price"><strong>{{ hotel.pricePerNight ?? t('unknown') }}</strong><span>{{ t('perNight') }}</span></div>
              </div>
              <button class="feedback-link" type="button" @click="openHotelDetails(hotel.id, 'home')">
                {{ t('viewRooms') }}
                <span aria-hidden="true">↗</span>
              </button>
            </div>
          </article>
        </section>
        <section v-else class="home-prompt">
          <span class="home-prompt-icon" aria-hidden="true">⌖</span>
          <div>
            <h2>{{ t('homePrompt') }}</h2>
            <p>{{ t('homePromptDescription') }}</p>
          </div>
        </section>
      </section>

      <section v-else-if="activePage === 'hotel-detail'" class="hotel-detail-page">
        <button type="button" class="back-button" @click="closeHotelDetails">← {{ t('back') }}</button>
        <div v-if="hotelDetail" class="hotel-detail-content">
          <section class="hotel-detail-heading">
            <img v-if="hotelDetail.hotel.image" :src="hotelDetail.hotel.image" :alt="`${hotelDetail.hotel.name}酒店图片`" />
            <div>
              <p class="eyebrow">{{ t('detailSubtitle') }}</p>
              <h1>{{ hotelDetail.hotel.name }}</h1>
              <p class="hotel-location">{{ hotelDetail.hotel.address }} · {{ cityLabel(hotelDetail.hotel.city) }}</p>
            </div>
          </section>
          <section class="detail-section">
            <h2>{{ t('details') }}</h2>
            <dl class="detail-grid">
              <div v-for="field in hotelFields(hotelDetail)" :key="field[0]">
                <dt>{{ field[0] }}</dt><dd>{{ field[1] }}</dd>
              </div>
            </dl>
          </section>
          <section class="detail-section">
            <h2>{{ t('roomOptions') }}</h2>
            <p v-if="!hotelDetail.roomOffers?.length">{{ t('noRoomInfo') }}</p>
            <article v-for="room in hotelDetail.roomOffers" :key="room.id" class="detail-record">
              <h3>{{ room.roomName || t('unknown') }}</h3>
              <dl class="detail-grid">
                <div v-for="field in roomFields(room)" :key="field[0]">
                  <dt>{{ field[0] }}</dt><dd>{{ field[1] }}</dd>
                </div>
              </dl>
            </article>
          </section>
          <section class="detail-section">
            <h2>{{ t('reviews') }}</h2>
            <p v-if="!hotelDetail.reviews?.length">{{ t('noReviews') }}</p>
            <article v-for="(review, index) in paginatedReviews" :key="review.reviewId || `${reviewPage}-${index}`" class="detail-record">
              <dl class="detail-grid">
                <div v-for="field in reviewFields(review)" :key="field[0]">
                  <dt>{{ field[0] }}</dt><dd>{{ field[1] }}</dd>
                </div>
              </dl>
            </article>
            <nav v-if="reviewPageCount > 1" class="review-pagination" aria-label="酒店评价分页">
              <button type="button" :disabled="reviewPage === 1" @click="changeReviewPage(reviewPage - 1)">{{ t('previousPage') }}</button>
              <span>第 {{ reviewPage }} / {{ reviewPageCount }} 页</span>
              <button type="button" :disabled="reviewPage === reviewPageCount" @click="changeReviewPage(reviewPage + 1)">{{ t('nextPage') }}</button>
            </nav>
          </section>
        </div>
      </section>

      <section v-else-if="activePage === 'history'" class="history-page">
        <div v-if="error" class="error-banner" role="alert">{{ error }}</div>
        <section class="history-workspace" :aria-label="t('historyTitle')">
          <aside class="history-panel">
            <div class="history-heading">
              <h2>{{ t('historyTitle') }}</h2>
            </div>
            <p v-if="historyBusy" class="history-empty" role="status">{{ t('historyLoading') }}</p>
            <p v-else-if="historyLoaded && !historySessions.length" class="history-empty">{{ t('noHistory') }}</p>
            <ul v-if="historySessions.length" class="history-list">
              <li v-for="item in historySessions" :key="item.sessionId" class="history-item" :class="{ active: historySelection?.latestReply.sessionId === item.sessionId }">
                <button type="button" class="history-select" :disabled="sessionBusy" :aria-current="historySelection?.latestReply.sessionId === item.sessionId ? 'true' : undefined" @click="selectHistory(item.sessionId)">
                  <span>{{ item.title || t('untitledSession') }}</span>
                  <time>{{ item.updatedAt.replace('T', ' ').slice(0, 16) }}</time>
                </button>
                <button type="button" class="history-delete" :disabled="sessionBusy" :aria-label="`${t('deleteSession')}: ${item.title || t('untitledSession')}`" @click="deleteSession(item.sessionId)">×</button>
              </li>
            </ul>
          </aside>
          <div class="history-details">
            <div class="history-heading">
              <h2>{{ t('historyDetail') }}</h2>
              <button v-if="historySelection" type="button" class="new-session-button" :disabled="sessionBusy" @click="continueConversation">
                {{ t('continueConversation') }}
              </button>
            </div>
            <p v-if="!historySelection" class="history-empty">{{ t('selectHistory') }}</p>
            <template v-else>
              <span class="state-chip history-state">{{ stateLabel(historySelection.latestReply.state) }}</span>
              <div class="history-messages" aria-live="polite">
                <article v-for="message in historySelection.messages" :key="message.id" class="message" :class="message.role">
                  <div class="message-avatar">{{ message.role === 'agent' ? 'S' : 'Y' }}</div>
                  <div class="message-body">
                    <p>{{ message.role === 'agent' ? displayText(message.text) : message.text }}</p>
                    <div v-if="message.hotelCards?.length" class="message-hotel-cards">
                      <button
                        v-for="(card, index) in message.hotelCards"
                        :key="card.hotelId"
                        type="button"
                        class="message-hotel-card"
                        :aria-label="`${card.name}，${t('viewRooms')}`"
                        @click="openHotelDetails(card.hotelId, 'history')"
                      >
                        <span class="message-card-image">
                          <img v-if="card.image" :src="card.image" :alt="`${card.name}酒店图片`" />
                          <span v-else aria-hidden="true">住</span>
                        </span>
                        <span class="message-card-content">
                          <span class="message-card-overline">
                            {{ t('recommendation') }} 0{{ index + 1 }}
                            <span>{{ card.score }} {{ t('matchScore') }}</span>
                          </span>
                          <strong>{{ card.name }}</strong>
                          <span class="message-card-location">{{ cityLabel(card.city) }} · {{ card.address || t('unknown') }}</span>
                          <span class="message-card-price">
                            {{ card.roomName || t('unknown') }} · {{ card.pricePerNight ?? t('unknown') }}{{ card.pricePerNight == null ? '' : t('perNight') }}
                          </span>
                          <span v-if="card.reasons?.length" class="message-card-evidence">
                            选择理由：{{ card.reasons.join('；') }}
                          </span>
                          <span v-if="card.satisfiedRequiredConditions?.length" class="message-card-evidence">
                            满足必要条件：{{ card.satisfiedRequiredConditions.join('；') }}
                          </span>
                          <span v-if="card.satisfiedPreferences?.length" class="message-card-evidence">
                            满足偏好：{{ card.satisfiedPreferences.join('；') }}
                          </span>
                          <span v-if="card.unmetPreferences?.length" class="message-card-evidence">
                            未满足偏好：{{ card.unmetPreferences.join('；') }}
                          </span>
                          <span v-if="card.unknownPreferences?.length" class="message-card-evidence">
                            未知偏好：{{ card.unknownPreferences.join('；') }}
                          </span>
                        </span>
                      </button>
                    </div>
                    <div v-if="message.appliedRelaxations?.length" class="message-relaxations">
                      <strong>已确认并实际应用的条件调整</strong>
                      <span v-for="(relaxation, index) in message.appliedRelaxations" :key="`${relaxation.condition}-${index}`">
                        {{ relaxationLabel(relaxation.condition) }}：{{ relaxation.beforeValue }} → {{ relaxation.afterValue }}
                      </span>
                    </div>
                  </div>
                </article>
              </div>
              <div v-if="historySelection.latestReply.observations?.length || historySelection.latestReply.evaluation" class="observations">
                <p class="eyebrow">{{ t('observations') }}</p>
                <div v-for="item in historySelection.latestReply.observations" :key="`${item.toolName}-${item.status}`" class="observation">
                  <span class="observation-indicator" :class="item.status.toLowerCase()"></span>
                  <strong>{{ toolLabel(item.toolName) }} · {{ statusLabel(item.status) }}</strong>
                  <span>{{ displayText(item.summary) }}</span>
                </div>
                <EvaluationPanel v-if="historySelection.latestReply.evaluation" :evaluation="historySelection.latestReply.evaluation" />
              </div>
              <div v-if="historySelection.latestReply.requirements" class="requirements-note">
                <p class="eyebrow">{{ t('currentSearch') }}</p>
                <span v-if="historySelection.latestReply.requirements.city">{{ cityLabel(historySelection.latestReply.requirements.city) }}</span>
                <span v-if="historySelection.latestReply.requirements.checkIn || historySelection.latestReply.requirements.checkOut">{{ historySelection.latestReply.requirements.checkIn || '—' }} → {{ historySelection.latestReply.requirements.checkOut || '—' }}</span>
                <span v-if="historySelection.latestReply.requirements.maxPricePerNight">{{ t('maxPerNight') }} {{ historySelection.latestReply.requirements.maxPricePerNight }}{{ t('perNight') }}</span>
              </div>
            </template>
          </div>
        </section>
      </section>

      <section v-else class="recommendation-page">
        <section class="hero recommendation-hero">
          <p class="hero-copy">{{ t('recommendDescription') }}</p>
          <button type="button" class="new-session-button" :disabled="sessionBusy" @click="createSession">
            + {{ t('newSession') }}
          </button>
        </section>

        <section class="workspace" :aria-label="t('assistantLabel')">
      <div class="chat-column">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">{{ t('companion') }}</p>
            <h2>{{ t('chatTitle') }}</h2>
          </div>
          <span class="state-chip">{{ stateLabel(state) }}</span>
        </div>

        <div class="conversation" aria-live="polite">
          <article
            v-for="(message, index) in messages"
            :key="`${message.role}-${index}`"
            class="message"
            :class="message.role"
          >
            <div class="message-avatar">{{ message.role === 'agent' ? 'S' : 'Y' }}</div>
            <div class="message-body">
              <p>{{ message.role === 'agent' ? displayText(message.text) : message.text }}</p>
              <div v-if="message.hotelCards?.length" class="message-hotel-cards">
                <button
                  v-for="(card, index) in message.hotelCards"
                  :key="card.hotelId"
                  type="button"
                  class="message-hotel-card"
                  :aria-label="`${card.name}，${t('viewRooms')}`"
                  @click="openHotelDetails(card.hotelId, 'recommendation')"
                >
                  <span class="message-card-image">
                    <img v-if="card.image" :src="card.image" :alt="`${card.name}酒店图片`" />
                    <span v-else aria-hidden="true">住</span>
                  </span>
                  <span class="message-card-content">
                    <span class="message-card-overline">
                      {{ t('recommendation') }} 0{{ index + 1 }}
                      <span>{{ card.score }} {{ t('matchScore') }}</span>
                    </span>
                    <strong>{{ card.name }}</strong>
                    <span class="message-card-location">{{ cityLabel(card.city) }} · {{ card.address || t('unknown') }}</span>
                    <span class="message-card-price">
                      {{ card.roomName || t('unknown') }} · {{ card.pricePerNight ?? t('unknown') }}{{ card.pricePerNight == null ? '' : t('perNight') }}
                    </span>
                    <span v-if="card.reasons?.length" class="message-card-evidence">
                      选择理由：{{ card.reasons.join('；') }}
                    </span>
                    <span v-if="card.satisfiedRequiredConditions?.length" class="message-card-evidence">
                      满足必要条件：{{ card.satisfiedRequiredConditions.join('；') }}
                    </span>
                    <span v-if="card.satisfiedPreferences?.length" class="message-card-evidence">
                      满足偏好：{{ card.satisfiedPreferences.join('；') }}
                    </span>
                    <span v-if="card.unmetPreferences?.length" class="message-card-evidence">
                      未满足偏好：{{ card.unmetPreferences.join('；') }}
                    </span>
                    <span v-if="card.unknownPreferences?.length" class="message-card-evidence">
                      未知偏好：{{ card.unknownPreferences.join('；') }}
                    </span>
                  </span>
                </button>
              </div>
              <div v-if="message.appliedRelaxations?.length" class="message-relaxations">
                <strong>已确认并实际应用的条件调整</strong>
                <span v-for="(relaxation, index) in message.appliedRelaxations" :key="`${relaxation.condition}-${index}`">
                  {{ relaxationLabel(relaxation.condition) }}：{{ relaxation.beforeValue }} → {{ relaxation.afterValue }}
                </span>
              </div>
            </div>
          </article>
          <div v-if="loading" class="thinking"><span></span><span></span><span></span> {{ t('loading') }}</div>
          <div v-if="error" class="error-banner" role="alert">{{ error }}</div>
        </div>

        <form class="composer" @submit.prevent="sendMessage">
          <label class="sr-only" for="request">{{ t('describeNeeds') }}</label>
          <textarea
            id="request"
            v-model="draft"
            rows="2"
            :placeholder="t('example')"
            :disabled="sessionBusy || !sessionId"
            @keydown="submitOnEnter"
          ></textarea>
          <div class="composer-footer">
            <span>{{ t('dateHelp') }}</span>
            <button type="submit" :disabled="!canSend" :aria-label="t('sendNeeds')">
              <span>{{ loading ? t('searching') : t('send') }}</span>
              <span aria-hidden="true">↗</span>
            </button>
          </div>
        </form>

        <div v-if="observations.length || evaluation" class="observations">
          <p class="eyebrow">{{ t('observations') }}</p>
          <div v-for="item in observations" :key="`${item.toolName}-${item.status}`" class="observation">
            <span class="observation-indicator" :class="item.status.toLowerCase()"></span>
            <strong>{{ toolLabel(item.toolName) }} · {{ statusLabel(item.status) }}</strong>
            <span>{{ displayText(item.summary) }}</span>
          </div>
          <EvaluationPanel v-if="evaluation" :evaluation="evaluation" />
        </div>
      </div>

      <div class="results-column-wrap">
      <aside class="results-column" tabindex="0" aria-labelledby="recommendations-title">
        <div class="results-heading">
          <p class="eyebrow">{{ t('shortlist') }}</p>
          <h2 id="recommendations-title">{{ t('bestMatches') }}</h2>
          <p>{{ t('mustHaves') }}</p>
        </div>

        <div v-if="!recommendations.length" class="empty-state">
          <div class="empty-art" aria-hidden="true"><span></span><span></span><span></span></div>
          <p>{{ t('recommendationsPrompt') }}</p>
        </div>

        <article v-for="(hotel, index) in recommendations" :key="hotel.hotelId" class="hotel-card">
          <div class="hotel-card-top">
            <span class="match-index">{{ t('recommendation') }} 0{{ index + 1 }}</span>
            <span class="score-pill">{{ hotel.score }} {{ t('rating') }}</span>
          </div>
          <h3>{{ hotel.name }}</h3>
          <p class="hotel-location">{{ hotel.address }}</p>
          <div class="hotel-stats">
            <div><strong>{{ hotel.rating == null ? t('unknown') : hotel.rating.toFixed(1) }}</strong><span>{{ t('hotelRating') }}</span></div>
            <div><strong>{{ hotel.pricePerNight ?? t('unknown') }}<small>{{ t('perNight') }}</small></strong><span>{{ t('sourcePrice') }}</span></div>
          </div>
          <ul class="reasons">
            <li v-for="reason in hotel.reasons" :key="reason">{{ displayText(reason) }}</li>
          </ul>
          <div v-if="hotel.routes?.length" class="route-options">
            <h4>{{ t('route') }}</h4>
            <article v-for="route in hotel.routes" :key="`${route.transportMode}-${route.hotelId}`">
              <strong>{{ routeModeLabel(route.transportMode) }}</strong>
              <span>{{ routeDistance(route.distanceMeters) }} · {{ routeDuration(route.durationSeconds) }}</span>
              <ol v-if="route.routeSteps?.length">
                <li v-for="(step, index) in route.routeSteps" :key="index">{{ step }}</li>
              </ol>
            </article>
          </div>
          <p v-else-if="observations.some(item => item.toolName === 'distance_calculation' && item.status === 'UNAVAILABLE')" class="route-unavailable">{{ t('routeUnavailable') }}</p>
          <button class="feedback-link" type="button" @click="openHotelDetails(hotel.hotelId, 'recommendation')">
            {{ t('viewRooms') }}
            <span aria-hidden="true">↗</span>
          </button>
          <div v-if="showFeedback === hotel.hotelId" class="feedback-form">
            <label :for="`rating-${hotel.hotelId}`">{{ t('yourRating') }}</label>
            <select :id="`rating-${hotel.hotelId}`" v-model="feedback[hotel.hotelId].rating">
              <option v-for="rating in [5, 4, 3, 2, 1]" :key="rating" :value="rating">{{ rating }} / 5 分</option>
            </select>
            <label :for="`comment-${hotel.hotelId}`">{{ t('shareExperience') }}</label>
            <textarea :id="`comment-${hotel.hotelId}`" v-model="feedback[hotel.hotelId].comment" rows="2"></textarea>
            <button class="feedback-submit" type="button" :disabled="sessionBusy" @click="submitFeedback(hotel.hotelId)">{{ t('saveReview') }}</button>
          </div>
          <button
            v-else
            class="feedback-link"
            type="button"
            @click="showFeedback = hotel.hotelId; feedback[hotel.hotelId] = { rating: 5, comment: '' }"
          >{{ t('writeReview') }} <span aria-hidden="true">↗</span></button>
        </article>

        <div v-if="requirements" class="requirements-note">
          <p class="eyebrow">{{ t('currentSearch') }}</p>
          <span>{{ cityLabel(requirements.city) }} · {{ requirements.checkIn }} → {{ requirements.checkOut }}</span>
          <span v-if="requirements.maxPricePerNight">{{ t('maxPerNight') }} {{ requirements.maxPricePerNight }}{{ t('perNight') }}</span>
        </div>
      </aside>
      </div>
        </section>

        <footer><span>住选</span><span>{{ t('footer') }}</span></footer>
      </section>
    </main>
  </div>
</template>
