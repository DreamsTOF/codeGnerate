<template>
  <div id="UserLoginPage">
    <div class="body">
      <div class="main-box">
        <!-- 注册表单 -->
        <div :class="['container', 'container-register', { 'is-txl': !isLogin }]">
          <form @submit.prevent="handleRegisterSubmit">
            <h2 class="title">注册</h2>
            <span class="subtitle">创建您的账户</span>
            <input
              class="form__input"
              type="text"
              placeholder="用户名"
              v-model="registerForm.userAccount"
            />
            <input
              class="form__input"
              type="password"
              placeholder="密码"
              v-model="registerForm.userPassword"
            />
            <input
              class="form__input"
              type="password"
              placeholder="确认密码"
              v-model="registerForm.checkPassword"
            />
            <div class="form__button" @click="handleRegisterSubmit">立即注册</div>
          </form>
        </div>

        <!-- 登录表单 -->
        <div :class="['container', 'container-login', { 'is-txl is-z200': isLogin }]">
          <form @submit.prevent="handleLoginSubmit">
            <h2 class="title">登录</h2>
            <span class="subtitle">欢迎回来</span>
            <input
              class="form__input"
              type="text"
              placeholder="用户名"
              v-model="loginForm.userAccount"
            />
            <input
              class="form__input"
              type="password"
              placeholder="密码"
              v-model="loginForm.userPassword"
            />
            <div class="tips">
              没有账号？
              <a @click="isLogin = false">去注册</a>
            </div>
            <div class="form__button" @click="handleLoginSubmit">立即登录</div>
          </form>
        </div>

        <!-- 切换区域 -->
        <div :class="['switch', { 'login': isLogin }]">
          <div class="switch__container">
            <h2>{{ isLogin ? '您好 !' : '欢迎回来 !' }}</h2>
            <p>
              {{
                isLogin
                  ? '如果您还没有账号，请点击下方立即注册按钮'
                  : '如果您已经注册过账号，请点击下方立即登录按钮'
              }}
            </p>
            <div class="form__button" @click="isLogin = !isLogin">
              {{ isLogin ? '立即注册' : '立即登录' }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { userLogin, userRegister } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

// 恢复你初始的逻辑，默认显示登录界面
const isLogin = ref(true)

const loginForm = reactive({
  userAccount: '',
  userPassword: '',
})

const registerForm = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const loginUserStore = useLoginUserStore()

const handleLoginSubmit = async () => {
  if (!loginForm.userAccount) {
    message.error('请输入账号')
    return
  }
  if (!loginForm.userPassword || loginForm.userPassword.length < 8) {
    message.error('密码长度不能小于8位')
    return
  }

  const res = await userLogin(loginForm)
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}

const handleRegisterSubmit = async () => {
  if (!registerForm.userAccount) {
    message.error('请输入账号')
    return
  }
  if (!registerForm.userPassword || registerForm.userPassword.length < 8) {
    message.error('密码长度不能小于8位')
    return
  }
  if (registerForm.userPassword !== registerForm.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }

  const res = await userRegister(registerForm)
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功')
    isLogin.value = true
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style lang="scss" scoped>
.body {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: "Montserrat", sans-serif;
  font-size: 12px;
  background-color: #f0f2f5; /* 页面的中性背景色 */
  color: #a0a5a8;
}

.main-box {
  position: relative;
  width: 1000px;
  min-width: 1000px;
  min-height: 600px;
  height: 600px;
  background-color: #fff;
  box-shadow: 0 0 20px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
  overflow: hidden;

  .container {
    display: flex;
    justify-content: center;
    align-items: center;
    position: absolute;
    top: 0;
    width: 500px;
    height: 100%;
    padding: 25px;
    background: linear-gradient(135deg, #e6e9ff 0%, #f0f3ff 100%);
    transition: all 1.25s;

    form {
      display: flex;
      justify-content: center;
      align-items: center;
      flex-direction: column;
      width: 100%;
      height: 100%;

      .title {
        font-size: 34px;
        font-weight: 700;
        line-height: 2;
        color: #7b68ee; /* 紫色调 */
        margin-bottom: 10px;
      }

      .subtitle {
        font-size: 14px;
        color: #8a91b4;
        margin-bottom: 30px;
      }

      .tips {
        color: #8a91b4;
        text-align: right;
        font-size: 13px;
        margin: 8px 0 16px;
        width: 350px;

        a {
          color: #7b68ee;
          cursor: pointer;
          font-weight: bold;

          &:hover {
            text-decoration: underline;
          }
        }
      }

      .form__input {
        width: 350px;
        height: 40px;
        margin: 10px 0;
        padding-left: 15px;
        font-size: 14px;
        letter-spacing: 0.15px;
        border: none;
        outline: none;
        background-color: transparent;
        border-bottom: 2px solid #dcdce5;
        transition: 0.25s ease;

        &::placeholder {
          color: #a0a5a8;
        }

        &:focus {
          border-bottom-color: #7b68ee;
        }
      }
    }
  }

  /* 初始状态：注册表单在右侧，登录表单也在右侧但在下方 */
  .container-register {
    z-index: 100;
    left: calc(100% - 500px);
  }

  .container-login {
    left: calc(100% - 500px);
    z-index: 0;
  }

  /* is-txl 类用于将表单移动到左侧 */
  .is-txl {
    left: 0;
    transition: 1.25s;
    transform-origin: right;
  }

  /* is-z200 类用于提升表单的层级 */
  .is-z200 {
    z-index: 200;
    transition: 1.25s;
  }

  /* 修正逻辑：当 isLogin 为 false (注册状态) 时，注册表单移动到左侧并提升层级 */
  .container-register.is-txl {
    left: 0;
    z-index: 200;
  }
  /* 修正逻辑：当 isLogin 为 true (登录状态) 时，登录表单移动到左侧并提升层级 */
  .container-login.is-txl {
    left: 0;
    z-index: 200;
  }


  .switch {
    display: flex;
    justify-content: center;
    align-items: center;
    position: absolute;
    top: 0;
    left: 0;
    height: 100%;
    width: 500px;
    padding: 50px;
    z-index: 300;
    transition: 1.25s;
    background-image: url("@/assets/images/background.jpg");
    background-size: cover;
    background-position: center;
    overflow: hidden;
    color: #fff;

    .switch__container {
      display: flex;
      justify-content: center;
      align-items: center;
      flex-direction: column;
      position: absolute;
      width: 100%;
      height: 100%;
      padding: 50px 55px;
      transition: 1.25s;
      background-color: rgba(0, 0, 0, 0.3); /* 深色叠加层以提高文本可读性 */

      h2 {
        font-size: 34px;
        font-weight: 700;
        line-height: 3;
        color: #fff;
        text-shadow: 0 2px 4px rgba(0,0,0,0.5);
      }

      p {
        font-size: 14px;
        letter-spacing: 0.25px;
        text-align: center;
        line-height: 1.6;
        text-shadow: 0 1px 3px rgba(0,0,0,0.5);
      }
    }
  }

  /* 当 isLogin 为 true 时，切换区域移动到右侧 */
  .login {
    left: calc(100% - 500px);
  }

  .form__button {
    width: 220px;
    height: 50px;
    border-radius: 25px;
    margin-top: 40px;
    text-align: center;
    line-height: 50px;
    font-size: 15px;
    font-weight: bold;
    letter-spacing: 2px;
    background: linear-gradient(to right, #8971ea, #7b68ee, #6a82fb);
    color: #f9f9f9;
    cursor: pointer;
    border: none;
    box-shadow: 0 4px 15px rgba(123, 104, 238, 0.4);
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(123, 104, 238, 0.6);
    }
  }
}
</style>

