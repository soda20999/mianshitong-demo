<template>
  <div class="editor-shell">
    <div class="toolbar">
      <el-button size="small" @click="toggleBold">B</el-button>
      <el-button size="small" @click="toggleItalic">I</el-button>
      <el-button size="small" @click="toggleBulletList">列表</el-button>
      <el-button size="small" @click="setParagraph">段落</el-button>
    </div>
    <editor-content :editor="editor" class="editor-body" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, watch } from "vue";
import { EditorContent, useEditor } from "@tiptap/vue-3";
import StarterKit from "@tiptap/starter-kit";

const props = defineProps({
  modelValue: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["update:modelValue"]);

const editor = useEditor({
  content: props.modelValue,
  extensions: [StarterKit],
  onUpdate: ({ editor: instance }) => {
    emit("update:modelValue", instance.getHTML());
  }
});

watch(
  () => props.modelValue,
  (value) => {
    if (editor.value && value !== editor.value.getHTML()) {
      editor.value.commands.setContent(value || "", false);
    }
  }
);

onBeforeUnmount(() => {
  editor.value?.destroy();
});

function toggleBold() {
  editor.value?.chain().focus().toggleBold().run();
}

function toggleItalic() {
  editor.value?.chain().focus().toggleItalic().run();
}

function toggleBulletList() {
  editor.value?.chain().focus().toggleBulletList().run();
}

function setParagraph() {
  editor.value?.chain().focus().setParagraph().run();
}
</script>

<style scoped>
.editor-shell {
  border: 1px solid var(--border-cream);
  border-radius: 12px;
  background: var(--pure-white);
  overflow: hidden;
}

.toolbar {
  border-bottom: 1px solid var(--border-cream);
  background: var(--ivory);
  padding: 8px;
  display: flex;
  gap: 8px;
}

.editor-body {
  min-height: 180px;
  padding: 12px;
  color: var(--anthropic-near-black);
}

.editor-body :deep(.ProseMirror) {
  outline: none;
  min-height: 150px;
}
</style>
