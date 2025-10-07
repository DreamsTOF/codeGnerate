<template>
  <a-modal
    :open="props.open"
    title="ai额度兑换，一次50万token"
    @ok="handleSubmit"
    @cancel="handleCancel"
    @update:open="(value: boolean) => emit('update:open', value)"
    :confirm-loading="loading"
  >
    <a-form name="formData" layout="vertical" :model="formData">
      <a-form-item name="cdKey" label="兑换码">
        <a-input
          v-model:value="formData.cdKey"
          placeholder="请输入兑换码"
          allow-clear
        />
      </a-form-item>
    </a-form>

    <!-- 获取兑换码按钮 -->
    <div class="action-buttons">
      <a-button
        type="primary"
        @click="handleGetCdKey"
        :loading="getCdKeyLoading"
        block
      >
        获取兑换码
      </a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch, toRefs } from 'vue'
import { message } from 'ant-design-vue'
import { useCdKey, getCdKey } from '@/api/accessKeyController.ts'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits(['update:open'])

// 表单数据
const formData = reactive<{
  cdKey: string
}>({
  cdKey: '',
})


// 提交任务状态
const loading = ref(false)

// 获取兑换码状态
const getCdKeyLoading = ref(false)


/**
 * 获取兑换码
 */
const handleGetCdKey = async () => {
  getCdKeyLoading.value = true

  try {
    const res = await getCdKey()
    if (res.data.code === 0 && res.data.data) {
      // 将获取到的兑换码填入输入框
      formData.cdKey = res.data.data
      message.success('获取兑换码成功！')
    } else {
      message.error('获取兑换码失败：' + res.data.message)
    }
  } catch (error) {
    message.error('获取兑换码失败，请稍后重试')
  } finally {
    getCdKeyLoading.value = false
  }
}

/**
 * 提交兑换
 */
const handleSubmit = async () => {
  // 校验兑换码是否为空
  if (!formData.cdKey) {
    message.error('请输入兑换码')
    return
  }

  loading.value = true

  try {
    // 调用兑换 API，使用PUT方法将兑换码传递给后端校验
    const res = await useCdKey({
      cdKey: formData.cdKey,
    })

    // 操作成功
    if (res.data.code === 0 && res.data.data) {
      message.success('兑换成功！')
      // 清空表单
      formData.cdKey = ''
      emit('update:open', false)
    } else {
      message.error('兑换失败：' + res.data.message)
    }
  } catch (error) {
    message.error('兑换失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  emit('update:open', false)
}



// 监听弹窗打开状态
const { open } = toRefs(props)
watch(open, (newVal) => {
  if (newVal) {
    // 重置表单
    formData.cdKey = ''
  }
})
</script>

<style scoped>

.action-buttons {
  margin-top: 16px;
}
</style>
