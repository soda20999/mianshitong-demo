import client from "@/api/client";

export const listHotQuestionsApi = (params) => client.get("/hot-questions", { params });
export const getHotQuestionDetailApi = (id, params) => client.get(`/hot-questions/${id}`, { params });
export const hotQuestionActionApi = (id, action) => client.post(`/hot-questions/${id}/action`, { action });
export const favoriteHotQuestionApi = (id, favorite) => client.post(`/hot-questions/${id}/favorite`, { favorite });
export const scoreHotQuestionPracticeApi = (id, answer) => client.post(`/hot-questions/${id}/practice-score`, { answer });
export const uploadQuestionBankApi = (file) => {
  const form = new FormData();
  form.append("file", file);
  return client.post("/question-banks", form);
};
export const listQuestionBanksApi = () => client.get("/question-banks");
export const getQuestionBankDetailApi = (id) => client.get(`/question-banks/${id}`);
export const deleteQuestionBankApi = (id) => client.delete(`/question-banks/${id}`);
export const batchDeleteQuestionBanksApi = (bankFileIds) => client.post("/question-banks/batch-delete", { bankFileIds });
