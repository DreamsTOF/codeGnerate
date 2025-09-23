<template>
  <a-modal
    v-model:open="visible"
    :title="isSelf ? '修改个人信息' : '修改用户信息'"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-form :model="formState" layout="vertical">
      <!-- 头像上传 -->
      <a-form-item label="头像">
        <div class="avatar-upload">
          <a-avatar
            :src="previewAvatar || formState.userAvatar"
            :size="64"
            class="avatar-preview"
          >
            {{ formState.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <div class="avatar-actions">
            <a-upload
              :show-upload-list="false"
              :before-upload="beforeAvatarUpload"
              :custom-request="handleAvatarUpload"
              accept="image/*"
            >
              <a-button size="small">
                <template #icon><UploadOutlined /></template>
                上传头像
              </a-button>
            </a-upload>
            <a-button
              v-if="formState.userAvatar"
              size="small"
              danger
              @click="removeAvatar"
            >
              <template #icon><DeleteOutlined /></template>
              移除
            </a-button>
          </div>
        </div>
      </a-form-item>

      <a-form-item label="用户名">
        <a-input v-model:value="formState.userName" />
      </a-form-item>
      <a-form-item label="个人简介">
        <a-textarea v-model:value="formState.userProfile" :rows="4" />
      </a-form-item>
      <!-- 修改密码部分 -->
      <a-form-item label="新密码" :help="passwordHelpText" :validate-status="passwordStatus">
        <a-input-password
          v-model:value="formState.userPassword"
          placeholder="至少8个字符"
          :visibilityToggle="true"
          @change="checkPasswordStrength"
        />
      </a-form-item>

      <a-form-item
        v-if="!isSelf && loginUserStore.loginUser?.userRole === 'admin'"
        label="用户角色"
      >
        <a-select v-model:value="formState.userRole">
          <a-select-option value="user">普通用户</a-select-option>
          <a-select-option value="admin">管理员</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { updateUser,updateMyInfo,updateMyAvatar } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  open: Boolean,
  userData: Object, // 要编辑的用户数据
  isSelf: Boolean, // 是否编辑当前用户
})

const emit = defineEmits(['update:open', 'updated'])

const loginUserStore = useLoginUserStore()
const visible = ref(props.open)
const formState = ref({
  ...props.userData,
})

// 头像相关状态
const previewAvatar = ref('')
const uploadingAvatar = ref(false)

// 密码强度状态
const passwordStatus = ref('')
const passwordHelpText = ref('')

// 检查密码强度
const checkPasswordStrength = () => {
  if (!formState.value.userPassword) {
    passwordStatus.value = ''
    passwordHelpText.value = ''
    return
  }

  if (formState.value.userPassword.length < 8) {
    passwordStatus.value = 'error'
    passwordHelpText.value = '密码长度不能少于8个字符'
  } else {
    passwordStatus.value = 'success'
    passwordHelpText.value = '密码强度足够'
  }
}

// 监听props变化
watch(
  () => props.open,
  (val) => (visible.value = val),
)
watch(
  () => props.userData,
  (val) =>
    (formState.value = {
      ...val,
      userPassword: '', // 确保每次用户数据变化时密码字段被清空
    }),
  { deep: true },
)
watch(visible, (val) => emit('update:open', val))

const handleOk = async () => {
  // 验证密码
  if (formState.value.userPassword && formState.value.userPassword.length < 8) {
    message.error('密码长度不能少于8个字符')
    return
  }

  try {
    // 准备提交数据
    const submitData = {
      ...formState.value,
    }

    // 如果密码为空，则删除密码字段不提交
    if (!submitData.userPassword) {
      delete submitData.userPassword
    }

    let res;
    if (!props.isSelf) {
      res = await updateUser(submitData)
    } else {
      res = await updateMyInfo(submitData)
    }
    if (res.data.code === 0) {
      message.success('修改成功')

      // 如果是修改当前用户，更新store
      if (props.isSelf) {
        loginUserStore.setLoginUser({
          ...loginUserStore.loginUser,
          ...formState.value,
        })
      }

      emit('updated', formState.value)
      visible.value = false
    } else {
      message.error('修改失败: ' + res.data.message)
    }
  } catch (e) {
    message.error('修改失败')
  }
}

// 头像上传相关方法
const beforeAvatarUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    message.error('只能上传图片文件!')
    return false
  }
  if (!isLt5M) {
    message.error('图片大小不能超过 5MB!')
    return false
  }

  // 预览图片
  const reader = new FileReader()
  reader.onload = (e) => {
    previewAvatar.value = e.target?.result as string
  }
  reader.readAsDataURL(file)

  return false // 阻止默认上传行为
}

const handleAvatarUpload = async ({ file }: any) => {
  if (!props.isSelf) {
    message.error('只能修改自己的头像')
    return
  }

  uploadingAvatar.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)

    const res = await updateMyAvatar({}, formData)
    if (res.data.code === 0) {
      message.success('头像上传成功')
      formState.value.userAvatar = res.data.data || previewAvatar.value

      // 更新store中的头像
      if (loginUserStore.loginUser) {
        loginUserStore.setLoginUser({
          ...loginUserStore.loginUser,
          userAvatar: formState.value.userAvatar
        })
      }
    } else {
      message.error('头像上传失败: ' + res.data.message)
      previewAvatar.value = ''
    }
  } catch (error) {
    message.error('头像上传失败')
    previewAvatar.value = ''
  } finally {
    uploadingAvatar.value = false
  }
}

const removeAvatar = async () => {
  if (!props.isSelf) {
    message.error('只能移除自己的头像')
    return
  }

  try {
    const res = await updateMyAvatar({ avatarUrl: '' }, {})
    if (res.data.code === 0) {
      message.success('头像移除成功')
      formState.value.userAvatar = ''
      previewAvatar.value = ''

      // 更新store中的头像
      if (loginUserStore.loginUser) {
        loginUserStore.setLoginUser({
          ...loginUserStore.loginUser,
          userAvatar: ''
        })
      }
    } else {
      message.error('头像移除失败: ' + res.data.message)
    }
  } catch (error) {
    message.error('头像移除失败')
  }
}

const handleCancel = () => {
  // 重置密码字段
  formState.value.userPassword = ''
  passwordStatus.value = ''
  passwordHelpText.value = ''
  // 重置头像预览
  previewAvatar.value = ''
  visible.value = false
}
</script>

<style scoped>
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-preview {
  flex-shrink: 0;
  border: 2px dashed #d9d9d9;
  transition: border-color 0.3s ease;
}

.avatar-preview:hover {
  border-color: #1890ff;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 头像预览容器 */
.avatar-upload {
  background: #fafafa;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}
</style>
