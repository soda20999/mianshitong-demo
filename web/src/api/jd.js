import client from "@/api/client";

export const analyzeJdApi = (payload) => client.post("/jd/analyze", payload);
export const listJdHistoryApi = () => client.get("/jd/history");
