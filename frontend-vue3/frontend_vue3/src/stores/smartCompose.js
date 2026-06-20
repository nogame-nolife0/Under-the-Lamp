import { defineStore } from 'pinia'
import { ref } from 'vue'

const defaultForm = () => ({
  title: '',
  query: '',
  subject: '',
  durationMinutes: 90,
  totalScore: 100,
  paperType: 'EXAM',
})

export const useSmartComposeStore = defineStore('smartCompose', () => {
  const form = ref(defaultForm())
  const previewQuestions = ref([])
  const composeCondition = ref({})
  const previewMessage = ref('')
  const paperId = ref(null)
  const activePreviewQuestionId = ref(null)

  function setPreviewResult({ questions = [], composeCondition: condition = {}, message = '' } = {}) {
    previewQuestions.value = questions.map((item) => ({ ...item }))
    composeCondition.value = condition
    previewMessage.value = message
    activePreviewQuestionId.value = previewQuestions.value[0]?.questionId ?? null
    paperId.value = null
  }

  function setActivePreviewQuestionId(id) {
    activePreviewQuestionId.value = id
  }

  function setPaperId(id) {
    paperId.value = id
  }

  function clearPreview() {
    previewQuestions.value = []
    composeCondition.value = {}
    previewMessage.value = ''
    activePreviewQuestionId.value = null
    paperId.value = null
  }

  function clear() {
    form.value = defaultForm()
    clearPreview()
  }

  return {
    form,
    previewQuestions,
    composeCondition,
    previewMessage,
    paperId,
    activePreviewQuestionId,
    setPreviewResult,
    setActivePreviewQuestionId,
    setPaperId,
    clearPreview,
    clear,
  }
})
