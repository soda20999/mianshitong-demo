<template>
  <div class="page-shell interview-page">
    <section-panel
      kicker="Simulator"
      title="模拟面试"
      subtitle="支持友好型 / 压力型 / 追问型 / 技术深挖型"
    >
      <div class="start-grid">
        <el-form :model="startForm" label-position="top">
          <el-form-item label="题集">
            <el-select v-model="startForm.questionSetId" placeholder="选择题集">
              <el-option
                v-for="item in questionSets"
                :key="item.id"
                :label="`${item.jobTitle} (${item.level})`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="面试官风格">
            <el-select v-model="startForm.style">
              <el-option label="友好型" value="FRIENDLY" />
              <el-option label="压力型" value="PRESSURE" />
              <el-option label="追问型" value="FOLLOW_UP" />
              <el-option label="技术深挖型" value="DEEP_TECH" />
            </el-select>
          </el-form-item>
          <el-form-item label="会话标题">
            <el-input v-model="startForm.title" />
          </el-form-item>
          <el-button type="primary" :loading="starting" @click="startInterview">开始面试</el-button>
          <el-button
            v-if="session"
            :disabled="session.status === 'FINISHED'"
            @click="finishInterview"
          >
            结束并生成报告
          </el-button>
        </el-form>

        <div class="session-meta ring-card">
          <p class="editorial-kicker">Session Meta</p>
          <p>当前会话：{{ session?.title || "未开始" }}</p>
          <p>状态：{{ session?.status || "IDLE" }}</p>
          <p>消息轮次：{{ session?.messages?.length || 0 }}</p>
          <p>评分轮次：{{ interviewStore.scoreHistory.length }}</p>
        </div>
      </div>
    </section-panel>

    <section-panel kicker="Conversation" title="多轮追问对话">
      <div class="chat-window">
        <div
          v-for="(msg, idx) in session?.messages || []"
          :key="idx"
          :class="['chat-item', msg.role === 'user' ? 'user' : 'ai']"
        >
          <span class="role">{{ msg.role === "user" ? "你" : "AI面试官" }}</span>
          <p>{{ msg.content }}</p>
        </div>
      </div>
      <div class="answer-box">
        <el-input
          v-model="answerText"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="请输入你的回答，系统会给出逐轮评分和追问"
        />
        <el-button type="primary" :loading="answering" @click="sendAnswer">提交回答</el-button>
      </div>
    </section-panel>

    <section-panel kicker="Score" title="逐轮评分反馈">
      <el-empty v-if="!interviewStore.scoreHistory.length" description="暂无评分" />
      <div v-else class="score-list">
        <el-card v-for="(item, index) in interviewStore.scoreHistory" :key="index" class="score-card">
          <div class="score-head">
            <strong>第 {{ index + 1 }} 轮</strong>
            <el-tag type="success">总分 {{ item.total }}</el-tag>
          </div>
          <p>正确性 {{ item.correctness }} / 完整性 {{ item.completeness }} / 条理性 {{ item.logic }}</p>
          <p>表达 {{ item.expression }} / 技术深度 {{ item.depth }}</p>
          <p>优点：{{ (item.advantages || []).join("；") }}</p>
          <p>不足：{{ (item.gaps || []).join("；") }}</p>
          <p>建议：{{ item.suggestion }}</p>
          <p>推荐表达：{{ item.recommendedAnswer }}</p>
        </el-card>
      </div>
    </section-panel>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { listQuestionSetsApi } from "@/api/question";
import { answerInterviewApi, finishInterviewApi, startInterviewApi } from "@/api/interview";
import { useInterviewStore } from "@/store/interview";

const route = useRoute();
const router = useRouter();
const interviewStore = useInterviewStore();

const questionSets = ref([]);
const starting = ref(false);
const answering = ref(false);
const answerText = ref("");

const startForm = reactive({
  questionSetId: null,
  style: "FOLLOW_UP",
  title: "Java 后端模拟面试"
});

const session = computed(() => interviewStore.activeSession);

onMounted(async () => {
  await fetchQuestionSets();
  const qid = Number(route.query.questionSetId || 0);
  if (qid) {
    startForm.questionSetId = qid;
  }
});

async function fetchQuestionSets() {
  try {
    questionSets.value = await listQuestionSetsApi();
    if (!startForm.questionSetId && questionSets.value.length) {
      startForm.questionSetId = questionSets.value[0].id;
    }
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function startInterview() {
  if (!startForm.questionSetId) {
    ElMessage.warning("请先选择题集");
    return;
  }
  starting.value = true;
  try {
    const data = await startInterviewApi(startForm);
    interviewStore.setActiveSession(data);
    ElMessage.success("面试开始");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    starting.value = false;
  }
}

async function sendAnswer() {
  if (!session.value?.id) {
    ElMessage.warning("请先开始面试");
    return;
  }
  if (!answerText.value.trim()) {
    ElMessage.warning("请输入回答内容");
    return;
  }
  answering.value = true;
  try {
    const answer = answerText.value.trim();
    interviewStore.appendMessage({
      role: "user",
      content: answer
    });
    const data = await answerInterviewApi(session.value.id, { answer });
    interviewStore.appendScore(data.scoreDetail);
    interviewStore.appendMessage({
      role: "ai",
      content: data.followUpQuestion
    });
    answerText.value = "";
    ElMessage.success("已评分并生成追问");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    answering.value = false;
  }
}

async function finishInterview() {
  if (!session.value?.id) {
    return;
  }
  try {
    const data = await finishInterviewApi(session.value.id);
    interviewStore.setActiveSession(data);
    ElMessage.success("已结束面试，正在跳转面试报告");
    router.push("/reports");
  } catch (error) {
    ElMessage.error(error.message);
  }
}
</script>

<style scoped>
.interview-page {
  display: grid;
  gap: 16px;
}

.start-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
}

.session-meta {
  padding: 16px;
  display: grid;
  gap: 8px;
  align-content: start;
}

.chat-window {
  display: grid;
  gap: 10px;
  max-height: 360px;
  overflow: auto;
}

.chat-item {
  border-radius: 12px;
  border: 1px solid var(--border-warm);
  padding: 10px 12px;
}

.chat-item.ai {
  background: var(--ivory);
}

.chat-item.user {
  background: rgba(201, 100, 66, 0.12);
}

.role {
  font-size: 12px;
  color: var(--stone-gray);
}

.chat-item p {
  color: var(--dark-warm);
}

.answer-box {
  display: grid;
  gap: 10px;
}

.score-list {
  display: grid;
  gap: 10px;
}

.score-card {
  background: var(--pure-white);
}

.score-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

@media (max-width: 991px) {
  .start-grid {
    grid-template-columns: 1fr;
  }
}
</style>
