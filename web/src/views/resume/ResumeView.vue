<template>
  <div class="page-shell resume-page">
    <section-panel kicker="Resume" title="简历上传与解析" subtitle="支持本地 PDF / Word 简历上传，自动提取文本并解析">
      <div class="upload-form ring-card">
        <input
          ref="resumeInputRef"
          class="hidden-input"
          type="file"
          accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          @change="onFileChange"
        />
        <div class="upload-row">
          <div class="selected-file" :class="{ empty: !selectedFileName }">
            {{ selectedFileName || "未选择文件（仅支持 .pdf/.doc/.docx）" }}
          </div>
        </div>
        <div class="upload-actions">
          <el-button type="primary" :loading="uploading" @click="pickResumeFile">
            上传并解析
          </el-button>
        </div>
      </div>
    </section-panel>

    <section-panel kicker="Result" title="简历版本管理" subtitle="自动按上传顺序生成版本，支持重新解析与查看详情">
      <div class="version-summary">
        <div class="summary-item ring-card">
          <p>版本数量</p>
          <h4>{{ resumeList.length }}</h4>
        </div>
        <div class="summary-item ring-card">
          <p>最近上传</p>
          <h4>{{ latestUploadAt }}</h4>
        </div>
      </div>

      <el-table :data="resumeList" stripe class="version-table">
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column label="上传时间" min-width="200">
          <template #default="{ row }">
            {{ formatDateTime(row.uploadedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="技能标签" min-width="220">
          <template #default="{ row }">
            <div class="skill-preview">
              <el-tag
                v-for="item in previewSkills(row)"
                :key="item"
                size="small"
                class="tag skill-tag"
                :title="item"
              >
                {{ item }}
              </el-tag>
              <el-popover
                v-if="hiddenSkillCount(row) > 0"
                placement="top"
                trigger="hover"
                width="420"
                popper-class="skill-popover"
              >
                <template #reference>
                  <el-tag size="small" class="tag more-tag">+{{ hiddenSkillCount(row) }}</el-tag>
                </template>
                <div class="popover-skill-list">
                  <el-tag
                    v-for="item in row.parseResult?.skills || []"
                    :key="`all-${row.id}-${item}`"
                    size="small"
                    class="tag popover-tag"
                    :title="item"
                  >
                    {{ item }}
                  </el-tag>
                </div>
              </el-popover>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看详情</el-button>
            <el-button size="small" type="primary" :loading="reparsingId === row.id" @click="reparse(row.id)">
              重新解析
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section-panel>

    <el-dialog v-model="detailVisible" width="760px" title="简历解析详情">
      <template v-if="activeResume">
        <h4>技能关键词</h4>
        <div class="tag-list">
          <el-tag v-for="item in activeResume.parseResult?.skills || []" :key="item">{{ item }}</el-tag>
        </div>

        <h4>项目经历</h4>
        <ul>
          <li v-for="item in activeResume.parseResult?.projects || []" :key="item">{{ item }}</li>
        </ul>

        <h4>可深挖点</h4>
        <ul>
          <li v-for="item in activeResume.parseResult?.deepDivePoints || []" :key="item">{{ item }}</li>
        </ul>

        <h4>风险项</h4>
        <ul>
          <li v-for="item in activeResume.parseResult?.risks || []" :key="item">{{ item }}</li>
        </ul>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listResumesApi, parseResumeApi, uploadResumeApi } from "@/api/resume";

const uploading = ref(false);
const reparsingId = ref(null);
const resumeList = ref([]);
const detailVisible = ref(false);
const activeResume = ref(null);
const selectedFileName = ref("");
const resumeInputRef = ref(null);
const TABLE_SKILL_LIMIT = 4;

const latestUploadAt = computed(() => {
  if (!resumeList.value.length) {
    return "暂无";
  }
  return formatDateTime(resumeList.value[0].uploadedAt);
});

onMounted(fetchResumes);

async function fetchResumes() {
  try {
    resumeList.value = await listResumesApi();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function pickResumeFile() {
  if (uploading.value) {
    return;
  }
  resumeInputRef.value?.click();
}

async function onFileChange(event) {
  const [file] = event.target.files || [];
  if (!file) {
    clearSelectedFile();
    return;
  }
  if (!/\.(pdf|doc|docx)$/i.test(file.name)) {
    ElMessage.warning("仅支持 pdf/doc/docx 文件");
    clearSelectedFile();
    return;
  }
  selectedFileName.value = file.name;
  await uploadResume(file);
}

function clearSelectedFile() {
  selectedFileName.value = "";
  if (resumeInputRef.value) {
    resumeInputRef.value.value = "";
  }
}

async function uploadResume(file) {
  uploading.value = true;
  try {
    await uploadResumeApi(file);
    ElMessage.success("上传成功，已完成解析");
    clearSelectedFile();
    await fetchResumes();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    uploading.value = false;
  }
}

async function reparse(id) {
  reparsingId.value = id;
  try {
    await parseResumeApi(id);
    ElMessage.success("解析完成");
    await fetchResumes();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    reparsingId.value = null;
  }
}

function openDetail(row) {
  activeResume.value = row;
  detailVisible.value = true;
}

function previewSkills(row) {
  const skills = row?.parseResult?.skills || [];
  return skills.slice(0, TABLE_SKILL_LIMIT);
}

function hiddenSkillCount(row) {
  const total = row?.parseResult?.skills?.length || 0;
  return Math.max(0, total - TABLE_SKILL_LIMIT);
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
.resume-page {
  display: grid;
  gap: 16px;
}

.upload-form {
  max-width: 760px;
  padding: 20px;
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

.upload-actions {
  margin-top: 12px;
}

.version-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.summary-item {
  padding: 12px 14px;
  background: var(--warm-sand);
}

.summary-item p {
  font-size: 12px;
  letter-spacing: 0.12px;
  text-transform: uppercase;
  color: var(--dark-warm);
}

.summary-item h4 {
  margin-top: 4px;
  font-size: 1.25rem;
  color: var(--anthropic-near-black);
}

.version-table {
  border-radius: var(--radius-medium);
  border: 1px solid var(--border-cream);
  overflow: hidden;
}

:deep(.version-table .el-table__inner-wrapper::before) {
  background: var(--border-cream);
}

:deep(.version-table .el-table__header-wrapper th.el-table__cell) {
  background: var(--warm-sand);
  color: var(--anthropic-near-black);
  border-bottom: 1px solid var(--border-cream);
}

:deep(.version-table .el-table__body-wrapper td.el-table__cell) {
  background: var(--ivory);
  color: var(--olive-gray);
  border-bottom: 1px solid var(--border-cream);
}

:deep(.version-table .el-table__body tr:hover > td.el-table__cell) {
  background: rgba(201, 100, 66, 0.08);
}

:deep(.version-table .el-table__empty-block) {
  background: var(--ivory);
  color: var(--stone-gray);
}

.tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

:global(.skill-popover) {
  max-width: min(88vw, 460px);
}

.skill-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.skill-tag {
  max-width: 220px;
}

.more-tag {
  background: var(--warm-sand);
  border-color: var(--border-warm);
  color: var(--charcoal-warm);
}

.popover-skill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.popover-tag {
  max-width: 360px;
}

:deep(.skill-tag .el-tag__content),
:deep(.popover-tag .el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.version-table .el-tag) {
  background: rgba(201, 100, 66, 0.12);
  border-color: rgba(201, 100, 66, 0.28);
  color: var(--dark-warm);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hidden-input {
  display: none;
}

@media (max-width: 640px) {
  .selected-file {
    min-width: 100%;
  }
}
</style>
