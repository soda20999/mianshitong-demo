import client from "@/api/client";

export const startInterviewApi = (payload) => client.post("/interviews/start", payload);
export const answerInterviewApi = (sessionId, payload) =>
  client.post(`/interviews/${sessionId}/answer`, payload, { timeout: 120000 });
export const finishInterviewApi = (sessionId) => client.post(`/interviews/${sessionId}/finish`);
export const listInterviewsApi = () => client.get("/interviews");
