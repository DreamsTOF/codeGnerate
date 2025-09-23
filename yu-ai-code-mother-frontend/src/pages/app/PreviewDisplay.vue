<template>
  <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
    <div class="placeholder-icon">🌐</div>
    <p>网站文件生成完成后将在这里展示</p>
  </div>
  <div v-else-if="isGenerating" class="preview-loading">
    <a-spin size="large" />
    <p>正在生成网站...</p>
  </div>
  <iframe
    v-else
    :src="previewUrl"
    class="preview-iframe"
    frameborder="0"
    @load="$emit('load')"
  ></iframe>
</template>

<script setup lang="ts">
import { defineProps, defineEmits } from 'vue';

// 定义组件接收的属性
defineProps<{
  previewUrl?: string;
  isGenerating: boolean;
}>();

// 定义组件可以触发的事件
defineEmits(['load']);
</script>

<style scoped>
/* 从主页面复制过来的相关样式 */
.preview-placeholder,
.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
  background-color: #fafafa;
}
.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.preview-loading p {
  margin-top: 16px;
}
.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>

