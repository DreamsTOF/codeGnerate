<template>
  <div class="code-display-container">
    <a-spin :spinning="loadingFiles || loadingContent" :tip="loadingTip">
      <a-tabs v-if="files.length > 0" v-model:activeKey="activeFile" @change="selectFile" type="card" class="file-tabs" size="small">
        <a-tab-pane v-for="file in files" :key="file" :tab="getFileName(file)"></a-tab-pane>
      </a-tabs>

      <div class="code-content-wrapper" :class="{ 'no-tabs': files.length === 0 }">
        <div v-if="files.length === 0 && !loadingFiles" class="empty-state">
          <p>未能加载代码文件。</p>
          <p>这可能是一个无代码应用，或者文件尚未生成。</p>
        </div>
        <div v-else class="code-wrapper">
          <a-button class="copy-button" @click="copyCode" size="small" type="default" v-if="fileContent">
            <template #icon><CopyOutlined /></template>
            复制
          </a-button>
          <pre><code :class="languageClass" v-html="highlightedContent"></code></pre>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { message } from 'ant-design-vue';
import { CopyOutlined } from '@ant-design/icons-vue';
import { STATIC_BASE_URL } from '@/config/env';
import { listStaticFiles } from '@/api/staticResourceController.ts';

import hljs from 'highlight.js/lib/core';
import xml from 'highlight.js/lib/languages/xml';
import css from 'highlight.js/lib/languages/css';
import javascript from 'highlight.js/lib/languages/javascript';
import typescript from 'highlight.js/lib/languages/typescript';
import json from 'highlight.js/lib/languages/json';
import scss from 'highlight.js/lib/languages/scss';
import less from 'highlight.js/lib/languages/less';
import 'highlight.js/styles/atom-one-light.css';

// 注册所有需要用到的语言
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('css', css);
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('json', json);
hljs.registerLanguage('scss', scss);
hljs.registerLanguage('less', less);

const props = defineProps<{
  appId: string;
  codeGenType: string;
}>();

const files = ref<string[]>([]);
const activeFile = ref<string>('');
const fileContent = ref('');
const loadingFiles = ref(false);
const loadingContent = ref(false);

const loadingTip = computed(() => {
  if (loadingFiles.value) return '正在加载文件列表...';
  if (loadingContent.value) return '正在加载文件内容...';
  return '';
});

/**
 * 获取文件列表
 */
const fetchFileList = async () => {
  if (!props.appId || !props.codeGenType) {
    files.value = [];
    return;
  }
  loadingFiles.value = true;
  fileContent.value = '';
  activeFile.value = '';
  try {
    const listResponse = await listStaticFiles({
      codeGenType: props.codeGenType.toUpperCase(),
      appId: props.appId,
    });
    const fileList = listResponse.data.data;
    if (Array.isArray(fileList)) {
      files.value = fileList;
      if (fileList.length > 0) {
        const defaultFile =
          fileList.find(f => f.toLowerCase().includes('index.html')) ||
          fileList.find(f => f.toLowerCase().includes('index.jsx')) ||
          fileList.find(f => f.toLowerCase().includes('index.tsx')) ||
          fileList.find(f => f.toLowerCase().includes('app.vue')) ||
          fileList[0];
        await selectFile(defaultFile);
      }
    } else {
      message.error('获取文件列表失败：响应格式不正确');
      files.value = [];
    }
  } catch (error) {
    message.error(error.message || '获取文件列表失败，请检查网络或后端服务');
    console.error('Failed to fetch file list:', error);
    files.value = [];
  } finally {
    loadingFiles.value = false;
  }
};

/**
 * 根据文件路径获取并显示文件内容
 * @param filePath
 */
const selectFile = async (filePath: string | number) => {
  const path = String(filePath);
  if (!path) return;

  activeFile.value = path;
  loadingContent.value = true;
  fileContent.value = '';

  try {
    const fileUrl = `${STATIC_BASE_URL}/${props.codeGenType}_${props.appId}/${path}`;
    const response = await fetch(fileUrl);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const content = await response.text();
    fileContent.value = content;
  } catch (error) {
    message.error(`加载文件内容失败: ${path}`);
    console.error(`Failed to fetch file content for ${path}:`, error);
    fileContent.value = `// 加载文件失败: ${path}`;
  } finally {
    loadingContent.value = false;
  }
};

/**
 * 从完整路径中提取文件名用于标签页显示
 */
const getFileName = (path: string) => {
  return path.split('/').pop() || path;
};

/**
 * 根据文件后缀名，返回 highlight.js 使用的语言类名
 */
const languageClass = computed(() => {
  if (!activeFile.value) return 'language-plaintext';
  const extension = activeFile.value.split('.').pop()?.toLowerCase();
  const lang = (() => {
    switch (extension) {
      case 'html':
      case 'vue':
        return 'xml';
      case 'js':
      case 'jsx':
        return 'javascript';
      case 'ts':
      case 'tsx':
        return 'typescript';
      case 'css':
        return 'css';
      case 'scss':
        return 'scss';
      case 'less':
        return 'less';
      case 'json':
        return 'json';
      default:
        return 'plaintext';
    }
  })();
  return `language-${lang}`;
});

const highlightedContent = computed(() => {
  if (!fileContent.value) return '';
  const lang = languageClass.value.replace('language-', '');
  if (lang === 'plaintext' || !hljs.getLanguage(lang)) {
    return fileContent.value;
  }
  try {
    return hljs.highlight(fileContent.value, {
      language: lang,
      ignoreIllegals: true,
    }).value;
  } catch (error) {
    console.error('Code highlighting failed:', error);
    return fileContent.value;
  }
});

// 复制到剪贴板
const copyCode = async () => {
  if (!fileContent.value) return;
  try {
    await navigator.clipboard.writeText(fileContent.value);
    message.success('代码已复制到剪贴板');
  } catch (error) {
    message.error('复制失败');
  }
};

watch(
  () => [props.appId, props.codeGenType],
  () => {
    fetchFileList();
  },
  { immediate: true }
);

</script>

<style scoped>
/*
  根容器:
  - 必须有明确的高度 (height: 100%)。
  - 必须是 flex 容器 (display: flex)，这样它的子元素才能基于它来伸缩。
*/
.code-display-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #f7f7f7;
}

/*
  Antd Spin 组件的包装层 1 (ant-spin-nested-loading):
  - 必须伸展 (flex: 1) 来填满父容器的剩余空间。
  - 必须允许被压缩 (min-height: 0)。
  - 它自身也必须是 flex 容器，来管理它的子元素。
*/
:deep(.ant-spin-nested-loading) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/*
  Antd Spin 组件的包装层 2 (ant-spin-container)，这是我们内容的直接父元素:
  - 同样，必须伸展 (flex: 1) 来填满它的父容器。
  - 同样，必须是 flex 容器，来管理我们的 <a-tabs> 和代码区 <div>。
*/
:deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/*
  文件标签页:
  - 高度固定，不允许被压缩或拉伸 (flex-shrink: 0)。
*/
.file-tabs {
  flex-shrink: 0;
  background-color: #ffffff;
  border-radius: 8px 8px 0 0;
  border-bottom: 1px solid #e8e8e8;
}
:deep(.ant-tabs-nav) {
  margin-bottom: 0 !important;
}
:deep(.ant-tabs-tab) {
  user-select: none;
}

/*
  代码内容的包裹器 (这是最终产生滚动条的元素):
  - 必须伸展 (flex-grow: 1) 来填满剩余的所有空间。
  - **最关键的一步**: 必须设置 min-height: 0，允许它在空间不足时进行收缩。
  - 当内容超出其边界时，显示滚动条 (overflow: auto)。
*/
.code-content-wrapper {
  flex-grow: 1;
  min-height: 0;
  overflow: auto;
  position: relative; /* 用于内部 copy 按钮的定位 */
  background-color: #ffffff;
  border: 1px solid #e8e8e8;
  border-top: none;
  border-radius: 0 0 8px 8px;
}
.code-content-wrapper.no-tabs {
  border-top: 1px solid #e8e8e8;
  border-radius: 8px;
}

/* --- 以下为视觉样式，与布局问题关系不大 --- */

.code-wrapper {
  position: relative;
}

.copy-button {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  opacity: 0.7;
  transition: opacity 0.3s;
}
.copy-button:hover {
  opacity: 1;
}

pre {
  margin: 0;
  /*
    关键修改 (添加水平滚动条):
    - `width: fit-content` 让 pre 元素的宽度由其内容（code 标签）决定。
    - `min-width: 100%` 确保 pre 元素在内容不长时也能撑满容器。
    - 这两行代码共同作用，使得当代码行过长时，宽度信息能传递到外层滚动容器
      `.code-content-wrapper`，从而触发水平滚动条。
  */
  width: fit-content;
  min-width: 100%;
}

pre > code {
  padding: 1.5rem;
  font-size: 14px;
  line-height: 1.6;
  background-color: #ffffff;
  color: #383a42;
  box-sizing: border-box;
  display: block;
  min-width: 100%;
  width: fit-content;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #888;
  font-size: 14px;
}



</style>
