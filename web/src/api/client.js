import axios from "axios";
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "@/api/tokenStore";

const DEFAULT_TIMEOUT = Number(import.meta.env.VITE_API_TIMEOUT || 120000);
const AI_TIMEOUT = Number(import.meta.env.VITE_AI_API_TIMEOUT || 300000);
const AI_TIMEOUT_PATTERNS = [
  /^\/resumes\/\d+\/parse$/,
  /^\/jd\/analyze$/,
  /^\/questions\/generate$/,
  /^\/hot-questions\/\d+\/practice-score$/,
  /^\/question-banks$/,
  /^\/interviews\/\d+\/answer$/
];

const normalizeUrl = (url = "") => url.replace(/^https?:\/\/[^/]+/i, "").split("?")[0];
const isAiRequest = (url) => AI_TIMEOUT_PATTERNS.some((pattern) => pattern.test(normalizeUrl(url)));

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: DEFAULT_TIMEOUT
});

let refreshPromise = null;

client.interceptors.request.use((config) => {
  if (isAiRequest(config.url || "") && (!config.timeout || config.timeout < AI_TIMEOUT)) {
    config.timeout = AI_TIMEOUT;
  }
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload.success === "boolean") {
      if (!payload.success) {
        return Promise.reject(new Error(payload.message || "Request failed"));
      }
      return payload.data;
    }
    return payload;
  },
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      const refreshToken = getRefreshToken();
      const path = normalizeUrl(originalRequest.url || "");
      if (refreshToken && path !== "/auth/refresh" && path !== "/auth/logout") {
        originalRequest._retry = true;
        try {
          const tokens = await refreshAccessToken(refreshToken);
          originalRequest.headers = originalRequest.headers || {};
          originalRequest.headers.Authorization = `Bearer ${tokens.accessToken || tokens.token}`;
          return client(originalRequest);
        } catch {
          clearTokens();
        }
      }
    }
    const msg = error.response?.data?.message || error.message || "Network error";
    return Promise.reject(new Error(msg));
  }
);

async function refreshAccessToken(refreshToken) {
  if (!refreshPromise) {
    refreshPromise = client.post("/auth/refresh", { refreshToken })
      .then((data) => {
        setTokens(data);
        return data;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export default client;
