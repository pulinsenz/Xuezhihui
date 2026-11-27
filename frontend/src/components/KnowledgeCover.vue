<template>
  <div class="knowledge-cover" aria-hidden="true">
    <img v-if="src" :src="src" class="knowledge-cover__image" alt="" loading="lazy" />
    <div v-else class="knowledge-cover__title">{{ name }}</div>
  </div>
</template>

<script setup>
defineProps({
  src: String,
  name: String,
})
</script>

<style scoped>
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

.knowledge-cover__title {
  display: grid;
  place-items: center;
  min-height: var(--cover-height);
  padding: 36px 20px 20px;
  background: var(--kb-cover-bg);
  color: var(--brand);
  font-size: var(--text-heading);
  font-weight: 700;
  line-height: 1.4;
  overflow-wrap: anywhere;
  text-align: center;
  text-wrap: balance;
}
</style>
