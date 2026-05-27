import { ref } from "vue";
import { defineStore } from "pinia";

export const useAppStore = defineStore("app", () => {
  const mobileNavOpen = ref(false);

  function openNav() {
    mobileNavOpen.value = true;
  }

  function closeNav() {
    mobileNavOpen.value = false;
  }

  return {
    mobileNavOpen,
    openNav,
    closeNav
  };
});
