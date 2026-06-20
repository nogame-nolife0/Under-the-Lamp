<script setup>
import { computed, ref } from 'vue'
import { ElImageViewer } from 'element-plus'
import { isImageErrorText, parseRichBlocks, renderMathHtml } from '@/utils/renderMath'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
  emptyText: {
    type: String,
    default: '暂无内容',
  },
  imageMap: {
    type: Object,
    default: () => ({}),
  },
  editable: {
    type: Boolean,
    default: false,
  },
  previewable: {
    type: Boolean,
    default: true,
  },
  imageScope: {
    type: String,
    default: 'STEM',
  },
  compact: {
    type: Boolean,
    default: false,
  },
  /** 块级大图（如单独电路图预览区），默认题内行内小图 */
  block: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['image-click'])

const blocks = computed(() => parseRichBlocks(props.content || ''))
const previewVisible = ref(false)
const previewUrls = ref([])

function imageUrl(index) {
  return props.imageMap?.[index] || props.imageMap?.[String(index)] || ''
}

function handleImageClick(index) {
  const url = imageUrl(index)
  if (props.editable) {
    emit('image-click', { index, scope: props.imageScope })
    return
  }
  if (!props.previewable || !url) return
  previewUrls.value = [url]
  previewVisible.value = true
}

function closePreview() {
  previewVisible.value = false
  previewUrls.value = []
}
</script>

<template>
  <div v-if="content" class="rich-content">
    <template v-for="(segment, idx) in blocks" :key="idx">
      <div v-if="segment.type === 'image'" class="image-block">
        <img
          v-if="imageUrl(segment.index)"
          :src="imageUrl(segment.index)"
          :alt="`嵌入图片 ${segment.index}`"
          class="embedded-image"
          :class="{
            'is-editable': editable,
            'is-inline': !block,
            'is-compact': compact && !block,
            'is-block': block,
            'is-previewable': previewable && !editable,
          }"
          :title="editable ? '点击上传图片替换' : previewable ? '点击放大查看原图' : undefined"
          loading="lazy"
          @click.stop="handleImageClick(segment.index)"
        />
        <span
          v-else
          class="image-marker"
          :class="{ 'is-editable': editable }"
          :title="`嵌入图片 ${segment.index}`"
          @click.stop="editable && handleImageClick(segment.index)"
        >
          图{{ segment.index }}
        </span>
      </div>
      <span
        v-else
        class="rich-text"
        :class="{ 'is-error': isImageErrorText(segment.content) }"
        v-html="renderMathHtml(segment.content)"
      />
    </template>
  </div>
  <span v-else class="empty-text">{{ emptyText }}</span>
  <el-image-viewer
    v-if="previewVisible"
    :url-list="previewUrls"
    teleported
    @close="closePreview"
  />
</template>

<style scoped>
.rich-content {
  line-height: 1.85;
  font-size: 15px;
  color: var(--color-text-primary);
  word-break: break-word;
}

.image-block {
  display: inline;
  vertical-align: middle;
  white-space: nowrap;
}

.embedded-image {
  display: inline-block;
  vertical-align: -0.22em;
  width: auto;
  height: auto;
  margin: 0 2px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  object-fit: contain;
  image-rendering: auto;
}

/* 题内行内公式/符号图：缩小显示，原图不压缩，点击可放大 */
.embedded-image.is-inline {
  max-height: 1.35em;
  max-width: 5.5em;
}

.embedded-image.is-inline.is-compact {
  max-height: 1.2em;
  max-width: 4.5em;
}

.embedded-image.is-block {
  display: block;
  max-height: 280px;
  max-width: min(100%, 520px);
  margin: 8px 0;
  vertical-align: middle;
}

.embedded-image.is-editable,
.image-marker.is-editable,
.embedded-image.is-previewable {
  cursor: zoom-in;
}

.embedded-image.is-previewable:hover,
.embedded-image.is-editable:hover {
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}

.image-marker.is-editable:hover {
  background: var(--color-primary-light);
}

.image-marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 24px;
  margin: 0 4px;
  padding: 0 8px;
  border-radius: 6px;
  background: var(--color-primary-light);
  color: hsl(var(--shade-hue), 65%, 62%);
  font-size: 13px;
  font-weight: 600;
  vertical-align: middle;
  animation: pulse-border 2.5s ease-in-out infinite;
}

.rich-text :deep(.math-inline) {
  margin: 0 2px;
}

.rich-text :deep(.math-block) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 3px solid var(--color-primary-border);
  background: var(--color-primary-light);
  border-radius: 0 8px 8px 0;
  overflow-x: auto;
}

.rich-text :deep(.text-part) {
  white-space: pre-wrap;
}

.rich-text.is-error {
  color: var(--color-warning);
  font-size: 13px;
}

.empty-text {
  color: var(--color-text-placeholder);
  font-size: 13px;
}
</style>
