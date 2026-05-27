import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/store/auth";
import AppLayout from "@/components/layout/AppLayout.vue";
import AuthView from "@/views/auth/AuthView.vue";
import DashboardView from "@/views/dashboard/DashboardView.vue";
import ResumeView from "@/views/resume/ResumeView.vue";
import JDAnalysisView from "@/views/jd/JDAnalysisView.vue";
import QuestionGeneratorView from "@/views/questions/QuestionGeneratorView.vue";
import InterviewView from "@/views/interview/InterviewView.vue";
import ReportView from "@/views/report/ReportView.vue";
import HistoryView from "@/views/history/HistoryView.vue";
import HotQuestionView from "@/views/hot/HotQuestionView.vue";
import ProfileView from "@/views/profile/ProfileView.vue";
import AdminUsersView from "@/views/admin/AdminUsersView.vue";
import AdminResumesView from "@/views/admin/AdminResumesView.vue";
import AdminInterviewsView from "@/views/admin/AdminInterviewsView.vue";
import AdminPromptsView from "@/views/admin/AdminPromptsView.vue";
import AdminReviewView from "@/views/admin/AdminReviewView.vue";
import AdminSystemView from "@/views/admin/AdminLogsView.vue";
import AdminRiskView from "@/views/admin/AdminRiskView.vue";
import AdminSensitiveView from "@/views/admin/AdminSensitiveView.vue";

const routes = [
  {
    path: "/auth",
    component: AuthView,
    meta: { guestOnly: true }
  },
  {
    path: "/",
    component: AppLayout,
    children: [
      { path: "", redirect: "/dashboard" },
      { path: "dashboard", component: DashboardView, meta: { title: "总览" } },
      { path: "resume", component: ResumeView, meta: { title: "简历管理" } },
      { path: "jd", component: JDAnalysisView, meta: { title: "JD 分析" } },
      { path: "questions", component: QuestionGeneratorView, meta: { title: "题目生成" } },
      { path: "interview", component: InterviewView, meta: { title: "模拟面试" } },
      { path: "reports", component: ReportView, meta: { title: "面试报告" } },
      { path: "history", component: HistoryView, meta: { title: "历史记录" } },
      { path: "hot", component: HotQuestionView, meta: { title: "题库" } },
      { path: "profile", component: ProfileView, meta: { title: "个人中心" } },
      { path: "admin/users", component: AdminUsersView, meta: { title: "用户管理", requiresAdmin: true } },
      { path: "admin/resumes", component: AdminResumesView, meta: { title: "简历管理", requiresAdmin: true } },
      { path: "admin/interviews", component: AdminInterviewsView, meta: { title: "面试记录", requiresAdmin: true } },
      { path: "admin/prompts", component: AdminPromptsView, meta: { title: "Prompt 模板", requiresAdmin: true } },
      { path: "admin/risk", component: AdminRiskView, meta: { title: "限流与风控配置", requiresAdmin: true } },
      { path: "admin/sensitive", component: AdminSensitiveView, meta: { title: "敏感词", requiresAdmin: true } },
      { path: "admin/reviews", component: AdminReviewView, meta: { title: "审核内容", requiresAdmin: true } },
      { path: "admin/system", component: AdminSystemView, meta: { title: "系统调用情况", requiresAdmin: true } },
      { path: "admin/profile", component: ProfileView, meta: { title: "个人中心", requiresAdmin: true } },
      { path: "admin/logs", redirect: "/admin/system", meta: { requiresAdmin: true } }
    ]
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/dashboard"
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (authStore.token && !authStore.user) {
    try {
      await authStore.fetchProfile();
    } catch {
      await authStore.logout();
    }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return authStore.isAdmin ? "/admin/users" : "/dashboard";
  }

  if (!to.meta.guestOnly && !authStore.isAuthenticated) {
    return "/auth";
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return "/dashboard";
  }

  if (
    authStore.isAdmin &&
    !to.meta.guestOnly &&
    !to.meta.requiresAdmin &&
    !to.path.startsWith("/admin/")
  ) {
    return "/admin/users";
  }

  return true;
});

export default router;
