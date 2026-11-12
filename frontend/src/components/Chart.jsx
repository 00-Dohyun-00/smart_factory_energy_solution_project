import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'

/**
 * 공통 차트 컴포넌트 (ECharts)
 *
 * 사용 예:
 *   <Chart option={option} height={300} />
 *
 * option 작성법은 ECharts 예제 참고: https://echarts.apache.org/examples/ko/index.html
 * option 값이 바뀌면 차트가 자동으로 다시 그려집니다. (실시간 데이터 갱신)
 */
export default function Chart({ option, height = 300 }) {
  const containerRef = useRef(null)
  const chartRef = useRef(null)

  // 차트 생성 / 화면 크기 변경 시 자동 리사이즈 / 화면에서 사라질 때 정리
  useEffect(() => {
    const chart = echarts.init(containerRef.current)
    chartRef.current = chart

    const resizeObserver = new ResizeObserver(() => chart.resize())
    resizeObserver.observe(containerRef.current)

    return () => {
      resizeObserver.disconnect()
      chart.dispose()
    }
  }, [])

  // option이 바뀔 때마다 차트 갱신
  useEffect(() => {
    if (option) {
      chartRef.current?.setOption(option)
    }
  }, [option])

  return <div ref={containerRef} style={{ width: '100%', height }} />
}
