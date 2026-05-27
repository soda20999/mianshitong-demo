<template>
  <div class="page-shell profile-page">
    <section-panel kicker="Profile" title="个人信息维护" subtitle="头像、昵称、目标岗位">
      <el-form :model="form" label-position="top" class="profile-form">
        <el-form-item label="昵称">
          <div class="nickname-row">
            <el-avatar :size="64" :src="form.avatar || undefined" class="profile-avatar">
              {{ (form.nickname || "U").slice(0, 1) }}
            </el-avatar>
            <el-input v-model="form.nickname" maxlength="40" :disabled="!editing" />
          </div>
        </el-form-item>

        <el-form-item label="目标岗位">
          <el-input v-model="form.targetPosition" maxlength="100" :disabled="!editing" />
        </el-form-item>

        <el-form-item label="头像">
          <input
            ref="avatarInputRef"
            class="hidden-input"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            @change="onAvatarFileChange"
          />
          <div class="avatar-actions">
            <el-button :disabled="!editing" @click="pickAvatarFile">本地上传头像</el-button>
            <span class="avatar-hint">{{ avatarFileName || "支持 png/jpg/webp/gif，大小不超过 2MB" }}</span>
            <el-button v-if="editing && form.avatar" text @click="clearAvatar">清空</el-button>
          </div>
        </el-form-item>

        <div class="profile-actions">
          <el-button v-if="!editing" type="primary" @click="startEdit">编辑资料</el-button>
          <template v-else>
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          </template>
        </div>
      </el-form>
    </section-panel>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/store/auth";
import SectionPanel from "@/components/common/SectionPanel.vue";

const authStore = useAuthStore();
const saving = ref(false);
const editing = ref(false);
const avatarInputRef = ref(null);
const avatarFileName = ref("");
const form = reactive({
  nickname: "",
  avatar: "",
  targetPosition: ""
});

watch(
  () => authStore.user,
  (user) => {
    if (!user) return;
    syncForm(user);
  },
  { immediate: true }
);

function syncForm(user) {
  form.nickname = user.nickname || "";
  form.avatar = user.avatar || "";
  form.targetPosition = user.targetPosition || "";
  avatarFileName.value = "";
}

function startEdit() {
  editing.value = true;
}

function cancelEdit() {
  if (authStore.user) {
    syncForm(authStore.user);
  }
  editing.value = false;
  clearAvatarInput();
}

function pickAvatarFile() {
  if (!editing.value) return;
  avatarInputRef.value?.click();
}

function clearAvatar() {
  if (!editing.value) return;
  form.avatar = "";
  avatarFileName.value = "";
  clearAvatarInput();
}

function clearAvatarInput() {
  if (avatarInputRef.value) {
    avatarInputRef.value.value = "";
  }
}

function onAvatarFileChange(event) {
  const [file] = event.target.files || [];
  if (!file) {
    clearAvatarInput();
    return;
  }
  if (!file.type.startsWith("image/")) {
    ElMessage.warning("请上传图片格式头像");
    clearAvatarInput();
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning("头像大小不能超过 2MB");
    clearAvatarInput();
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    form.avatar = String(reader.result || "");
    avatarFileName.value = file.name;
  };
  reader.onerror = () => {
    ElMessage.error("头像读取失败，请重试");
  };
  reader.readAsDataURL(file);
}

async function save() {
  if (!form.nickname || !form.targetPosition) {
    ElMessage.warning("昵称和目标岗位不能为空");
    return;
  }
  saving.value = true;
  try {
    await authStore.updateProfile({ ...form });
    ElMessage.success("已更新个人信息");
    editing.value = false;
    clearAvatarInput();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  display: grid;
  gap: 16px;
}

.profile-form {
  max-width: 760px;
}

.nickname-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
}

.profile-avatar {
  flex-shrink: 0;
  border: 1px solid var(--border-cream);
  box-shadow: var(--shadow-ring);
}

.avatar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.avatar-hint {
  color: var(--stone-gray);
  font-size: 14px;
}

.profile-actions {
  display: flex;
  gap: 10px;
}

.hidden-input {
  display: none;
}

@media (max-width: 640px) {
  .nickname-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
