<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="面试记录管理" subtitle="仅查看面试会话与评分数据">
      <el-table :data="pagedInterviews" stripe>
        <el-table-column prop="id" label="会话ID" width="110" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="style" label="风格" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="totalScore" label="得分" width="100" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="interviews.length"
        />
      </div>
    </section-panel>

    <el-drawer v-model="detailVisible" title="面试记录详情" size="70%">
      <template v-if="currentInterview">
        <article class="detail-card ring-card">
          <h4>基础信息</h4>
          <p>会话ID：{{ currentInterview.id }}</p>
          <p>用户ID：{{ currentInterview.userId }}</p>
          <p>题集ID：{{ currentInterview.questionSetId }}</p>
          <p>标题：{{ currentInterview.title }}</p>
          <p>风格：{{ currentInterview.style }}</p>
          <p>状态：{{ currentInterview.status }}</p>
          <p>总分：{{ currentInterview.totalScore ?? "-" }}</p>
          <p>创建时间：{{ currentInterview.createdAt }}</p>
          <p>结束时间：{{ currentInterview.finishedAt || "-" }}</p>
        </article>

        <article class="detail-card ring-card">
          <h4>完整消息记录</h4>
          <div v-if="(currentInterview.messages || []).length" class="chat-list">
            <div
              v-for="(item, index) in currentInterview.messages || []"
              :key="`msg-${index}`"
              :class="['chat-item', item.role === 'user' ? 'user' : 'ai']"
            >
              <div class="chat-meta">
                <span class="role">{{ item.role === "user" ? "用户" : "AI面试官" }}</span>
                <span class="time">{{ item.time || "-" }}</span>
              </div>
              <p>{{ item.content }}</p>
            </div>
          </div>
          <p v-else class="empty-text">暂无消息记录</p>
        </article>

        <article class="detail-card ring-card">
          <h4>逐轮评分记录</h4>
          <div v-if="(currentInterview.scoreHistory || []).length" class="score-list">
            <el-card v-for="(item, index) in currentInterview.scoreHistory || []" :key="`score-${index}`" class="score-card">
              <div class="score-head">
                <strong>第 {{ index + 1 }} 轮</strong>
                <el-tag type="success">总分 {{ item.total ?? "-" }}</el-tag>
              </div>
              <p>正确性 {{ item.correctness ?? "-" }} / 完整性 {{ item.completeness ?? "-" }} / 条理性 {{ item.logic ?? "-" }}</p>
              <p>表达 {{ item.expression ?? "-" }} / 技术深度 {{ item.depth ?? "-" }}</p>
              <p>优点：{{ joinList(item.advantages) }}</p>
              <p>不足：{{ joinList(item.gaps) }}</p>
              <p>建议：{{ item.suggestion || "暂无" }}</p>
              <p>推荐表达：{{ item.recommendedAnswer || "暂无" }}</p>
            </el-card>
          </div>
          <p v-else class="empty-text">暂无评分记录</p>
        </article>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listAdminInterviewsApi } from "@/api/admin";

const interviews = ref([]);
const page = ref(1);
const pageSize = ref(10);
const detailVisible = ref(false);
const currentInterview = ref(null);

const pagedInterviews = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return interviews.value.slice(start, start + pageSize.value);
});

onMounted(async () => {
  try {
    interviews.value = await listAdminInterviewsApi();
    const maxPage = Math.max(1, Math.ceil(interviews.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
});

function openDetail(row) {
  currentInterview.value = row;
  detailVisible.value = true;
}

function joinList(value) {
  return Array.isArray(value) && value.length ? value.join("；") : "暂无";
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

.detail-card {
  margin-bottom: 12px;
  padding: 12px;
}

.detail-card h4 {
  margin: 0 0 10px;
}

.detail-card p {
  margin: 0 0 6px;
}

.chat-list {
  display: grid;
  gap: 10px;
}

.chat-item {
  border-radius: 12px;
  border: 1px solid var(--border-warm);
  padding: 10px 12px;
}

.chat-item.ai {
  background: var(--ivory);
}

.chat-item.user {
  background: rgba(201, 100, 66, 0.12);
}

.chat-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.role {
  font-size: 12px;
  color: var(--stone-gray);
}

.time {
  font-size: 12px;
  color: var(--stone-gray);
}

.score-list {
  display: grid;
  gap: 10px;
}

.score-card {
  background: var(--pure-white);
}

.score-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.empty-text {
  color: var(--stone-gray);
}
</style>
