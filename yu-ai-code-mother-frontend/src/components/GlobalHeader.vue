<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.ico" alt="Logo" />
            <h1 class="site-title">AI应用生成平台</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div v-if="loginUserStore.loginUser.id">
          <a-dropdown>
            <a-space class="user-login-status">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              {{ loginUserStore.loginUser.userName ?? '无名' }}
            </a-space>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="openUserInfoModal">
                  <SettingOutlined />
                  修改个人信息
                </a-menu-item>
                <a-menu-item @click="doLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
        <div v-else>
          <a-button type="primary" href="/user/login">登录</a-button>
        </div>
      </a-col>
    </a-row>

    <!-- 用户信息修改弹窗 -->
    <UserInfoUpdateModal
      v-model:open="userInfoModalVisible"
      :user-data="loginUserStore.loginUser"
      :is-self="true"
      @updated="handleUserInfoUpdated"
    />
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined, SettingOutlined } from '@ant-design/icons-vue'
import UserInfoUpdateModal from '@/components/UserInfoUpdateModal.vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])

// 用户信息修改弹窗状态
const userInfoModalVisible = ref(false)
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  }
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 打开用户信息修改弹窗
const openUserInfoModal = () => {
  userInfoModalVisible.value = true
}

// 用户信息更新成功回调
const handleUserInfoUpdated = (updatedInfo: any) => {
  // 更新store中的用户信息
  if (loginUserStore.loginUser) {
    loginUserStore.setLoginUser({
      ...loginUserStore.loginUser,
      ...updatedInfo,
    })
  }
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: #ffffff !important;
  border-bottom: 1px solid #e8ecef !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04) !important;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 28px;
  width: 28px;
  border-radius: 4px;
  transition: transform 0.2s ease;
}

.logo:hover {
  transform: scale(1.05);
}

.site-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50 !important;
  letter-spacing: -0.5px;
}

.ant-menu-horizontal {
  background: transparent !important;
  border-bottom: none !important;
  color: #2c3e50 !important;
}

.ant-menu-item {
  color: #7f8c8d !important;
  border-radius: 6px !important;
  transition: all 0.2s ease !important;
  margin: 0 2px !important;
}

.ant-menu-item:hover {
  background: rgba(24, 144, 255, 0.08) !important;
  color: #1890ff !important;
}

.ant-menu-item-selected {
  background: #1890ff !important;
  color: #ffffff !important;
  font-weight: 500 !important;
}

/* 用户下拉菜单 */
.user-login-status {
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #e8ecef;
  transition: all 0.2s ease;
  background: #ffffff;
  cursor: pointer;
}

.user-login-status:hover {
  background: #f8f9fa;
  border-color: #1890ff;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.1);
}

/* 下拉菜单样式 */
:deep(.ant-dropdown-menu) {
  background: #ffffff !important;
  border: 1px solid #e8ecef !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08) !important;
  padding: 8px 0 !important;
}

:deep(.ant-dropdown-menu-item) {
  color: #2c3e50 !important;
  border-radius: 4px !important;
  margin: 2px 8px !important;
  transition: all 0.2s ease !important;
  padding: 8px 16px !important;
}

:deep(.ant-dropdown-menu-item:hover) {
  background: rgba(24, 144, 255, 0.08) !important;
  color: #1890ff !important;
}

:deep(.ant-avatar) {
  border: 1px solid #e8ecef !important;
  background: #f8f9fa !important;
}

/* 登录按钮样式 */
:deep(.ant-btn-primary) {
  background: #1890ff !important;
  border: none !important;
  border-radius: 6px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
}

:deep(.ant-btn-primary:hover) {
  background: #40a9ff !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.3) !important;
}
</style>
