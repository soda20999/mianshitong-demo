import client from "@/api/client";

export const loginApi = (payload) => client.post("/auth/login", payload);
export const registerApi = (payload) => client.post("/auth/register", payload);
export const refreshApi = (refreshToken) => client.post("/auth/refresh", { refreshToken });
export const logoutApi = (accessToken, refreshToken) =>
  client.post(
    "/auth/logout",
    refreshToken ? { refreshToken } : null,
    accessToken
      ? {
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        }
      : undefined
  );
export const getProfileApi = () => client.get("/auth/profile");
export const updateProfileApi = (payload) => client.put("/auth/profile", payload);
