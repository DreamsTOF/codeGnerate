<template>
  <div class="markdown-content" v-html="renderedMarkdown"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

// 引入代码高亮样式
import 'highlight.js/styles/github.css'

interface Props {
  content: string
}

const props = defineProps<Props>()

// 配置 markdown-it 实例
const md: MarkdownIt = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return (
          '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
        )
      } catch {
        // 忽略错误，使用默认处理
      }
    }

    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  },
})

// 计算渲染后的 Markdown
const renderedMarkdown = computed(() => {
  return md.render(props.content)
})
</script>

<style scoped>
.markdown-content {
  line-height: 1.6;
  color: #ffffff !important;
  word-wrap: break-word;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}

/* 全局样式，影响 v-html 内容 */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 1.5em 0 0.5em 0;
  font-weight: 600;
  line-height: 1.25;
  color: #ffffff !important;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.markdown-content :deep(h1) {
  font-size: 1.5em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.3em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.1em;
}

.markdown-content :deep(p) {
  margin: 0.8em 0;
  color: rgba(255, 255, 255, 0.9) !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.8em 0;
  padding-left: 1.5em;
}

.markdown-content :deep(li) {
  margin: 0.3em 0;
  color: rgba(255, 255, 255, 0.9) !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.markdown-content :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1em;
  border-left: 4px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8) !important;
  backdrop-filter: blur(5px);
  border-radius: 0 4px 4px 0;
}

.markdown-content :deep(code) {
  background-color: rgba(255, 255, 255, 0.1);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #ffffff !important;
}

.markdown-content :deep(pre) {
  background-color: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  padding: 1em;
  overflow-x: auto;
  margin: 1em 0;
  backdrop-filter: blur(10px);
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  font-size: 0.9em;
  line-height: 1.4;
  color: #ffffff !important;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  margin: 1em 0;
  width: 100%;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.markdown-content :deep(table th),
.markdown-content :deep(table td) {
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 0.5em 0.8em;
  text-align: left;
  color: #ffffff !important;
}

.markdown-content :deep(table th) {
  background-color: rgba(255, 255, 255, 0.1);
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.markdown-content :deep(table tr:nth-child(even)) {
  background-color: rgba(255, 255, 255, 0.05);
}

.markdown-content :deep(a) {
  color: #87ceeb !important;
  text-decoration: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
  opacity: 0.8;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.5em 0;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
  margin: 1.5em 0;
}

/* 代码高亮样式优化 */
.markdown-content :deep(.hljs) {
  background-color: rgba(0, 0, 0, 0.3) !important;
  border-radius: 6px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  line-height: 1.4;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #ffffff !important;
}

/* 特定语言的代码块样式 */
.markdown-content :deep(.hljs-keyword) {
  color: #ff79c6 !important;
  font-weight: 600;
}

.markdown-content :deep(.hljs-string) {
  color: #f1fa8c !important;
}

.markdown-content :deep(.hljs-comment) {
  color: #6272a4 !important;
  font-style: italic;
}

.markdown-content :deep(.hljs-number) {
  color: #bd93f9 !important;
}

.markdown-content :deep(.hljs-function) {
  color: #50fa7b !important;
}

.markdown-content :deep(.hljs-tag) {
  color: #ff79c6 !important;
}

.markdown-content :deep(.hljs-attr) {
  color: #50fa7b !important;
}

.markdown-content :deep(.hljs-title) {
  color: #8be9fd !important;
  font-weight: 600;
}
</style>
