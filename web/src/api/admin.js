import client from "@/api/client";

export const listAdminUsersApi = () => client.get("/admin/users");
export const updateAdminUserApi = (userId, payload) => client.put(`/admin/users/${userId}`, payload);

export const listAdminResumesApi = () => client.get("/admin/resumes");
export const listAdminInterviewsApi = () => client.get("/admin/interviews");
export const listAdminReportsApi = () => client.get("/admin/reports");

export const listAdminPromptsApi = () => client.get("/admin/prompts");
export const createAdminPromptApi = (payload) => client.post("/admin/prompts", payload);
export const updateAdminPromptApi = (promptId, payload) => client.put(`/admin/prompts/${promptId}`, payload);

export const listAdminAiLogsApi = () => client.get("/admin/ai-logs");

export const getRiskConfigApi = () => client.get("/admin/risk-config");
export const updateRiskConfigApi = (payload) => client.put("/admin/risk-config", payload);

export const listSensitiveWordsApi = () => client.get("/admin/sensitive-words");
export const addSensitiveWordApi = (payload) => client.post("/admin/sensitive-words", payload);
export const removeSensitiveWordApi = (id) => client.delete(`/admin/sensitive-words/${id}`);
