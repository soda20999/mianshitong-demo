<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="用户管理" subtitle="启用状态、目标岗位维护">
      <el-table :data="pagedUsers" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="role" label="角色" width="110" />
        <el-table-column prop="targetPosition" label="目标岗位" min-width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? "启用" : "禁用" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" @click="editRow(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="users.length"
        />
      </div>
    </section-panel>

    <el-dialog v-model="visible" title="编辑用户" width="460px">
      <el-form v-if="form" :model="form" label-position="top">
        <el-form-item label="目标岗位">
          <el-input v-model="form.targetPosition" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :disabled="form.role === 'ADMIN'" />
          <p v-if="form.role === 'ADMIN'" class="hint">管理员账号不可禁用</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listAdminUsersApi, updateAdminUserApi } from "@/api/admin";

const users = ref([]);
const visible = ref(false);
const form = ref(null);
const page = ref(1);
const pageSize = ref(10);

const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return users.value.slice(start, start + pageSize.value);
});

onMounted(fetchUsers);

async function fetchUsers() {
  try {
    users.value = await listAdminUsersApi();
    const maxPage = Math.max(1, Math.ceil(users.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function editRow(row) {
  form.value = {
    id: row.id,
    targetPosition: row.targetPosition || "",
    enabled: row.enabled,
    role: row.role
  };
  visible.value = true;
}

async function save() {
  if (!form.value) return;
  try {
    await updateAdminUserApi(form.value.id, {
      targetPosition: form.value.targetPosition,
      enabled: form.value.enabled
    });
    ElMessage.success("保存成功");
    visible.value = false;
    await fetchUsers();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.hint {
  margin: 6px 0 0;
  color: var(--stone-gray);
  font-size: 12px;
}
</style>
