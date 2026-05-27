import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getProfileApi, loginApi, logoutApi, registerApi, updateProfileApi } from "@/api/auth";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "@/api/tokenStore";

const USER_KEY = "xiaozhi_user";

export const useAuthStore = defineStore("auth", () => {
  const token = ref(getAccessToken());
  const user = ref(loadUser());
  const loading = ref(false);

  const isAuthenticated = computed(() => Boolean(token.value));
  const isAdmin = computed(() => user.value?.role === "ADMIN");

  function setAuth(data) {
    setTokens(data);
    token.value = getAccessToken();
    user.value = data.user;
    sessionStorage.setItem(USER_KEY, JSON.stringify(data.user));
  }

  function setProfile(profile) {
    user.value = profile;
    sessionStorage.setItem(USER_KEY, JSON.stringify(profile));
  }

  function clearAuth() {
    token.value = "";
    user.value = null;
    clearTokens();
    sessionStorage.removeItem(USER_KEY);
  }

  async function login(payload) {
    loading.value = true;
    try {
      const data = await loginApi(payload);
      setAuth(data);
      return data;
    } finally {
      loading.value = false;
    }
  }

  async function register(payload) {
    loading.value = true;
    try {
      const data = await registerApi(payload);
      setAuth(data);
      return data;
    } finally {
      loading.value = false;
    }
  }

  async function fetchProfile() {
    if (!token.value) {
      return null;
    }
    const profile = await getProfileApi();
    setProfile(profile);
    return profile;
  }

  async function updateProfile(payload) {
    const profile = await updateProfileApi(payload);
    setProfile(profile);
    return profile;
  }

  async function logout() {
    const accessToken = getAccessToken();
    const refreshToken = getRefreshToken();
    clearAuth();
    if (!accessToken && !refreshToken) {
      return;
    }
    try {
      await logoutApi(accessToken, refreshToken);
    } catch {
      // ignore logout network errors
    }
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    isAdmin,
    login,
    register,
    fetchProfile,
    updateProfile,
    logout
  };
});

function loadUser() {
  const raw = sessionStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    sessionStorage.removeItem(USER_KEY);
    return null;
  }
}
