<template>
  <a-modal
    :open="props.open"
    title="会员码兑换"
    @ok="handleSubmit"
    @cancel="handleCancel"
    @update:open="(value) => emit('update:open', value)"
    :confirm-loading="loading"
  >
    <a-form name="formData" layout="vertical" :model="formData">
      <a-form-item name="vipCode" label="兑换码">
        <a-input
          v-model:value="formData.vipCode"
          placeholder="请输入会员兑换码"
          allow-clear
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { exchangeVip  } from '@/api/userController.ts'
import { useRouter } from 'vue-router'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits(['update:open'])

// 表单数据
const formData = reactive<API.VipExchangeRequest>({
  vipCode: '',
})

// 提交任务状态
const loading = ref(false)

const router = useRouter()

/**
 * 提交表单
 */
const handleSubmit = async () => {
  // 校验兑换码是否为空
  if (!formData.vipCode) {
    message.error('请输入兑换码')
    return
  }

  loading.value = true

  try {
    // 调用兑换 API
    const res = await exchangeVip ({
      vipCode: formData.vipCode,
    })

    // 操作成功
    if (res.data.code === 0 && res.data.data) {
      message.success('兑换成功！')
      emit('update:open', false)
      // 跳转到主页或其他页面
      router.push({
        path: `/`,
      })
    } else {
      message.error('兑换失败：' + res.data.message)
    }
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
  } catch (error) {
    message.error('兑换失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  emit('update:open', false)
}
</script>
