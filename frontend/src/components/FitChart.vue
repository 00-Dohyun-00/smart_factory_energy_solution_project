<!--
  남은 공간을 꽉 채우는 차트
  공통 차트(Chart.vue)는 높이를 숫자(px)로만 받으므로, 이 상자의 높이를 재서 그 숫자를 넘겨 줍니다.
  카드 안에서 남은 높이만큼 그래프를 키우거나 줄일 때 씁니다. (부모는 세로 flex여야 함)

  사용 예: <FitChart :option="option" :min-height="140" />
  여러 개를 나눠 채울 때는 부모 쪽 CSS에서 flex 값으로 비율을 정합니다. (예: .power { flex: 2 })
-->
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Chart from '@/components/Chart.vue'

defineProps({
  option: { type: Object, default: null },
  // 공간이 아주 작아도 그래프가 이보다 작아지지는 않음 (px)
  minHeight: { type: Number, default: 120 },
})

const box = ref(null)
const height = ref(0) // 상자의 지금 높이 (px). 재기 전에는 0이라 차트를 그리지 않음
let observer = null

onMounted(() => {
  // 상자 크기가 바뀔 때마다(창 크기 변경 등) 높이를 다시 잼
  observer = new ResizeObserver(() => {
    height.value = Math.floor(box.value.clientHeight)
  })
  observer.observe(box.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <div ref="box" class="fit" :style="{ minHeight: `${minHeight}px` }">
    <!-- 차트를 상자 위에 겹쳐 놓아, 차트 높이가 상자 높이를 다시 밀어내지 않게 함 -->
    <div class="inner">
      <Chart v-if="height" :option="option" :height="height" />
    </div>
  </div>
</template>

<style scoped>
.fit {
  position: relative;
  flex: 1;
  min-width: 0;
}
.inner {
  position: absolute;
  inset: 0;
}
</style>
