import client from "@/api/client";

export const listReportsApi = () => client.get("/reports");
export const getReportDetailApi = (reportId) => client.get(`/reports/${reportId}`);
export const favoriteReportQuestionApi = (reportId, questionIndex) =>
  client.post(`/reports/${reportId}/favorite`, { questionIndex });
export const redoWrongQuestionsApi = (reportId) => client.post(`/reports/${reportId}/redo-wrong`);
export const exportReportPdfApi = (reportId) => client.get(`/reports/${reportId}/export-pdf`, { responseType: "blob" });
export const batchExportReportPdfApi = (reportIds) =>
  client.post("/reports/export-pdf", { reportIds }, { responseType: "blob" });
