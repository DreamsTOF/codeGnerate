import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

// 在 ESM 模块中重新构建 __dirname 和 __filename
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// === 配置区 ===
// 自动生成的父类目录 (对应 openapi2ts.config.ts 的 serversPath)
const BASE_DIR = path.resolve(__dirname, '../src/services/base')
// 手写子类(代理类)存放目录
const TARGET_DIR = path.resolve(__dirname, '../src/services')

// 自动生成的文件的后缀 (openapi2ts 默认行为)
const SUFFIX_PARENT = 'Controller'
// 你想要的子类后缀 (例如: authController -> authChild)
const SUFFIX_CHILD = 'Child'

// 确保目录存在
if (!fs.existsSync(BASE_DIR)) {
  console.error(`❌ 错误：找不到 Base 目录 ${BASE_DIR}，请先运行 openapi2ts 生成！`)
  process.exit(1)
}

// === 逻辑开始 ===
console.log('🚀 开始生成/更新代理子类...')

const files = fs.readdirSync(BASE_DIR)
// 过滤出所有的 Controller 文件，排除 index.ts 和其他杂项
const parentFiles = files.filter(
  (file) => file.endsWith('.ts') && !file.toLowerCase().startsWith('index'),
)

const exportList = []

parentFiles.forEach((fileName) => {
  // 处理文件名
  // 例如 fileName = "AppVersionController.ts"
  const baseNameWithoutExt = fileName.replace('.ts', '') // AppVersionController

  // 提取核心业务名: AppVersionController -> AppVersion
  // 注意：这里简单的 replace 可能不严谨，建议根据 index.ts 里的导出名来处理，
  // 但 openapi2ts 生成的文件名通常很规范。
  let coreName = baseNameWithoutExt
  if (coreName.endsWith(SUFFIX_PARENT)) {
    coreName = coreName.substring(0, coreName.length - SUFFIX_PARENT.length)
  }

  // 构建子类名: AppVersionChild
  const childName = `${coreName}${SUFFIX_CHILD}`
  const childFileName = `${childName}.ts`
  const childFilePath = path.join(TARGET_DIR, childFileName)

  // 1. 检查子类是否存在
  if (!fs.existsSync(childFilePath)) {
    // --- 模板生成 ---
    // 关键点：
    // 1. 引入 Parent
    // 2. 导出 default { ...Parent }
    const template = `// @ts-ignore
import * as Parent from './base/${baseNameWithoutExt}';

/**
 * ${childName}
 * * 这是一个代理文件。
 * - 默认情况：直接继承自动生成的 ${baseNameWithoutExt} 所有方法。
 * - 手动重写：在下方定义同名方法，即可覆盖父类逻辑。
 */

// 示例：重写方法 (取消注释生效)
// const someMethod = async (params: any) => {
//   console.log('⚡️ 这是一个手动拦截的方法');
//   return { success: true };
// };

export default {
  ...Parent,
  // someMethod, // <--- 放入这里覆盖 Parent 中的同名 key
};
`
    fs.writeFileSync(childFilePath, template)
    console.log(`✅ [新建] ${childFileName}`)
  } else {
    console.log(`🛡️  [跳过] ${childFileName} (已存在，保留您的修改)`)
  }

  exportList.push(childName)
})

// === 生成统一入口 index.ts ===
// 这步很重要，确保外部 import { xxx } from '@/services' 时拿到的是子类
const indexContent = `
// 此文件由脚本自动生成，请勿手动修改
${exportList.map((name) => `import ${name} from './${name}';`).join('\n')}

export default {
  ${exportList.join(',\n  ')},
};
`

fs.writeFileSync(path.join(TARGET_DIR, 'index.ts'), indexContent)
console.log('🎉 index.ts 已重新生成，所有服务已指向 Child 代理类！')
