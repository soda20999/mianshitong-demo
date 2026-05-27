<template>
  <div class="page-shell report-page">
    <section-panel kicker="Reports" title="面试复盘报告" subtitle="异步生成任务状态：PENDING / RUNNING / SUCCESS / FAIL">
      <div class="actions">
        <el-button type="primary" @click="fetchReports">刷新状态</el-button>
        <el-button :disabled="!activeReport" @click="redoWrongQuestions">重做错题</el-button>
        <el-button type="success" :loading="batchExporting" :disabled="!selectedReportIds.length" @click="batchExportPdf">
          批量导出PDF
        </el-button>
      </div>
      <el-table :data="reports" stripe @row-click="selectReport" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="报告ID" width="120" />
        <el-table-column prop="interviewId" label="面试ID" width="120" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="overallScore" label="总分" width="100" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              :loading="exportingId === row.id"
              :disabled="row.status !== 'SUCCESS'"
              @click.stop="exportSinglePdf(row)"
            >
              导出PDF
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section-panel>

    <section-panel kicker="Detail" title="报告详情">
      <template v-if="activeReport">
        <div class="report-head">
          <h3>综合得分：{{ activeReport.overallScore ?? "--" }}</h3>
          <el-tag :type="tagType(activeReport.status)">{{ activeReport.status }}</el-tag>
        </div>
        <template v-if="activeReport.status === 'SUCCESS'">
          <div class="detail-layout">
            <article class="detail-card ring-card">
              <header class="detail-head">
                <p class="editorial-kicker">Scoring</p>
                <h4>能力维度评分</h4>
              </header>
              <div class="dimension-list">
                <div v-for="(score, name) in activeReport.dimensions || {}" :key="name" class="dimension-item">
                  <span>{{ name }}</span>
                  <el-progress :percentage="score" color="var(--terracotta-brand)" />
                </div>
              </div>
            </article>

            <div class="two-col-group">
              <article class="detail-card ring-card detail-card-scroll">
                <header class="detail-head">
                  <p class="editorial-kicker">Weak Points</p>
                  <h4>高频失分点 / 薄弱知识点</h4>
                </header>
                <ul class="card-list">
                  <li
                    v-for="(item, idx) in listOrPlaceholder(activeReport.weakPoints, '暂无失分点')"
                    :key="`weak-${idx}-${item}`"
                  >
                    {{ item }}
                  </li>
                </ul>
              </article>

              <article class="detail-card ring-card">
                <header class="detail-head">
                  <p class="editorial-kicker">Common Weakness</p>
                  <h4>常见薄弱点分析</h4>
                </header>
                <el-empty v-if="!commonWeakPoints.length" description="暂无足够数据" />
                <ul v-else class="card-list">
                  <li v-for="item in commonWeakPoints" :key="item.point">
                    {{ item.point }}（出现 {{ item.count }} 次）
                  </li>
                </ul>
              </article>
            </div>

            <article class="detail-card ring-card detail-card-scroll">
              <header class="detail-head">
                <p class="editorial-kicker">Roadmap</p>
                <h4>建议复习路线</h4>
              </header>
              <ul class="card-list">
                <li
                  v-for="(item, idx) in listOrPlaceholder(activeReport.reviewRoadmap, '暂无复习路线')"
                  :key="`road-${idx}-${item}`"
                >
                  {{ item }}
                </li>
              </ul>
            </article>

            <article class="detail-card ring-card detail-card-scroll">
              <header class="detail-head">
                <p class="editorial-kicker">Questions</p>
                <h4>本场问题列表</h4>
              </header>
              <ol class="question-list">
                <li
                  v-for="(item, index) in listOrPlaceholder(activeReport.questionList, '暂无题目记录')"
                  :key="`${index}-${item}`"
                  class="question-item"
                >
                  <span class="question-text">{{ item }}</span>
                  <el-button v-if="activeReport.questionList?.length" text class="favorite-btn" @click="favoriteQuestion(index)">
                    <el-icon size="16" :color="isQuestionFavorited(index) ? 'var(--terracotta-brand)' : 'var(--stone-gray)'">
                      <StarFilled v-if="isQuestionFavorited(index)" />
                      <Star v-else />
                    </el-icon>
                  </el-button>
                </li>
              </ol>
            </article>

            <div class="two-col-group">
              <article class="detail-card ring-card">
                <header class="detail-head">
                  <p class="editorial-kicker">Answer Highlights</p>
                  <h4>用户回答摘录</h4>
                </header>
                <ul class="card-list">
                  <li
                    v-for="(item, idx) in listOrPlaceholder(activeReport.userAnswerHighlights, '暂无回答摘录')"
                    :key="`highlight-${idx}-${item}`"
                  >
                    {{ item }}
                  </li>
                </ul>
              </article>

              <article class="detail-card ring-card detail-card-scroll">
                <header class="detail-head">
                  <p class="editorial-kicker">Recommended Expression</p>
                  <h4>AI 推荐标准表达</h4>
                </header>
                <ul class="card-list">
                  <li
                    v-for="(item, idx) in listOrPlaceholder(activeReport.aiStandardAnswers, '暂无标准表达')"
                    :key="`answer-${idx}-${item}`"
                  >
                    {{ item }}
                  </li>
                </ul>
              </article>
            </div>
          </div>
        </template>
        <p v-else>报告仍在生成中，请点击“刷新状态”获取最新结果。</p>
      </template>
      <p v-else>请选择一份报告查看详情。</p>
    </section-panel>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Star, StarFilled } from "@element-plus/icons-vue";
import SectionPanel from "@/components/common/SectionPanel.vue";
import {
  batchExportReportPdfApi,
  exportReportPdfApi,
  favoriteReportQuestionApi,
  getReportDetailApi,
  listReportsApi,
  redoWrongQuestionsApi
} from "@/api/report";
import { listInterviewsApi } from "@/api/interview";
import { listQuestionSetsApi } from "@/api/question";

const router = useRouter();
const reports = ref([]);
const activeReport = ref(null);
const favoriteQuestionMap = ref({});
const interviews = ref([]);
const questionSets = ref([]);
const selectedReportIds = ref([]);
const exportingId = ref(null);
const batchExporting = ref(false);

const commonWeakPoints = computed(() => {
  const counter = new Map();
  reports.value
    .filter((item) => item.status === "SUCCESS")
    .forEach((item) => {
      (item.weakPoints || []).forEach((point) => {
        counter.set(point, (counter.get(point) || 0) + 1);
      });
    });
  return [...counter.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([point, count]) => ({ point, count }));
});

onMounted(async () => {
  await Promise.all([fetchReports(), fetchFavoriteMeta()]);
});

async function fetchReports() {
  try {
    reports.value = await listReportsApi();
    const latestIdSet = new Set(reports.value.map((item) => item.id));
    selectedReportIds.value = selectedReportIds.value.filter((id) => latestIdSet.has(id));
    if (reports.value.length && !activeReport.value) {
      await selectReport(reports.value[0]);
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function selectReport(row) {
  try {
    activeReport.value = await getReportDetailApi(row.id);
    syncFavoriteState(activeReport.value.id, activeReport.value.interviewId);
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function fetchFavoriteMeta() {
  try {
    const [interviewList, questionSetList] = await Promise.all([listInterviewsApi(), listQuestionSetsApi()]);
    interviews.value = interviewList || [];
    questionSets.value = questionSetList || [];
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function ensureFavoriteMap(reportId) {
  if (!favoriteQuestionMap.value[reportId]) {
    favoriteQuestionMap.value[reportId] = [];
  }
}

function syncFavoriteState(reportId, interviewId) {
  ensureFavoriteMap(reportId);
  const interview = interviews.value.find((item) => item.id === interviewId);
  if (!interview) {
    favoriteQuestionMap.value[reportId] = [];
    return;
  }
  const favoriteIds = new Set(interview.favoriteQuestionIds || []);
  const questionSet = questionSets.value.find((item) => item.id === interview.questionSetId);
  if (!questionSet || !questionSet.questions) {
    favoriteQuestionMap.value[reportId] = [];
    return;
  }
  favoriteQuestionMap.value[reportId] = questionSet.questions
    .map((question, index) => ({ id: question.id, index }))
    .filter((item) => favoriteIds.has(item.id))
    .map((item) => item.index);
}

function isQuestionFavorited(index) {
  const reportId = activeReport.value?.id;
  if (!reportId) return false;
  ensureFavoriteMap(reportId);
  return favoriteQuestionMap.value[reportId].includes(index);
}

async function favoriteQuestion(index) {
  if (!activeReport.value) return;
  try {
    const data = await favoriteReportQuestionApi(activeReport.value.id, index);
    const favorited = Boolean(data?.favorited);
    ensureFavoriteMap(activeReport.value.id);
    if (favorited) {
      if (!favoriteQuestionMap.value[activeReport.value.id].includes(index)) {
        favoriteQuestionMap.value[activeReport.value.id] = [
          ...favoriteQuestionMap.value[activeReport.value.id],
          index
        ];
      }
      ElMessage.success("已收藏该题目");
    } else {
      favoriteQuestionMap.value[activeReport.value.id] = favoriteQuestionMap.value[activeReport.value.id].filter(
        (item) => item !== index
      );
      ElMessage.success("已取消收藏");
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function redoWrongQuestions() {
  if (!activeReport.value) {
    ElMessage.warning("请先选择一份报告");
    return;
  }
  try {
    const questionSet = await redoWrongQuestionsApi(activeReport.value.id);
    ElMessage.success("错题重做题集已生成，正在进入模拟面试");
    router.push(`/interview?questionSetId=${questionSet.id}`);
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function onSelectionChange(rows) {
  selectedReportIds.value = (rows || []).map((item) => item.id);
}

async function exportSinglePdf(row) {
  if (!row || row.status !== "SUCCESS") {
    ElMessage.warning("仅支持导出已生成成功的报告");
    return;
  }
  exportingId.value = row.id;
  try {
    const blob = await exportReportPdfApi(row.id);
    triggerDownload(blob, `report-${row.id}.pdf`);
    ElMessage.success("报告导出成功");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    exportingId.value = null;
  }
}

async function batchExportPdf() {
  if (!selectedReportIds.value.length) {
    ElMessage.warning("请先勾选要导出的报告");
    return;
  }
  const successIds = reports.value
    .filter((item) => selectedReportIds.value.includes(item.id) && item.status === "SUCCESS")
    .map((item) => item.id);
  if (!successIds.length) {
    ElMessage.warning("所选报告均未生成成功，无法导出");
    return;
  }
  batchExporting.value = true;
  try {
    const blob = await batchExportReportPdfApi(successIds);
    triggerDownload(blob, `reports-${Date.now()}.zip`);
    ElMessage.success(`已导出 ${successIds.length} 份报告`);
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    batchExporting.value = false;
  }
}

function triggerDownload(blobData, fileName) {
  const blob = blobData instanceof Blob ? blobData : new Blob([blobData]);
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  window.URL.revokeObjectURL(url);
}

function listOrPlaceholder(list, placeholder) {
  if (Array.isArray(list) && list.length) {
    return list;
  }
  return [placeholder];
}

function tagType(status) {
  if (status === "SUCCESS") return "success";
  if (status === "RUNNING") return "warning";
  if (status === "FAIL") return "danger";
  return "info";
}
</script>

<style scoped>
.report-page {
  display: grid;
  gap: 16px;
}

.actions {
  margin-bottom: 10px;
  display: flex;
  gap: 10px;
}

.report-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-layout {
  display: grid;
  gap: 14px;
}

.two-col-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: stretch;
}

.detail-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  min-height: 100%;
  height: 100%;
  overflow: hidden;
}

.two-col-group > .detail-card {
  min-height: 100%;
}

.dimension-list {
  display: grid;
  gap: 8px;
}

.dimension-item {
  display: grid;
  grid-template-columns: 90px 1fr;
  align-items: center;
  gap: 10px;
}

.question-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.question-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 0;
}

.question-text {
  flex: 1;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.favorite-btn {
  padding: 2px;
}

.card-list {
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
  list-style: none;
}

.detail-card-scroll .card-list,
.detail-card-scroll .question-list {
  max-height: clamp(220px, 42vh, 460px);
  overflow: auto;
  padding-right: 6px;
}

.detail-card-scroll .card-list::-webkit-scrollbar,
.detail-card-scroll .question-list::-webkit-scrollbar {
  width: 8px;
}

.detail-card-scroll .card-list::-webkit-scrollbar-thumb,
.detail-card-scroll .question-list::-webkit-scrollbar-thumb {
  background: rgba(176, 174, 165, 0.7);
  border-radius: var(--radius-xlarge);
}

.card-list li {
  position: relative;
  padding-left: 14px;
  line-height: 1.6;
  color: var(--olive-gray);
  margin-bottom: 0;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.card-list li::before {
  content: "";
  position: absolute;
  left: 0;
  top: 9px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--terracotta-brand);
}

.question-item::before {
  content: "";
  width: 6px;
  height: 6px;
  margin-top: 10px;
  border-radius: 50%;
  background: var(--terracotta-brand);
  flex-shrink: 0;
}

:deep(.detail-card .el-empty) {
  padding: 8px 0;
}

.detail-head {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-cream);
}

.detail-head h4 {
  font-size: 1.1rem;
  line-height: 1.2;
}

ul,
ol {
  margin: 0;
  color: var(--olive-gray);
}

li {
  margin-bottom: 6px;
}

@media (max-width: 991px) {
  .two-col-group {
    grid-template-columns: 1fr;
  }
}
</style>
