<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-button @click="saveCurrentVersion" :loading="savingVersion" :disabled="!isOwner">
          <template #icon>
            <SaveOutlined />
          </template>
          保存版本
        </a-button>
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
          type="primary"
          ghost
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="getFullMessageContent(message)" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI 正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
          v-if="selectedElementInfo"
          class="selected-element-alert"
          type="info"
          closable
          @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              :placeholder="getInputPlaceholder()"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating"
            />
            <div class="input-actions">
              <a-button
                type="primary"
                @click="sendMessage"
                :loading="isGenerating"
                :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <!-- 视图切换按钮 -->
          <a-radio-group v-model:value="activeView" button-style="solid" size="small">
            <a-radio-button value="preview">
              <template #icon><EyeOutlined /></template>
              界面
            </a-radio-button>
            <a-radio-button value="code">
              <template #icon><CodeOutlined /></template>
              代码
            </a-radio-button>
          </a-radio-group>
          <div class="preview-actions">
            <a-button
              v-if="isOwner && previewUrl && activeView === 'preview'"
              type="link"
              :danger="isEditMode"
              @click="toggleEditMode"
              :class="{ 'edit-mode-active': isEditMode }"
              style="padding: 0; height: auto; margin-right: 12px"
            >
              <template #icon>
                <EditOutlined />
              </template>
              {{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <!-- 使用动态组件来展示不同视图 -->
        <div class="preview-content">
          <component
            :is="activeView === 'preview' ? PreviewDisplay : CodeDisplay"
            v-if="appInfo"
            :preview-url="previewUrl"
            :is-generating="isGenerating"
            @load="onIframeLoad"
            :app-id="appId"
            :code-gen-type="appInfo.codeGenType"
          />
        </div>
      </div>

      <!-- 右侧版本列表 -->
      <div class="version-section">
        <VersionSidebar
          :app-id="appId"
          @select-version="handleSelectVersion"
          @restore-version="handleRestoreVersion"
        />
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useLoginUserStore } from '@/stores/loginUser';
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
} from '@/api/appController';
import { listAppChatHistory } from '@/api/chatHistoryController';
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes';
import request from '@/request';

import MarkdownRenderer from '@/components/MarkdownRenderer.vue';
import AppDetailModal from '@/components/AppDetailModal.vue';
import DeploySuccessModal from '@/components/DeploySuccessModal.vue';
import VersionSidebar from './VersionSidebar.vue';
import aiAvatar from '@/assets/aiAvatar.jpg';
import { getStaticPreviewUrl } from '@/config/env';
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor';

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  SaveOutlined,
  EyeOutlined,
  CodeOutlined,
} from '@ant-design/icons-vue';
import { startChatStream, type ChatStreamCallbacks } from '@/utils/chatStreamHandler.ts';
import {type ParsedEventData} from '@/utils/eventDataProcessor.ts';
import { save, list, compare, restore } from '@/api/appVersionController';

// 懒加载组件
import PreviewDisplay from './PreviewDisplay.vue';
import CodeDisplay from './CodeDisplay.vue';

const route = useRoute();
const router = useRouter();
const loginUserStore = useLoginUserStore();

// 应用信息
const appInfo = ref<API.AppVO>();
const appId = ref<any>();

// 为“保存版本”按钮新增 loading 状态
const savingVersion = ref(false);

// 对话相关
interface Message {
  id?: number; // 【修改 1】增加 id 字段
  type: 'user' | 'ai';
  content: string;
  loading?: boolean;
  createTime?: string;
  toolInfo?: {
    toolName: string;
    status: 'request' | 'stream' | 'executed';
    content?: string;
  };
}

const messages = ref<Message[]>([]);
const userInput = ref('');
const isGenerating = ref(false);
const messagesContainer = ref<HTMLElement>();

// 对话历史相关
const loadingHistory = ref(false);
const hasMoreHistory = ref(false);
const lastCreateTime = ref<string>();
const historyLoaded = ref(false);

// 预览相关
const previewUrl = ref('');
const previewReady = ref(false);

// 部署相关
const deploying = ref(false);
const deployModalVisible = ref(false);
const deployUrl = ref('');

// 下载相关
const downloading = ref(false);

// 可视化编辑相关
const isEditMode = ref(false);
const selectedElementInfo = ref<ElementInfo | null>(null);
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo;
  },
});

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id;
});

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin';
});

// 应用详情相关
const appDetailVisible = ref(false);

// 视图切换
const activeView = ref<'preview' | 'code'>('preview');

// 版本选择处理
const handleSelectVersion = (version: any) => {
  console.log('选择版本:', version);
  // 可以在这里添加版本选择的逻辑
};

// 版本回滚处理
const handleRestoreVersion = (version: any) => {
  console.log('回滚版本:', version);
  // 版本回滚成功后刷新应用信息
  setTimeout(() => {
    fetchAppInfo();
  }, 1000);
};


// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true;
};

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return;
  loadingHistory.value = true;
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    };
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value;
    }
    const res = await listAppChatHistory(params);
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || [];
      if (chatHistories.length > 0) {
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            id: chat.id, // 【修改 2】保存消息的 id
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
          }))
          .reverse();
        if (isLoadMore) {
          messages.value.unshift(...historyMessages);
        } else {
          messages.value = historyMessages;
        }
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime;
        hasMoreHistory.value = chatHistories.length === 10;
      } else {
        hasMoreHistory.value = false;
      }
      historyLoaded.value = true;
    }
  } catch (error) {
    console.error('加载对话历史失败：', error);
    message.error('加载对话历史失败');
  } finally {
    loadingHistory.value = false;
  }
};

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true);
};

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string;
  if (!id) {
    message.error('应用ID不存在');
    router.push('/');
    return;
  }

  appId.value = id;

  try {
    const res = await getAppVoById({ id: id as unknown as number });
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data;
      await loadChatHistory();
      if (messages.value.length >= 2) {
        updatePreview();
      }
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt);
      }
    } else {
      message.error('获取应用信息失败');
      router.push('/');
    }
  } catch (error) {
    console.error('获取应用信息失败：', error);
    message.error('获取应用信息失败');
    router.push('/');
  }
};

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  messages.value.push({
    type: 'user',
    content: prompt,
  });

  const aiMessageIndex = messages.value.length;
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  });

  await nextTick();
  scrollToBottom();

  isGenerating.value = true;
  await generateCode(prompt, aiMessageIndex);
};

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) {
    return;
  }

  let messageContent = userInput.value.trim();
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`;
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`;
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`;
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`;
    }
    messageContent += elementContext;
  }
  userInput.value = '';
  messages.value.push({
    type: 'user',
    content: messageContent,
  });

  if (selectedElementInfo.value) {
    clearSelectedElement();
    if (isEditMode.value) {
      toggleEditMode();
    }
  }

  const aiMessageIndex = messages.value.length;
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  });

  await nextTick();
  scrollToBottom();

  isGenerating.value = true;
  await generateCode(messageContent, aiMessageIndex);
};

const getFullMessageContent = (message: Message) => {
  let content = message.content || '';

  if (message.toolInfo) {
    const toolInfo = message.toolInfo;
    let toolContent = '';

    if (toolInfo.status === 'request') {
      toolContent = `\n\n🔧 正在调用工具: ${toolInfo.toolName}`;
      if (toolInfo.content) {
        toolContent += `\n\`\`\`json\n${toolInfo.content}\n\`\`\``;
      }
    } else if (toolInfo.status === 'executed') {
      toolContent = `\n\n✅ 工具执行完成: ${toolInfo.toolName}`;
      if (toolInfo.content) {
        toolContent += `\n\`\`\`\n${toolInfo.content}\n\`\`\``;
      }
    }
    content += toolContent;
  }
  return content;
};

// 生成代码
const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  let streamController: any = null;
  let fullContent = '';

  const callbacks: ChatStreamCallbacks = {
    onAiResponse: (chunk: string, rawData: ParsedEventData) => {
      fullContent += chunk;
      messages.value[aiMessageIndex].content = fullContent;
      messages.value[aiMessageIndex].loading = false;
      scrollToBottom();
    },
    onToolRequest: (data: any, rawData: ParsedEventData) => {
      let toolContent = '';
      if (data.arguments) {
        try {
          const args = JSON.parse(data.arguments);
          toolContent = `参数: ${JSON.stringify(args, null, 2)}`;
        } catch (e) {
          toolContent = `参数: ${data.arguments}`;
        }
      } else {
        toolContent = data.description || rawData.displayText || '正在调用工具...';
      }
      messages.value[aiMessageIndex].toolInfo = {
        toolName: data.toolName || data.name || '未知工具',
        status: 'request',
        content: toolContent
      };
      scrollToBottom();
    },
    onToolStream: (chunk: string, rawData: ParsedEventData) => {
      if (messages.value[aiMessageIndex].toolInfo) {
        messages.value[aiMessageIndex].toolInfo!.status = 'stream';
        messages.value[aiMessageIndex].toolInfo!.content = (messages.value[aiMessageIndex].toolInfo?.content || '') + chunk;
      } else {
        messages.value[aiMessageIndex].toolInfo = { toolName: '工具', status: 'stream', content: chunk };
      }
      scrollToBottom();
    },
    onToolExecuted: (data: any, rawData: ParsedEventData) => {
      if (messages.value[aiMessageIndex].toolInfo) {
        messages.value[aiMessageIndex].toolInfo!.status = 'executed';
        messages.value[aiMessageIndex].toolInfo!.content = data.result || data.output || '工具执行完成';
      } else {
        messages.value[aiMessageIndex].toolInfo = {
          toolName: data.toolName || data.name || '工具',
          status: 'executed',
          content: data.result || data.output || '工具执行完成'
        };
      }
      scrollToBottom();
    },
    onDone: () => {
      isGenerating.value = false;
      messages.value[aiMessageIndex].loading = false;
      setTimeout(async () => {
        await fetchAppInfo();
        updatePreview();
      }, 1000);
    },
    onError: (error: any) => {
      console.error('流式请求错误:', error);
      const errorMessage = error.message || '生成过程中出现错误';
      messages.value[aiMessageIndex].content = `❌ ${errorMessage}`;
      messages.value[aiMessageIndex].loading = false;
      message.error(errorMessage);
      isGenerating.value = false;
    },
    onFirstChunk: () => {
      messages.value[aiMessageIndex].loading = false;
    }
  };

  try {
    streamController = startChatStream({ appId: appId.value || '', userMessage: userMessage }, callbacks);
  } catch (error) {
    console.error('启动流式请求失败:', error);
    handleError(error, aiMessageIndex);
  }
  return streamController;
};

const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error);
  messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。';
  messages.value[aiMessageIndex].loading = false;
  message.error('生成失败，请重试');
  isGenerating.value = false;
};

// 更新预览
const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML;
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value);
    if (previewUrl.value !== newPreviewUrl) {
      previewUrl.value = newPreviewUrl;
    }
    previewReady.value = true;
  }
};


const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
  }
};

const saveCurrentVersion = async () => {
  if (!appId.value || !appInfo.value) {
    message.error('应用信息不完整，无法保存版本');
    return;
  }
  const versionMessage = prompt('请输入版本说明（选填）', `版本 ${new Date().toLocaleString()}`);
  if (versionMessage === null) {
    return;
  }
  savingVersion.value = true;
  try {
    // 【修改 3】从后往前遍历消息数组，找到最后一条包含 id 的消息
    const lastMessageWithId = [...messages.value].reverse().find(m => m.id);
    const lastChatHistoryId = lastMessageWithId ? lastMessageWithId.id : undefined;

    const codeGenTypeForApi = appInfo.value?.codeGenType?.toUpperCase() as API.AppVersionSaveRequest['codeGenType'];
    const params: API.AppVersionSaveRequest = {
      appId: appId.value as number,
      message: versionMessage,
      codeGenType: codeGenTypeForApi,
    };
    const res = await save(params);
    if (res.data.code === 0) {
      message.success('版本保存成功！');
    } else {
      message.error(`版本保存失败：${res.data.message}`);
    }
  } catch (error: any) {
    console.error('版本保存失败：', error);
    message.error(`版本保存失败：${error.message || '请重试'}`);
  } finally {
    savingVersion.value = false;
  }
};

const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在');
    return;
  }
  downloading.value = true;
  try {
    const API_BASE_URL = request.defaults.baseURL || '';
    const url = `${API_BASE_URL}/app/download/${appId.value}`;
    const response = await fetch(url, { method: 'GET', credentials: 'include' });
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`);
    }
    const contentDisposition = response.headers.get('Content-Disposition');
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`;
    const blob = await response.blob();
    const downloadUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(downloadUrl);
    message.success('代码下载成功');
  } catch (error) {
    console.error('下载失败：', error);
    message.error('下载失败，请重试');
  } finally {
    downloading.value = false;
  }
};

const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在');
    return;
  }
  deploying.value = true;
  try {
    const res = await deployAppApi({ appId: appId.value as unknown as number });
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data;
      deployModalVisible.value = true;
      message.success('部署成功');
    } else {
      message.error('部署失败：' + res.data.message);
    }
  } catch (error) {
    console.error('部署失败：', error);
    message.error('部署失败，请重试');
  } finally {
    deploying.value = false;
  }
};

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank');
  }
};

const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank');
  }
};

const onIframeLoad = () => {
  previewReady.value = true;
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement;
  if (iframe) {
    visualEditor.init(iframe);
    visualEditor.onIframeLoad();
  }
};

const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`);
  }
};

const deleteApp = async () => {
  if (!appInfo.value?.id) return;
  try {
    const res = await deleteAppApi({ id: appInfo.value.id });
    if (res.data.code === 0) {
      message.success('删除成功');
      appDetailVisible.value = false;
      router.push('/');
    } else {
      message.error('删除失败：' + res.data.message);
    }
  } catch (error) {
    console.error('删除失败：', error);
    message.error('删除失败');
  }
};

const toggleEditMode = () => {
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement;
  if (!iframe || !previewReady.value) {
    message.warning('请等待页面加载完成');
    return;
  }
  const newEditMode = visualEditor.toggleEditMode();
  isEditMode.value = newEditMode;
};

const clearSelectedElement = () => {
  selectedElementInfo.value = null;
  visualEditor.clearSelection();
};

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`;
  }
  return '请描述你想生成的网站，越详细效果越好哦';
};

onMounted(() => {
  fetchAppInfo();
  window.addEventListener('message', (event) => {
    visualEditor.handleIframeMessage(event);
  });
});

onUnmounted(() => {
  // EventSource will be cleaned up automatically
});
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #f5f5f5;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: white;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  min-height: 0;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #1a1a1a;;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
  border-top: 1px solid #e0e0e0;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  background: white;
  border: 1px solid #d0d0d0;
  color: #333;
}

.input-wrapper .ant-input:focus {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.input-wrapper .ant-input::placeholder {
  color: #999;
}

.input-wrapper .ant-input {
  padding-right: 50px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

/* 右侧版本列表区域 */
.version-section {
  width: 320px;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-height: 0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e0e0e0;
  background: #fafafa;
}

.preview-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.preview-content {
  flex: 1;
  position: relative;
  min-height: 0;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 45vh;
  }

  .version-section {
    width: 100%;
    height: 200px;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}
</style>

