<template>
  <div class="knowledge-cover" aria-hidden="true">
    <img v-if="src" :src="src" class="knowledge-cover__image" alt="" loading="lazy" />
    <div v-else class="knowledge-cover__placeholder">
      <span class="knowledge-cover__title">{{ name }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  src: String,
  name: String,
})
</script>

<style scoped>
@font-face {
  font-family: 'Kaushan Script';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('../assets/fonts/kaushan-script/latin-ext.woff2') format('woff2');
  unicode-range: U+0100-02BA, U+02BD-02C5, U+02C7-02CC, U+02CE-02D7, U+02DD-02FF, U+0304,
    U+0308, U+0329, U+1D00-1DBF, U+1E00-1E9F, U+1EF2-1EFF, U+2020, U+20A0-20AB,
    U+20AD-20C4, U+2113, U+2C60-2C7F, U+A720-A7FF;
}

@font-face {
  font-family: 'Kaushan Script';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('../assets/fonts/kaushan-script/latin.woff2') format('woff2');
  unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC,
    U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215,
    U+FEFF, U+FFFD;
}

.knowledge-cover {
  --cover-height: clamp(120px, 11vw, 145px);
  overflow: hidden;
  background: var(--bg);
}

.knowledge-cover__image {
  display: block;
  width: 100%;
  height: var(--cover-height);
  object-fit: cover;
  transition: transform 0.3s ease;
}

:global(.kb-card:hover .knowledge-cover__image) {
  transform: scale(1.03);
}

.knowledge-cover__placeholder {
  display: grid;
  place-items: center;
  height: var(--cover-height);
  padding: 32px 20px 16px;
  background: var(--kb-cover-bg);
}

.knowledge-cover__title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  padding-inline: 0.2em;
  color: color-mix(in srgb, var(--el-color-warning) 68%, color-mix(in srgb, var(--el-color-danger), var(--t1)));
  font-family: 'Kaushan Script', 'Kaiti SC', KaiTi, STKaiti, cursive;
  font-size: var(--text-num);
  line-height: 1.2;
  overflow-wrap: anywhere;
  text-align: center;
  text-wrap: balance;
}
</style>
