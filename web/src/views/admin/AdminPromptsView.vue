<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="Prompt 模板管理" subtitle="按业务模块维护 Prompt，支持富文本编辑">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新建模板</el-button>
      </div>

      <el-table :data="pagedPrompts" stripe class="prompt-table">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="module" label="模块" width="180" />
        <el-table-column prop="name" label="名称" min-width="220" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="200" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="prompts.length"
        />
      </div>

      <el-dialog v-model="editDialogVisible" :title="currentId ? '编辑 Prompt 模板' : '新建 Prompt 模板'" width="860px">
        <el-form :model="form" label-position="top">
          <el-form-item label="模块">
            <el-input v-model="form.module" placeholder="resume-parse / question-generate ..." />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="内容">
            <rich-text-editor v-model="form.content" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="save">保存</el-button>
        </template>
      </el-dialog>

      <el-drawer v-model="detailVisible" title="Prompt 模板详情" size="60%">
        <template v-if="detailItem">
          <div class="detail-card ring-card">
            <p><strong>ID：</strong>{{ detailItem.id }}</p>
            <p><strong>模块：</strong>{{ detailItem.module }}</p>
            <p><strong>名称：</strong>{{ detailItem.name }}</p>
            <p><strong>更新时间：</strong>{{ detailItem.updatedAt }}</p>
          </div>
          <div class="detail-card ring-card">
            <h4>内容</h4>
            <div class="prompt-preview" v-html="detailItem.content || '<p>暂无内容</p>'" />
          </div>
        </template>
      </el-drawer>
    </section-panel>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import RichTextEditor from "@/components/richtext/RichTextEditor.vue";
import { createAdminPromptApi, listAdminPromptsApi, updateAdminPromptApi } from "@/api/admin";

const prompts = ref([]);
const currentId = ref(0);
const page = ref(1);
const pageSize = ref(10);
const editDialogVisible = ref(false);
const detailVisible = ref(false);
const detailItem = ref(null);
const form = reactive({
  module: "",
  name: "",
  content: ""
});

const pagedPrompts = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return prompts.value.slice(start, start + pageSize.value);
});

onMounted(fetchPrompts);

async function fetchPrompts() {
  try {
    prompts.value = await listAdminPromptsApi();
    const maxPage = Math.max(1, Math.ceil(prompts.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function setForm(row) {
  currentId.value = row.id;
  form.module = row.module || "";
  form.name = row.name || "";
  form.content = row.content || "";
}

function openCreate() {
  currentId.value = 0;
  form.module = "";
  form.name = "";
  form.content = "";
  editDialogVisible.value = true;
}

function openEdit(row) {
  setForm(row);
  editDialogVisible.value = true;
}

function openDetail(row) {
  detailItem.value = row;
  detailVisible.value = true;
}

async function save() {
  if (!form.module || !form.name || !form.content) {
    ElMessage.warning("请完整填写 Prompt 信息");
    return;
  }
  try {
    if (currentId.value) {
      await updateAdminPromptApi(currentId.value, form);
    } else {
      await createAdminPromptApi(form);
    }
    ElMessage.success("保存成功");
    editDialogVisible.value = false;
    await fetchPrompts();
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

.toolbar {
  margin-bottom: 12px;
}

.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.detail-card {
  margin-bottom: 12px;
  padding: 12px;
}

.detail-card p {
  margin: 0 0 8px;
}

.detail-card h4 {
  margin: 0 0 12px;
}

.prompt-preview {
  white-space: normal;
  word-break: break-word;
  line-height: 1.7;
}
</style>
