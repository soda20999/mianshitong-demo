import client from "@/api/client";

export const uploadResumeApi = (file) => {
  const form = new FormData();
  form.append("file", file);
  return client.post("/resumes", form);
};
export const listResumesApi = () => client.get("/resumes");
export const parseResumeApi = (resumeId) => client.post(`/resumes/${resumeId}/parse`);
