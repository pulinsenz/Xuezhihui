<script setup>
import { ref, onMounted } from 'vue'
import { getSettings, updateVectorizeDefault, updateCollapseRefs } from '../api/settings'
import { THEMES, getTheme, setTheme } from '../theme'
import { toast } from '../components/toast'
import XSwitch from '../components/XSwitch.vue'
import XIcon from '../components/XIcon.vue'

const currentTheme = ref(getTheme())
const defaultVectorize = ref(1)
const collapseRefs = ref(1)
const savingSwitch = ref(false)

onMounted(async () => {
  try {
    const data = await getSettings()
    defaultVectorize.value = data.defaultVectorize ?? 1
    collapseRefs.value = data.collapseRefs ?? 1
  } catch (e) {
    // 错误已由拦截器提示
  }
})

function pickTheme(key) {
  setTheme(key)
  currentTheme.value = key
  toast.success(`已切换为「${THEMES.find((t) => t.key === key)?.name}」主题`)
}

async function onVectorize(val) {
  savingSwitch.value = true
  try {
    await updateVectorizeDefault(val)
    toast.success(val === 1 ? '已开启默认入库' : '已关闭默认入库，新上传文档将不入库')
  } catch (e) {
    defaultVectorize.value = val === 1 ? 0 : 1
  } finally {
    savingSwitch.value = false
  }
}

async function onCollapse(val) {
  savingSwitch.value = true
  try {
    await updateCollapseRefs(val)
    toast.success(val === 1 ? '参考文献默认折叠' : '参考文献默认展开')
  } catch (e) {
    collapseRefs.value = val === 1 ? 0 : 1
  } finally {
    savingSwitch.value = false
  }
}
</script>

<template>
  <div class="page settings-page">
    <!-- 外观主题：三卡点选（radio 语义：互斥、有默认选中），即点即生效 -->
    <div class="x-card sec">
      <div class="sec-head">
        <b>外观主题</b>
        <span class="sec-sub">选择后立即生效，无需保存；偏好保存在本机浏览器</span>
      </div>
      <div class="theme-cards">
        <button
          v-for="t in THEMES" :key="t.key"
          class="theme-card" :class="{ on: currentTheme === t.key }"
          @click="pickTheme(t.key)"
        >
          <span class="tc-swatches">
            <i v-for="(s, i) in t.swatches" :key="i" :style="{ background: s }" />
          </span>
          <span class="tc-name">
            <span class="radio-dot" :class="{ on: currentTheme === t.key }" />
            <b>{{ t.name }}</b>
            <XIcon v-if="currentTheme === t.key" name="circleCheckFilled" :size="15" class="ck" />
          </span>
          <span class="tc-desc">{{ t.desc }}</span>
        </button>
      </div>
    </div>

    <!-- 行为偏好：开关拨动立即生效（不进保存表单），失败自动回拨 -->
    <div class="x-card sec">
      <div class="sec-head">
        <b>行为偏好</b>
        <span class="sec-sub">开关拨动立即生效并保存到账号</span>
      </div>
      <div class="pref-row">
        <div class="pref-copy">
          <b>上传文档默认入库</b>
          <span>开启时新上传的文档自动解析并向量化；关闭后仅保存文件，可稍后手动入库</span>
        </div>
        <XSwitch :model-value="defaultVectorize" :disabled="savingSwitch" @change="onVectorize" />
      </div>
      <div class="pref-row">
        <div class="pref-copy">
          <b>参考文献默认折叠</b>
          <span>AI 回答末尾的引用来源列表默认收起，点击展开</span>
        </div>
        <XSwitch :model-value="collapseRefs" :disabled="savingSwitch" @change="onCollapse" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page { display: flex; flex-direction: column; gap: 18px; max-width: 760px; }
.sec { padding: 20px 22px; }
.sec-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px; }
.sec-head b { font-size: 15px; }
.sec-sub { font-size: 12px; color: var(--muted); }

.theme-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.theme-card {
  text-align: left; border: 1.5px solid var(--line); border-radius: 14px;
  background: var(--surface); padding: 14px; cursor: pointer;
  display: flex; flex-direction: column; gap: 8px;
}
.theme-card:hover { border-color: var(--green); }
.theme-card.on {
  border-color: var(--green);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--green) 12%, transparent);
}
.tc-swatches { display: flex; gap: 6px; margin-bottom: 2px; }
.tc-swatches i { width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--surface); box-shadow: 0 0 0 1px var(--line); }
.tc-name { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.tc-name .ck { margin-left: auto; color: var(--green); }
.radio-dot { width: 15px; height: 15px; border-radius: 50%; border: 2px solid var(--dot); position: relative; flex: none; }
.radio-dot.on { border-color: var(--green); }
.radio-dot.on::after { content: ""; position: absolute; inset: 3px; border-radius: 50%; background: var(--green); }
.tc-desc { font-size: 11px; color: var(--muted); }

.pref-row {
  display: flex; align-items: center; gap: 16px;
  padding: 13px 0; border-top: 1px dashed var(--mint-deep);
}
.pref-row:first-of-type { border-top: none; }
.pref-copy { display: flex; flex-direction: column; gap: 4px; }
.pref-copy b { font-size: 13.5px; }
.pref-copy span { font-size: 12px; color: var(--muted); line-height: 1.7; }
.pref-row .x-switch { margin-left: auto; }
</style>
