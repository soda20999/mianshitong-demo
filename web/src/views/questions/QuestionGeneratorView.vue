<template>
  <div class="page-shell question-page">
    <section-panel kicker="Generator" title="AI 题目生成" subtitle="根据简历 + 岗位 + 面试方向生成题目">
      <el-form :model="form" label-position="top" class="question-form">
        <el-row :gutter="12">
          <el-col :md="12" :sm="24">
            <el-form-item label="选择简历">
              <el-select v-model="form.resumeId" placeholder="请选择已上传简历">
                <el-option v-for="item in resumes" :key="item.id" :label="item.fileName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :sm="24">
            <el-form-item label="目标岗位">
              <el-input v-model="form.jobTitle" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :md="8" :sm="24">
            <el-form-item label="面试方向">
              <el-select v-model="form.direction">
                <el-option label="后端" value="后端" />
                <el-option label="前端" value="前端" />
                <el-option label="算法" value="算法" />
                <el-option label="测试" value="测试" />
                <el-option label="产品" value="产品" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="8" :sm="24">
            <el-form-item label="难度">
              <el-select v-model="form.level">
                <el-option label="初级" value="初级" />
                <el-option label="中级" value="中级" />
                <el-option label="高级" value="高级" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="8" :sm="24">
            <el-form-item label="公司风格">
              <el-select v-model="form.companyStyle">
                <el-option label="大厂" value="大厂" />
                <el-option label="外包" value="外包" />
                <el-option label="创业公司" value="创业公司" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目类别">
          <el-checkbox-group v-model="form.categories">
            <el-checkbox label="自我介绍类" />
            <el-checkbox label="项目深挖类" />
            <el-checkbox label="八股知识类" />
            <el-checkbox label="场景设计类" />
            <el-checkbox label="手撕/SQL/Redis/JVM/并发专题题" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="题目数量">
          <el-slider v-model="form.count" :min="5" :max="20" />
        </el-form-item>
        <el-button type="primary" :loading="generating" @click="generate">生成题目</el-button>
      </el-form>
    </section-panel>

    <section-panel kicker="Question Set" title="生成结果" subtitle="可直接用于模拟面试">
      <template v-if="latestSet">
        <div class="result-head">
          <div>
            <strong>{{ latestSet.jobTitle }}</strong>
            <p>{{ latestSet.direction }} / {{ latestSet.level }} / {{ latestSet.companyStyle }}</p>
          </div>
          <el-button type="primary" @click="goInterview(latestSet.id)">用此题集开始面试</el-button>
        </div>
        <ol class="question-list">
          <li v-for="item in latestSet.questions || []" :key="item.id">
            <span class="q-tag">{{ item.category }}</span>
            <span>{{ item.content }}</span>
          </li>
        </ol>
      </template>
      <p v-else>暂无题集，请先生成。</p>
    </section-panel>

    <section-panel kicker="History" title="历史题集">
      <el-table :data="questionSets" stripe class="history-set-table">
        <el-table-column prop="jobTitle" label="岗位" min-width="220" />
        <el-table-column prop="direction" label="方向" min-width="120" />
        <el-table-column prop="level" label="难度" min-width="120" />
        <el-table-column prop="createdAt" label="生成时间" min-width="220" />
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button size="small" @click="latestSet = row">查看</el-button>
            <el-button size="small" type="primary" @click="goInterview(row.id)">开始面试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section-panel>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listResumesApi } from "@/api/resume";
import { generateQuestionsApi, listQuestionSetsApi } from "@/api/question";

const router = useRouter();
const resumes = ref([]);
const questionSets = ref([]);
const latestSet = ref(null);
const generating = ref(false);

const form = reactive({
  resumeId: null,
  jobTitle: "Java后端工程师",
  direction: "后端",
  level: "中级",
  companyStyle: "大厂",
  categories: ["项目深挖类", "八股知识类", "场景设计类"],
  count: 10
});

onMounted(async () => {
  await Promise.all([fetchResumes(), fetchQuestionSets()]);
});

async function fetchResumes() {
  try {
    resumes.value = await listResumesApi();
    if (!form.resumeId && resumes.value.length) {
      form.resumeId = resumes.value[0].id;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function fetchQuestionSets() {
  try {
    questionSets.value = await listQuestionSetsApi();
    latestSet.value = questionSets.value[0] || null;
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function generate() {
  if (!form.resumeId || !form.jobTitle || !form.categories.length) {
    ElMessage.warning("请完整填写生成参数");
    return;
  }
  generating.value = true;
  try {
    const data = await generateQuestionsApi(form);
    latestSet.value = data;
    ElMessage.success("题目生成成功");
    await fetchQuestionSets();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    generating.value = false;
  }
}

function goInterview(questionSetId) {
  router.push(`/interview?questionSetId=${questionSetId}`);
}
</script>

<style scoped>
.question-page {
  display: grid;
  gap: 16px;
}

.question-form {
  max-width: 900px;
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.result-head p {
  color: var(--olive-gray);
}

.question-list {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 10px;
}

.q-tag {
  display: inline-block;
  margin-right: 8px;
  border: 1px solid var(--border-warm);
  background: var(--warm-sand);
  color: var(--charcoal-warm);
  border-radius: var(--radius-xlarge);
  padding: 2px 8px;
  font-size: 12px;
}

.history-set-table {
  width: 100%;
}

:deep(.history-set-table .el-table__header table),
:deep(.history-set-table .el-table__body table) {
  width: 100% !important;
}

@media (max-width: 768px) {
  .result-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
