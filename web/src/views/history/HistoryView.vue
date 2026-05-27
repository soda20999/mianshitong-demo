<template>
  <div class="page-shell history-page">
    <section-panel kicker="History" title="历史面试记录" subtitle="查看每场题目、回答、评分和复盘">
      <el-table :data="overview.interviews || []" stripe @row-click="openInterviewDetail">
        <el-table-column prop="id" label="会话ID" width="120" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="style" label="风格" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="totalScore" label="得分" width="100" />
        <el-table-column prop="createdAt" label="时间" min-width="180" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openInterviewDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section-panel>

    <section-panel kicker="Wrong Book" title="错题本与收藏">
      <div class="wrong-book-grid">
        <article class="book-card ring-card">
          <header class="book-head">
            <h4>错题本</h4>
            <el-tag size="small" type="danger">{{ (overview.wrongQuestions || []).length }}</el-tag>
          </header>
          <el-empty v-if="!(overview.wrongQuestions || []).length" description="暂无错题记录" />
          <div v-else class="book-list">
            <div v-for="item in overview.wrongQuestions || []" :key="`${item.interviewId}-${item.questionId}`" class="book-item">
              <p class="book-question">{{ item.question }}</p>
              <div class="book-meta">
                <span class="meta-pill">面试 #{{ item.interviewId }}</span>
              </div>
            </div>
          </div>
        </article>

        <article class="book-card ring-card">
          <header class="book-head">
            <h4>收藏题目</h4>
            <el-tag size="small" type="success">{{ (overview.favorites || []).length }}</el-tag>
          </header>
          <el-empty v-if="!(overview.favorites || []).length" description="暂无收藏记录" />
          <div v-else class="book-list">
            <div v-for="item in overview.favorites || []" :key="`${item.interviewId}-${item.questionId}`" class="book-item">
              <p class="book-question">{{ item.question }}</p>
              <div class="book-meta">
                <span class="meta-pill">面试 #{{ item.interviewId }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section-panel>

    <section-panel kicker="Growth" title="学习成长曲线">
      <el-empty v-if="growthScores.length === 0" description="暂无成长数据" />
      <div v-else class="curve-wrapper">
        <svg class="growth-svg" viewBox="0 0 760 260" preserveAspectRatio="none">
          <polyline class="baseline" :points="baselinePoints" />
          <polyline class="curve" :points="growthPolyline" />
          <g v-for="point in growthPoints" :key="point.index">
            <circle class="point-dot" :cx="point.x" :cy="point.y" r="4" />
            <text class="point-label" :x="point.x" :y="point.y - 10">{{ point.score }}</text>
            <text class="x-label" :x="point.x" y="244">第{{ point.index + 1 }}场</text>
          </g>
        </svg>
      </div>
    </section-panel>

    <el-dialog v-model="detailVisible" title="面试详情" width="980px" destroy-on-close>
      <template v-if="activeInterview">
        <div class="dialog-head">
          <div>
            <h4>{{ activeInterview.title || `面试 ${activeInterview.id}` }}</h4>
            <p>风格：{{ activeInterview.style }} | 状态：{{ activeInterview.status }} | 总分：{{ activeInterview.totalScore ?? "--" }}</p>
          </div>
        </div>

        <h4>题目、回答与评分</h4>
        <el-empty v-if="!interviewRounds.length" description="暂无问答数据" />
        <div v-else class="round-list">
          <el-card v-for="round in interviewRounds" :key="round.round" class="round-card">
            <div class="round-head">
              <strong>第 {{ round.round }} 轮</strong>
              <el-tag type="success">总分 {{ round.score?.total ?? "--" }}</el-tag>
            </div>
            <p><strong>题目：</strong>{{ round.question }}</p>
            <p><strong>回答：</strong>{{ round.answer }}</p>
            <p>
              <strong>评分：</strong>
              正确性 {{ round.score?.correctness ?? "--" }} /
              完整性 {{ round.score?.completeness ?? "--" }} /
              条理性 {{ round.score?.logic ?? "--" }} /
              表达 {{ round.score?.expression ?? "--" }} /
              深度 {{ round.score?.depth ?? "--" }}
            </p>
            <p><strong>复盘建议：</strong>{{ round.score?.suggestion || "暂无" }}</p>
          </el-card>
        </div>

        <h4>复盘报告</h4>
        <template v-if="activeReportDetail && activeReportDetail.status === 'SUCCESS'">
          <p><strong>综合得分：</strong>{{ activeReportDetail.overallScore ?? "--" }}</p>
          <p><strong>薄弱点：</strong>{{ (activeReportDetail.weakPoints || []).join("；") || "暂无" }}</p>
          <p><strong>复习路线：</strong>{{ (activeReportDetail.reviewRoadmap || []).join("；") || "暂无" }}</p>
        </template>
        <p v-else>该场面试暂无可用复盘报告。</p>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { getHistoryOverviewApi } from "@/api/history";
import { getReportDetailApi, listReportsApi } from "@/api/report";

const overview = ref({
  interviews: [],
  favorites: [],
  wrongQuestions: [],
  growthScores: []
});
const reports = ref([]);
const detailVisible = ref(false);
const activeInterview = ref(null);
const activeReportDetail = ref(null);

const growthScores = computed(() => (overview.value.growthScores || []).map((item) => Number(item) || 0));

const growthPoints = computed(() => {
  const scores = growthScores.value;
  if (!scores.length) return [];

  const width = 760;
  const height = 260;
  const paddingX = 36;
  const paddingTop = 24;
  const paddingBottom = 52;
  const usableWidth = width - paddingX * 2;
  const usableHeight = height - paddingTop - paddingBottom;

  const maxScore = Math.max(...scores, 100);
  const minScore = Math.min(...scores, 0);
  const range = Math.max(maxScore - minScore, 1);
  const step = scores.length === 1 ? 0 : usableWidth / (scores.length - 1);

  return scores.map((score, index) => {
    const x = paddingX + step * index;
    const y = paddingTop + ((maxScore - score) / range) * usableHeight;
    return { index, score, x, y };
  });
});

const growthPolyline = computed(() => growthPoints.value.map((point) => `${point.x},${point.y}`).join(" "));
const baselinePoints = computed(() => {
  if (!growthPoints.value.length) return "";
  const first = growthPoints.value[0];
  const last = growthPoints.value[growthPoints.value.length - 1];
  return `${first.x},208 ${last.x},208`;
});

const interviewRounds = computed(() => {
  const interview = activeInterview.value;
  if (!interview) return [];

  const messages = interview.messages || [];
  const scoreHistory = interview.scoreHistory || [];
  const rounds = [];
  let currentQuestion = "";
  let scoreIndex = 0;

  for (const message of messages) {
    if (message.role === "ai") {
      currentQuestion = message.content || "";
      continue;
    }
    if (message.role === "user") {
      rounds.push({
        round: rounds.length + 1,
        question: currentQuestion || "未记录题目",
        answer: message.content || "未记录回答",
        score: scoreHistory[scoreIndex] || null
      });
      scoreIndex += 1;
    }
  }
  return rounds;
});

onMounted(async () => {
  await Promise.all([fetchHistory(), fetchReports()]);
});

async function fetchHistory() {
  try {
    overview.value = await getHistoryOverviewApi();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function fetchReports() {
  try {
    reports.value = await listReportsApi();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function openInterviewDetail(row) {
  activeInterview.value = row;
  detailVisible.value = true;
  await loadReportDetail(row.id);
}

async function loadReportDetail(interviewId) {
  activeReportDetail.value = null;
  const reportSummary = reports.value.find((item) => item.interviewId === interviewId);
  if (!reportSummary) return;
  try {
    activeReportDetail.value = await getReportDetailApi(reportSummary.id);
  } catch (error) {
    ElMessage.error(error.message);
  }
}
</script>

<style scoped>
.history-page {
  display: grid;
  gap: 16px;
}

.wrong-book-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.book-card {
  padding: 16px 18px;
  display: grid;
  gap: 12px;
  align-content: start;
  min-height: 0;
}

.book-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-cream);
}

.book-list {
  display: grid;
  gap: 10px;
  max-height: clamp(220px, 34vh, 320px);
  overflow-y: auto;
  padding-right: 4px;
}

.book-item {
  border: 1px solid var(--border-cream);
  border-radius: var(--radius-small);
  background: var(--pure-white);
  padding: 8px 10px;
  display: grid;
  gap: 6px;
}

.book-question {
  color: var(--charcoal-warm);
  line-height: 1.55;
  word-break: break-word;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.book-meta {
  display: flex;
  justify-content: flex-end;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--radius-xlarge);
  background: var(--warm-sand);
  color: var(--dark-warm);
  font-size: 12px;
}

ul {
  margin: 0;
  padding-left: 18px;
  color: var(--olive-gray);
}

li {
  margin-bottom: 6px;
}

.curve-wrapper {
  width: 100%;
  overflow-x: auto;
}

.growth-svg {
  width: 100%;
  min-width: 680px;
  height: 260px;
}

.baseline {
  fill: none;
  stroke: var(--border-warm);
  stroke-width: 2;
}

.curve {
  fill: none;
  stroke: var(--terracotta-brand);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.point-dot {
  fill: var(--terracotta-brand);
}

.point-label {
  fill: var(--dark-warm);
  font-size: 12px;
  text-anchor: middle;
}

.x-label {
  fill: var(--stone-gray);
  font-size: 12px;
  text-anchor: middle;
}

.dialog-head {
  margin-bottom: 8px;
}

.dialog-head p {
  color: var(--olive-gray);
}

.round-list {
  display: grid;
  gap: 10px;
  margin-bottom: 8px;
}

.round-card p {
  color: var(--olive-gray);
  margin-bottom: 6px;
}

.round-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

@media (max-width: 991px) {
  .wrong-book-grid {
    grid-template-columns: 1fr;
  }
}
</style>
