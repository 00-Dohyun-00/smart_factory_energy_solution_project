<!--
  날씨 아이콘 (SVG 직접 그림, 라이브러리 없음)
  날씨 글자(백엔드가 준 값)에 맞는 그림을 보여 줍니다. 모르는 날씨면 아무것도 그리지 않습니다.
  색은 글자색(currentColor)을 따라가므로 부모에서 color로 바꿉니다. 상태색은 쓰지 않습니다.

  사용 예: <WeatherIcon text="흐림" :size="24" />
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 날씨 글자. 예: '맑음', '구름많음', '흐림', '비', '눈'
  text: { type: String, default: '' },
  size: { type: Number, default: 24 },
})

// 날씨 글자 → 그림 종류. 글자에 '눈'이 있으면 눈, '비'가 있으면 비 (예: '비/눈'은 눈 그림)
const kind = computed(() => {
  const text = props.text ?? ''
  if (text.includes('눈')) return 'snow'
  if (text.includes('비') || text.includes('소나기')) return 'rain'
  if (text.includes('구름')) return 'partly'
  if (text.includes('흐림')) return 'cloud'
  if (text.includes('맑음')) return 'sun'
  return ''
})
</script>

<template>
  <svg
    v-if="kind"
    class="weather-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    role="img"
    :aria-label="text"
  >
    <!-- 맑음: 해 -->
    <template v-if="kind === 'sun'">
      <circle cx="12" cy="12" r="4.2" />
      <path
        d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6"
      />
    </template>

    <!-- 구름많음: 해 + 구름 -->
    <template v-else-if="kind === 'partly'">
      <path d="M8 3.5v1.5M3.5 8H5M4.8 4.8l1 1M11.2 4.8l-1 1" />
      <path d="M5.6 10.6A3.4 3.4 0 0 1 11.3 7" />
      <path d="M8.5 19.5h9a3.5 3.5 0 0 0 .4-7 5 5 0 0 0-9.6 1.3A2.9 2.9 0 0 0 8.5 19.5z" />
    </template>

    <!-- 흐림: 구름 -->
    <template v-else-if="kind === 'cloud'">
      <path d="M7 18.5h10.5a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.6A3.3 3.3 0 0 0 7 18.5z" />
    </template>

    <!-- 비: 구름 + 빗줄기 -->
    <template v-else-if="kind === 'rain'">
      <path d="M7 14.5h10.5a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.6A3.3 3.3 0 0 0 7 14.5z" />
      <path d="M8.5 17.5l-1 2.5M12.5 17.5l-1 2.5M16.5 17.5l-1 2.5" />
    </template>

    <!-- 눈: 구름 + 눈송이 -->
    <template v-else-if="kind === 'snow'">
      <path d="M7 14.5h10.5a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.6A3.3 3.3 0 0 0 7 14.5z" />
      <path d="M8 18.5v.01M12 20v.01M16 18.5v.01M10 21.5v.01M14 21.5v.01" stroke-width="2.4" />
    </template>
  </svg>
</template>

<style scoped>
.weather-icon {
  display: block;
  flex: none;
}
</style>
