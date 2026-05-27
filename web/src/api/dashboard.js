import client from "@/api/client";

export const getDashboardOverviewApi = () => client.get("/dashboard/overview");
