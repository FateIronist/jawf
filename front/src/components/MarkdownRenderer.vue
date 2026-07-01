<template>
  <div class="markdown-body" v-html="renderedContent"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

// 导入 highlight.js 样式
import 'highlight.js/styles/github-dark.css'

const props = defineProps<{
  content: string
}>()

// 创建 markdown-it 实例并配置
const md = new MarkdownIt({
  html: true, // 允许 HTML 标签
  linkify: true, // 自动识别链接
  typographer: true, // 启用排版替换
  breaks: true, // 换行符转换为 <br>
  highlight: function (str: string, lang: string) {
    // 代码高亮
    if (lang && hljs.getLanguage(lang)) {
      try {
        const highlighted = hljs.highlight(str, {
          language: lang,
          ignoreIllegals: true,
        }).value
        return `<pre class="hljs-code-block"><div class="code-header"><span class="code-lang">${lang}</span><button class="copy-btn" onclick="navigator.clipboard.writeText(this.parentElement.nextElementSibling.textContent)">复制</button></div><code class="hljs language-${lang}">${highlighted}</code></pre>`
      } catch (__) {}
    }
    // 没有指定语言或语言不支持时，使用自动检测
    const autoHighlighted = hljs.highlightAuto(str).value
    return `<pre class="hljs-code-block"><div class="code-header"><span class="code-lang">auto</span><button class="copy-btn" onclick="navigator.clipboard.writeText(this.parentElement.nextElementSibling.textContent)">复制</button></div><code class="hljs">${autoHighlighted}</code></pre>`
  },
})

// 自定义渲染规则 - 表格
md.renderer.rules.table_open = function () {
  return '<div class="table-wrapper"><table>'
}
md.renderer.rules.table_close = function () {
  return '</table></div>'
}

// 渲染内容
const renderedContent = computed(() => {
  if (!props.content) return ''
  return md.render(props.content)
})
</script>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
  color: #303133;
}

/* 标题 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-body :deep(h1) {
  font-size: 1.5em;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.markdown-body :deep(h2) {
  font-size: 1.3em;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
}

.markdown-body :deep(h3) {
  font-size: 1.1em;
}

/* 段落 */
.markdown-body :deep(p) {
  margin-bottom: 12px;
}

/* 链接 */
.markdown-body :deep(a) {
  color: #409eff;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 粗体和斜体 */
.markdown-body :deep(strong) {
  font-weight: 600;
}

.markdown-body :deep(em) {
  font-style: italic;
}

/* 列表 */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
  margin-top: 4px;
  margin-bottom: 0;
}

/* 引用块 */
.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 4px solid #409eff;
  background-color: #f5f7fa;
  color: #606266;
}

.markdown-body :deep(blockquote p) {
  margin-bottom: 0;
}

/* 行内代码 */
.markdown-body :deep(code:not(.hljs)) {
  padding: 2px 6px;
  font-size: 0.9em;
  background-color: #f5f7fa;
  border-radius: 4px;
  color: #e6a23c;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* 代码块 */
.markdown-body :deep(.hljs-code-block) {
  position: relative;
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  background-color: #1e1e1e;
}

.markdown-body :deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background-color: #2d2d2d;
  border-bottom: 1px solid #404040;
}

.markdown-body :deep(.code-lang) {
  font-size: 12px;
  color: #888;
  text-transform: uppercase;
}

.markdown-body :deep(.copy-btn) {
  padding: 4px 8px;
  font-size: 12px;
  color: #ccc;
  background-color: #404040;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.markdown-body :deep(.copy-btn:hover) {
  background-color: #555;
  color: #fff;
}

.markdown-body :deep(code.hljs) {
  display: block;
  padding: 16px;
  overflow-x: auto;
  font-size: 0.9em;
  line-height: 1.6;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* 表格 */
.markdown-body :deep(.table-wrapper) {
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid #e4e7ed;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 10px 16px;
  text-align: left;
  border: 1px solid #e4e7ed;
}

.markdown-body :deep(th) {
  background-color: #f5f7fa;
  font-weight: 600;
}

.markdown-body :deep(tr:hover) {
  background-color: #f5f7fa;
}

/* 水平线 */
.markdown-body :deep(hr) {
  margin: 16px 0;
  border: none;
  border-top: 1px solid #e4e7ed;
}

/* 图片 */
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

/* 复选框 */
.markdown-body :deep(input[type="checkbox"]) {
  margin-right: 8px;
}
</style>
