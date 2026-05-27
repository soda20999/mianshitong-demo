<template>
  <div class="page-shell admin-page">
    <section-panel kicker="Admin" title="限流与风控配置" subtitle="接口限流、上传安全、内容风控、幂等控制">
      <el-form :model="form" label-position="top" class="risk-form">
        <el-row :gutter="12">
          <el-col :md="8" :sm="24">
            <el-form-item label="每分钟限流次数">
              <el-input-number v-model="form.rateLimitPerMinute" :min="1" :max="500" :disabled="!isEditing" />
            </el-form-item>
          </el-col>
          <el-col :md="8" :sm="24">
            <el-form-item label="每日最多生成题目">
              <el-input-number
                v-model="form.maxQuestionGeneratePerDay"
                :min="1"
                :max="5000"
                :disabled="!isEditing"
              />
            </el-form-item>
          </el-col>
          <el-col :md="8" :sm="24">
            <el-form-item label="每日最多生成报告">
              <el-input-number
                v-model="form.maxReportGeneratePerDay"
                :min="1"
                :max="200"
                :disabled="!isEditing"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :md="12" :sm="24">
            <el-form-item label="上传大小限制(MB)">
              <el-input-number v-model="form.uploadMaxMb" :min="1" :max="100" :disabled="!isEditing" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :sm="24">
            <el-form-item label="允许上传类型">
              <el-input v-model="form.uploadAllowTypes" :disabled="!isEditing" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :md="8" :sm="24">
            <el-form-item label="输入最大长度">
              <el-input-number v-model="form.inputMaxLength" :min="100" :max="100000" :disabled="!isEditing" />
            </el-form-item>
          </el-col>
          <el-col :md="16" :sm="24">
            <el-form-item label="安全开关">
              <div class="switches">
                <el-switch v-model="form.promptInjectionCheck" active-text="Prompt 注入防护" :disabled="!isEditing" />
                <el-switch v-model="form.outputSafetyCheck" active-text="输出安全校验" :disabled="!isEditing" />
                <el-switch v-model="form.idempotencyCheck" active-text="接口幂等校验" :disabled="!isEditing" />
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="actions">
          <el-button v-if="!isEditing" type="primary" @click="startEdit">编辑配置</el-button>
          <template v-else>
            <el-button @click="cancelEdit">取消编辑</el-button>
            <el-button type="primary" @click="save">保存风控配置</el-button>
          </template>
        </div>
      </el-form>
    </section-panel>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import SectionPanel from "@/components/common/SectionPanel.vue";
import { getRiskConfigApi, updateRiskConfigApi } from "@/api/admin";

const form = reactive({
  rateLimitPerMinute: 10,
  maxQuestionGeneratePerDay: 50,
  maxReportGeneratePerDay: 5,
  uploadMaxMb: 10,
  uploadAllowTypes: "pdf,doc,docx",
  inputMaxLength: 20000,
  promptInjectionCheck: true,
  outputSafetyCheck: true,
  idempotencyCheck: true
});
const isEditing = ref(false);
const snapshot = ref(null);

onMounted(fetchConfig);

async function fetchConfig() {
  try {
    const config = await getRiskConfigApi();
    Object.assign(form, config);
    snapshot.value = { ...config };
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function startEdit() {
  isEditing.value = true;
}

function cancelEdit() {
  if (snapshot.value) {
    Object.assign(form, snapshot.value);
  }
  isEditing.value = false;
}

async function save() {
  try {
    await updateRiskConfigApi(form);
    snapshot.value = { ...form };
    isEditing.value = false;
    ElMessage.success("风控配置已更新");
  } catch (error) {
    ElMessage.error(error.message);
  }
}
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.risk-form {
  max-width: 920px;
}

.switches {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  gap: 8px;
}
</style>
