<template>
  <div class="layout-root">
    <aside class="layout-sidebar" :class="{ 'is-collapsed': sidebarCollapsed }">
      <div class="brand" :class="{ 'is-collapsed': sidebarCollapsed }">
        <div class="brand-content">
          <div class="brand-mark">{{ sidebarCollapsed ? "面" : "面试通" }}</div>
          <p v-if="!sidebarCollapsed" class="brand-sub">AI Interview Studio</p>
        </div>
        <button
          class="sidebar-toggle-btn"
          type="button"
          :aria-label="sidebarCollapsed ? '灞曞紑渚ф爮' : '鏀惰捣渚ф爮'"
          @click="toggleSidebar"
        >
          <el-icon>
            <component :is="sidebarCollapsed ? Expand : Fold" />
          </el-icon>
        </button>
      </div>
      <div class="sidebar-menu-wrap">
        <el-menu
          :default-active="activePath"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          class="nav-menu"
          @select="onSelect"
        >
          <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path" :title="item.label">
            <el-icon class="menu-item-icon">
              <component :is="item.icon" />
            </el-icon>
            <template #title>{{ item.label }}</template>
          </el-menu-item>
        </el-menu>
      </div>
    </aside>

    <div class="layout-main">
      <header class="layout-header" :class="{ 'is-scrolled': headerScrolled }">
        <div class="header-left">
          <el-button class="mobile-toggle" @click="appStore.openNav">鑿滃崟</el-button>
          <div>
            <p class="editorial-kicker">Interview Platform</p>
            <h3>{{ route.meta.title || "面试通" }}</h3>
          </div>
        </div>
        <div class="header-right">
          <div
            class="user-meta profile-entry"
            role="button"
            tabindex="0"
            @click="goProfile"
            @keydown.enter="goProfile"
            @keydown.space.prevent="goProfile"
          >
            <el-avatar :size="40" :src="authStore.user?.avatar || undefined" class="user-avatar">
              {{ (authStore.user?.nickname || "U").slice(0, 1) }}
            </el-avatar>
            <div class="user-info">
              <strong>{{ authStore.user?.nickname }}</strong>
              <span>{{ authStore.user?.targetPosition }}</span>
            </div>
          </div>
          <el-button type="default" @click="logout">退出</el-button>
        </div>
      </header>

      <main class="layout-content">
        <router-view />
      </main>
    </div>

    <el-drawer
      v-model="appStore.mobileNavOpen"
      :with-header="false"
      direction="ltr"
      size="280px"
      class="mobile-drawer"
    >
      <div class="brand">
        <div class="brand-mark">面试通</div>
        <p class="brand-sub">AI Interview Studio</p>
      </div>
      <el-menu :default-active="activePath" class="nav-menu" @select="onSelectFromMobile">
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <el-icon class="menu-item-icon">
            <component :is="item.icon" />
          </el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  ChatDotRound,
  Clock,
  CollectionTag,
  DataAnalysis,
  Document,
  EditPen,
  Expand,
  Files,
  Fold,
  HomeFilled,
  Memo,
  User
} from "@element-plus/icons-vue";
import { useAuthStore } from "@/store/auth";
import { useAppStore } from "@/store/app";

const authStore = useAuthStore();
const appStore = useAppStore();
const route = useRoute();
const router = useRouter();
const SIDEBAR_COLLAPSE_KEY = "interview-studio-sidebar-collapsed";
const headerScrolled = ref(false);
let revealObserver = null;

const sidebarCollapsed = ref(
  typeof window !== "undefined" &&
    window.localStorage.getItem(SIDEBAR_COLLAPSE_KEY) === "1"
);

const userMenus = [
  { path: "/dashboard", label: "总览", icon: HomeFilled },
  { path: "/resume", label: "简历管理", icon: Document },
  { path: "/jd", label: "JD 分析", icon: DataAnalysis },
  { path: "/questions", label: "题目生成", icon: EditPen },
  { path: "/interview", label: "模拟面试", icon: ChatDotRound },
  { path: "/reports", label: "面试报告", icon: Memo },
  { path: "/history", label: "历史记录", icon: Clock },
  { path: "/hot", label: "题库", icon: CollectionTag },
  { path: "/profile", label: "个人中心", icon: User }
];

const adminMenus = [
  { path: "/admin/users", label: "用户管理", icon: User },
  { path: "/admin/resumes", label: "简历管理", icon: Document },
  { path: "/admin/interviews", label: "面试记录", icon: ChatDotRound },
  { path: "/admin/prompts", label: "Prompt 模板", icon: EditPen },
  { path: "/admin/risk", label: "限流与风控配置", icon: DataAnalysis },
  { path: "/admin/sensitive", label: "敏感词管理", icon: CollectionTag },
  { path: "/admin/reviews", label: "审核内容", icon: Memo },
  { path: "/admin/system", label: "系统调用情况", icon: Files },
  { path: "/admin/profile", label: "个人中心", icon: User }
];

const activePath = computed(() => route.path);
const visibleMenus = computed(() => (authStore.isAdmin ? adminMenus : userMenus));

watch(sidebarCollapsed, (value) => {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(SIDEBAR_COLLAPSE_KEY, value ? "1" : "0");
});

watch(
  () => route.fullPath,
  async () => {
    await nextTick();
    refreshRevealItems();
    syncHeaderScrolledState();
  }
);

onMounted(async () => {
  if (typeof window === "undefined") {
    return;
  }
  window.addEventListener("scroll", syncHeaderScrolledState, { passive: true });
  syncHeaderScrolledState();
  initRevealObserver();
  await nextTick();
  refreshRevealItems();
});

onBeforeUnmount(() => {
  if (typeof window !== "undefined") {
    window.removeEventListener("scroll", syncHeaderScrolledState);
  }
  revealObserver?.disconnect();
  revealObserver = null;
});

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
}

function onSelect(path) {
  router.push(path);
}

function onSelectFromMobile(path) {
  appStore.closeNav();
  router.push(path);
}

function goProfile() {
  if (authStore.isAdmin) {
    if (route.path !== "/admin/profile") {
      router.push("/admin/profile");
    }
    return;
  }
  if (route.path !== "/profile") {
    router.push("/profile");
  }
}

async function logout() {
  await authStore.logout();
  ElMessage.success("已退出登录");
  router.replace("/auth");
}

function syncHeaderScrolledState() {
  if (typeof window === "undefined") {
    return;
  }
  headerScrolled.value = window.scrollY > 6;
}

function initRevealObserver() {
  if (typeof window === "undefined" || !window.IntersectionObserver) {
    return;
  }
  revealObserver = new window.IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) {
          return;
        }
        entry.target.classList.add("is-visible");
        revealObserver?.unobserve(entry.target);
      });
    },
    {
      threshold: 0.12,
      rootMargin: "0px 0px -10% 0px"
    }
  );
}

function refreshRevealItems() {
  if (typeof document === "undefined") {
    return;
  }
  const items = [...document.querySelectorAll(".layout-content .section-block")];
  if (!items.length) {
    return;
  }

  if (!revealObserver) {
    items.forEach((item) => item.classList.add("is-visible"));
    return;
  }

  revealObserver.disconnect();
  items.forEach((item, index) => {
    item.classList.add("reveal-item");
    item.style.setProperty("--reveal-delay", `${Math.min(index * 70, 420)}ms`);
    if (!item.classList.contains("is-visible")) {
      revealObserver?.observe(item);
    }
  });
}
</script>

<style scoped>
.layout-root {
  min-height: 100vh;
  display: flex;
  background: var(--parchment);
}

.layout-sidebar {
  width: 260px;
  border-right: none;
  background: var(--ivory);
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  transition: width var(--motion-medium) var(--motion-ease);
  overflow: hidden;
  box-shadow: none;
}

.layout-sidebar.is-collapsed {
  width: 84px;
}

.brand {
  padding: 20px 16px 12px;
  border-bottom: 1px solid var(--border-cream);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  position: relative;
}

.brand.is-collapsed {
  padding: 14px 12px;
  justify-content: center;
  align-items: center;
  position: static;
}

.brand-content {
  min-width: 0;
}

.brand-mark {
  font-family: var(--font-serif);
  font-size: 32px;
  line-height: 1.1;
  white-space: nowrap;
}

.brand-sub {
  margin-top: 6px;
  color: var(--stone-gray);
  font-size: 14px;
}

.sidebar-toggle-btn {
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-cream);
  border-radius: var(--radius-xlarge);
  background: var(--ivory);
  color: var(--stone-gray);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: var(--shadow-ring);
  transition:
    transform var(--motion-fast) var(--motion-ease),
    background-color var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease),
    border-color var(--motion-fast) var(--motion-ease);
}

.sidebar-toggle-btn:hover {
  background: var(--warm-sand-hover);
  color: var(--anthropic-near-black);
  border-color: var(--ring-warm);
  transform: translateY(-1px);
}

.sidebar-toggle-btn:focus-visible {
  outline: none;
  box-shadow:
    0 0 0 1px var(--ring-warm),
    0 0 0 3px rgba(209, 207, 197, 0.34);
}

.brand.is-collapsed .brand-mark {
  font-size: 28px;
}

.layout-main {
  flex: 1;
  min-width: 0;
}

.sidebar-menu-wrap {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 6;
  padding: 14px 22px;
  border-bottom: none;
  background: rgba(245, 244, 237, 0.74);
  backdrop-filter: blur(10px);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  transition:
    background-color var(--motion-fast) var(--motion-ease);
}

.layout-header.is-scrolled {
  background: rgba(250, 249, 245, 0.96);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-entry {
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-medium);
  transition:
    transform var(--motion-fast) var(--motion-ease),
    background-color var(--motion-fast) var(--motion-ease),
    box-shadow var(--motion-fast) var(--motion-ease);
}

.profile-entry:hover {
  background: var(--warm-sand);
  transform: translateY(-1px);
  box-shadow: var(--shadow-ring);
}

.user-avatar {
  border: 1px solid var(--border-cream);
  box-shadow: var(--shadow-ring);
}

.user-info {
  text-align: right;
  display: grid;
}

.user-info strong {
  color: var(--anthropic-near-black);
  font-size: 15px;
}

.user-info span {
  color: var(--stone-gray);
  font-size: 12px;
}

.mobile-toggle {
  display: none;
}

.layout-content {
  padding: 0;
}

.layout-content :deep(.page-shell) {
  max-width: none;
  margin: 0;
  padding: 0;
}

.nav-menu {
  border-right: none;
  background: transparent;
  padding: 8px 8px 12px;
}

.layout-sidebar .nav-menu {
  flex: 1;
  min-height: 100%;
  padding: 0;
  overflow: hidden;
}

.layout-sidebar .nav-menu :deep(.el-menu-item),
.layout-sidebar .nav-menu :deep(.el-sub-menu__title) {
  margin: 4px 0;
  border-radius: var(--radius-small);
  height: 48px;
  line-height: 48px;
  overflow: hidden;
}

.nav-menu :deep(.el-menu-item),
.nav-menu :deep(.el-sub-menu__title) {
  margin: 4px 4px;
  border-radius: var(--radius-small);
  height: 42px;
  line-height: 42px;
  position: relative;
  overflow: hidden;
  transition:
    background-color var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease),
    transform var(--motion-fast) var(--motion-ease);
}

.nav-menu :deep(.el-menu-item:hover),
.nav-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(201, 100, 66, 0.1);
  color: var(--anthropic-near-black);
}

.layout-sidebar .nav-menu :deep(.el-menu-item.is-active) {
  background: rgba(201, 100, 66, 0.12);
  color: var(--terracotta-brand);
  font-weight: 500;
}

.nav-menu :deep(.el-menu-item.is-active) {
  background: rgba(201, 100, 66, 0.12);
  color: var(--terracotta-brand);
  font-weight: 500;
}

.layout-sidebar .nav-menu :deep(.el-sub-menu .el-menu-item) {
  margin: 0;
  padding-left: 52px !important;
}

.nav-menu :deep(.el-sub-menu .el-menu-item) {
  margin-left: 14px;
}

.menu-item-icon {
  font-size: 17px;
  transition:
    transform var(--motion-fast) var(--motion-ease),
    color var(--motion-fast) var(--motion-ease);
}

.nav-menu :deep(.el-menu-item:hover) .menu-item-icon,
.nav-menu :deep(.el-sub-menu__title:hover) .menu-item-icon {
  transform: translateY(-1px) rotate(-4deg);
  color: var(--terracotta-brand);
}

.layout-sidebar.is-collapsed .nav-menu {
  padding: 10px 0 72px;
}

.layout-sidebar.is-collapsed .nav-menu :deep(.el-menu-item),
.layout-sidebar.is-collapsed .nav-menu :deep(.el-sub-menu__title) {
  width: 44px;
  margin: 6px auto;
  justify-content: center;
}

.layout-sidebar.is-collapsed .nav-menu :deep(.el-sub-menu .el-menu-item) {
  margin: 6px auto;
}

.layout-sidebar.is-collapsed .sidebar-toggle-btn {
  position: absolute;
  left: 50%;
  right: auto;
  bottom: 14px;
  top: auto;
  transform: translateX(-50%);
  z-index: 3;
}

.layout-sidebar.is-collapsed .sidebar-toggle-btn:hover {
  transform: translateX(-50%) translateY(-1px);
}

@media (max-width: 991px) {
  .layout-sidebar {
    display: none;
  }

  .mobile-toggle {
    display: inline-flex;
  }

  .layout-header {
    padding: 12px 14px;
  }

  .layout-content {
    padding: 0;
  }
}
</style>

