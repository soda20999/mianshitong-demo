<template>
  <div class="page-shell hot-page">
    <section-panel kicker="Question Bank" title="题库" subtitle="热门问题排行 + 本地上传题库">
      <el-tabs v-model="activeTab" class="bank-tabs">
        <el-tab-pane label="热门问题排行" name="hot">
          <el-form inline class="hot-filter-form">
            <el-form-item label="岗位">
              <el-input v-model="filters.position" placeholder="例如 Java 后端工程师" />
            </el-form-item>
            <el-form-item label="标签">
              <el-select v-model="filters.tag" class="hot-tag-select" clearable>
                <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchHotList">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="hotList" stripe class="hot-table">
            <el-table-column label="排名" width="90">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="position" label="岗位" width="180" show-overflow-tooltip />
            <el-table-column prop="tag" label="标签" width="120" show-overflow-tooltip />
            <el-table-column prop="content" label="问题" min-width="320" show-overflow-tooltip />
            <el-table-column label="热度" width="220">
              <template #default="{ row }">
                浏览 {{ row.views }} / 收藏 {{ row.favorites }} / 练习 {{ row.practices }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250">
              <template #default="{ row }">
                <el-button size="small" @click="openHotDetail(row)">查看详情</el-button>
                <el-button
                  size="small"
                  type="primary"
                  :loading="practicingQuestionId === row.id"
                  @click="startPractice(row)"
                >
                  开始练习
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的题库" name="local">
          <div class="upload-form ring-card">
            <input
              ref="bankInputRef"
              class="hidden-input"
              type="file"
              accept=".pdf,.doc,.docx,.md,.markdown,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/markdown,text/plain"
              @change="onBankFileChange"
            />
            <div class="upload-row">
              <div class="selected-file" :class="{ empty: !selectedFileName }">
                {{ selectedFileName || "未选择文件（仅支持 .pdf/.doc/.docx/.md/.markdown）" }}
              </div>
              <el-button type="primary" :loading="bankUploading" @click="pickBankFile">上传题库</el-button>
            </div>
            <p class="upload-tip">上传后会保存到当前账号，可随时切换查看与阅读详情。</p>
          </div>

          <div class="bank-list ring-card">
            <div class="bank-list-head">
              <h4>已上传题库</h4>
              <el-button
                type="danger"
                plain
                :loading="batchDeleting"
                :disabled="!selectedBankIds.length"
                @click="batchDeleteBanks"
              >
                批量删除
              </el-button>
            </div>
            <el-table :data="bankList" stripe class="bank-table" @selection-change="onBankSelectionChange">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="fileName" label="文件名" min-width="260" show-overflow-tooltip />
              <el-table-column label="类型" width="120">
                <template #default="{ row }">{{ fileTypeLabel(row.fileType) }}</template>
              </el-table-column>
              <el-table-column label="上传时间" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="200">
                <template #default="{ row }">
                  <el-button size="small" @click="openBankDetail(row)">查看</el-button>
                  <el-button size="small" type="danger" :loading="deletingBankId === row.id" @click="deleteBank(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section-panel>

    <el-dialog v-model="hotDetailVisible" title="问题详情" width="760px" destroy-on-close>
      <template v-if="activeQuestion">
        <div class="detail-meta">
          <p><strong>岗位：</strong>{{ activeQuestion.position }}</p>
          <p><strong>标签：</strong>{{ activeQuestion.tag }}</p>
          <p>
            <strong>热度：</strong>
            浏览 {{ activeQuestion.views }} / 收藏 {{ activeQuestion.favorites }} / 练习 {{ activeQuestion.practices }}
          </p>
        </div>
        <div class="detail-content">
          <div class="detail-header">
            <h4>问题内容</h4>
            <el-button text class="favorite-toggle" :loading="favoriteUpdating" @click="toggleDetailFavorite">
              <el-icon size="18" :color="activeQuestion.favorited ? '#d97706' : 'var(--stone-gray)'">
                <StarFilled v-if="activeQuestion.favorited" />
                <Star v-else />
              </el-icon>
              {{ activeQuestion.favorited ? "取消收藏" : "收藏问题" }}
            </el-button>
          </div>
          <p>{{ activeQuestion.content }}</p>
        </div>
        <div class="detail-content answer-content">
          <h4>标准答案</h4>
          <pre>{{ activeQuestion.answer || "暂无标准答案" }}</pre>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="practiceVisible" title="开始练习" width="820px" destroy-on-close @closed="resetPracticeDialog">
      <template v-if="practiceQuestion">
        <div class="detail-meta">
          <p><strong>岗位：</strong>{{ practiceQuestion.position }}</p>
          <p><strong>标签：</strong>{{ practiceQuestion.tag }}</p>
        </div>
        <div class="detail-content">
          <h4>问题内容</h4>
          <p>{{ practiceQuestion.content }}</p>
        </div>
        <div class="practice-answer-box">
          <h4>你的回答</h4>
          <el-input
            v-model="practiceAnswerDraft"
            type="textarea"
            :rows="6"
            maxlength="5000"
            show-word-limit
            placeholder="请输入你的回答"
          />
        </div>
        <div class="practice-actions">
          <el-button @click="submitPracticeAnswer">提交回答</el-button>
          <el-button type="primary" :loading="practiceScoring" @click="analyzePracticeAnswer">AI解析回答</el-button>
        </div>

        <div v-if="practiceResult" class="practice-result ring-card">
          <h4>评分结果</h4>
          <p class="practice-score">总分：{{ practiceResult.scoreDetail?.total ?? "-" }}</p>
          <ul class="practice-dimensions">
            <li>正确性：{{ practiceResult.scoreDetail?.correctness ?? "-" }}</li>
            <li>完整性：{{ practiceResult.scoreDetail?.completeness ?? "-" }}</li>
            <li>条理性：{{ practiceResult.scoreDetail?.logic ?? "-" }}</li>
            <li>表达：{{ practiceResult.scoreDetail?.expression ?? "-" }}</li>
            <li>技术深度：{{ practiceResult.scoreDetail?.depth ?? "-" }}</li>
          </ul>
          <p v-if="practiceResult.scoreDetail?.suggestion"><strong>改进建议：</strong>{{ practiceResult.scoreDetail.suggestion }}</p>
          <div class="detail-content answer-content">
            <h4>正确答案</h4>
            <pre>{{ practiceResult.correctAnswer || "暂无标准答案" }}</pre>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="bankDetailVisible" :title="activeBank ? `${activeBank.fileName} 详情` : '题库详情'" width="860px">
      <template v-if="activeBank">
        <div class="detail-meta">
          <p><strong>文件类型：</strong>{{ fileTypeLabel(activeBank.fileType) }}</p>
          <p><strong>上传时间：</strong>{{ formatDateTime(activeBank.uploadedAt) }}</p>
        </div>
        <p v-if="isLegacyTruncated(activeBank.content)" class="truncated-tip">
          当前题库为旧版本上传，内容可能已被截断。请重新上传该文件以查看完整内容。
        </p>
        <pre class="bank-detail-content">{{ activeBank.content }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { Star, StarFilled } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import {
  batchDeleteQuestionBanksApi,
  deleteQuestionBankApi,
  favoriteHotQuestionApi,
  getHotQuestionDetailApi,
  getQuestionBankDetailApi,
  hotQuestionActionApi,
  listHotQuestionsApi,
  listQuestionBanksApi,
  scoreHotQuestionPracticeApi,
  uploadQuestionBankApi
} from "@/api/hot";

const activeTab = ref("hot");
const tagOptions = ref([]);
const HOT_SESSION_CACHE_KEY = "hot-question-session-cache-v1";
const HOT_SESSION_REFRESHED_KEY = "hot-question-session-refreshed-v1";

const filters = reactive({
  position: "",
  tag: ""
});

const hotList = ref([]);
const hotDetailVisible = ref(false);
const activeQuestion = ref(null);
const favoriteUpdating = ref(false);
const practicingQuestionId = ref(null);

const practiceVisible = ref(false);
const practiceQuestion = ref(null);
const practiceAnswerDraft = ref("");
const submittedPracticeAnswer = ref("");
const practiceScoring = ref(false);
const practiceResult = ref(null);

const bankUploading = ref(false);
const selectedFileName = ref("");
const bankInputRef = ref(null);
const bankList = ref([]);
const activeBank = ref(null);
const bankDetailVisible = ref(false);
const selectedBankIds = ref([]);
const deletingBankId = ref(null);
const batchDeleting = ref(false);

onMounted(async () => {
  await fetchBankList();

  const restored = restoreHotSessionCache();
  if (!isHotSessionRefreshed()) {
    await fetchHotList({ markSessionRefreshed: true });
    return;
  }
  if (!restored) {
    await fetchHotList();
  }
});

async function fetchHotList(options = {}) {
  const { markSessionRefreshed = false } = options;
  try {
    const params = {
      position: filters.position?.trim() || undefined,
      tag: filters.tag || undefined
    };
    hotList.value = await listHotQuestionsApi(params);
    mergeTagOptions();
    persistHotSessionCache();
    if (markSessionRefreshed) {
      markHotSessionRefreshed();
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function mergeTagOptions() {
  const tags = new Set(tagOptions.value);
  for (const item of hotList.value || []) {
    if (item?.tag) {
      tags.add(item.tag);
    }
  }
  tagOptions.value = [...tags];
}

function syncHotQuestion(updated) {
  if (!updated?.id) {
    return;
  }
  const index = hotList.value.findIndex((item) => item.id === updated.id);
  if (index >= 0) {
    hotList.value[index] = {
      ...hotList.value[index],
      ...updated
    };
  }
  persistHotSessionCache();
}

function restoreHotSessionCache() {
  if (typeof window === "undefined") {
    return false;
  }
  try {
    const cacheRaw = window.sessionStorage.getItem(HOT_SESSION_CACHE_KEY);
    if (!cacheRaw) {
      return false;
    }
    const cache = JSON.parse(cacheRaw);
    if (!cache || typeof cache !== "object") {
      return false;
    }
    hotList.value = Array.isArray(cache.hotList) ? cache.hotList : [];
    tagOptions.value = Array.isArray(cache.tagOptions) ? cache.tagOptions : [];
    filters.position = cache.filters?.position || "";
    filters.tag = cache.filters?.tag || "";
    return true;
  } catch {
    return false;
  }
}

function persistHotSessionCache() {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(
      HOT_SESSION_CACHE_KEY,
      JSON.stringify({
        hotList: hotList.value || [],
        tagOptions: tagOptions.value || [],
        filters: {
          position: filters.position || "",
          tag: filters.tag || ""
        }
      })
    );
  } catch {
    // ignore cache write failures
  }
}

function isHotSessionRefreshed() {
  if (typeof window === "undefined") {
    return false;
  }
  try {
    return window.sessionStorage.getItem(HOT_SESSION_REFRESHED_KEY) === "1";
  } catch {
    return false;
  }
}

function markHotSessionRefreshed() {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(HOT_SESSION_REFRESHED_KEY, "1");
  } catch {
    // ignore cache write failures
  }
}

async function openHotDetail(row) {
  try {
    const detail = await getHotQuestionDetailApi(row.id, { recordView: true });
    activeQuestion.value = detail;
    syncHotQuestion(detail);
    hotDetailVisible.value = true;
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function toggleDetailFavorite() {
  if (!activeQuestion.value?.id) {
    return;
  }
  favoriteUpdating.value = true;
  const nextFavorite = !activeQuestion.value.favorited;
  try {
    const updated = await favoriteHotQuestionApi(activeQuestion.value.id, nextFavorite);
    activeQuestion.value = updated;
    syncHotQuestion(updated);
    ElMessage.success(nextFavorite ? "已收藏该问题" : "已取消收藏");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    favoriteUpdating.value = false;
  }
}

async function startPractice(row) {
  practicingQuestionId.value = row.id;
  try {
    const updated = await hotQuestionActionApi(row.id, "PRACTICE");
    syncHotQuestion(updated);
    practiceQuestion.value = await getHotQuestionDetailApi(row.id);
    resetPracticeResult();
    practiceVisible.value = true;
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    practicingQuestionId.value = null;
  }
}

function submitPracticeAnswer() {
  const answer = practiceAnswerDraft.value.trim();
  if (!answer) {
    ElMessage.warning("请先输入你的回答");
    return;
  }
  submittedPracticeAnswer.value = answer;
  ElMessage.success("回答已提交，可点击 AI解析回答");
}

async function analyzePracticeAnswer() {
  if (!practiceQuestion.value?.id) {
    return;
  }
  if (!submittedPracticeAnswer.value) {
    ElMessage.warning("请先点击“提交回答”");
    return;
  }
  practiceScoring.value = true;
  try {
    const result = await scoreHotQuestionPracticeApi(practiceQuestion.value.id, submittedPracticeAnswer.value);
    practiceResult.value = result;
    if (result?.question) {
      practiceQuestion.value = result.question;
      syncHotQuestion(result.question);
    }
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    practiceScoring.value = false;
  }
}

function resetPracticeResult() {
  practiceAnswerDraft.value = "";
  submittedPracticeAnswer.value = "";
  practiceResult.value = null;
}

function resetPracticeDialog() {
  practiceQuestion.value = null;
  resetPracticeResult();
}

function pickBankFile() {
  if (bankUploading.value) {
    return;
  }
  bankInputRef.value?.click();
}

async function onBankFileChange(event) {
  const [file] = event.target.files || [];
  if (!file) {
    clearSelectedFile();
    return;
  }
  if (!/\.(pdf|doc|docx|md|markdown)$/i.test(file.name)) {
    ElMessage.warning("仅支持 pdf/doc/docx/md/markdown 文件");
    clearSelectedFile();
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning("文件大小不能超过 10MB");
    clearSelectedFile();
    return;
  }

  selectedFileName.value = file.name;
  await uploadBankFile(file);
}

function clearSelectedFile() {
  selectedFileName.value = "";
  if (bankInputRef.value) {
    bankInputRef.value.value = "";
  }
}

async function uploadBankFile(file) {
  bankUploading.value = true;
  try {
    await uploadQuestionBankApi(file);
    ElMessage.success("题库上传成功");
    clearSelectedFile();
    await Promise.all([fetchBankList(), fetchHotList()]);
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    bankUploading.value = false;
  }
}

async function fetchBankList() {
  try {
    bankList.value = await listQuestionBanksApi();
    selectedBankIds.value = selectedBankIds.value.filter((id) => bankList.value.some((item) => item.id === id));
    if (!bankList.value.length) {
      activeBank.value = null;
      return;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function loadBankDetail(id, openDialog = false) {
  try {
    activeBank.value = await getQuestionBankDetailApi(id);
    if (openDialog) {
      bankDetailVisible.value = true;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openBankDetail(row) {
  loadBankDetail(row.id, true);
}

function onBankSelectionChange(rows) {
  selectedBankIds.value = (rows || []).map((item) => item.id);
}

async function deleteBank(row) {
  try {
    await ElMessageBox.confirm(`确认删除题库“${row.fileName}”吗？`, "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }

  deletingBankId.value = row.id;
  try {
    await deleteQuestionBankApi(row.id);
    ElMessage.success("删除成功");
    if (activeBank.value?.id === row.id) {
      activeBank.value = null;
      bankDetailVisible.value = false;
    }
    selectedBankIds.value = selectedBankIds.value.filter((id) => id !== row.id);
    await Promise.all([fetchBankList(), fetchHotList()]);
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    deletingBankId.value = null;
  }
}

async function batchDeleteBanks() {
  if (!selectedBankIds.value.length) {
    ElMessage.warning("请先选择要删除的题库");
    return;
  }

  const ids = [...selectedBankIds.value];
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个题库吗？`, "批量删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }

  batchDeleting.value = true;
  try {
    const deletedCount = await batchDeleteQuestionBanksApi(ids);
    ElMessage.success(`已删除 ${deletedCount} 个题库`);
    if (activeBank.value && ids.includes(activeBank.value.id)) {
      activeBank.value = null;
      bankDetailVisible.value = false;
    }
    selectedBankIds.value = [];
    await Promise.all([fetchBankList(), fetchHotList()]);
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    batchDeleting.value = false;
  }
}

function fileTypeLabel(type) {
  if (!type) {
    return "-";
  }
  return type.toUpperCase();
}

function isLegacyTruncated(content) {
  return typeof content === "string" && content.length === 50000;
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (num) => String(num).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`;
}
</script>

<style scoped>
.hot-page {
  display: grid;
  gap: 16px;
}

.hot-page > * {
  min-width: 0;
}

.bank-tabs {
  width: 100%;
  min-width: 0;
}

.hot-filter-form {
  margin-bottom: 8px;
}

.upload-form {
  padding: 16px;
}

.upload-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.selected-file {
  flex: 1;
  min-width: 280px;
  border: 1px solid var(--border-warm);
  border-radius: var(--radius-medium);
  background: var(--ivory);
  color: var(--charcoal-warm);
  padding: 10px 12px;
}

.selected-file.empty {
  color: var(--stone-gray);
}

.upload-tip {
  margin-top: 10px;
  color: var(--stone-gray);
  font-size: 13px;
}

.bank-list {
  padding: 14px;
  min-width: 0;
  overflow: hidden;
}

.bank-list h4 {
  margin-bottom: 0;
}

.bank-list-head {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.bank-detail-content {
  margin-top: 12px;
  padding: 12px;
  border-radius: var(--radius-medium);
  border: 1px solid var(--border-cream);
  background: var(--warm-sand);
  color: var(--dark-warm);
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.65;
  max-height: 70vh;
  overflow: auto;
}

.truncated-tip {
  color: #b45309;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: var(--radius-small);
  padding: 8px 10px;
}

:deep(.bank-tabs .el-tabs__content) {
  min-width: 0;
}

:deep(.hot-table .el-table__header table),
:deep(.hot-table .el-table__body table) {
  width: 100% !important;
}

:deep(.hot-table .el-table__body-wrapper) {
  overflow-x: auto;
}

:deep(.bank-table .el-table__header table),
:deep(.bank-table .el-table__body table) {
  width: 100% !important;
}

:deep(.bank-table .el-table__body-wrapper) {
  max-height: 360px;
  overflow: auto;
}

.detail-meta {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-meta p {
  color: var(--olive-gray);
}

.detail-content h4 {
  margin-bottom: 8px;
}

.detail-content p {
  color: var(--dark-warm);
  line-height: 1.7;
  white-space: pre-wrap;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.favorite-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.answer-content {
  margin-top: 14px;
}

.answer-content pre {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--dark-warm);
  background: var(--warm-sand);
  border: 1px solid var(--border-cream);
  border-radius: var(--radius-small);
  padding: 10px 12px;
  max-height: 320px;
  overflow: auto;
}

.practice-answer-box {
  margin-top: 14px;
}

.practice-answer-box h4 {
  margin-bottom: 8px;
}

.practice-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

.practice-result {
  margin-top: 14px;
  padding: 12px;
}

.practice-score {
  margin-top: 8px;
  color: var(--terracotta-brand);
  font-weight: 700;
}

.practice-dimensions {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--dark-warm);
  display: grid;
  gap: 4px;
}

.hot-tag-select {
  width: 180px;
}

.hidden-input {
  display: none;
}
</style>
