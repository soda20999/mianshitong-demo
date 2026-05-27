<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="系统调用情况" subtitle="系统调用概览（含 AI 调用日志）">
      <div class="summary-grid">
        <article class="summary-card ring-card">
          <p class="label">AI 调用总量</p>
          <strong>{{ summary.totalCalls }}</strong>
        </article>
        <article class="summary-card ring-card">
          <p class="label">成功率</p>
          <strong>{{ summary.successRate }}%</strong>
        </article>
        <article class="summary-card ring-card">
          <p class="label">Token 总量</p>
          <strong>{{ summary.totalTokens }}</strong>
        </article>
        <article class="summary-card ring-card">
          <p class="label">AI 成本(USD)</p>
          <strong>{{ summary.totalCost }}</strong>
        </article>
        <article class="summary-card ring-card">
          <p class="label">面试会话总量</p>
          <strong>{{ interviews.length }}</strong>
        </article>
        <article class="summary-card ring-card">
          <p class="label">报告成功率</p>
          <strong>{{ summary.reportSuccessRate }}%</strong>
        </article>
      </div>
    </section-panel>

    <section-panel kicker="Admin" title="模块调用统计">
      <el-table :data="moduleStats" stripe>
        <el-table-column prop="module" label="模块" min-width="180" />
        <el-table-column prop="calls" label="调用次数" width="110" />
        <el-table-column prop="successRate" label="成功率" width="110" />
        <el-table-column prop="promptTokens" label="Prompt Tokens" width="140" />
        <el-table-column prop="completionTokens" label="Completion Tokens" width="160" />
        <el-table-column prop="totalCost" label="成本(USD)" width="120" />
      </el-table>
    </section-panel>

    <section-panel kicker="Admin" title="AI 调用日志">
      <el-table :data="pagedLogs" stripe>
        <el-table-column prop="id" label="日志ID" width="100" />
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="promptTokens" label="Prompt Tokens" width="140" />
        <el-table-column prop="completionTokens" label="Completion Tokens" width="160" />
        <el-table-column prop="cost" label="成本(USD)" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="time" label="时间" min-width="180" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="logs.length"
        />
      </div>
    </section-panel>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listAdminAiLogsApi, listAdminInterviewsApi, listAdminReportsApi } from "@/api/admin";

const logs = ref([]);
const interviews = ref([]);
const reports = ref([]);
const page = ref(1);
const pageSize = ref(10);

onMounted(async () => {
  try {
    const [logData, interviewData, reportData] = await Promise.all([
      listAdminAiLogsApi(),
      listAdminInterviewsApi(),
      listAdminReportsApi()
    ]);
    logs.value = logData;
    interviews.value = interviewData;
    reports.value = reportData;
    const maxPage = Math.max(1, Math.ceil(logs.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
});

const pagedLogs = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return logs.value.slice(start, start + pageSize.value);
});

const summary = computed(() => {
  const totalCalls = logs.value.length;
  const successCalls = logs.value.filter((item) => item.status === "SUCCESS").length;
  const promptTokens = logs.value.reduce((sum, item) => sum + Number(item.promptTokens || 0), 0);
  const completionTokens = logs.value.reduce((sum, item) => sum + Number(item.completionTokens || 0), 0);
  const totalCost = logs.value.reduce((sum, item) => sum + Number(item.cost || 0), 0);
  const reportSuccessCount = reports.value.filter((item) => item.status === "SUCCESS").length;
  const reportSuccessRate = reports.value.length
    ? ((reportSuccessCount / reports.value.length) * 100).toFixed(1)
    : "0.0";

  return {
    totalCalls,
    successRate: totalCalls ? ((successCalls / totalCalls) * 100).toFixed(1) : "0.0",
    totalTokens: promptTokens + completionTokens,
    totalCost: totalCost.toFixed(4),
    reportSuccessRate
  };
});

const moduleStats = computed(() => {
  const bucket = new Map();
  logs.value.forEach((item) => {
    const module = item.module || "UNKNOWN";
    if (!bucket.has(module)) {
      bucket.set(module, {
        module,
        calls: 0,
        successCalls: 0,
        promptTokens: 0,
        completionTokens: 0,
        totalCost: 0
      });
    }
    const target = bucket.get(module);
    target.calls += 1;
    if (item.status === "SUCCESS") {
      target.successCalls += 1;
    }
    target.promptTokens += Number(item.promptTokens || 0);
    target.completionTokens += Number(item.completionTokens || 0);
    target.totalCost += Number(item.cost || 0);
  });
  return [...bucket.values()]
    .map((item) => ({
      module: item.module,
      calls: item.calls,
      successRate: `${item.calls ? ((item.successCalls / item.calls) * 100).toFixed(1) : "0.0"}%`,
      promptTokens: item.promptTokens,
      completionTokens: item.completionTokens,
      totalCost: item.totalCost.toFixed(4)
    }))
    .sort((a, b) => b.calls - a.calls);
});
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-card {
  padding: 12px;
}

.summary-card .label {
  margin: 0;
  color: var(--stone-gray);
  font-size: 13px;
}

.summary-card strong {
  display: inline-block;
  margin-top: 8px;
  font-size: 24px;
}

.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 991px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
