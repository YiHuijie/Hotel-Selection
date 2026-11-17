<script setup>
defineProps({
  evaluation: { type: Object, required: true },
})

function percentage(value, total) {
  if (value == null || total == null || total === 0) return '—'
  return `${(value * 100 / total).toFixed(1)}%（${value}/${total}）`
}
</script>

<template>
  <section class="evaluation" aria-label="评估结果">
    <p class="eyebrow">会话评估结果</p>
    <div class="evaluation-grid">
      <div><span>运行时间</span><strong>{{ evaluation.durationMillis == null ? '—' : `${evaluation.durationMillis} 毫秒` }}</strong></div>
      <div><span>模型调用次数</span><strong>{{ evaluation.modelCalls == null ? '—' : `${evaluation.modelCalls} 次` }}</strong></div>
      <div><span>满足硬条件率</span><strong>{{ percentage(evaluation.hardSatisfied, evaluation.hardTotal) }}</strong></div>
      <div><span>满足偏好率</span><strong>{{ percentage(evaluation.preferenceSatisfied, evaluation.preferenceTotal) }}</strong></div>
      <div><span>会话错误率</span><strong>{{ percentage(evaluation.errorCount, evaluation.requestCount) }}</strong></div>
    </div>
    <p class="evaluation-note">
      会话各轮后端处理耗时及模型调用累计（含记忆更新和格式重试）；不含网络传输、评估持久化和事务提交。
      硬条件按各轮推荐酒店累计；偏好按各轮酒店与偏好项累计，重复推荐分别计数，未知计入分母<span v-if="evaluation.preferenceUnknown != null">（未知 {{ evaluation.preferenceUnknown }} 项）</span>。
      错误率为当前会话已进入处理的失败消息请求数 / 总请求数。
    </p>
    <p v-if="!evaluation.historyComplete" class="evaluation-note">
      旧会话仅保留升级前最近一轮及升级后的评估数据，耗时、调用次数和满足率不完整；错误率仍按已记录请求累计。
    </p>
  </section>
</template>
