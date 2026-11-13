# 3. 프론트엔드 가이드

"설비 목록을 불러와 화면에 보여주고, 새 설비를 등록하는 화면"을 예시로 설명합니다.
아래 예시 코드는 실제로 빌드해서 확인한 코드입니다. 새 화면을 만들 때 복사해서 이름만 바꿔 쓰세요.

> Vue 3의 **`<script setup>`(Composition API)** 방식만 사용합니다. 인터넷 예제 중 `export default { data() {...}, methods: {...} }` 형태(Options API)는 쓰지 않습니다.

## 데이터가 흐르는 방식

```
페이지(화면) → Store(Pinia) → API 함수 → 백엔드
     ↑              │
     └── 상태 변경 시 화면 자동 갱신
```

| 구분 | 하는 일 |
|---|---|
| **api** | 백엔드 호출 함수 |
| **stores** | 여러 화면에서 같이 쓰는 데이터 보관 (Pinia) |
| **pages** | 화면 단위 컴포넌트 (URL 하나 = 페이지 하나) |
| **components** | 여러 화면에서 재사용하는 UI 조각 (버튼, 카드, 차트 등) |
| **layouts** | 여러 페이지가 공유하는 틀 (헤더, 사이드바) |
| **router** | 페이지(URL) 목록 |

## 폴더 구조 규칙

```
src/
├── api/
│   ├── client.js          # API 호출 공통 함수 (이미 만들어져 있음)
│   └── deviceApi.js       # 기능별 API 함수
├── stores/
│   └── useDeviceStore.js  # 기능별 Store (이름은 use + 기능 + Store)
├── components/            # 공통 UI 컴포넌트
│   └── Chart.vue          # 공통 차트 (이미 만들어져 있음)
├── layouts/
│   └── MainLayout.vue     # 공통 틀 (헤더, 사이드바)
├── pages/
│   ├── MainPage.vue       # 메인 화면 (/)
│   ├── NotFoundPage.vue   # 404 화면
│   └── DevicePage.vue     # 설비 화면 (예시)
├── router/
│   └── index.js           # 페이지(URL) 목록
├── styles/
│   ├── reset.css          # 브라우저 기본 스타일 제거 (수정 X)
│   └── global.css         # 전체 공통 스타일
├── assets/                # 이미지, 아이콘
├── App.vue                # 최상위 컴포넌트 (건드리지 않음)
└── main.js                # 시작 파일 (건드리지 않음)
```

- 화면(컴포넌트) 파일은 **`.vue`**, 이름은 **PascalCase**: `DevicePage.vue`, `EnergyChart.vue`
- 그 외 파일은 **`.js`**, 이름은 **camelCase**: `deviceApi.js`, `useDeviceStore.js`

## `.vue` 파일 구조

한 파일에 코드(`script`), 화면(`template`), 스타일(`style`)이 함께 들어갑니다.

```vue
<script setup>
// JavaScript 코드 (데이터, 함수)
</script>

<template>
  <!-- 화면 (HTML) -->
</template>

<style scoped>
/* 이 컴포넌트에만 적용되는 스타일 */
</style>
```

## import 경로: `@/` 사용

`@/`는 `src/` 폴더를 뜻합니다. `../../` 같은 상대 경로 대신 항상 `@/`로 씁니다.

```js
import { api } from '@/api/client'            // ✅
import { api } from '../../api/client'        // ❌ 폴더 위치가 바뀌면 깨짐
import Chart from '@/components/Chart.vue'    // ✅ .vue 파일은 확장자까지 씀
import Chart from '@/components/Chart'        // ❌ 파일을 찾지 못함
```

## 백엔드 호출 방식

`/api/...`로 요청하면 개발 서버가 자동으로 백엔드(8080)에 전달합니다. (`vite.config.js`의 proxy 설정)
그래서 코드에는 `http://localhost:8080`을 **절대 직접 쓰지 않습니다.**

모든 API 호출은 이미 만들어진 `src/api/client.js`의 `api()` 함수를 사용합니다.

```js
api('/주소')                                    // GET
api('/주소', { method: 'POST', body: ... })     // POST
```

## 예시: 설비 화면 만들기

만드는 순서: **API 함수 → Store → 페이지 → 주소 등록**

> 백엔드가 어떤 값을 주고받는지는 Swagger(http://localhost:8080/swagger-ui.html)에서 확인합니다.
> 예: 설비 응답 `{ "id": 1, "name": "공조기-1", "location": "A라인" }`

### ① API 함수 (`src/api/deviceApi.js`)

```js
import { api } from '@/api/client'

// 설비 목록 조회
export const getDevices = () => api('/devices')

// 설비 등록 (body 예: { name: '공조기-1', location: 'A라인' })
export const createDevice = (body) =>
  api('/devices', { method: 'POST', body: JSON.stringify(body) })
```

### ② Store (`src/stores/useDeviceStore.js`)

Store는 **데이터(상태)와 그 데이터를 바꾸는 함수**를 한곳에 모아둔 것입니다.
어느 컴포넌트에서든 꺼내 쓸 수 있고, 값이 바뀌면 그 값을 쓰는 화면이 자동으로 다시 그려집니다.

```js
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { createDevice, getDevices } from '@/api/deviceApi'

export const useDeviceStore = defineStore('device', () => {
  const devices = ref([]) // 초기값
  const loading = ref(false)

  async function fetchDevices() {
    loading.value = true // ref 값은 .value로 읽고 씀
    try {
      devices.value = await getDevices()
    } finally {
      loading.value = false
    }
  }

  async function addDevice(body) {
    const created = await createDevice(body)
    devices.value.push(created)
  }

  // 밖에서 쓸 값과 함수를 return
  return { devices, loading, fetchDevices, addDevice }
})
```

- `defineStore('device', ...)`의 첫 번째 값(`'device'`)은 Store 이름입니다. Store마다 다르게 지어야 합니다.

### ③ 페이지 (`src/pages/DevicePage.vue`)

```vue
<script setup>
import { onMounted, ref } from 'vue'
import { useDeviceStore } from '@/stores/useDeviceStore'

// Store 가져오기
const deviceStore = useDeviceStore()

// 이 화면에서만 쓰는 값은 ref
const name = ref('')

// 화면이 처음 뜰 때 한 번 실행
onMounted(() => {
  deviceStore.fetchDevices()
})

async function handleSubmit() {
  if (!name.value.trim()) return
  await deviceStore.addDevice({ name: name.value })
  name.value = ''
}
</script>

<template>
  <p v-if="deviceStore.loading">불러오는 중...</p>

  <div v-else>
    <h1>설비 목록</h1>
    <!-- @submit.prevent: 제출 시 페이지 새로고침 막기 -->
    <form @submit.prevent="handleSubmit">
      <!-- v-model: 입력값과 name 연결 -->
      <input v-model="name" placeholder="설비 이름" />
      <button type="submit">등록</button>
    </form>
    <ul>
      <li v-for="device in deviceStore.devices" :key="device.id">{{ device.name }}</li>
    </ul>
  </div>
</template>
```

**자주 쓰는 템플릿 문법**

| 문법 | 뜻 | 예 |
|---|---|---|
| `{{ 값 }}` | 값 출력 | `{{ device.name }}` |
| `v-if` / `v-else` | 조건에 따라 보이기 | `v-if="loading"` |
| `v-for` | 목록 반복 (`:key` 필수) | `v-for="d in devices" :key="d.id"` |
| `v-model` | 입력창과 값 연결 | `v-model="name"` |
| `:속성` | 속성에 JavaScript 값 넣기 | `:option="option"` |
| `@이벤트` | 이벤트 처리 | `@click="handleClick"` |

> `<script>`에서는 `name.value`처럼 `.value`를 붙이고, `<template>`에서는 `name`만 씁니다. (템플릿에서는 자동으로 풀림)

### ④ 페이지 주소 등록 (`src/router/index.js`)

`MainLayout`의 `children`에 한 줄 추가하면 http://localhost:5173/devices 로 들어갈 수 있습니다.

```js
import DevicePage from '@/pages/DevicePage.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', component: MainPage },
      { path: 'devices', component: DevicePage }, // 추가
    ],
  },
  ...
]
```

## 에러 처리

API 호출이 실패하면 `api()` 함수가 **백엔드가 보낸 에러 메시지**로 에러를 던집니다.
`try-catch`로 잡아서 `error.message`를 사용자에게 보여주면 됩니다.

```js
async function handleSubmit() {
  try {
    await deviceStore.addDevice({ name: name.value })
    name.value = ''
  } catch (error) {
    alert(error.message) // 예: "name: 공백일 수 없습니다"
  }
}
```

## 차트 그리기 (ECharts)

차트는 이미 만들어진 `Chart` 컴포넌트를 사용합니다. **어떤 차트를 그릴지는 `option` 객체로 정합니다.**

```vue
<script setup>
import Chart from '@/components/Chart.vue'

const option = {
  tooltip: { trigger: 'axis' }, // 마우스를 올리면 값 표시
  xAxis: { type: 'category', data: ['00시', '06시', '12시', '18시'] },
  yAxis: { type: 'value', name: 'kWh' },
  series: [{ type: 'line', data: [120, 200, 150, 80], smooth: true }],
}
</script>

<template>
  <Chart :option="option" :height="300" />
</template>
```

- `series`의 `type`만 바꾸면 다른 차트가 됩니다: `'line'`(선), `'bar'`(막대), `'pie'`(원형), `'gauge'`(게이지)
- 원하는 모양은 [ECharts 예제 모음](https://echarts.apache.org/examples/ko/index.html)에서 찾고, 예제 코드의 `option` 부분만 복사해서 쓰면 됩니다.
- 화면 크기가 바뀌면 차트도 자동으로 크기가 맞춰집니다.

백엔드 데이터로 그릴 때는 `computed`로 `option`을 만듭니다. Store 데이터가 바뀌면 `option`도 다시 계산되고, 차트가 자동으로 다시 그려집니다.

```js
import { computed } from 'vue'
import { useEnergyStore } from '@/stores/useEnergyStore'

const energyStore = useEnergyStore() // 예: energyStore.usages = [{ time: '00시', value: 120 }, ...]

const option = computed(() => ({
  xAxis: { type: 'category', data: energyStore.usages.map((u) => u.time) },
  yAxis: { type: 'value' },
  series: [{ type: 'line', data: energyStore.usages.map((u) => u.value) }],
}))
```

## 페이지 이동 (Vue Router)

| 하고 싶은 것 | 방법 |
|---|---|
| 링크 클릭으로 이동 | `<RouterLink to="/devices">설비</RouterLink>` |
| 코드에서 이동 (등록 후 목록으로 등) | `const router = useRouter()` → `router.push('/devices')` |
| 주소의 값 읽기 (`/devices/3`의 3) | 주소를 `path: 'devices/:id'`로 등록 → `const route = useRoute()` → `route.params.id` |

`useRouter`, `useRoute`는 `'vue-router'`에서 import 합니다. `RouterLink`는 import 없이 바로 씁니다.

> `<a href="...">`를 쓰면 페이지 전체가 새로고침되어 Store 데이터가 날아갑니다. 내부 이동은 꼭 `RouterLink`를 쓰세요.

## ref vs Pinia Store, 언제 뭘 쓰나요?

| 상황 | 사용 |
|---|---|
| 이 컴포넌트 안에서만 쓰는 값 (입력창 값, 모달 열림 여부) | `ref` |
| 여러 화면/컴포넌트에서 같이 쓰는 값 (설비 목록, 로그인 사용자) | Pinia Store |
| 백엔드에서 가져온 데이터 | Pinia Store |

## 스타일 작성

- `reset.css`가 브라우저 기본 스타일(여백, 글자 크기, 목록 점 등)을 모두 없애둔 상태입니다. `h1`도 일반 글자와 크기가 같으니 필요한 스타일은 직접 지정하세요.
- 프로젝트 전체에 적용할 스타일(폰트, 색상 등)만 `global.css`에 넣고, 나머지는 각 `.vue` 파일의 `<style scoped>`에 작성합니다. (`scoped`를 붙이면 그 컴포넌트에만 적용됨)

## 주의사항

- Store에서 값을 꺼낼 때 **구조 분해하지 마세요.** 값이 바뀌어도 화면이 갱신되지 않습니다.
  - ❌ `const { devices } = useDeviceStore()`
  - ✅ `const deviceStore = useDeviceStore()` → `deviceStore.devices`
- `<script>`에서 `ref` 값을 읽고 쓸 때 **`.value`를 빼먹지 마세요.** (`name = ''` ❌ / `name.value = ''` ✅)
- 백엔드 응답의 필드 이름(`name`, `location` 등)은 Swagger에 나온 그대로 써야 합니다. 오타가 나도 오류 없이 `undefined`가 되니 화면에 값이 안 나오면 이름부터 확인하세요.
- 커밋 전에 `npm run format`(코드 자동 정렬), `npm run build`, `npm run lint`를 실행해주세요.
  - VS Code에 **Vue (Official)** 확장과 **Prettier** 확장을 설치하고 "Format On Save"를 켜면 저장할 때마다 자동 정렬됩니다.
