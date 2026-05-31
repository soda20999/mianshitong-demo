import client from "@/api/client";

export const uploadResumeApi = (file, onUploadProgress) => {
  const form = new FormData();
  form.append("file", file);
  return client.post("/resumes", form, {
    headers: {
      "Content-Type": "multipart/form-data"
    },
    onUploadProgress
  });
};
export const listResumesApi = () => client.get("/resumes");
export const parseResumeApi = (resumeId) => client.post(`/resumes/${resumeId}/parse`);
