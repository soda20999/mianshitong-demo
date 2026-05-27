import client from "@/api/client";

export const getHistoryOverviewApi = () => client.get("/history/overview");
