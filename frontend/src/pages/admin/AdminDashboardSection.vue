<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  formatDuration,
  formatTrendAxisValue,
  formatTrendValue,
  metricText,
  trendTypeOptions
} from './adminPageUtils';

const props = defineProps({
  dashboard: {
    type: Object,
    default: null
  },
  isLoading: {
    type: Boolean,
    required: true
  },
  messageTrendRange: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['range-change']);

const activeTrendType = ref('message');
const dashboardTrendShell = ref(null);
const dashboardTrendChart = ref(null);
const dashboardTrendOverlay = ref(null);
let dashboardChart = null;
let dashboardTrendAnimationFrame = 0;
let dashboardTrendEcharts = null;
let dashboardTrendEchartsPromise = null;
let isDashboardTrendMounted = false;

const averageResponseStatus = computed(() => {
  const value = props.dashboard?.averageResponseTimeMs;
  if (value === null || value === undefined) {
    return 'pending';
  }
  if (value > 15000) {
    return 'danger';
  }
  if (value <= 10000) {
    return 'good';
  }
  return 'warn';
});

const messageTrendPoints = computed(() => {
  const points = props.dashboard?.messageTrendPoints;
  return Array.isArray(points) ? points : [];
});

const dashboardTrendSeries = computed(() => {
  const series = props.dashboard?.dashboardTrendSeries;
  if (Array.isArray(series) && series.length > 0) {
    return series;
  }
  const fallbackPoints = messageTrendPoints.value.map((point) => ({
    label: point.label || '-',
    value: point.messageCount || 0
  }));
  return [{
    type: 'message',
    title: '消息趋势',
    summaryLabel: '消息数',
    unit: '条',
    color: '#4C4F69',
    summaryValue: fallbackPoints.reduce((total, point) => total + point.value, 0),
    thresholds: [],
    points: fallbackPoints
  }];
});

const activeDashboardTrend = computed(() => {
  return dashboardTrendSeries.value.find((series) => series.type === activeTrendType.value)
    || dashboardTrendSeries.value[0]
    || null;
});

const activeTrendPoints = computed(() => {
  const points = activeDashboardTrend.value?.points;
  return Array.isArray(points) ? points : [];
});

const activeTrendThresholds = computed(() => {
  const thresholds = activeDashboardTrend.value?.thresholds;
  return Array.isArray(thresholds) ? thresholds : [];
});

const activeTrendSummaryValue = computed(() => {
  const value = activeDashboardTrend.value?.summaryValue;
  return Number.isFinite(Number(value)) ? Number(value) : 0;
});

const activeTrendOptionIndex = computed(() => {
  return Math.max(0, trendTypeOptions.findIndex((option) => option.type === activeTrendType.value));
});

const dashboardTrendRangeIndex = computed(() => props.messageTrendRange === 'month' ? 1 : 0);

onMounted(async () => {
  isDashboardTrendMounted = true;
  window.addEventListener('resize', resizeDashboardTrendChart);
  await renderDashboardTrendChart();
});

onUnmounted(() => {
  isDashboardTrendMounted = false;
  window.removeEventListener('resize', resizeDashboardTrendChart);
  disposeDashboardTrendChart();
});

watch([activeTrendPoints, activeTrendType], async () => {
  await renderDashboardTrendChart();
}, { deep: true });

watch(dashboardTrendSeries, (series) => {
  if (series.length > 0 && !series.some((item) => item.type === activeTrendType.value)) {
    activeTrendType.value = series[0].type;
  }
}, { deep: true });

async function setActiveTrendType(value) {
  if (activeTrendType.value === value) {
    return;
  }
  activeTrendType.value = value;
}

function setMessageTrendRange(value) {
  if (props.messageTrendRange === value || props.isLoading) {
    return;
  }
  emit('range-change', value);
}

async function renderDashboardTrendChart() {
  await nextTick();
  if (!isDashboardTrendMounted || !dashboardTrendChart.value) {
    disposeDashboardTrendChart();
    return;
  }
  const echarts = await loadDashboardTrendEcharts();
  if (!isDashboardTrendMounted || !dashboardTrendChart.value) {
    return;
  }
  if (!dashboardChart) {
    dashboardChart = echarts.init(dashboardTrendChart.value, null, { renderer: 'canvas' });
  }

  const trend = activeDashboardTrend.value;
  const points = activeTrendPoints.value;
  const labels = points.map((point) => point.label || '-');
  const trendValues = points.map((point) => Number(point.value || 0));
  const thresholdValues = activeTrendThresholds.value.map((threshold) => Number(threshold.value || 0));
  const maxTrendValue = Math.max(...trendValues, ...thresholdValues, 0);
  const yAxisMax = Math.max(5, Math.ceil(maxTrendValue * 1.18));
  const trendColor = trend?.color || '#4C4F69';

  dashboardChart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    color: [trendColor],
    textStyle: {
      fontFamily: '"Cascadia Mono", "Microsoft YaHei", Consolas, monospace',
      color: '#303446'
    },
    tooltip: {
      trigger: 'axis',
      borderWidth: 1,
      borderColor: 'rgba(76, 79, 105, 0.14)',
      backgroundColor: 'rgba(255, 255, 255, 0.98)',
      padding: [10, 12],
      textStyle: {
        color: '#303446',
        fontFamily: '"Cascadia Mono", "Microsoft YaHei", Consolas, monospace',
        fontSize: 12
      },
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: 'rgba(76, 79, 105, 0.22)',
          width: 1
        }
      },
      formatter(params) {
        const index = params?.[0]?.dataIndex ?? 0;
        const point = points[index] || {};
        return [
          `<strong>${point.label || ''}</strong>`,
          `${trend?.summaryLabel || '数值'}: ${formatTrendValue(point.value || 0, trend?.unit)}`
        ].join('<br/>');
      }
    },
    legend: { show: false },
    grid: {
      top: 28,
      right: 28,
      bottom: 30,
      left: 42,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisTick: { show: false },
      axisLine: {
        lineStyle: { color: 'rgba(76, 79, 105, 0.12)' }
      },
      axisLabel: {
        color: 'rgba(48, 52, 70, 0.52)',
        fontSize: 12,
        interval: 0
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: yAxisMax,
      minInterval: 1,
      splitLine: {
        lineStyle: {
          color: 'rgba(76, 79, 105, 0.12)',
          type: 'dashed'
        }
      },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: 'rgba(48, 52, 70, 0.52)',
        fontSize: 12,
        formatter(value) {
          return formatTrendAxisValue(value, trend?.unit);
        }
      }
    },
    series: [
      createTrendSeries(trend?.summaryLabel || '趋势', trendValues, trend)
    ]
  }, true);
  dashboardChart.resize();
  renderDashboardTrendOverlay(labels, trendValues, trendColor);
}

async function renderDashboardTrendOverlay(labels, data, color = '#4C4F69') {
  await nextTick();
  if (!dashboardChart || !dashboardTrendShell.value || !dashboardTrendOverlay.value || !data.length) {
    return;
  }
  window.cancelAnimationFrame(dashboardTrendAnimationFrame);
  const shellRect = dashboardTrendShell.value.getBoundingClientRect();
  const svg = dashboardTrendOverlay.value;
  svg.setAttribute('viewBox', `0 0 ${shellRect.width} ${shellRect.height}`);
  svg.innerHTML = '';

  const pixelPoints = data.map((value, index) => {
    const [x, y] = dashboardChart.convertToPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [labels[index], value]);
    return { x, y, value };
  });
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', createSmoothPath(pixelPoints));
  path.setAttribute('class', 'dashboard-trend-svg-line');
  path.style.stroke = color;
  svg.appendChild(path);

  const circles = pixelPoints.map((point) => {
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('cx', point.x);
    circle.setAttribute('cy', point.y);
    circle.setAttribute('r', '0');
    circle.setAttribute('class', 'dashboard-trend-svg-point');
    circle.style.stroke = color;
    svg.appendChild(circle);
    return circle;
  });

  const pathLength = path.getTotalLength();
  path.style.strokeDasharray = String(pathLength);
  path.style.strokeDashoffset = String(pathLength);
  const startedAt = window.performance.now();
  const lineDuration = 1680;
  const pointDuration = 180;
  const pointRadius = 3.5;

  function step(now) {
    if (!dashboardTrendOverlay.value) {
      return;
    }
    const elapsed = now - startedAt;
    const lineProgress = easeInOutCubic(Math.min(1, elapsed / lineDuration));
    path.style.strokeDashoffset = String(pathLength * (1 - lineProgress));
    circles.forEach((circle, index) => {
      const position = circles.length <= 1 ? 1 : index / (circles.length - 1);
      const pointStart = position * Math.max(0, lineDuration - pointDuration);
      const pointProgress = Math.max(0, Math.min(1, (elapsed - pointStart) / pointDuration));
      circle.setAttribute('r', String(pointRadius * easeOutCubic(pointProgress)));
    });

    if (elapsed < lineDuration + pointDuration) {
      dashboardTrendAnimationFrame = window.requestAnimationFrame(step);
    }
  }

  dashboardTrendAnimationFrame = window.requestAnimationFrame(step);
}

function createSmoothPath(points) {
  if (!points.length) {
    return '';
  }
  if (points.length === 1) {
    const point = points[0];
    return `M ${point.x} ${point.y}`;
  }
  return points.reduce((path, point, index) => {
    if (index === 0) {
      return `M ${point.x} ${point.y}`;
    }
    const previous = points[index - 1];
    const distance = point.x - previous.x;
    const controlOne = {
      x: previous.x + distance * 0.42,
      y: previous.y
    };
    const controlTwo = {
      x: point.x - distance * 0.42,
      y: point.y
    };
    return `${path} C ${controlOne.x} ${controlOne.y}, ${controlTwo.x} ${controlTwo.y}, ${point.x} ${point.y}`;
  }, '');
}

function easeOutCubic(value) {
  return 1 - Math.pow(1 - value, 3);
}

function easeInOutCubic(value) {
  if (value <= 0) {
    return 0;
  }
  if (value >= 1) {
    return 1;
  }
  return value < 0.5
    ? 4 * value * value * value
    : 1 - Math.pow(-2 * value + 2, 3) / 2;
}

function createTrendSeries(name, data, trend) {
  const color = trend?.color || '#4C4F69';
  const thresholds = Array.isArray(trend?.thresholds) ? trend.thresholds : [];
  return {
    name,
    type: 'line',
    data,
    smooth: true,
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 10,
    connectNulls: false,
    lineStyle: {
      width: 3,
      color,
      opacity: 0,
      cap: 'round',
      join: 'round'
    },
    itemStyle: {
      color: '#ffffff',
      opacity: 0,
      borderWidth: 2,
      borderColor: color
    },
    markLine: thresholds.length ? {
      symbol: 'none',
      silent: true,
      data: thresholds.map((threshold) => ({
        name: threshold.label,
        yAxis: threshold.value,
        label: {
          formatter: threshold.label,
          position: 'insideEndTop',
          color: threshold.color || '#4C4F69',
          fontSize: 12,
          fontWeight: 700
        },
        lineStyle: {
          color: threshold.color || '#4C4F69',
          type: 'dashed',
          width: 1.5
        }
      }))
    } : undefined,
    emphasis: {
      focus: 'series',
      lineStyle: {
        width: 3
      }
    }
  };
}

function resizeDashboardTrendChart() {
  dashboardChart?.resize();
  if (dashboardChart) {
    const trend = activeDashboardTrend.value;
    const points = activeTrendPoints.value;
    renderDashboardTrendOverlay(
      points.map((point) => point.label || '-'),
      points.map((point) => Number(point.value || 0)),
      trend?.color || '#4C4F69'
    );
  }
}

function disposeDashboardTrendChart() {
  window.cancelAnimationFrame(dashboardTrendAnimationFrame);
  if (dashboardTrendOverlay.value) {
    dashboardTrendOverlay.value.innerHTML = '';
  }
  dashboardChart?.dispose();
  dashboardChart = null;
}

async function loadDashboardTrendEcharts() {
  if (dashboardTrendEcharts) {
    return dashboardTrendEcharts;
  }
  if (!dashboardTrendEchartsPromise) {
    dashboardTrendEchartsPromise = Promise.all([
      import('echarts/core'),
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ]).then(([
      echarts,
      { LineChart },
      { GridComponent, TooltipComponent, MarkLineComponent },
      { CanvasRenderer }
    ]) => {
      echarts.use([
        LineChart,
        GridComponent,
        TooltipComponent,
        MarkLineComponent,
        CanvasRenderer
      ]);
      dashboardTrendEcharts = echarts;
      return echarts;
    });
  }
  return dashboardTrendEchartsPromise;
}
</script>

<template>
  <section class="admin-section kc-content">
    <div class="kc-metric-grid">
      <article class="kc-metric-card">
        <span>活跃用户</span>
        <strong>{{ isLoading ? '...' : metricText(dashboard?.activeUserCount) }}</strong>
        <small>最近 24 小时登录</small>
      </article>
      <article class="kc-metric-card">
        <span>消息数</span>
        <strong>{{ isLoading ? '...' : metricText(dashboard?.messageCount) }}</strong>
        <small>全量 chat_message</small>
      </article>
      <article class="kc-metric-card">
        <span>会话数</span>
        <strong>{{ isLoading ? '...' : metricText(dashboard?.conversationCount) }}</strong>
        <small>全量会话</small>
      </article>
      <article class="kc-metric-card">
        <span>流量数</span>
        <strong>{{ isLoading ? '...' : metricText(dashboard?.trafficCharacterCount) }}</strong>
        <small>消息字符量</small>
      </article>
      <article class="kc-metric-card" :class="averageResponseStatus">
        <span>平均响应时间</span>
        <strong>{{ formatDuration(dashboard?.averageResponseTimeMs) }}</strong>
        <small>10 秒内良好，超过 15 秒标红</small>
      </article>
      <article class="kc-metric-card muted">
        <span>知识错误率</span>
        <strong>待接入</strong>
        <small>RAG 评估完善后统计</small>
      </article>
      <article class="kc-metric-card muted">
        <span>无知识率</span>
        <strong>待接入</strong>
        <small>RAG 检索链路完善后统计</small>
      </article>
    </div>

    <article class="dashboard-trend-card">
      <header class="dashboard-trend-header">
        <div class="dashboard-trend-heading">
          <div class="dashboard-trend-title">
            <strong>趋势分析</strong>
            <span aria-label="趋势分析说明">?</span>
          </div>
          <div
            class="dashboard-trend-actions dashboard-trend-type-switch"
            :style="{ '--trend-index': activeTrendOptionIndex }"
            role="group"
            aria-label="趋势类型"
          >
            <span class="dashboard-trend-indicator" aria-hidden="true"></span>
            <button
              v-for="option in trendTypeOptions"
              :key="option.type"
              type="button"
              :class="{ active: activeDashboardTrend?.type === option.type }"
              :disabled="isLoading || !dashboardTrendSeries.some((series) => series.type === option.type)"
              @click="setActiveTrendType(option.type)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <div
          class="dashboard-trend-actions dashboard-trend-range-switch"
          :style="{ '--trend-index': dashboardTrendRangeIndex }"
          role="group"
          aria-label="趋势范围"
        >
          <span class="dashboard-trend-indicator" aria-hidden="true"></span>
          <button
            type="button"
            :class="{ active: messageTrendRange === 'day' }"
            :disabled="isLoading"
            @click="setMessageTrendRange('day')"
          >
            24小时
          </button>
          <button
            type="button"
            :class="{ active: messageTrendRange === 'month' }"
            :disabled="isLoading"
            @click="setMessageTrendRange('month')"
          >
            本月
          </button>
        </div>
      </header>
      <div class="dashboard-trend-summary" :aria-label="`${activeDashboardTrend?.summaryLabel || '趋势'}统计`">
        <span class="dashboard-trend-summary-card">
          <i :style="{ backgroundColor: activeDashboardTrend?.color || '#4C4F69' }"></i>
          <span class="dashboard-trend-summary-copy">
            <b>{{ activeDashboardTrend?.summaryLabel || '趋势数' }}：{{ formatTrendValue(activeTrendSummaryValue, activeDashboardTrend?.unit) }}</b>
            <small>单位：{{ activeDashboardTrend?.unit || '-' }}</small>
          </span>
        </span>
      </div>
      <div
        v-if="activeTrendPoints.length"
        ref="dashboardTrendShell"
        class="dashboard-trend-chart"
        :aria-label="`${activeDashboardTrend?.title || '趋势'}折线图`"
      >
        <div ref="dashboardTrendChart" class="dashboard-trend-chart-canvas"></div>
        <svg ref="dashboardTrendOverlay" class="dashboard-trend-overlay" aria-hidden="true"></svg>
      </div>
      <p v-else class="dashboard-trend-empty">
        {{ isLoading ? '趋势数据加载中...' : '暂无趋势数据' }}
      </p>
    </article>
  </section>
</template>
