<template>
  <div class="version-sidebar">
    <!-- 版本列表头部 -->
    <div class="version-header">
      <div class="version-title">
        <HistoryOutlined />
        版本历史
      </div>
      <a-button
        type="text"
        size="small"
        @click="refreshVersions"
        :loading="loading"
      >
        <template #icon><ReloadOutlined /></template>
      </a-button>
    </div>

    <!-- 版本列表 -->
    <div class="version-list">
      <div v-if="loading && versions.length === 0" class="loading-container">
        <a-spin size="small" />
        <span>加载版本列表...</span>
      </div>

      <div v-else-if="versions.length === 0" class="empty-container">
        <FileImageOutlined />
        <span>暂无版本</span>
      </div>

      <div
        v-for="version in versions"
        v-else
        :key="version.id"
        class="version-item"
        :class="{ 'version-item-selected': selectedVersionId === version.id }"
        @click="selectVersion(version)"
      >
        <!-- 方形版本卡片 -->
        <div class="version-card">
          <!-- 版本缩略图（带悬停操作） -->
          <div class="version-thumbnail">
            <img
              v-if="version.cover"
              :src="version.cover"
              :alt="`版本 ${version.version}`"
              @error="handleImageError"
            />
            <div v-else class="thumbnail-placeholder">
              <FileImageOutlined />
            </div>
            <!-- 悬停时显示的操作层 -->
            <div class="version-overlay">
              <div class="overlay-actions">
                <a-button
                  type="primary"
                  size="small"
                  @click.stop="compareVersion(version)"
                  :disabled="!selectedVersionId || selectedVersionId === version.id"
                  title="对比版本"
                >
                  <DiffOutlined />
                </a-button>
                <a-button
                  type="default"
                  size="small"
                  @click.stop="restoreVersion(version)"
                  title="回滚到此版本"
                >
                  <UndoOutlined />
                </a-button>
              </div>
            </div>
          </div>

          <!-- 版本号 -->
          <div class="version-number">v{{ version.version }}</div>
        </div>
      </div>
    </div>

    <!-- 版本对比弹窗 -->
    <a-modal
      v-model:open="compareModalVisible"
      title="版本对比"
      width="90%"
      :footer="null"
      @cancel="closeCompareModal"
    >
      <div v-if="compareData" class="compare-content">
        <div class="compare-header">
          <div class="compare-from">
            <h4>源版本: {{ compareData.fromVersionData?.version }}</h4>
            <p>{{ compareData.fromVersionData?.message || '无描述' }}</p>
          </div>
          <div class="compare-to">
            <h4>目标版本: {{ compareData.toVersionData?.version }}</h4>
            <p>{{ compareData.toVersionData?.message || '无描述' }}</p>
          </div>
        </div>
        <div class="compare-diff">
          <a-tabs>
            <a-tab-pane key="content" tab="代码内容">
              <div class="diff-content">
                <div class="diff-section">
                  <h5>源版本代码</h5>
                  <pre><code>{{ compareData.fromVersionData?.content || '无内容' }}</code></pre>
                </div>
                <div class="diff-section">
                  <h5>目标版本代码</h5>
                  <pre><code>{{ compareData.toVersionData?.content || '无内容' }}</code></pre>
                </div>
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  HistoryOutlined,
  ReloadOutlined,
  FileImageOutlined,
  MoreOutlined,
  DiffOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue';
import { list, compare, restore } from '@/api/appVersionController';
// 类型从 API 命名空间中获取

interface Props {
  appId: number;
  visible?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  visible: true,
});

const emit = defineEmits<{
  selectVersion: [version: API.AppVersionQueryVO];
  restoreVersion: [version: API.AppVersionQueryVO];
}>();

// 版本列表数据
const versions = ref<API.AppVersionQueryVO[]>([]);
const loading = ref(false);
const selectedVersionId = ref<number | null>(null);

// 对比相关
const compareModalVisible = ref(false);
const compareData = ref<API.AppVersionCompareVO | null>(null);

// 加载版本列表
const loadVersions = async () => {
  if (!props.appId) return;

  loading.value = true;
  try {
    const res = await list({
      appId: props.appId,
      pageNum: 1,
      pageSize: 50,
      sortField: 'createTime',
      sortOrder: 'desc',
    });

    if (res.data.code === 0 && res.data.data) {
      versions.value = res.data.data.records || [];
      if (versions.value.length > 0 && !selectedVersionId.value) {
        selectVersion(versions.value[0]);
      }
    }
  } catch (error) {
    console.error('加载版本列表失败：', error);
    message.error('加载版本列表失败');
  } finally {
    loading.value = false;
  }
};

// 刷新版本列表
const refreshVersions = () => {
  loadVersions();
};

// 选择版本
const selectVersion = (version: API.AppVersionQueryVO) => {
  selectedVersionId.value = version.id || null;
  emit('selectVersion', version);
};

// 对比版本
const compareVersion = async (version: API.AppVersionQueryVO) => {
  if (!selectedVersionId.value || selectedVersionId.value === version.id) {
    message.warning('请选择不同的版本进行对比');
    return;
  }

  try {
    const res = await compare({
      appId: props.appId,
      fromVersion: selectedVersionId.value,
      toVersion: version.id,
    });

    if (res.data.code === 0 && res.data.data) {
      compareData.value = res.data.data;
      compareModalVisible.value = true;
    } else {
      message.error('版本对比失败：' + res.data.message);
    }
  } catch (error) {
    console.error('版本对比失败：', error);
    message.error('版本对比失败');
  }
};

// 关闭对比弹窗
const closeCompareModal = () => {
  compareModalVisible.value = false;
  compareData.value = null;
};

// 回滚版本
const restoreVersion = async (version: API.AppVersionQueryVO) => {
  try {
    const res = await restore({
      appId: props.appId,
      id: version.id,
    });

    if (res.data.code === 0 && res.data.data) {
      message.success('版本回滚成功');
      emit('restoreVersion', version);
    } else {
      message.error('版本回滚失败：' + res.data.message);
    }
  } catch (error) {
    console.error('版本回滚失败：', error);
    message.error('版本回滚失败');
  }
};

// 处理图片加载错误
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
  const placeholder = img.nextElementSibling as HTMLElement;
  if (placeholder) {
    placeholder.style.display = 'flex';
  }
};

// 获取存储类型颜色
const getStorageTypeColor = (type?: string) => {
  return type === 'FULL' ? 'blue' : 'green';
};

// 获取存储类型文本
const getStorageTypeText = (type?: string) => {
  return type === 'FULL' ? '完整版本' : '差异版本';
};

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '';
  const date = new Date(time);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;

  return date.toLocaleDateString();
};

// 监听appId变化
watch(() => props.appId, (newAppId) => {
  if (newAppId) {
    loadVersions();
  }
}, { immediate: true });

// 组件挂载时加载数据
onMounted(() => {
  if (props.appId) {
    loadVersions();
  }
});
</script>

<style scoped>
.version-sidebar {
  width: 320px;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  box-shadow: inset 0 0 15px rgb(255, 255, 255);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  overflow: hidden;
  min-height: 0;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.version-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: #1a1a1a;
}

.version-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  align-content: start;
  /* 自定义滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #c1c1c1 #f1f1f1;
}

.version-list::-webkit-scrollbar {
  width: 6px;
}

.version-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.version-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
  transition: background-color 0.2s ease;
}

.version-list::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.version-list::-webkit-scrollbar-corner {
  background: transparent;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  color: #666;
  grid-column: 1 / -1;
}

.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  color: #999;
  grid-column: 1 / -1;
}

.version-item {
  cursor: pointer;
  transition: all 0.2s ease;
}

.version-card {
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s ease;
  height: 100%;
  box-shadow: inset 0 0 10px rgb(255, 255, 255);
}

.version-item:hover .version-card {
  border-color: #1890ff;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
}

.version-item-selected .version-card {
  border-color: #1890ff;
  background: #e6f7ff;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.25);
}

.version-thumbnail {
  width: 100%;
  height: 120px;
  background: #f5f5f5;
  position: relative;
  overflow: hidden;
}

.version-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s ease;
}

.version-item:hover .version-thumbnail img {
  transform: scale(1.05);
}

.thumbnail-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
  font-size: 24px;
}

/* 悬停操作层 */
.version-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s ease;
  backdrop-filter: blur(2px);
}

.version-item:hover .version-overlay {
  opacity: 1;
}

.overlay-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
}

.overlay-actions .ant-btn {
  min-width: 32px;
  height: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 14px;
}

.version-number {
  padding: 6px 4px;
  font-size: 11px;
  font-weight: 600;
  color: #1a1a1a;
  text-align: center;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

.compare-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.compare-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;
}

.compare-from,
.compare-to {
  flex: 1;
}

.compare-from h4,
.compare-to h4 {
  margin: 0 0 8px 0;
  color: #1a1a1a;
}

.compare-from p,
.compare-to p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.diff-content {
  display: flex;
  gap: 16px;
  max-height: 60vh;
  overflow-y: auto;
}

.diff-section {
  flex: 1;
  background: #f8f9fa;
  border-radius: 6px;
  padding: 16px;
}

.diff-section h5 {
  margin: 0 0 12px 0;
  color: #1a1a1a;
  font-size: 14px;
}

.diff-section pre {
  margin: 0;
  padding: 12px;
  background: white;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
  font-size: 12px;
  line-height: 1.4;
  overflow-x: auto;
}

.diff-section code {
  font-family: 'Monaco', 'Menlo', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .version-sidebar {
    width: 280px;
  }

  .version-list {
    grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
    gap: 8px;
  }

  .version-thumbnail {
    height: 100px;
  }

  /* 移动端优化滚动条 */
  .version-list::-webkit-scrollbar {
    width: 4px;
  }

  .thumbnail-placeholder {
    font-size: 20px;
  }

  .overlay-actions .ant-btn {
    min-width: 28px;
    height: 28px;
    font-size: 12px;
  }

  .version-number {
    font-size: 10px;
    padding: 4px 2px;
  }

  .diff-content {
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .version-sidebar {
    width: 100%;
    height: 200px;
  }

  .version-list {
    grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
    gap: 6px;
  }

  .version-thumbnail {
    height: 90px;
  }

  /* 移动端滚动条更细 */
  .version-list::-webkit-scrollbar {
    width: 3px;
  }

  .version-list::-webkit-scrollbar-thumb {
    background: #d0d0d0;
  }

  .version-list::-webkit-scrollbar-thumb:hover {
    background: #b0b0b0;
  }

  .thumbnail-placeholder {
    font-size: 18px;
  }

  .overlay-actions {
    gap: 4px;
    padding: 4px;
  }

  .overlay-actions .ant-btn {
    min-width: 24px;
    height: 24px;
    font-size: 10px;
  }

  .version-number {
    font-size: 9px;
    padding: 3px 2px;
  }
}
</style>