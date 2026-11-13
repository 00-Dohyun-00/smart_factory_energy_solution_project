<!--
  공통 차트 컴포넌트 (ECharts)

  사용 예:
    <Chart :option="option" :height="300" />

  option 작성법은 ECharts 예제 참고: https://echarts.apache.org/examples/ko/index.html
  option 값이 바뀌면 차트가 자동으로 다시 그려집니다. (실시간 데이터 갱신)
-->
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, default: null },
  height: { type: Number, default: 300 },
})

const container = ref(null)
let chart = null
let resizeObserver = null

// 차트 생성 + 화면 크기 변경 시 자동 리사이즈
onMounted(() => {
  chart = echarts.init(container.value)
  if (props.option) {
    chart.setOption(props.option)
  }

  resizeObserver = new ResizeObserver(() => chart.resize())
  resizeObserver.observe(container.value)
})

// option이 바뀔 때마다 차트 갱신
watch(
  () => props.option,
  (option) => {
    if (chart && option) {
      chart.setOption(option)
    }
  },
)

// 화면에서 사라질 때 정리
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="container" :style="{ width: '100%', height: `${height}px` }"></div>
</template>
