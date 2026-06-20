import { defineStore } from 'pinia'
import { uploadWord } from '@/api/import'

function normalizeFiles(files) {
  return Array.isArray(files) ? files.filter(Boolean) : (files ? [files] : [])
}

export const useImportStore = defineStore('import', {
  state: () => ({
    uploading: false,
    uploadError: null,
    uploadMessage: '',
    lastBatchUuid: null,
    lastFileName: null,
    lastTotalCount: 0,
  }),

  actions: {
    async startUpload(stemFiles, answerFiles, subject) {
      const normalizedStemFiles = normalizeFiles(stemFiles)
      const normalizedAnswerFiles = normalizeFiles(answerFiles)
      if (normalizedStemFiles.length === 0) {
        throw new Error('请至少选择一份试卷文件')
      }

      this.uploading = true
      this.uploadError = null
      this.uploadMessage = '正在解析 Word 文档…'

      try {
        const data = await uploadWord(normalizedStemFiles, normalizedAnswerFiles, subject)
        this.lastBatchUuid = data.batchUuid
        this.lastFileName = data.fileName
        this.lastTotalCount = data.totalCount || 0

        const stemCount = normalizedStemFiles.length
        const answerCount = normalizedAnswerFiles.length
        let modeText = '仅试卷'
        if (answerCount > 0) {
          modeText = stemCount > 1 || answerCount > 1
            ? `${stemCount}份试卷+${answerCount}份答案`
            : '试卷+答案'
        } else if (stemCount > 1) {
          modeText = `${stemCount}份试卷`
        }
        this.uploadMessage = `${modeText}解析完成，共识别 ${this.lastTotalCount} 道题`
        return data
      } catch (error) {
        this.uploadError = error.message || '解析失败'
        this.uploadMessage = ''
        throw error
      } finally {
        this.uploading = false
      }
    },

    clearLastBatch() {
      this.lastBatchUuid = null
      this.lastFileName = null
      this.lastTotalCount = 0
      this.uploadMessage = ''
      this.uploadError = null
    },
  },
})
