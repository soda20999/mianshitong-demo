<template>
  <div class="page-shell dashboard-page">
    <section class="hero section-block">
      <p class="editorial-kicker">Editorial Interview Workspace</p>
      <h1>把每一场面试，变成可量化的成长闭环</h1>
      <p>
        从简历解析、岗位分析到模拟面试、评分与复盘，所有链路都围绕真实招聘流程设计。
      </p>
      <div class="hero-actions">
        <el-button type="primary" @click="$router.push('/questions')">开始生成题目</el-button>
        <el-button @click="$router.push('/interview')">进入模拟面试</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <el-card v-for="item in stats" :key="item.label" class="stat-card ring-card">
        <p class="label">{{ item.label }}</p>
        <h3>{{ item.value }}</h3>
      </el-card>
    </section>

    <section-panel
      kicker="Workflow"
      title="核心流程"
      subtitle="注册登录 -> 上传简历 -> JD分析 -> 生成题目 -> 模拟面试 -> 评分追问 -> 报告复盘"
    >
      <el-steps :active="7" align-center>
        <el-step title="注册登录" />
        <el-step title="上传简历" />
        <el-step title="JD分析" />
        <el-step title="生成题目" />
        <el-step title="模拟面试" />
        <el-step title="AI评分追问" />
        <el-step title="报告复盘" />
      </el-steps>
    </section-panel>

    <section-panel kicker="Growth Curve" title="学习成长曲线" subtitle="最近面试得分趋势">
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { getDashboardOverviewApi } from "@/api/dashboard";

const overview = ref({
  resumeCount: 0,
  questionSetCount: 0,
  interviewCount: 0,
  reportCount: 0,
  growthScores: []
});

const stats = computed(() => [
  { label: "简历版本", value: overview.value.resumeCount },
  { label: "题集数量", value: overview.value.questionSetCount },
  { label: "面试场次", value: overview.value.interviewCount },
  { label: "报告数量", value: overview.value.reportCount }
]);

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

onMounted(async () => {
  try {
    overview.value = await getDashboardOverviewApi();
  } catch (error) {
    ElMessage.error(error.message);
  }
});
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 16px;
}

.hero {
  border-radius: 32px;
  display: grid;
  gap: 14px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.stats-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.stat-card {
  background: var(--ivory);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 1px;
  background: rgba(201, 100, 66, 0.35);
}

.label {
  color: var(--stone-gray);
  font-size: 13px;
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

@media (max-width: 991px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
