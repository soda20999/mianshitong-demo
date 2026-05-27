<template>
  <div class="auth-page">
    <div class="auth-panel whisper-card">
      <section class="auth-intro">
        <p class="editorial-kicker">AI Interview Assistant</p>
        <h1>面试通</h1>
        <p>围绕目标岗位完成简历解析、JD分析、题目生成、模拟面试、AI评分和复盘报告。</p>
      </section>

      <section class="auth-form">
        <el-tabs v-model="activeName" stretch>
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <el-form v-if="activeName === 'login'" :model="loginForm" @submit.prevent="onLogin">
          <el-form-item label="邮箱">
            <el-input v-model="loginForm.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" :loading="authStore.loading" class="full-btn" @click="onLogin">登录</el-button>
        </el-form>

        <el-form v-else :model="registerForm" @submit.prevent="onRegister">
          <el-form-item label="昵称">
            <el-input v-model="registerForm.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="registerForm.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="registerForm.password" type="password" show-password placeholder="6-32位密码" />
          </el-form-item>
          <el-form-item label="目标岗位">
            <el-input v-model="registerForm.targetPosition" placeholder="例如 Java后端工程师" />
          </el-form-item>
          <el-button type="primary" :loading="authStore.loading" class="full-btn" @click="onRegister">注册并进入</el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/store/auth";

const router = useRouter();
const authStore = useAuthStore();
const activeName = ref("login");

const loginForm = reactive({
  email: "",
  password: ""
});

const registerForm = reactive({
  nickname: "",
  email: "",
  password: "",
  targetPosition: ""
});

async function onLogin() {
  if (!loginForm.email || !loginForm.password) {
    ElMessage.warning("请填写邮箱和密码");
    return;
  }
  try {
    await authStore.login(loginForm);
    ElMessage.success("登录成功");
    router.replace("/dashboard");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function onRegister() {
  if (!registerForm.nickname || !registerForm.email || !registerForm.password || !registerForm.targetPosition) {
    ElMessage.warning("请完整填写注册信息");
    return;
  }
  try {
    await authStore.register(registerForm);
    ElMessage.success("注册成功");
    router.replace("/dashboard");
  } catch (error) {
    ElMessage.error(error.message);
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: var(--parchment);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.auth-panel {
  max-width: 1080px;
  width: 100%;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 24px;
  padding: 28px;
  border-radius: 24px;
}

.auth-intro {
  border-radius: 16px;
  background: var(--ivory);
  border: 1px solid var(--border-cream);
  padding: 24px;
  display: grid;
  gap: 12px;
}

.auth-form {
  border-radius: 16px;
  background: var(--pure-white);
  border: 1px solid var(--border-cream);
  padding: 20px;
}

.full-btn {
  width: 100%;
}

@media (max-width: 768px) {
  .auth-panel {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}
</style>
