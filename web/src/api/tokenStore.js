const ACCESS_TOKEN_KEY = "xiaozhi_access_token";
const REFRESH_TOKEN_KEY = "xiaozhi_refresh_token";
const LEGACY_TOKEN_KEY = "xiaozhi_token";

export function getAccessToken() {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY) || sessionStorage.getItem(LEGACY_TOKEN_KEY) || "";
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY) || "";
}

export function setTokens({ accessToken, refreshToken, token } = {}) {
  const nextAccessToken = accessToken || token || "";
  if (nextAccessToken) {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, nextAccessToken);
    sessionStorage.setItem(LEGACY_TOKEN_KEY, nextAccessToken);
  }
  if (refreshToken) {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
}
