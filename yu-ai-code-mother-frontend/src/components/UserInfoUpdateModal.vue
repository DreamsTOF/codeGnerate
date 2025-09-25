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
            <!-- 核心修改：移除了 custom-request 属性 -->
            <a-upload
              :show-upload-list="false"
              :before-upload="beforeAvatarUpload"
              accept="image/*"
              :disabled="uploadingAvatar"
            >
              <a-button size="small" :loading="uploadingAvatar">
                <template #icon><UploadOutlined /></template>
                上传新头像
              </a-button>
            </a-upload>
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
          placeholder="至少8个字符，留空则不修改"
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
import { updateUser, updateMyInfo, updateMyAvatar } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { UploadOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  open: Boolean,
  userData: Object, // 要编辑的用户数据
  isSelf: Boolean, // 是否编辑当前用户
})

const emit = defineEmits(['update:open', 'updated'])

const loginUserStore = useLoginUserStore()
const visible = ref(props.open)
const formState = ref<any>({})

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
  (val) => {
    formState.value = {
      ...val,
      userPassword: '',
    }
    if (val) {
      previewAvatar.value = val.userAvatar
    }
  },
  { deep: true, immediate: true },
)
watch(visible, (val) => emit('update:open', val))

const handleOk = async () => {
  if (formState.value.userPassword && formState.value.userPassword.length < 8) {
    message.error('密码长度不能少于8个字符')
    return
  }

  try {
    const submitData = { ...formState.value }
    if (!submitData.userPassword) {
      delete submitData.userPassword
    }

    const res = props.isSelf ? await updateMyInfo(submitData) : await updateUser(submitData);

    if (res?.data?.code === 0) {
      message.success('修改成功')
      if (props.isSelf) {
        const updatedUserData = { ...loginUserStore.loginUser, ...formState.value }
        loginUserStore.setLoginUser(updatedUserData)
      }
      emit('updated', formState.value)
      visible.value = false
    } else {
      message.error('修改失败: ' + (res?.data?.message || '未知错误'))
    }
  } catch (e: any) {
    message.error('修改失败: ' + e.message)
  }
}

/**
 * 核心修改：在 before-upload 钩子中处理所有逻辑
 */
const beforeAvatarUpload = (file: File) => {
  // 1. 文件校验
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件!')
    return false; // 返回 false 阻止组件默认行为
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    message.error('图片大小不能超过 5MB!')
    return false; // 返回 false 阻止组件默认行为
  }

  // 2. 创建本地预览
  previewAvatar.value = URL.createObjectURL(file);

  // 3. 手动调用上传函数
  handleAvatarUpload(file);

  // 4. 返回 false，告诉 a-upload 组件我们的任务已经完成，不需要它再做任何事
  return false;
}

/**
 * 上传头像的实际执行函数
 */
const handleAvatarUpload = async (file: File) => {
  if (!props.isSelf) {
    message.error('只能修改自己的头像');
    return;
  }

  uploadingAvatar.value = true;
  const originalAvatar = formState.value.userAvatar;
  try {
    const res = await updateMyAvatar(file);

    if (res?.data?.code === 0) {
      message.success('头像上传成功');
      const newAvatarUrl = res.data.data;
      formState.value.userAvatar = newAvatarUrl;
      previewAvatar.value = newAvatarUrl;

      if (loginUserStore.loginUser) {
        loginUserStore.setLoginUser({
          ...loginUserStore.loginUser,
          userAvatar: newAvatarUrl,
        });
      }
    } else {
      message.error('头像上传失败: ' + (res?.data?.message || '未知错误'));
      previewAvatar.value = originalAvatar;
    }
  } catch (error: any) {
    message.error('头像上传失败: ' + error.message);
    previewAvatar.value = originalAvatar;
  } finally {
    uploadingAvatar.value = false;
  }
};

const handleCancel = () => {
  formState.value.userPassword = ''
  passwordStatus.value = ''
  passwordHelpText.value = ''
  previewAvatar.value = props.userData?.userAvatar || ''
  visible.value = false
}
</script>

<style scoped>
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fafafa;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}
.avatar-preview {
  flex-shrink: 0;
  border: 2px dashed #d9d9d9;
  transition: border-color 0.3s ease;
  cursor: pointer;
}
.avatar-preview:hover {
  border-color: #1890ff;
}
.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>

