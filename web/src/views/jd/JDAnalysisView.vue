<template>
  <div class="page-shell jd-page">
    <section-panel kicker="JD Analyzer" title="岗位 JD 分析" subtitle="提取关键词、技能栈和面试关注点">
      <el-form :model="form" label-position="top">
        <el-form-item label="目标岗位">
          <el-input v-model="form.jobTitle" placeholder="例如：Java后端工程师" />
        </el-form-item>
        <el-form-item label="JD 内容">
          <rich-text-editor v-model="form.jdContent" />
        </el-form-item>
        <el-button type="primary" :loading="analyzing" @click="analyze">开始分析</el-button>
      </el-form>
    </section-panel>

    <section-panel kicker="Output" title="AI 分析输出" subtitle="核心技能 / 面试重点 / 补强建议">
      <template v-if="latest">
        <div class="output-grid">
          <article class="output-card ring-card keyword-card">
            <header class="output-head">
              <p class="editorial-kicker">Keywords</p>
              <h4>关键词</h4>
            </header>
            <div class="keyword-wall">
              <el-tag
                v-for="(item, idx) in listOrPlaceholder(latest.keywords, '暂无关键词')"
                :key="`keyword-${idx}-${item}`"
                class="tag keyword-tag"
              >
                {{ item }}
              </el-tag>
            </div>
          </article>

          <article class="output-card ring-card">
            <header class="output-head">
              <p class="editorial-kicker">Core Skills</p>
              <h4>核心技能</h4>
            </header>
            <ul class="content-list">
              <li
                v-for="(item, idx) in listOrPlaceholder(latest.coreSkills, '暂无核心技能')"
                :key="`core-${idx}-${item}`"
              >
                {{ item }}
              </li>
            </ul>
          </article>

          <article class="output-card ring-card">
            <header class="output-head">
              <p class="editorial-kicker">Interview Focus</p>
              <h4>面试关注点</h4>
            </header>
            <ul class="content-list">
              <li
                v-for="(item, idx) in listOrPlaceholder(latest.interviewFocuses, '暂无面试关注点')"
                :key="`focus-${idx}-${item}`"
              >
                {{ item }}
              </li>
            </ul>
          </article>

          <article class="output-card ring-card">
            <header class="output-head">
              <p class="editorial-kicker">Suggestions</p>
              <h4>建议补充</h4>
            </header>
            <ul class="content-list">
              <li
                v-for="(item, idx) in listOrPlaceholder(latest.suggestions, '暂无补强建议')"
                :key="`suggest-${idx}-${item}`"
              >
                {{ item }}
              </li>
            </ul>
          </article>
        </div>
      </template>
      <p v-else>暂无分析结果</p>
    </section-panel>

    <section-panel kicker="History" title="历史分析记录">
      <el-table :data="history" stripe class="history-table" @row-click="viewHistoryDetail">
        <el-table-column prop="jobTitle" label="岗位" width="200" />
        <el-table-column prop="keywords" label="关键词" min-width="320">
          <template #default="{ row }">
            <el-tag v-for="item in row.keywords || []" :key="item" class="tag">{{ item }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="viewHistoryDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section-panel>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import RichTextEditor from "@/components/richtext/RichTextEditor.vue";
import { analyzeJdApi, listJdHistoryApi } from "@/api/jd";

const form = reactive({
  jobTitle: "Java后端工程师",
  jdContent: "<p>负责高并发后端系统开发，熟悉Java、Redis、MySQL、Spring Boot，具备线上问题排查与性能优化经验。</p>"
});

const analyzing = ref(false);
const latest = ref(null);
const history = ref([]);

onMounted(fetchHistory);

async function fetchHistory() {
  try {
    history.value = await listJdHistoryApi();
    latest.value = history.value[0] || null;
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function analyze() {
  const plain = form.jdContent.replace(/<[^>]+>/g, "").trim();
  if (!form.jobTitle || !plain) {
    ElMessage.warning("请填写岗位和 JD 内容");
    return;
  }
  analyzing.value = true;
  try {
    const data = await analyzeJdApi({
      jobTitle: form.jobTitle,
      jdContent: plain
    });
    latest.value = data;
    ElMessage.success("分析完成");
    await fetchHistory();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    analyzing.value = false;
  }
}

function viewHistoryDetail(row) {
  latest.value = row || null;
}

function listOrPlaceholder(list, placeholder) {
  if (Array.isArray(list) && list.length) {
    return list;
  }
  return [placeholder];
}
</script>

<style scoped>
.jd-page {
  display: grid;
  gap: 16px;
}

.output-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.output-card {
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  align-content: start;
}

.keyword-card {
  background: var(--ivory);
  border-color: var(--border-warm);
  box-shadow:
    inset 0 1px 0 rgba(201, 100, 66, 0.28),
    var(--shadow-ring);
}

.output-head {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-cream);
}

.output-head h4 {
  font-size: 1.18rem;
  line-height: 1.2;
}

.keyword-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.keyword-tag {
  margin: 0;
  max-width: 100%;
  background: rgba(201, 100, 66, 0.12);
  border-color: rgba(201, 100, 66, 0.28);
  color: var(--dark-warm);
}

:deep(.keyword-tag .el-tag__content) {
  max-width: 270px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.jd-page .el-tag) {
  background: rgba(201, 100, 66, 0.12);
  border-color: rgba(201, 100, 66, 0.28);
  color: var(--dark-warm);
}

.content-list {
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
  list-style: none;
}

.content-list li {
  position: relative;
  margin: 0;
  padding-left: 14px;
  color: var(--olive-gray);
  line-height: 1.6;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.content-list li::before {
  content: "";
  position: absolute;
  top: 9px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--terracotta-brand);
}

:deep(.history-table .el-table__body-wrapper .el-table__row) {
  cursor: pointer;
}

ul {
  margin: 0;
  padding-left: 16px;
}

li {
  margin-bottom: 6px;
}

@media (max-width: 768px) {
  .output-grid {
    grid-template-columns: 1fr;
  }
}
</style>
