<template>
  <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
    <div class="loading-animation">
      <div class="loading-cube">
        <div class="cube-face cube-face-front">🌐</div>
        <div class="cube-face cube-face-back">💻</div>
        <div class="cube-face cube-face-right">🚀</div>
        <div class="cube-face cube-face-left">⚡</div>
        <div class="cube-face cube-face-top">🎨</div>
        <div class="cube-face cube-face-bottom">✨</div>
      </div>
    </div>
    <p class="placeholder-text">网站生成引擎正在启动...</p>
  </div>
  <div v-else-if="isGenerating" class="preview-loading">
    <div class="loading-animation">
      <div class="loading-cube">
        <div class="cube-face cube-face-front">🌐</div>
        <div class="cube-face cube-face-back">💻</div>
        <div class="cube-face cube-face-right">🚀</div>
        <div class="cube-face cube-face-left">⚡</div>
        <div class="cube-face cube-face-top">🎨</div>
        <div class="cube-face cube-face-bottom">✨</div>
      </div>
    </div>
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
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}

.placeholder-text {
  margin-top: 24px;
  font-size: 16px;
  color: #666;
  text-align: center;
}

.preview-loading p {
  margin-top: 16px;
  font-size: 16px;
  color: #1890ff;
  font-weight: 500;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

/* 3D立方体加载动画 */
.loading-animation {
  perspective: 1000px;
  width: 80px;
  height: 80px;
}

.loading-cube {
  width: 100%;
  height: 100%;
  position: relative;
  transform-style: preserve-3d;
  animation: rotateCube 3s infinite linear;
}

@keyframes rotateCube {
  0% {
    transform: rotateX(0deg) rotateY(0deg);
  }
  100% {
    transform: rotateX(360deg) rotateY(360deg);
  }
}

.cube-face {
  position: absolute;
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: 2px solid rgba(255, 255, 255, 0.3);
  opacity: 0.8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
  font-weight: bold;
}

.cube-face-front {
  transform: translateZ(40px);
}

.cube-face-back {
  transform: rotateY(180deg) translateZ(40px);
}

.cube-face-right {
  transform: rotateY(90deg) translateZ(40px);
}

.cube-face-left {
  transform: rotateY(-90deg) translateZ(40px);
}

.cube-face-top {
  transform: rotateX(90deg) translateZ(40px);
}

.cube-face-bottom {
  transform: rotateX(-90deg) translateZ(40px);
}

/* 为placeholder添加更柔和的背景 */
.preview-placeholder {
  background: rgba(255, 255, 255, 0.1);
}

.preview-loading {
  background: rgba(255, 255, 255, 0.15);
}

/* 添加呼吸效果 */
@keyframes breathe {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.loading-animation {
  animation: breathe 2s ease-in-out infinite;
}

/* 预览状态时的特殊效果 */
.preview-placeholder .loading-cube {
  animation: rotateCube 4s infinite linear, breathe 2s ease-in-out infinite;
}

.preview-loading .loading-cube {
  animation: rotateCube 1.5s infinite linear;
}
</style>

