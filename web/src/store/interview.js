import { ref } from "vue";
import { defineStore } from "pinia";

export const useInterviewStore = defineStore("interview", () => {
  const activeSession = ref(null);
  const scoreHistory = ref([]);

  function setActiveSession(session) {
    activeSession.value = session;
    scoreHistory.value = session?.scoreHistory ? [...session.scoreHistory] : [];
  }

  function appendMessage(message) {
    if (!activeSession.value) {
      return;
    }
    const list = activeSession.value.messages || [];
    list.push(message);
    activeSession.value.messages = list;
  }

  function appendScore(score) {
    scoreHistory.value.push(score);
    if (activeSession.value) {
      const list = activeSession.value.scoreHistory || [];
      list.push(score);
      activeSession.value.scoreHistory = list;
    }
  }

  function clear() {
    activeSession.value = null;
    scoreHistory.value = [];
  }

  return {
    activeSession,
    scoreHistory,
    setActiveSession,
    appendMessage,
    appendScore,
    clear
  };
});
