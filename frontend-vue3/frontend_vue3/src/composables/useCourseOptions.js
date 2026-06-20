import { onMounted, ref } from 'vue'
import { listCourseNames } from '@/api/question'

export function useCourseOptions() {
  const courseOptions = ref([])
  const loadingCourses = ref(false)

  async function loadCourseOptions() {
    loadingCourses.value = true
    try {
      courseOptions.value = (await listCourseNames()) || []
    } finally {
      loadingCourses.value = false
    }
  }

  onMounted(() => {
    loadCourseOptions()
  })

  return {
    courseOptions,
    loadingCourses,
    loadCourseOptions,
  }
}
