<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="敏感词管理" subtitle="AI 内容风控配置">
      <div class="sensitive-actions">
        <el-input v-model="newWord" placeholder="输入敏感词" style="max-width: 260px" />
        <el-button type="primary" @click="addWord">新增敏感词</el-button>
      </div>

      <el-table :data="pagedWords" stripe>
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="word" label="敏感词" min-width="220" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? "启用" : "禁用" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="removeWord(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="words.length"
        />
      </div>
    </section-panel>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { addSensitiveWordApi, listSensitiveWordsApi, removeSensitiveWordApi } from "@/api/admin";

const words = ref([]);
const newWord = ref("");
const page = ref(1);
const pageSize = ref(10);

const pagedWords = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return words.value.slice(start, start + pageSize.value);
});

onMounted(fetchWords);

async function fetchWords() {
  try {
    words.value = await listSensitiveWordsApi();
    const maxPage = Math.max(1, Math.ceil(words.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function addWord() {
  if (!newWord.value.trim()) {
    ElMessage.warning("请输入敏感词");
    return;
  }
  try {
    await addSensitiveWordApi({ word: newWord.value.trim() });
    newWord.value = "";
    ElMessage.success("新增成功");
    await fetchWords();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function removeWord(id) {
  try {
    await removeSensitiveWordApi(id);
    ElMessage.success("删除成功");
    await fetchWords();
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

.sensitive-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
