import { defineStore } from 'pinia'
import { ref } from 'vue'

const defaultPaperForm = () => ({
  title: '',
  subject: '',
  durationMinutes: 90,
})

const defaultQuery = () => ({
  pageNum: 1,
  pageSize: 10,
  subject: '',
  questionType: '',
  keyword: '',
})

export const useComposeStore = defineStore('compose', () => {
  const selectedQuestions = ref([])
  const selectedPanelVisible = ref(false)
  const paperForm = ref(defaultPaperForm())
  const query = ref(defaultQuery())

  function addQuestion(question) {
    if (selectedQuestions.value.some((q) => q.id === question.id)) {
      return
    }
    selectedQuestions.value.push({
      ...question,
      sortOrder: selectedQuestions.value.length + 1,
      score: question.scoreDefault ?? 5,
    })
  }

  function removeQuestion(id) {
    selectedQuestions.value = selectedQuestions.value
      .filter((q) => q.id !== id)
      .map((q, index) => ({ ...q, sortOrder: index + 1 }))
  }

  function moveUp(id) {
    const list = [...selectedQuestions.value]
    const index = list.findIndex((q) => q.id === id)
    if (index <= 0) return
    ;[list[index - 1], list[index]] = [list[index], list[index - 1]]
    selectedQuestions.value = list.map((q, i) => ({ ...q, sortOrder: i + 1 }))
  }

  function moveDown(id) {
    const list = [...selectedQuestions.value]
    const index = list.findIndex((q) => q.id === id)
    if (index < 0 || index >= list.length - 1) return
    ;[list[index], list[index + 1]] = [list[index + 1], list[index]]
    selectedQuestions.value = list.map((q, i) => ({ ...q, sortOrder: i + 1 }))
  }

  function updateScore(id, score) {
    const item = selectedQuestions.value.find((q) => q.id === id)
    if (item) {
      item.score = score
    }
  }

  function clearSelection() {
    selectedQuestions.value = []
  }

  function clear() {
    clearSelection()
    paperForm.value = defaultPaperForm()
    query.value = defaultQuery()
  }

  return {
    selectedQuestions,
    selectedPanelVisible,
    paperForm,
    query,
    addQuestion,
    removeQuestion,
    moveUp,
    moveDown,
    updateScore,
    clearSelection,
    clear,
  }
})
