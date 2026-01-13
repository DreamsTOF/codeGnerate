import { type OpenAPIObject } from 'openapi3-ts'

export default {
  requestLibPath: "import request from '@/request'",
  schemaPath: 'http://localhost:9999/api/v3/api-docs',
  // 1. 核心改动：生成到 base 子目录，作为“父类”存放区
  serversPath: './src/services/base',

  hook: {
    // 2. 复杂过滤逻辑：只保留特定包、特定 Controller 的特定方法
    customOpenAPI: (openAPI: OpenAPIObject) => {
      const newPaths = {}

      Object.keys(openAPI.paths).forEach((path) => {
        const pathItem = openAPI.paths[path]

        // --- 规则配置区 ---

        // 规则 A: Auth 模块全量保留
        if (path.startsWith('/auth')) {
          newPaths[path] = pathItem
          return
        }

        // 规则 B: User 模块下的 Profile 全量保留
        if (path.startsWith('/user/profile')) {
          newPaths[path] = pathItem
          return
        }

        // 规则 C: 复杂场景 - 只想要 UserFollow 的 add (POST) 方法
        if (path.startsWith('/user/follow')) {
          // 检查是否有 POST 方法
          if (pathItem.post) {
            // 必须重新构造对象，只把 post 拿出来，丢弃该路径下的 get/delete
            newPaths[path] = {
              post: pathItem.post,
              // 如果有公共参数(parameters)在 pathItem 层级，也记得带上
              parameters: pathItem.parameters,
            }
          }
          return
        }

        // 规则 D: 想要 AppVersion 的所有 GET 请求，不要 DELETE/POST
        if (path.startsWith('/app/version')) {
          const methods = {}
          if (pathItem.get) methods['get'] = pathItem.get

          // 只有当该路径下确实有 GET 方法时才保留
          if (Object.keys(methods).length > 0) {
            newPaths[path] = { ...methods, parameters: pathItem.parameters }
          }
          return
        }
      })

      // 替换原始 paths
      openAPI.paths = newPaths
      return openAPI
    },
  },
}
