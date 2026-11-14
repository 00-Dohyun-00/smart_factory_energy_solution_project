<!--
  설비 그림 (공장 레이아웃 SVG 안에서만 사용)
  <svg> 안에 넣어 쓰는 컴포넌트라 <g>로 시작합니다.
  좌표 기준: (x, y)가 그림의 "위쪽 가운데"이고, 그림 크기는 대략 너비 70 × 높이 58 입니다.

  사용 예: <MachineIcon type="furnace" :x="356" :y="44" :active="true" />
-->
<script setup>
defineProps({
  // fin | tube | core | furnace | dryer | injection | temp | conveyor | robot | leak | eol
  // | comp | chil | hvac | cabinet | transformer | pcs
  type: { type: String, default: '' },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  // 선택된 설비면 true (선 색이 강조색으로 바뀜)
  active: { type: Boolean, default: false },
})

// 컨베이어 롤러의 x 위치
const ROLLERS = [-58, -44, -30, -16, -2, 12, 26, 40, 54, 68]
</script>

<template>
  <g class="icon" :class="{ active }" :transform="`translate(${x} ${y})`">
    <!-- 핀 성형기: 프레스 + 롤러 + 주름진 핀 -->
    <template v-if="type === 'fin'">
      <rect x="-24" y="34" width="48" height="20" rx="2" />
      <rect x="-16" y="6" width="32" height="28" rx="2" />
      <line x1="-16" y1="18" x2="16" y2="18" />
      <circle class="part" cx="-9" cy="44" r="5" />
      <circle class="part" cx="9" cy="44" r="5" />
      <polyline class="open" points="24,46 27,41 30,46 33,41 36,46" />
    </template>

    <!-- 튜브 가공기: 가공 헤드 + 길게 나오는 튜브 -->
    <template v-else-if="type === 'tube'">
      <rect x="-30" y="38" width="60" height="14" rx="2" />
      <rect x="-30" y="14" width="20" height="24" rx="2" />
      <circle class="part" cx="-10" cy="26" r="4" />
      <line x1="-6" y1="22" x2="30" y2="22" />
      <line x1="-6" y1="26" x2="30" y2="26" />
      <line x1="-6" y1="30" x2="30" y2="30" />
    </template>

    <!-- 코어 조립기: 열교환기 코어(핀이 촘촘한 판) -->
    <template v-else-if="type === 'core'">
      <rect x="-22" y="8" width="44" height="44" rx="2" />
      <line
        v-for="lineX in [-14, -7, 0, 7, 14]"
        :key="lineX"
        :x1="lineX"
        y1="13"
        :x2="lineX"
        y2="47"
      />
      <rect class="part" x="-28" y="8" width="6" height="44" rx="1" />
      <rect class="part" x="22" y="8" width="6" height="44" rx="1" />
    </template>

    <!-- 브레이징로: 가열 구간이 나뉜 긴 터널 + 배기구 + 컨베이어 -->
    <template v-else-if="type === 'furnace'">
      <line x1="-86" y1="50" x2="86" y2="50" />
      <rect class="part" x="-84" y="43" width="9" height="7" />
      <rect class="part" x="75" y="43" width="9" height="7" />
      <rect x="-42" y="2" width="10" height="12" />
      <rect x="32" y="2" width="10" height="12" />
      <rect x="-72" y="14" width="144" height="34" rx="4" />
      <line x1="-36" y1="14" x2="-36" y2="48" />
      <line x1="0" y1="14" x2="0" y2="48" />
      <line x1="36" y1="14" x2="36" y2="48" />
      <circle
        v-for="dotX in [-54, -18, 18, 54]"
        :key="dotX"
        class="part"
        :cx="dotX"
        cy="31"
        r="4"
      />
    </template>

    <!-- 탈지·건조기: 둥근 문이 달린 챔버 -->
    <template v-else-if="type === 'dryer'">
      <rect x="-28" y="12" width="56" height="40" rx="3" />
      <circle cx="0" cy="33" r="13" />
      <circle class="part" cx="0" cy="33" r="7" />
      <line x1="-22" y1="18" x2="-12" y2="18" />
    </template>

    <!-- 사출기: 형체부 + 금형 + 사출 실린더 + 호퍼 -->
    <template v-else-if="type === 'injection'">
      <rect x="-36" y="40" width="72" height="12" rx="2" />
      <rect x="-36" y="14" width="26" height="26" rx="2" />
      <rect class="part" x="-10" y="18" width="8" height="18" />
      <rect x="-2" y="22" width="30" height="10" />
      <polygon points="11,8 27,8 22,22 16,22" />
      <rect x="28" y="20" width="8" height="14" />
    </template>

    <!-- 금형 온조기: 계기판이 달린 작은 캐비닛 -->
    <template v-else-if="type === 'temp'">
      <rect x="-18" y="8" width="36" height="44" rx="3" />
      <circle cx="0" cy="22" r="8" />
      <line x1="0" y1="22" x2="5" y2="17" />
      <rect class="part" x="-10" y="36" width="20" height="8" />
      <line x1="-8" y1="52" x2="-8" y2="57" />
      <line x1="8" y1="52" x2="8" y2="57" />
    </template>

    <!-- 조립 라인 컨베이어: 구동 모터 + 벨트 + 롤러 + 제품 -->
    <template v-else-if="type === 'conveyor'">
      <rect class="part" x="-40" y="14" width="14" height="14" />
      <rect class="part" x="0" y="14" width="14" height="14" />
      <rect class="part" x="40" y="14" width="14" height="14" />
      <rect x="-66" y="28" width="142" height="14" rx="7" />
      <circle v-for="rollerX in ROLLERS" :key="rollerX" class="part" :cx="rollerX" cy="35" r="3" />
      <rect class="motor" x="-84" y="22" width="18" height="24" rx="2" />
      <circle cx="-75" cy="34" r="5" />
    </template>

    <!-- 체결 로봇: 받침대 + 관절 팔 + 집게 -->
    <template v-else-if="type === 'robot'">
      <rect x="-13" y="46" width="26" height="8" rx="1" />
      <polyline class="arm" points="0,42 -12,20 12,10" />
      <circle cx="0" cy="42" r="7" />
      <circle class="part" cx="-12" cy="20" r="5" />
      <circle class="part" cx="12" cy="10" r="4" />
      <line x1="14" y1="9" x2="22" y2="4" />
      <line x1="14" y1="11" x2="22" y2="15" />
    </template>

    <!-- 기밀 검사기: 검사 챔버 + 헬륨 통 -->
    <template v-else-if="type === 'leak'">
      <rect x="-28" y="12" width="40" height="40" rx="6" />
      <rect class="part" x="-20" y="22" width="24" height="14" rx="2" />
      <line x1="12" y1="30" x2="18" y2="30" />
      <rect x="18" y="18" width="11" height="34" rx="5" />
      <line x1="23.5" y1="18" x2="23.5" y2="13" />
    </template>

    <!-- 성능 시험기: 시험대 + 모니터 + 시험 중인 모듈 -->
    <template v-else-if="type === 'eol'">
      <rect x="-28" y="38" width="56" height="14" rx="2" />
      <rect x="-24" y="8" width="28" height="20" rx="2" />
      <polyline class="open" points="-20,22 -14,16 -8,20 -1,13" />
      <line x1="-10" y1="28" x2="-10" y2="38" />
      <rect class="part" x="9" y="24" width="18" height="14" />
      <line x1="18" y1="24" x2="18" y2="16" />
    </template>

    <!-- 공기압축기: 가로로 누운 탱크 + 모터 + 펌프 -->
    <template v-else-if="type === 'comp'">
      <rect x="-28" y="30" width="56" height="18" rx="9" />
      <rect x="-18" y="12" width="22" height="18" rx="2" />
      <circle class="part" cx="14" cy="21" r="8" />
      <line x1="-18" y1="48" x2="-18" y2="54" />
      <line x1="18" y1="48" x2="18" y2="54" />
    </template>

    <!-- 냉각 칠러: 팬 2개가 달린 상자 -->
    <template v-else-if="type === 'chil'">
      <rect x="-27" y="10" width="54" height="42" rx="2" />
      <circle class="part" cx="-12" cy="27" r="9" />
      <circle class="part" cx="12" cy="27" r="9" />
      <line x1="-21" y1="27" x2="-3" y2="27" />
      <line x1="3" y1="27" x2="21" y2="27" />
      <line x1="-20" y1="44" x2="20" y2="44" />
    </template>

    <!-- 공조·조명: 공조기 본체 + 덕트 -->
    <template v-else-if="type === 'hvac'">
      <rect x="-28" y="16" width="36" height="34" rx="2" />
      <circle class="part" cx="-10" cy="33" r="10" />
      <rect x="8" y="26" width="22" height="14" />
      <line x1="15" y1="26" x2="15" y2="40" />
      <line x1="22" y1="26" x2="22" y2="40" />
    </template>

    <!-- 배전반: 차단기가 줄지어 있는 캐비닛 -->
    <template v-else-if="type === 'cabinet'">
      <rect x="-16" y="0" width="32" height="48" rx="2" />
      <rect class="part" x="-10" y="6" width="20" height="8" />
      <line x1="-10" y1="22" x2="10" y2="22" />
      <line x1="-10" y1="30" x2="10" y2="30" />
      <line x1="-10" y1="38" x2="10" y2="38" />
    </template>

    <!-- 수전설비: 변압기 기호(겹친 원 2개) -->
    <template v-else-if="type === 'transformer'">
      <circle class="open" cx="-7" cy="24" r="12" />
      <circle class="open" cx="7" cy="24" r="12" />
      <line x1="-19" y1="24" x2="-26" y2="24" />
      <line x1="19" y1="24" x2="26" y2="24" />
    </template>

    <!-- PCS(전력변환장치): 화면과 교류 물결 표시가 있는 캐비닛 -->
    <template v-else-if="type === 'pcs'">
      <rect x="-16" y="0" width="32" height="52" rx="2" />
      <rect class="part" x="-10" y="6" width="20" height="10" />
      <path class="open" d="M -9 30 q 4.5 -10 9 0 t 9 0" />
      <line x1="-9" y1="42" x2="9" y2="42" />
    </template>
  </g>
</template>

<style scoped>
.icon {
  fill: var(--card);
  stroke: var(--ink-2);
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.icon.active {
  stroke: var(--accent);
  stroke-width: 2;
}
.part {
  fill: var(--line);
}
.open {
  fill: none;
}
.arm {
  fill: none;
  stroke-width: 4;
}
.motor {
  fill: var(--line-strong);
}
</style>
