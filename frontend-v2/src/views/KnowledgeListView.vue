<script setup>
import { ref, computed, onMounted } from 'vue'
import { listMyKnowledge, listKnowledgeInvitations, acceptKnowledgeInvitation, rejectKnowledgeInvitation, readKnowledgeInvitation, createKnowledge, updateKnowledge, deleteKnowledge, favoriteKnowledge, unfavoriteKnowledge, uploadCover } from '../api/knowledge'
import { toast } from '../components/toast'
import XButton from '../components/XButton.vue'
import XIcon from '../components/XIcon.vue'
import XModal from '../components/XModal.vue'
import XInput from '../components/XInput.vue'
import XEmpty from '../components/XEmpty.vue'

const loading = ref(false)
const list = ref([])
const invitations = ref([])

const inviteOpen = ref(false)
const inviteBusy = ref(false)

const editing = ref(null) // null | {mode:'create'|'edit', form:{...}}
const saving = ref(false)

const keyword = ref('')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((x) => (x.name || '').toLowerCase().includes(k) || (x.description || '').toLowerCase().includes(k))
})

const roleOf = (kb) => (kb.isOwner ? 'owner' : kb.isMember ? 'member' : 'fav')
const roleLabel = { owner: '拥有者', member: '成员', fav: '已收藏' }

async function load() {
  loading.value = true
  try {
    list.value = await listMyKnowledge()
  } finally {
    loading.value = false
  }
  try {
    invitations.value = await listKnowledgeInvitations()
  } catch (e) { /* 邀请加载失败不打断主列表 */ }
}

onMounted(load)

// ---- 邀请：横幅只显示一条，其余进弹窗逐条处理（点空白处/Esc 关闭） ----
async function handleInvite(inv, action) {
  inviteBusy.value = true
  try {
    if (action === 'accept') {
      await readKnowledgeInvitation(inv.id)
      await acceptKnowledgeInvitation(inv.id)
      toast.success(`已加入「${inv.knowledgeName || '知识库'}」`)
    } else {
      await rejectKnowledgeInvitation(inv.id)
      toast.success('已拒绝邀请')
    }
    invitations.value = invitations.value.filter((x) => x.id !== inv.id)
  } finally {
    inviteBusy.value = false
  }
}

// ---- 新建 / 编辑 ----
function openCreate() {
  editing.value = { mode: 'create', form: { name: '', description: '', isPublic: 0 } }
}
function openEdit(kb) {
  editing.value = { mode: 'edit', id: kb.id, form: { name: kb.name, description: kb.description, isPublic: kb.isPublic ?? 0 } }
}

async function handleSave() {
  const f = editing.value.form
  if (!f.name.trim()) { toast.error('请输入知识库名称'); return }
  saving.value = true
  try {
    if (editing.value.mode === 'create') {
      await createKnowledge({ name: f.name.trim(), description: f.description.trim(), isPublic: f.isPublic })
      toast.success('知识库已创建')
    } else {
      await updateKnowledge({ id: editing.value.id, name: f.name.trim(), description: f.description.trim(), isPublic: f.isPublic })
      toast.success('已保存')
    }
    editing.value = null
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(kb) {
  if (!window.confirm(`确定删除「${kb.name}」吗？其文档将一并删除，可在回收站恢复（下一迭代上线）。`)) return
  await deleteKnowledge(kb.id)
  toast.success('已删除')
  load()
}

async function handleFav(kb) {
  if (kb.isFavorite) {
    await unfavoriteKnowledge(kb.id)
    toast.success('已取消收藏')
  } else {
    await favoriteKnowledge(kb.id)
    toast.success('已收藏')
  }
  load()
}

async function onCoverChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  const url = await uploadCover(file)
  if (editing.value) editing.value.form.cover = url
  toast.success('封面已上传')
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>我的知识库</h2>
        <div class="sub">全部（{{ list.length }}） · 拥有（{{ list.filter((x) => x.isOwner).length }}）</div>
      </div>
      <div class="head-tools">
        <div class="x-input search-box">
          <span class="prefix"><XIcon name="search" /></span>
          <input v-model="keyword" placeholder="搜索知识库…" />
        </div>
        <XButton @click="openCreate"><XIcon name="plus" :size="14" /> 新建知识库</XButton>
      </div>
    </div>

    <!-- 邀请横幅：>1 条时折叠为一条，下拉打开详情弹窗逐条处理 -->
    <div v-if="invitations.length" class="invite">
      <span class="idot" />
      <span class="iline" />
      <span class="inv-text">
        <b>{{ invitations[0].inviterName || invitations[0].inviter || '有人' }}</b>
        邀请你加入知识库「{{ invitations[0].knowledgeName || '未命名' }}」，角色为
        <b>{{ invitations[0].role === 'admin' ? '管理员' : '成员' }}</b>
        <span v-if="invitations.length > 1" class="inv-more">+{{ invitations.length - 1 }}</span>
      </span>
      <button class="inv-trigger" @click="inviteOpen = true">
        <XIcon name="arrowDown" :size="13" /> 全部邀请
      </button>
    </div>

    <XModal v-if="inviteOpen" title="协作邀请" @close="inviteOpen = false">
      <template #head-extra>
        <span class="x-tag blue">{{ invitations.length }} 条待处理</span>
      </template>
      <div v-for="inv in invitations" :key="inv.id" class="inv-row">
        <span class="inv-avatar">{{ (inv.inviterName || inv.inviter || '?').charAt(0) }}</span>
        <div class="inv-info">
          <b>{{ inv.inviterName || inv.inviter || '有人' }}</b>
          邀请你加入知识库<span class="inv-kb">「{{ inv.knowledgeName || '未命名' }}」</span>
          <span class="x-tag" :class="inv.role === 'admin' ? 'warn' : 'mint'">{{ inv.role === 'admin' ? '管理员' : '成员' }}</span>
        </div>
        <span class="inv-ops">
          <XButton size="sm" :loading="inviteBusy" @click="handleInvite(inv, 'accept')">接受</XButton>
          <XButton size="sm" type="soft" :disabled="inviteBusy" @click="handleInvite(inv, 'reject')">拒绝</XButton>
        </span>
      </div>
      <div class="inv-foot">处理完成后导航角标同步减少 · 点击弹窗外空白处或按 Esc 关闭</div>
    </XModal>

    <div v-if="loading" class="loading-tip">加载中…</div>

    <XEmpty
      v-else-if="filtered.length === 0 && list.length === 0"
      title="还没有任何知识库"
      sub="上传资料，自动解析、切分、向量化，几分钟织出你的第一张知识网络。"
      action-text="立即创建第一个知识库"
      @action="openCreate"
    />
    <XEmpty
      v-else-if="filtered.length === 0"
      title="没有匹配的知识库"
      sub="换个关键词试试。"
    />

    <div v-else class="kb-grid">
      <div v-for="kb in filtered" :key="kb.id" class="x-card kb-card" @click="openEdit(kb)" title="点击编辑信息（仅拥有者）">
        <div class="kb-cover">
          <img v-if="kb.cover" :src="kb.cover" alt="" class="kb-cover-img" />
          <template v-else>
            <span class="node" style="left:16px; top:20px; width:9px; height:9px" />
            <span class="node" style="left:52px; top:36px; width:6px; height:6px; background: var(--blue)" />
            <span class="node" style="right:58px; top:26px; width:7px; height:7px; background: var(--bright)" />
            <svg viewBox="0 0 300 96" preserveAspectRatio="none">
              <g style="stroke: var(--dot)" stroke-width="1" opacity=".55">
                <line x1="20" y1="24" x2="56" y2="40" /><line x1="56" y1="40" x2="238" y2="30" />
                <line x1="238" y1="30" x2="272" y2="52" />
              </g>
            </svg>
            <span v-if="kb.name" class="cover-letter">{{ kb.name.charAt(0) }}</span>
          </template>
          <button class="fav" :title="kb.isFavorite ? '取消收藏' : '收藏'" @click.stop="handleFav(kb)">
            <XIcon name="star" :size="15" :style="{ fill: kb.isFavorite ? '#E8B93C' : 'var(--dot)' }" />
          </button>
        </div>
        <div class="kb-body">
          <div class="kb-head-row">
            <span class="st" :class="kb.docCount ? 'ok' : 'pend'" />
            <b class="kb-name">{{ kb.name }}</b>
            <span class="x-tag" :class="kb.isPublic === 1 ? 'mint' : 'gray'" style="margin-left: auto">
              {{ kb.isPublic === 1 ? '公开' : '私有' }}
            </span>
          </div>
          <p class="kb-desc">{{ kb.description || '暂无知识库简介描述' }}</p>
          <div class="kb-meta">
            <span class="m-item"><XIcon name="view" />{{ kb.viewCount || 0 }}</span>
            <span class="m-item"><XIcon name="star" :style="{ fill: kb.isFavorite ? '#E8B93C' : 'currentColor' }" />{{ kb.favoriteCount || 0 }}</span>
            <span class="m-item"><XIcon name="folder" />{{ kb.docCount || 0 }} 文档</span>
            <span class="sep" />
            <span class="x-tag" :class="roleOf(kb) === 'member' ? 'blue' : 'mint'">{{ roleLabel[roleOf(kb)] }}</span>
          </div>
          <div class="kb-foot" @click.stop>
            <button class="kbf" @click="openEdit(kb)"><XIcon name="edit" :size="13" />编辑</button>
            <button v-if="kb.isOwner" class="kbf danger" @click="handleDelete(kb)"><XIcon name="delete" :size="13" />删除</button>
          </div>
        </div>
      </div>
    </div>

    <XModal
      v-if="editing"
      :title="editing.mode === 'create' ? '新建知识库' : '编辑知识库'"
      lock-mask
      @close="editing = null"
    >
      <div class="form-col">
        <label class="f-label">名称 <i>*</i></label>
        <XInput v-model="editing.form.name" placeholder="如：产品手册 v2" />
        <label class="f-label">封面</label>
        <div class="cover-line">
          <img v-if="editing.form.cover" :src="editing.form.cover" class="cover-prev" alt="" />
          <label class="x-btn plain sm cover-btn">
            <XIcon name="plus" :size="13" /> 上传封面
            <input type="file" accept="image/*" hidden @change="onCoverChange" />
          </label>
        </div>
        <label class="f-label">简介</label>
        <div class="x-input" style="height: auto; padding: 10px 14px">
          <textarea v-model="editing.form.description" rows="3" style="resize: vertical" placeholder="简要介绍该知识库包含哪些内容…" />
        </div>
        <label class="f-label">可见性</label>
        <div class="vis-row">
          <button class="vis-opt" :class="{ on: editing.form.isPublic === 0 }" @click="editing.form.isPublic = 0">私有</button>
          <button class="vis-opt" :class="{ on: editing.form.isPublic === 1 }" @click="editing.form.isPublic = 1">公开</button>
        </div>
      </div>
      <template #footer>
        <XButton type="plain" :disabled="saving" @click="editing = null">取消</XButton>
        <XButton :loading="saving" @click="handleSave">保存</XButton>
      </template>
    </XModal>
  </div>
</template>

<style scoped>
.head-tools { display: flex; gap: 10px; align-items: center; }
.search-box { width: 220px; height: 38px; border-radius: 999px; }

.invite {
  display: flex; align-items: center; gap: 12px;
  background: var(--blue-soft); border: 1px solid color-mix(in srgb, var(--blue) 28%, transparent);
  border-radius: 12px; padding: 11px 16px; margin-bottom: 20px;
  font-size: 13px; color: var(--blue-ink);
}
.idot { width: 10px; height: 10px; border-radius: 50%; background: var(--blue); box-shadow: 0 0 0 4px color-mix(in srgb, var(--blue) 15%, transparent); flex: none; }
.iline { width: 34px; height: 1.5px; background: repeating-linear-gradient(90deg, color-mix(in srgb, var(--blue) 55%, transparent) 0 5px, transparent 5px 9px); flex: none; }
.inv-text { min-width: 0; }
.inv-more { font-size: 11px; color: var(--blue-ink); background: var(--surface); border-radius: 999px; padding: 1px 8px; margin-left: 8px; }
.inv-trigger {
  margin-left: auto; display: inline-flex; align-items: center; gap: 6px; flex: none;
  font-size: 12px; font-weight: 600; color: var(--blue-ink); background: var(--surface);
  border-radius: 999px; padding: 0 12px; min-height: 28px; cursor: pointer;
}
.inv-trigger:hover { color: var(--blue); }

.inv-row { display: flex; align-items: center; gap: 12px; padding: 11px 0; }
.inv-row + .inv-row { border-top: 1px solid var(--line); }
.inv-avatar {
  width: 34px; height: 34px; border-radius: 50%; flex: none;
  background: linear-gradient(135deg, var(--green), var(--bright));
  color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600;
}
.inv-info { font-size: 13px; min-width: 0; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.inv-kb { color: var(--green-dark); font-weight: 600; }
.inv-ops { margin-left: auto; display: flex; gap: 8px; flex: none; }
.inv-foot { font-size: 11px; color: var(--muted); border-top: 1px dashed var(--mint-deep); padding-top: 10px; margin-top: 4px; }

.loading-tip { color: var(--muted); font-size: 13px; padding: 40px 0; text-align: center; }

.kb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 18px; }
.kb-card { overflow: hidden; cursor: pointer; transition: box-shadow .2s; }
.kb-card:hover { box-shadow: 0 6px 16px color-mix(in srgb, var(--green) 10%, transparent); }

.kb-cover { height: 92px; position: relative; background: linear-gradient(135deg, var(--mint), var(--cov1)); }
.kb-cover-img { width: 100%; height: 100%; object-fit: cover; position: absolute; inset: 0; }
.kb-cover svg { position: absolute; inset: 0; width: 100%; height: 100%; }
.kb-cover .node { position: absolute; border-radius: 50%; background: var(--green); }
.cover-letter {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 32px; font-weight: 700; color: var(--green-dark); opacity: .72;
}
.fav { position: absolute; top: 10px; right: 10px; width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: color-mix(in srgb, var(--surface) 70%, transparent); }
.fav:hover { background: var(--surface); }

.kb-body { padding: 13px 14px 14px; }
.kb-head-row { display: flex; align-items: center; gap: 8px; }
.kb-name { font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.kb-desc { font-size: 12px; color: var(--muted); line-height: 1.7; height: 40px; margin-top: 6px; overflow: hidden; }
.kb-meta { display: flex; align-items: center; gap: 10px; margin-top: 10px; font-size: 11px; color: var(--muted); }
.m-item { display: inline-flex; align-items: center; gap: 4px; }
.kb-meta .sep { flex: 1; height: 1px; background: repeating-linear-gradient(90deg, var(--line) 0 5px, transparent 5px 9px); }
.kb-foot { display: flex; gap: 8px; margin-top: 12px; border-top: 1px dashed var(--mint-deep); padding-top: 10px; }
.kbf {
  display: inline-flex; align-items: center; gap: 5px; font-size: 12px;
  color: var(--green-dark); background: var(--mint); border-radius: 999px;
  padding: 0 12px; min-height: 28px;
}
.kbf:hover { filter: brightness(.96); }
.kbf.danger { color: #C0453B; background: #FBEFEE; }

.form-col { display: flex; flex-direction: column; gap: 8px; }
.f-label { font-size: 13px; font-weight: 600; margin-top: 8px; }
.f-label i { color: var(--blue); font-style: normal; margin-left: 2px; }
.cover-line { display: flex; align-items: center; gap: 10px; }
.cover-prev { width: 72px; height: 44px; border-radius: 8px; object-fit: cover; }
.cover-btn { position: relative; overflow: hidden; }
.vis-row { display: flex; gap: 8px; }
.vis-opt {
  flex: 1; min-height: 34px; border-radius: 10px; font-size: 13px;
  border: 1.5px solid var(--line); color: var(--muted); background: var(--soft);
}
.vis-opt.on { border-color: var(--green); color: var(--green-dark); background: var(--mint); font-weight: 600; }
</style>
