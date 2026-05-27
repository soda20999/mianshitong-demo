<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="审核内容" subtitle="基于面试报告进行内容审核与质量巡检">
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="状态筛选" style="width: 140px">
          <el-option label="全部状态" value="ALL" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="RUNNING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="按报告ID / 用户ID / 会话ID搜索"
          clearable
          style="max-width: 320px"
        />
        <el-button @click="fetchReports">刷新</el-button>
      </div>

      <el-table :data="pagedReports" stripe>
        <el-table-column prop="id" label="报告ID" width="100" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="interviewId" label="会话ID" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="overallScore" label="总分" width="100" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="filteredReports.length"
        />
      </div>
    </section-panel>

    <el-drawer v-model="drawerVisible" title="审核详情" size="50%">
      <template v-if="current">
        <div class="detail-header ring-card">
          <p>报告ID：{{ current.id }}</p>
          <p>用户ID：{{ current.userId }}</p>
          <p>会话ID：{{ current.interviewId }}</p>
          <p>状态：{{ current.status }}</p>
          <p>总分：{{ current.overallScore ?? "-" }}</p>
        </div>

        <div class="detail-grid">
          <article class="detail-card ring-card">
            <h4>评分维度</h4>
            <ul class="card-list">
              <li v-for="[name, score] in dimensionEntries" :key="name">{{ name }}：{{ score }}</li>
              <li v-if="!dimensionEntries.length">暂无数据</li>
            </ul>
          </article>

          <article class="detail-card ring-card">
            <h4>薄弱点</h4>
            <ul class="card-list">
              <li v-for="item in ensureList(current.weakPoints)" :key="`weak-${item}`">{{ item }}</li>
            </ul>
          </article>

          <article class="detail-card ring-card">
            <h4>复习路线</h4>
            <ul class="card-list">
              <li v-for="item in ensureList(current.reviewRoadmap)" :key="`road-${item}`">{{ item }}</li>
            </ul>
          </article>

          <article class="detail-card ring-card">
            <h4>用户回答摘录</h4>
            <ul class="card-list">
              <li
                v-for="item in ensureList(current.userAnswerHighlights)"
                :key="`highlight-${item}`"
              >
                {{ item }}
              </li>
            </ul>
          </article>

          <article class="detail-card ring-card">
            <h4>AI 标准回答</h4>
            <ul class="card-list">
              <li v-for="item in ensureList(current.aiStandardAnswers)" :key="`answer-${item}`">
                {{ item }}
              </li>
            </ul>
          </article>

          <article class="detail-card ring-card">
            <h4>亮点</h4>
            <ul class="card-list">
              <li v-for="item in ensureList(current.brightSpots)" :key="`bright-${item}`">{{ item }}</li>
            </ul>
          </article>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listAdminReportsApi } from "@/api/admin";

const reports = ref([]);
const keyword = ref("");
const statusFilter = ref("ALL");
const drawerVisible = ref(false);
const current = ref(null);
const page = ref(1);
const pageSize = ref(10);

const filteredReports = computed(() => {
  const key = keyword.value.trim();
  return reports.value.filter((item) => {
    if (statusFilter.value !== "ALL" && item.status !== statusFilter.value) {
      return false;
    }
    if (!key) {
      return true;
    }
    return [item.id, item.userId, item.interviewId].some((value) => String(value).includes(key));
  });
});

const pagedReports = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return filteredReports.value.slice(start, start + pageSize.value);
});

const dimensionEntries = computed(() => Object.entries(current.value?.dimensions || {}));

watch([statusFilter, keyword, pageSize], () => {
  page.value = 1;
});

onMounted(fetchReports);

async function fetchReports() {
  try {
    reports.value = await listAdminReportsApi();
    const maxPage = Math.max(1, Math.ceil(filteredReports.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openDetail(row) {
  current.value = row;
  drawerVisible.value = true;
}

function ensureList(items) {
  return Array.isArray(items) && items.length ? items : ["暂无数据"];
}

function statusType(status) {
  if (status === "SUCCESS") return "success";
  if (status === "FAIL") return "danger";
  if (status === "RUNNING") return "warning";
  return "info";
}
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.detail-header {
  margin-bottom: 12px;
  padding: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.detail-header p {
  margin: 0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-card {
  padding: 12px;
}

.detail-card h4 {
  margin: 0 0 10px;
}

.card-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
}

@media (max-width: 991px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-header {
    grid-template-columns: 1fr;
  }
}
</style>
