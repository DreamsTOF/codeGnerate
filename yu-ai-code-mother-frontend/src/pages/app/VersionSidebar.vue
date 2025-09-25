<template>
  <div class="version-sidebar">
    <!-- 版本列表头部 -->
    <div class="version-header">
      <div class="version-title">
        <HistoryOutlined />
        版本历史
        <div class="refresh-button">
          <a-button
            type="primary"
            shape="circle"
            size="small"
            @click="refreshVersions"
            :loading="loading"
            title="刷新版本列表"
          >
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </div>
      </div>
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
      <div v-if="compareData" class="compare-container">
        <!-- 版本信息头 -->
        <div class="compare-header">
          <div class="compare-info">
            <h4>源版本: {{ compareData.fromVersionData?.version }}</h4>
            <p>{{ compareData.fromVersionData?.message || '无描述' }}</p>
          </div>
          <div class="compare-info">
            <h4>目标版本: {{ compareData.toVersionData?.version }}</h4>
            <p>{{ compareData.toVersionData?.message || '无描述' }}</p>
          </div>
        </div>

        <!-- 文件选择器 -->
        <div class="file-selector">
          <a-tag
            v-for="file in fileList"
            :key="file"
            :color="selectedFile === file ? 'blue' : 'default'"
            @click="selectedFile = file"
            style="cursor: pointer;"
          >
            {{ file }}
          </a-tag>
        </div>

        <!-- 差异对比视图 -->
        <div class="diff-view-container">
          <div class="diff-view">
            <div class="diff-pane">
              <div
                v-for="(line, index) in alignedDiff"
                :key="`left-${index}`"
                class="diff-line"
                :class="`diff-line-${line.left.type}`"
              >
                <span class="line-number">{{ line.left.lineNumber }}</span>
                <pre class="line-content">{{ line.left.content }}</pre>
              </div>
            </div>
            <div class="diff-pane">
              <div
                v-for="(line, index) in alignedDiff"
                :key="`right-${index}`"
                class="diff-line"
                :class="`diff-line-${line.right.type}`"
              >
                <span class="line-number">{{ line.right.lineNumber }}</span>
                <pre class="line-content">{{ line.right.content }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else>
        <a-spin tip="加载对比数据中..." />
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { message } from 'ant-design-vue';
import {
  HistoryOutlined,
  ReloadOutlined,
  FileImageOutlined,
  DiffOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue';
import { list, compare, restore } from '@/api/appVersionController';
// 假设的类型定义，实际项目中请从后端接口类型中导入

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
const fromContent = ref<Record<string, string>>({});
const toContent = ref<Record<string, string>>({});
const selectedFile = ref<string | null>(null);

// --- Diff 相关逻辑 ---
type DiffLineType = 'added' | 'removed' | 'unchanged' | 'empty';
interface DiffLine {
  content: string;
  type: DiffLineType;
  lineNumber?: number;
}
interface AlignedDiff {
  left: DiffLine;
  right: DiffLine;
}

// 基于LCS算法生成对齐的差异
const alignedDiff = computed((): AlignedDiff[] => {
  if (!selectedFile.value) return [];

  const oldText = fromContent.value[selectedFile.value] || '';
  const newText = toContent.value[selectedFile.value] || '';
  const oldLines = oldText.split('\n');
  const newLines = newText.split('\n');

  // LCS 矩阵
  const lcsMatrix = Array(oldLines.length + 1).fill(null).map(() => Array(newLines.length + 1).fill(0));
  for (let i = 1; i <= oldLines.length; i++) {
    for (let j = 1; j <= newLines.length; j++) {
      if (oldLines[i - 1] === newLines[j - 1]) {
        lcsMatrix[i][j] = lcsMatrix[i - 1][j - 1] + 1;
      } else {
        lcsMatrix[i][j] = Math.max(lcsMatrix[i - 1][j], lcsMatrix[i][j - 1]);
      }
    }
  }

  // 回溯LCS矩阵生成diff
  const result: AlignedDiff[] = [];
  let i = oldLines.length;
  let j = newLines.length;
  let oldLineNum = oldLines.length;
  let newLineNum = newLines.length;

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      result.unshift({
        left: { content: oldLines[i - 1], type: 'unchanged', lineNumber: oldLineNum-- },
        right: { content: newLines[j - 1], type: 'unchanged', lineNumber: newLineNum-- }
      });
      i--; j--;
    } else if (j > 0 && (i === 0 || lcsMatrix[i][j - 1] >= lcsMatrix[i - 1][j])) {
      result.unshift({
        left: { content: '', type: 'empty' },
        right: { content: newLines[j - 1], type: 'added', lineNumber: newLineNum-- }
      });
      j--;
    } else if (i > 0 && (j === 0 || lcsMatrix[i][j - 1] < lcsMatrix[i - 1][j])) {
      result.unshift({
        left: { content: oldLines[i - 1], type: 'removed', lineNumber: oldLineNum-- },
        right: { content: '', type: 'empty' }
      });
      i--;
    } else {
      break;
    }
  }
  return result;
});


// 从两个版本的内容中提取所有文件名
const fileList = computed(() => {
  const fromKeys = Object.keys(fromContent.value);
  const toKeys = Object.keys(toContent.value);
  const allKeys = new Set([...fromKeys, ...toKeys]);
  return Array.from(allKeys).sort();
});


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

  compareModalVisible.value = true;
  compareData.value = null; // 清空旧数据

  try {
    const res = await compare({
      appId: props.appId,
      fromVersion: selectedVersionId.value,
      toVersion: version.id,
    });

    if (res.data.code === 0 && res.data.data) {
      compareData.value = res.data.data;
      // 解析 JSON 字符串
      try {
        fromContent.value = JSON.parse(res.data.data.fromVersionData?.content || '{}');
        toContent.value = JSON.parse(res.data.data.toVersionData?.content || '{}');
        // 默认选择第一个文件
        if (fileList.value.length > 0) {
          selectedFile.value = fileList.value[0];
        }
      } catch (e) {
        console.error("解析版本内容失败: ", e);
        message.error("解析版本内容失败，可能不是有效的JSON格式。");
        fromContent.value = { 'error.txt': '源版本内容解析失败' };
        toContent.value = { 'error.txt': '目标版本内容解析失败' };
        selectedFile.value = 'error.txt';
      }
    } else {
      message.error('版本对比失败：' + res.data.message);
      closeCompareModal();
    }
  } catch (error) {
    console.error('版本对比失败：', error);
    message.error('版本对比失败');
    closeCompareModal();
  }
};

// 关闭对比弹窗
const closeCompareModal = () => {
  compareModalVisible.value = false;
  compareData.value = null;
  fromContent.value = {};
  toContent.value = {};
  selectedFile.value = null;
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
  align-items: center;
  padding: 16px 16px 8px 16px;
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

.refresh-button .ant-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #1890ff;
  border-color: #1890ff;
  color: white;
  font-size: 12px;
}

.refresh-button .ant-btn:hover {
  background: #40a9ff;
  border-color: #40a9ff;
}

.version-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-content: start;
}

.loading-container,
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  color: #666;
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
  width: 100%;
  box-shadow: inset 0 0 10px rgb(255, 255, 255);
}

.version-item:hover .version-card {
  border-color: #1890ff;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
}

.version-item-selected .version-card {
  border-color: #1890ff;
  background: #e6f7ff;
}

.version-thumbnail {
  width: 100%;
  height: 80px;
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
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: #f5f5f5; color: #999; font-size: 24px;
}

.version-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0, 0, 0, 0.7); display: flex; align-items: center;
  justify-content: center; opacity: 0; transition: opacity 0.2s ease;
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
  min-width: 32px; height: 32px; padding: 0; display: flex;
  align-items: center; justify-content: center; border-radius: 6px; font-size: 14px;
}

.version-number {
  padding: 6px 4px; font-size: 11px; font-weight: 600; color: #1a1a1a;
  text-align: center; background: #fafafa; border-top: 1px solid #f0f0f0;
}

/* --- 新增/修改的对比弹窗样式 --- */
.compare-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 75vh;
}

.compare-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  background: #f5f5f5;
  border-radius: 8px;
}

.compare-info {
  flex: 1;
}

.compare-info h4 {
  margin: 0 0 4px 0;
  color: #1a1a1a;
}

.compare-info p {
  margin: 0;
  color: #666;
  font-size: 12px;
}

.file-selector {
  padding: 8px;
  background: #fafafa;
  border-radius: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.diff-view-container {
  flex: 1;
  overflow: auto;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
}

.diff-view {
  display: flex;
  width: 100%;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  font-size: 12px;
}

.diff-pane {
  flex: 1;
  width: 50%;
  min-width: 0;
}

.diff-pane:first-child {
  border-right: 1px solid #e8e8e8;
}

.diff-line {
  display: flex;
  align-items: center;
  min-height: 20px;
  line-height: 20px;
  border-bottom: 1px solid #f5f5f5;
}

.line-number {
  min-width: 40px;
  padding: 0 10px;
  text-align: right;
  color: #aaa;
  background-color: #f7f7f7;
  user-select: none;
}

.line-content {
  flex-grow: 1;
  padding: 0 10px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.diff-line-added { background-color: #e6ffed; }
.diff-line-removed { background-color: #fff1f0; }
.diff-line-empty { background-color: #fafafa; }
.diff-line-unchanged { background-color: #fff; }
</style>
