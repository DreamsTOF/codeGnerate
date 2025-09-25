/**
 * 分屏版本对比功能演示
 * 展示左右分屏界面的核心逻辑
 */

export interface SplitViewDemo {
  title: string
  description: string
  code: string
}

/**
 * 演示数据：模拟两个版本的代码内容
 */
export function getDemoData() {
  const oldVersion = {
    version: 1,
    content: `function hello() {
  console.log("Hello, World!");
  return "Hello";
}

function calculateSum(a, b) {
  return a + b;
}

// 主函数
function main() {
  const result = hello();
  const sum = calculateSum(5, 3);
  console.log("Sum:", sum);
}`
  }

  const newVersion = {
    version: 2,
    content: `function hello() {
  console.log("Hello, Universe!");
  return "Hello";
}

function calculateSum(a, b) {
  // 添加参数验证
  if (typeof a !== 'number' || typeof b !== 'number') {
    throw new Error('Parameters must be numbers');
  }
  return a + b;
}

function calculateProduct(a, b) {
  return a * b;
}

// 主函数
function main() {
  const result = hello();
  const sum = calculateSum(5, 3);
  const product = calculateProduct(4, 6);
  console.log("Sum:", sum);
  console.log("Product:", product);
}`
  }

  return { oldVersion, newVersion }
}

/**
 * 模拟分屏显示逻辑
 */
export function simulateSplitView() {
  const { oldVersion, newVersion } = getDemoData()

  const oldLines = oldVersion.content.split('\n')
  const newLines = newVersion.content.split('\n')

  console.log('=== 分屏版本对比演示 ===')
  console.log('左侧：旧版本 (v1)'.padEnd(40) + '右侧：新版本 (v2)')
  console.log('-'.repeat(80))

  // 计算最大行数
  const maxLines = Math.max(oldLines.length, newLines.length)

  for (let i = 0; i < maxLines; i++) {
    const oldLine = oldLines[i] || ''
    const newLine = newLines[i] || ''

    const leftContent = `${String(i + 1).padStart(3)} | ${oldLine}`
    const rightContent = `${String(i + 1).padStart(3)} | ${newLine}`

    // 检查是否有变化
    const isChanged = oldLine !== newLine
    const hasOldLine = i < oldLines.length
    const hasNewLine = i < newLines.length

    let leftMarker = ''
    let rightMarker = ''

    if (isChanged) {
      if (hasOldLine && !hasNewLine) {
        leftMarker = ' [删除]'
      } else if (!hasOldLine && hasNewLine) {
        rightMarker = ' [新增]'
      } else {
        leftMarker = ' [修改]'
        rightMarker = ' [修改]'
      }
    }

    console.log(leftContent.padEnd(50) + rightMarker + rightMarker)
  }
}

/**
 * 功能特性说明
 */
export const features: SplitViewDemo[] = [
  {
    title: '左右分屏显示',
    description: '旧版本在左侧，新版本在右侧，完全分离显示，避免混淆',
    code: `<div class="compare-split-view">
  <div class="compare-panel compare-panel-left">
    <!-- 旧版本内容 -->
  </div>
  <div class="compare-panel compare-panel-right">
    <!-- 新版本内容 -->
  </div>
</div>`
  },
  {
    title: '行号标注',
    description: '每行都有清晰的行号显示，便于定位和讨论',
    code: `<div class="code-line">
  <div class="line-number">1</div>
  <div class="line-content">代码内容</div>
</div>`
  },
  {
    title: '差异高亮',
    description: '删除的行用红色背景+删除线，新增的行用绿色背景，未变化的行保持原样',
    code: `.line-removed { background: #fff2f0; color: #ff4d4f; text-decoration: line-through; }
.line-added { background: #f6ffed; color: #52c41a; }
.line-unchanged { background: white; color: #333; }`
  },
  {
    title: '独立滚动',
    description: '左右两个面板可以独立滚动，方便查看长文件的不同部分',
    code: `.panel-content {
  overflow-y: auto;
  height: 60vh;
}`
  },
  {
    title: '同步滚动（可选）',
    description: '可以扩展实现同步滚动功能，便于对比对应行',
    code: `// 可选的同步滚动功能
function setupSyncScroll() {
  const leftPanel = document.querySelector('.compare-panel-left .panel-content')
  const rightPanel = document.querySelector('.compare-panel-right .panel-content')

  leftPanel.addEventListener('scroll', () => {
    // 同步滚动逻辑
  })
}`
  }
]
