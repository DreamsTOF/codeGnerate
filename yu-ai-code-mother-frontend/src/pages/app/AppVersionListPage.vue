<template>
  <div class="version-list-container">
    <a-page-header
      :title="appName"
      @back="() => $router.back()"
    >
      <template #extra>
        <a-button type="primary" @click="handleCompareVersions">
          对比版本
        </a-button>
      </template>
    </a-page-header>

    <div class="version-content">
      <a-list
        :loading="loading"
        :data-source="versionList"
        :pagination="pagination"
        itemLayout="horizontal"
      >
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                <span class="version-title">版本 {{ item.version }}</span>
              </template>
              <template #description>
                <div class="version-info">
                  <span class="version-message">{{ item.message || '无描述' }}</span>
                  <span class="version-time">{{ formatTime(item.createTime) }}</span>
                </div>
              </template>
            </a-list-item-meta>
            <template #extra>
              <a-space>
                <a-button
                  type="link"
                  @click="handleViewVersion(item)"
                >
                  查看详情
                </a-button>
                <a-checkbox
                  v-model:checked="selectedVersions[item.id!]"
                  @change="handleVersionSelect"
                >
                  选择对比
                </a-checkbox>
              </a-space>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </div>

    <!-- 版本对比弹窗 -->
    <a-modal
      v-model:open="compareModalVisible"
      title="版本对比"
      width="90%"
      :footer="null"
      @cancel="handleCompareCancel"
    >
      <div v-if="compareResult" class="compare-content">
        <a-alert
          v-if="compareError"
          :message="compareError"
          type="error"
          show-icon
          class="compare-error"
        />
        <div v-else>
          <div class="compare-header">
            <div class="compare-header-item">
              <h4>版本 {{ compareResult.fromVersionData?.version }}</h4>
              <p>{{ compareResult.fromVersionData?.message || '无描述' }}</p>
            </div>
            <div class="compare-header-item">
              <h4>版本 {{ compareResult.toVersionData?.version }}</h4>
              <p>{{ compareResult.toVersionData?.message || '无描述' }}</p>
            </div>
          </div>
          <div class="compare-split-view">
            <!-- 左侧：旧版本 -->
            <div class="compare-panel compare-panel-left">
              <div class="panel-header">
                <h5>旧版本 (v{{ compareResult.fromVersionData?.version }})</h5>
              </div>
              <div class="panel-content">
                <div
                  v-for="(line, index) in fromVersionLines"
                  :key="'from-' + index"
                  class="code-line"
                  :class="getLineClass('from', index)"
                >
                  <div class="line-number">{{ index + 1 }}</div>
                  <div class="line-content">
                    <pre>{{ line }}</pre>
                  </div>
                </div>
              </div>
            </div>

            <!-- 右侧：新版本 -->
            <div class="compare-panel compare-panel-right">
              <div class="panel-header">
                <h5>新版本 (v{{ compareResult.toVersionData?.version }})</h5>
              </div>
              <div class="panel-content">
                <div
                  v-for="(line, index) in toVersionLines"
                  :key="'to-' + index"
                  class="code-line"
                  :class="getLineClass('to', index)"
                >
                  <div class="line-number">{{ index + 1 }}</div>
                  <div class="line-content">
                    <pre>{{ line }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- 版本详情弹窗 -->
    <a-modal
      v-model:open="detailModalVisible"
      :title="`版本 ${selectedVersion?.version} 详情`"
      width="80%"
      :footer="null"
      @cancel="handleDetailCancel"
    >
      <div v-if="selectedVersion" class="version-detail">
        <div class="version-meta">
          <a-descriptions :column="2">
            <a-descriptions-item label="版本号">
              {{ selectedVersion.version }}
            </a-descriptions-item>
            <a-descriptions-item label="存储类型">
              {{ selectedVersion.storageType }}
            </a-descriptions-item>
            <a-descriptions-item label="创建时间">
              {{ formatTime(selectedVersion.createTime) }}
            </a-descriptions-item>
            <a-descriptions-item label="描述">
              {{ selectedVersion.message || '无描述' }}
            </a-descriptions-item>
          </a-descriptions>
        </div>
        <div class="version-code">
          <h4>代码内容：</h4>
          <pre class="code-content">{{ selectedVersion.content }}</pre>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  list,
  getInfo,
  compare
} from '@/api/appVersionController'
import { formatTime } from '@/utils/time'
import { calculateDiff } from '@/utils/diffUtils'

const route = useRoute()
const appId = computed(() => route.params.id as string)
const appName = ref('应用版本管理')

// 数据状态
const versionList = ref<API.AppVersionQueryVO[]>([])
const loading = ref(false)
const selectedVersion = ref<API.AppVersionVO | null>(null)
const compareResult = ref<API.AppVersionCompareVO | null>(null)
const compareError = ref('')

// 弹窗状态
const detailModalVisible = ref(false)
const compareModalVisible = ref(false)

// 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number) => {
    pagination.value.current = page
    loadVersionList()
  }
})

// 选中的版本（用于对比）
const selectedVersions = ref<Record<string, boolean>>({})

// 计算版本差异
const versionDiffs = computed(() => {
  if (!compareResult.value?.fromVersionData?.content || !compareResult.value.toVersionData?.content) {
    return []
  }

  const fromLines = compareResult.value.fromVersionData.content.split('\n')
  const toLines = compareResult.value.toVersionData.content.split('\n')

  return calculateDiff(fromLines, toLines)
})

// 旧版本代码行
const fromVersionLines = computed(() => {
  if (!compareResult.value?.fromVersionData?.content) {
    return []
  }
  return compareResult.value.fromVersionData.content.split('\n')
})

// 新版本代码行
const toVersionLines = computed(() => {
  if (!compareResult.value?.toVersionData?.content) {
    return []
  }
  return compareResult.value.toVersionData.content.split('\n')
})

// 差异映射（用于高亮）
const diffMapping = computed(() => {
  const mapping: {
    removed: Set<number>
    added: Set<number>
    modified: Set<number>
    unchanged: Set<number>
  } = {
    removed: new Set(),
    added: new Set(),
    modified: new Set(),
    unchanged: new Set()
  }

  versionDiffs.value.forEach(diff => {
    if (diff.fromLine !== undefined && diff.toLine !== undefined) {
      // 未变化的行
      mapping.unchanged.add(diff.fromLine - 1)
      mapping.unchanged.add(diff.toLine - 1)
    } else if (diff.fromLine !== undefined) {
      // 删除的行
      mapping.removed.add(diff.fromLine - 1)
    } else if (diff.toLine !== undefined) {
      // 新增的行
      mapping.added.add(diff.toLine - 1)
    }
  })

  return mapping
})

// 获取行的样式类
const getLineClass = (version: 'from' | 'to', lineIndex: number) => {
  const mapping = diffMapping.value

  if (version === 'from') {
    if (mapping.removed.has(lineIndex)) {
      return 'line-removed'
    } else if (mapping.unchanged.has(lineIndex)) {
      return 'line-unchanged'
    }
  } else {
    if (mapping.added.has(lineIndex)) {
      return 'line-added'
    } else if (mapping.unchanged.has(lineIndex)) {
      return 'line-unchanged'
    }
  }

  return 'line-unchanged'
}

// 加载版本列表
const loadVersionList = async () => {
  loading.value = true
  try {
    const res = await list({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      appId: String(appId.value)
    })

    if (res.data.code === 0 && res.data.data) {
      versionList.value = res.data.data.records || []
      pagination.value.total = res.data.data.totalRow || 0
    } else {
      message.error(res.data.message || '加载版本列表失败')
    }
  } catch (error) {
    console.error('加载版本列表失败:', error)
    message.error('加载版本列表失败')
  } finally {
    loading.value = false
  }
}

// 查看版本详情
const handleViewVersion = async (version: API.AppVersionQueryVO) => {
  try {
    const res = await getInfo({
      id: String(version.id!)
    })

    if (res.data.code === 0 && res.data.data) {
      selectedVersion.value = res.data.data
      detailModalVisible.value = true
    } else {
      message.error(res.data.message || '获取版本详情失败')
    }
  } catch (error) {
    console.error('获取版本详情失败:', error)
    message.error('获取版本详情失败')
  }
}

// 处理版本选择
const handleVersionSelect = () => {
  const selectedIds = Object.entries(selectedVersions.value)
    .filter(([_, selected]) => selected)
    .map(([id, _]) => id)

  if (selectedIds.length > 2) {
    message.warning('最多只能选择两个版本进行对比')
    // 保留最后选择的两个版本
    const latestTwo = selectedIds.slice(-2)
    selectedVersions.value = {}
    latestTwo.forEach(id => {
      selectedVersions.value[id] = true
    })
  }
}

// 对比版本
const handleCompareVersions = async () => {
  const selectedIds = Object.entries(selectedVersions.value)
    .filter(([_, selected]) => selected)
    .map(([id, _]) => id)

  if (selectedIds.length !== 2) {
    message.warning('请选择两个版本进行对比')
    return
  }

  const [fromId, toId] = selectedIds

  try {
    const res = await compare({
      appId: String(appId.value),
      fromVersion: Number(fromId),
      toVersion: Number(toId)
    })

    if (res.data.code === 0 && res.data.data) {
      compareResult.value = res.data.data
      compareError.value = ''
      compareModalVisible.value = true
    } else {
      compareError.value = res.data.message || '对比版本失败'
      compareModalVisible.value = true
    }
  } catch (error) {
    console.error('对比版本失败:', error)
    compareError.value = '对比版本失败'
    compareModalVisible.value = true
  }
}

// 关闭详情弹窗
const handleDetailCancel = () => {
  detailModalVisible.value = false
  selectedVersion.value = null
}

// 关闭对比弹窗
const handleCompareCancel = () => {
  compareModalVisible.value = false
  compareResult.value = null
  compareError.value = ''
}

onMounted(() => {
  loadVersionList()
})
</script>

<style scoped>
.version-list-container {
  padding: 24px;
  background: #f5f5f5;
  min-height: 100vh;
}

.version-content {
  background: white;
  border-radius: 8px;
  padding: 24px;
}

.version-title {
  font-weight: 600;
  color: #1890ff;
}

.version-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-message {
  color: #666;
}

.version-time {
  color: #999;
  font-size: 12px;
}

.compare-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.compare-header-item {
  flex: 1;
  text-align: center;
}

.compare-header-item h4 {
  margin: 0 0 8px;
  color: #1890ff;
}

.compare-header-item p {
  margin: 0;
  color: #666;
}

.compare-split-view {
  display: flex;
  gap: 16px;
  height: 60vh;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
}

.compare-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}

.panel-header {
  padding: 12px 16px;
  background: #f0f0f0;
  border-bottom: 1px solid #d9d9d9;
  position: sticky;
  top: 0;
  z-index: 1;
}

.panel-header h5 {
  margin: 0;
  color: #333;
  font-size: 14px;
  font-weight: 600;
}

.compare-panel-left .panel-header {
  background: #fff2f0;
}

.compare-panel-right .panel-header {
  background: #f6ffed;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.code-line {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  min-height: 24px;
}

.code-line:last-child {
  border-bottom: none;
}

.line-number {
  width: 60px;
  flex-shrink: 0;
  padding: 4px 8px;
  background: #f5f5f5;
  color: #999;
  text-align: right;
  user-select: none;
  border-right: 1px solid #e8e8e8;
  font-size: 12px;
}

.line-content {
  flex: 1;
  padding: 4px 8px;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-x: auto;
}

.line-content pre {
  margin: 0;
  padding: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
}

/* 行状态样式 */
.line-unchanged {
  background: white;
}

.line-removed {
  background: #fff2f0;
}

.line-removed .line-number {
  background: #ffebe6;
  color: #ff4d4f;
}

.line-removed .line-content {
  color: #ff4d4f;
  text-decoration: line-through;
}

.line-added {
  background: #f6ffed;
}

.line-added .line-number {
  background: #e6f7e6;
  color: #52c41a;
}

.line-added .line-content {
  color: #52c41a;
}

/* 滚动条样式 */
.panel-content::-webkit-scrollbar {
  width: 8px;
}

.panel-content::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.panel-content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.version-detail {
  max-height: 70vh;
  overflow-y: auto;
}

.version-meta {
  margin-bottom: 24px;
}

.version-code h4 {
  margin-bottom: 16px;
  color: #333;
}

.code-content {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.compare-error {
  margin-bottom: 16px;
}
</style>
