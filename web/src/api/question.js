import client from "@/api/client";

export const generateQuestionsApi = (payload) => client.post("/questions/generate", payload);
export const listQuestionSetsApi = () => client.get("/questions");
