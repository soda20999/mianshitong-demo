<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="简历管理" subtitle="全量查看简历上传与解析结果">
      <el-table :data="pagedResumes" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="fileName" label="文件名" min-width="180" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column label="技能标签" min-width="260">
          <template #default="{ row }">
            <el-tag
              v-for="item in visibleSkills(row)"
              :key="`${row.id}-${item}`"
              size="small"
              class="tag"
            >
              {{ item }}
            </el-tag>
            <el-tag
              v-if="hiddenSkillCount(row) > 0"
              size="small"
              type="info"
              class="tag"
            >
              +{{ hiddenSkillCount(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadedAt" label="上传时间" min-width="180" />
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
          :total="resumes.length"
        />
      </div>
    </section-panel>

    <el-drawer v-model="detailVisible" title="简历详情" size="60%">
      <template v-if="currentResume">
        <article class="detail-card ring-card">
          <h4>基础信息</h4>
          <p>简历ID：{{ currentResume.id }}</p>
          <p>用户ID：{{ currentResume.userId }}</p>
          <p>文件名：{{ currentResume.fileName }}</p>
          <p>版本：{{ currentResume.version }}</p>
          <p>上传时间：{{ currentResume.uploadedAt }}</p>
        </article>

        <article class="detail-card ring-card">
          <h4>简历完整内容</h4>
          <pre>{{ currentResume.content || "暂无内容" }}</pre>
        </article>

        <article class="detail-card ring-card">
          <h4>解析结果</h4>
          <template v-if="currentResume.parseResult">
            <h5>技能关键词</h5>
            <div class="tag-list">
              <el-tag
                v-for="(item, index) in currentResume.parseResult?.skills || []"
                :key="`skill-${index}`"
                class="tag"
              >
                {{ item }}
              </el-tag>
            </div>

            <h5>项目经历</h5>
            <ul class="detail-list">
              <li v-for="(item, index) in listOrEmpty(currentResume.parseResult?.projects)" :key="`project-${index}`">
                {{ item }}
              </li>
            </ul>

            <h5>可深挖点</h5>
            <ul class="detail-list">
              <li
                v-for="(item, index) in listOrEmpty(currentResume.parseResult?.deepDivePoints)"
                :key="`deep-${index}`"
              >
                {{ item }}
              </li>
            </ul>

            <h5>风险项</h5>
            <ul class="detail-list">
              <li v-for="(item, index) in listOrEmpty(currentResume.parseResult?.risks)" :key="`risk-${index}`">
                {{ item }}
              </li>
            </ul>
          </template>
          <p v-else class="empty-text">暂无解析结果</p>
        </article>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listAdminResumesApi } from "@/api/admin";

const resumes = ref([]);
const page = ref(1);
const pageSize = ref(10);
const detailVisible = ref(false);
const currentResume = ref(null);

const pagedResumes = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return resumes.value.slice(start, start + pageSize.value);
});

onMounted(async () => {
  try {
    resumes.value = await listAdminResumesApi();
    const maxPage = Math.max(1, Math.ceil(resumes.value.length / pageSize.value));
    if (page.value > maxPage) {
      page.value = maxPage;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
});

function visibleSkills(row) {
  return (row.parseResult?.skills || []).slice(0, 4);
}

function hiddenSkillCount(row) {
  return Math.max(0, (row.parseResult?.skills || []).length - 4);
}

function openDetail(row) {
  currentResume.value = row;
  detailVisible.value = true;
}

function listOrEmpty(value) {
  return Array.isArray(value) && value.length ? value : ["暂无数据"];
}
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.tag {
  margin-right: 6px;
  margin-bottom: 6px;
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

.detail-card h5 {
  margin: 12px 0 8px;
}

.detail-card p {
  margin: 0 0 6px;
}

.detail-card pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 420px;
  overflow: auto;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
}

.empty-text {
  color: var(--stone-gray);
}
</style>
