<template>
  <div v-loading="loading">
    <div class="page-header">
      <div class="back-title">
        <el-button :icon="ArrowLeft" circle @click="goBack" />
        <div class="head-info">
          <div class="title-row">
            <img v-if="knowledge?.cover" :src="knowledge.cover" class="head-cover" alt="封面" />
            <div v-else class="head-cover head-cover-letter">{{ knowledge?.name?.charAt(0) }}</div>
            <div>
              <h2>{{ knowledge?.name || "知识库" }}</h2>
              <div class="meta-row">
                <el-avatar :size="18" class="author-avatar">{{ authorChar }}</el-avatar>
                <span class="author-name">{{ knowledge?.authorName || "未知用户" }}</span>
                <el-tag v-if="isOwner" size="small" type="success">我的</el-tag>
                <el-tag v-else-if="isMember" size="small" type="primary">协作者</el-tag>
                <el-tag v-if="knowledge?.isPublic === 1" size="small" type="success" effect="plain">公开</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">私有</el-tag>
                <span class="stat"><el-icon><View /></el-icon>{{ knowledge?.viewCount || 0 }}</span>
                <span class="stat"><el-icon><Star /></el-icon>{{ knowledge?.favoriteCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="loadDocs">刷新</el-button>
        <!-- 仅查看者：收藏 / 复制 -->
        <template v-if="!isOwner && !isMember">
          <el-button
            v-if="!knowledge?.isFavorite"
            type="warning"
            plain
            :icon="Star"
            @click="handleFavorite"
          >收藏</el-button>
          <el-button
            v-else
            type="warning"
            :icon="StarFilled"
            @click="handleUnfavorite"
          >已收藏</el-button>
          <el-button :icon="CopyDocument" @click="handleCopy">复制</el-button>
        </template>
        <!-- 作者/协作者：上传文档 -->
        <template v-if="canManage">
          <el-button
            type="primary"
            :icon="Upload"
            :loading="uploading"
            @click="uploadRef.click()"
          >上传文档</el-button>
          <input
            ref="uploadRef"
            type="file"
            class="hidden-input"
            @change="handleUpload"
          />
        </template>
        <!-- 仅作者：编辑 / 成员 / 删除 -->
        <template v-if="isOwner">
          <el-button :icon="Edit" @click="openEditDialog">编辑</el-button>
          <el-button :icon="UserFilled" @click="openMemberDialog">成员</el-button>
          <el-button type="danger" :icon="Delete" @click="handleDeleteKb">删除</el-button>
        </template>
      </div>
    </div>

    <el-alert
      v-if="knowledge?.description"
      :title="knowledge.description"
      type="info"
      :closable="false"
      class="desc-alert"
    />

    <template v-if="!loading">
      <!-- 作者/协作者：管理工具栏 -->
      <template v-if="canManage">
        <div class="docs-toolbar">
          <div class="filter-row">
            <span class="filter-label">是否入库</span>
            <el-segmented
              v-model="category"
              :options="categoryOptions"
              @change="loadDocs()"
            />
          </div>
          <div class="filter-row">
            <span class="filter-label">是否删除</span>
            <el-segmented
              v-model="deletedFilter"
              :options="deletedFilterOptions"
              @change="loadDocs()"
            />
          </div>
        </div>
        <div class="batch-toolbar">
          <el-button
            size="small"
            type="primary"
            :disabled="!selectedRows.length"
            :icon="RefreshRight"
            @click="handleBatchVectorize"
          >批量入库</el-button>
          <el-button
            size="small"
            :disabled="!selectedRows.length"
            :icon="RefreshLeft"
            @click="handleBatchRemoveVector"
          >批量移除入库</el-button>
          <el-button
            size="small"
            type="danger"
            :disabled="!selectedRows.length"
            :icon="Delete"
            @click="handleBatchDelete"
          >批量删除</el-button>
        </div>
      </template>

      <el-empty
        v-if="docs.length === 0"
        :description="canManage ? '暂无文档，点击右上角上传' : '该知识库暂无文档'"
      />

      <el-table
        v-else
        :data="docs"
        stripe
        class="doc-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="canManage" type="selection" width="45" />
        <el-table-column label="文件名" min-width="240">
          <template #default="{ row }">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{
              row.fileType || "未知"
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="向量化状态" width="150">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.vectorStatus === 'FAILED'"
              :content="row.errorMsg || '向量化失败'"
              placement="top"
            >
              <el-tag type="danger" size="small">失败</el-tag>
            </el-tooltip>
            <el-tag
              v-else-if="row.vectorStatus === 'SUCCESS'"
              type="success"
              size="small"
              >已入库</el-tag
            >
            <el-tooltip
              v-else-if="row.vectorStatus === 'SKIPPED'"
              :content="row.errorMsg || '重复文件'"
              placement="top"
            >
              <el-tag type="info" size="small">重复未入库</el-tag>
            </el-tooltip>
            <el-tag
              v-else-if="row.vectorStatus === 'REMOVED'"
              type="warning"
              size="small"
              >未入库</el-tag
            >
            <el-tag v-else type="warning" size="small">待处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.isDelete === 1 ? 'danger' : 'success'"
              size="small"
              effect="plain"
            >
              {{ row.isDelete === 1 ? "已删除" : "正常" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180">
          <template #default="{ row }">{{
            row.createTime?.replace("T", " ").slice(0, 19)
          }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="canManage ? 440 : 210">
          <template #default="{ row }">
            <!-- 已删除：用户删的可恢复，管理员删的锁定（外部查看者看不到已删除文档） -->
            <template v-if="row.isDelete === 1">
              <el-button
                v-if="row.deleteSource !== 'admin'"
                size="small"
                type="success"
                :loading="busyIds.includes(row.id)"
                @click="handleRestoreDoc(row)"
              >
                恢复
              </el-button>
              <el-tooltip
                v-else
                :content="'该文件已被管理员删除，无法恢复或重新上传'"
                placement="top"
              >
                <el-tag type="info" size="small">已被管理员删除</el-tag>
              </el-tooltip>
              <el-button
                size="small"
                type="danger"
                plain
                @click="handlePurgeDoc(row)"
                >删除</el-button
              >
            </template>
            <!-- 正常文档：向量详情 / 下载对所有可查看者开放 -->
            <template v-else>
              <el-button size="small" type="primary" plain @click="handleViewChunks(row)">向量详情</el-button>
              <el-button size="small" @click="handleDownloadDoc(row)">下载</el-button>
              <!-- 管理操作仅作者/协作者可见 -->
              <template v-if="canManage">
                <el-button
                  v-if="row.vectorStatus === 'SUCCESS'"
                  size="small"
                  type="warning"
                  plain
                  @click="handleRemoveVector(row)"
                >
                  移除入库
                </el-button>
                <el-button
                  v-if="row.vectorStatus === 'SKIPPED'"
                  size="small"
                  type="primary"
                  :loading="busyIds.includes(row.id)"
                  @click="handleReVectorize(row)"
                >
                  强制入库
                </el-button>
                <el-button
                  v-if="
                    row.vectorStatus === 'FAILED' ||
                    row.vectorStatus === 'PENDING' ||
                    row.vectorStatus === 'REMOVED'
                  "
                  size="small"
                  type="primary"
                  :loading="busyIds.includes(row.id)"
                  @click="handleReVectorize(row)"
                >
                  重新入库
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  @click="handleDeleteDoc(row)"
                  >禁用</el-button
                >
                <el-button
                  size="small"
                  type="danger"
                  plain
                  @click="handlePurgeDoc(row)"
                  >删除</el-button
                >
              </template>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 编辑知识库弹窗（仅作者） -->
    <el-dialog v-model="editDialogVisible" title="编辑知识库" width="440px">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面">
          <el-upload :show-file-list="false" :http-request="handleCoverUpload" accept="image/*">
            <img v-if="editForm.cover" :src="editForm.cover" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-placeholder"><el-icon><Plus /></el-icon>上传封面</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="可选" maxlength="512" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="editForm.isPublic" :active-value="1" :inactive-value="0" />
          <span class="switch-tip">公开后他人可浏览、收藏、复制</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 协作者管理弹窗（仅作者） -->
    <el-dialog v-model="memberDialogVisible" title="协作者管理" width="520px">
      <div class="invite-row">
        <el-input v-model="inviteAccount" placeholder="输入被邀请人的账号，回车发送消息" @keyup.enter="handleInvite" />
        <el-button type="primary" :loading="inviting" @click="handleInvite">发送邀请</el-button>
      </div>
      <el-table :data="members" v-loading="membersLoading" size="small">
        <el-table-column label="用户" min-width="140">
          <template #default="{ row }">
            <el-avatar :size="20" class="author-avatar">{{ (row.userName || row.userAccount || '?').charAt(0) }}</el-avatar>
            <span class="member-name">{{ row.userName || row.userAccount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userAccount" label="账号" min-width="120" />
        <el-table-column label="加入时间" width="160">
          <template #default="{ row }">{{ row.createTime?.replace("T", " ").slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="handleRemoveMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 向量详情弹窗：展示文档切片 -->
    <el-dialog
      v-model="chunkDialogVisible"
      :title="`向量详情 - ${chunkDocName}`"
      width="720px"
      top="6vh"
    >
      <div v-loading="chunkLoading">
        <el-empty v-if="!chunkLoading && chunks.length === 0" description="该文档暂无切片" />
        <div v-else class="chunk-list">
          <div v-for="(chunk, idx) in chunks" :key="idx" class="chunk-item">
            <div class="chunk-head">切片 {{ idx + 1 }}</div>
            <pre class="chunk-text">{{ chunk }}</pre>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ArrowLeft,
  CopyDocument,
  Delete,
  Document,
  Edit,
  Plus,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Star,
  StarFilled,
  Upload,
  UserFilled,
  View,
} from "@element-plus/icons-vue";
import {
  addMember,
  batchDeleteDocs,
  batchRemoveVector,
  batchVectorizeDoc,
  copyKnowledge,
  deleteDoc,
  deleteKnowledge,
  downloadDocFile,
  favoriteKnowledge,
  getDocChunks,
  getKnowledge,
  getTask,
  listDocs,
  listMembers,
  purgeDoc,
  reVectorizeDoc,
  removeDocVector,
  removeMember,
  restoreDoc,
  unfavoriteKnowledge,
  updateKnowledge,
  uploadCover,
  uploadDoc,
} from "../api/knowledge";

const route = useRoute();
const router = useRouter();
// 雪花 ID 超出 JS Number 安全整数范围，必须用字符串透传（后端已序列化为字符串）
const knowledgeId = route.params.id;

const knowledge = ref(null);
const docs = ref([]);
const loading = ref(false);
const uploading = ref(false);
const busyIds = ref([]); // 正在入库的文档 id，禁用按钮防重复提交
const selectedRows = ref([]); // 勾选的行，用于批量操作
const deletedFilter = ref(0); // 文档状态过滤：0=正常 1=已删除 null=全部
const category = ref("all"); // 向量状态分类：all=全部 vectorized=已入库 unvectorized=未入库
const categoryOptions = [
  { label: "全部", value: "all" },
  { label: "已入库", value: "vectorized" },
  { label: "未入库", value: "unvectorized" },
];
const deletedFilterOptions = [
  { label: "全部", value: null },
  { label: "正常", value: 0 },
  { label: "已删除", value: 1 },
];
const uploadRef = ref(null);
const pollTimers = new Set(); // 批量任务轮询定时器，组件卸载时统一清理
let pollTimer = null;

// 权限：作者/协作者可管理文档；作者另有知识库级操作
const isOwner = computed(() => knowledge.value?.isOwner === true);
const isMember = computed(() => knowledge.value?.isMember === true);
const canManage = computed(() => isOwner.value || isMember.value);
const authorChar = computed(() =>
  (knowledge.value?.authorName || "?").charAt(0).toUpperCase(),
);

// 编辑弹窗
const editDialogVisible = ref(false);
const editing = ref(false);
const editForm = reactive({ name: "", description: "", cover: "", isPublic: 0 });

// 成员弹窗
const memberDialogVisible = ref(false);
const members = ref([]);
const membersLoading = ref(false);
const inviteAccount = ref("");
const inviting = ref(false);

// 向量详情弹窗
const chunkDialogVisible = ref(false);
const chunkLoading = ref(false);
const chunkDocName = ref("");
const chunks = ref([]);

const loadDocs = async () => {
  loading.value = true;
  try {
    docs.value = await listDocs(
      knowledgeId,
      deletedFilter.value,
      category.value,
    );
  } finally {
    loading.value = false;
  }
};

const loadKnowledge = async () => {
  knowledge.value = await getKnowledge(knowledgeId);
};

// ---------- 查看者操作：收藏 / 复制 ----------

const handleFavorite = async () => {
  await favoriteKnowledge(knowledgeId);
  ElMessage.success("已收藏，可在「知识库」列表中查看");
  await loadKnowledge();
};

const handleUnfavorite = async () => {
  await unfavoriteKnowledge(knowledgeId);
  ElMessage.success("已取消收藏");
  await loadKnowledge();
};

const handleCopy = async () => {
  try {
    const newId = await copyKnowledge(knowledgeId);
    ElMessage.success("复制成功，你已成为副本作者，可自由编辑");
    router.push(`/knowledge/${newId}`);
  } catch (e) {
    // 错误已由拦截器提示
  }
};

// ---------- 作者操作：编辑信息 / 删除知识库 / 成员管理 ----------

const openEditDialog = () => {
  editForm.name = knowledge.value?.name || "";
  editForm.description = knowledge.value?.description || "";
  editForm.cover = knowledge.value?.cover || "";
  editForm.isPublic = knowledge.value?.isPublic === 1 ? 1 : 0;
  editDialogVisible.value = true;
};

const handleCoverUpload = async (options) => {
  const url = await uploadCover(options.file);
  editForm.cover = url;
  ElMessage.success("封面上传成功");
};

const handleEdit = async () => {
  if (!editForm.name.trim()) {
    ElMessage.warning("请输入知识库名称");
    return;
  }
  editing.value = true;
  try {
    await updateKnowledge({
      id: knowledgeId,
      name: editForm.name.trim(),
      description: editForm.description.trim() || undefined,
      cover: editForm.cover || undefined,
      isPublic: editForm.isPublic,
    });
    ElMessage.success("保存成功");
    editDialogVisible.value = false;
    await loadKnowledge();
  } finally {
    editing.value = false;
  }
};

const handleDeleteKb = async () => {
  await ElMessageBox.confirm(
    `确定删除知识库「${knowledge.value?.name}」吗？其下文档会一并删除。`,
    "删除确认",
    { type: "warning" },
  );
  await deleteKnowledge(knowledgeId);
  ElMessage.success("删除成功");
  router.push("/knowledge");
};

const openMemberDialog = async () => {
  memberDialogVisible.value = true;
  inviteAccount.value = "";
  await loadMembers();
};

const loadMembers = async () => {
  membersLoading.value = true;
  try {
    members.value = await listMembers(knowledgeId);
  } finally {
    membersLoading.value = false;
  }
};

const handleInvite = async () => {
  const account = inviteAccount.value.trim();
  if (!account) {
    ElMessage.warning("请输入被邀请人的账号");
    return;
  }
  inviting.value = true;
  try {
    await addMember(knowledgeId, { userAccount: account });
    ElMessage.success("消息已发送，等待对方接受");
    inviteAccount.value = "";
    await loadMembers();
  } finally {
    inviting.value = false;
  }
};

const handleRemoveMember = async (row) => {
  await ElMessageBox.confirm(
    `确定移除协作者「${row.userName || row.userAccount}」吗？移除后其将不能管理该知识库。`,
    "移除协作者",
    { type: "warning" },
  );
  await removeMember(knowledgeId, row.userId);
  ElMessage.success("已移除");
  await loadMembers();
};

// ---------- 文档管理（作者/协作者） ----------

const handleUpload = async (e) => {
  const file = e.target.files?.[0];
  e.target.value = "";
  if (!file) return;
  uploading.value = true;
  try {
    // 上传返回 taskId：向量化走 Redis 消息队列（Python worker 异步执行），前端轮询任务状态
    const taskId = await uploadDoc(knowledgeId, file);
    if (!taskId) {
      // 重复文件：已创建记录但默认未入库，提醒用户可强制入库
      ElMessage.warning(
        "该文件与已有文档内容相同，默认未入库；可点击「强制入库」",
      );
      await loadDocs();
      return;
    }
    ElMessage.success("上传成功，正在向量化...");
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      try {
        const task = await getTask(taskId);
        const done = task?.status === "SUCCESS" || task?.status === "FAILED";
        if (done) {
          clearInterval(pollTimer);
          await loadDocs();
          if (task.status === "FAILED") {
            ElMessage.warning("向量化失败：" + (task.message || "未知原因"));
          } else {
            ElMessage.success("向量化完成，已可检索");
          }
        }
      } catch (err) {
        // 任务状态查询失败（任务过期等）则停止轮询，依赖文档列表刷新兜底
        clearInterval(pollTimer);
        await loadDocs();
      }
    }, 1000);
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    uploading.value = false;
  }
};

// 强制入库（重复文件）/ 重新入库（失败、待处理）：重新提交向量化任务并轮询
const handleReVectorize = async (row) => {
  busyIds.value = [...busyIds.value, row.id];
  try {
    const taskId = await reVectorizeDoc(knowledgeId, row.id);
    ElMessage.success("已提交，正在向量化...");
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      try {
        const task = await getTask(taskId);
        const done = task?.status === "SUCCESS" || task?.status === "FAILED";
        if (done) {
          clearInterval(pollTimer);
          await loadDocs();
          if (task.status === "FAILED") {
            ElMessage.warning("向量化失败：" + (task.message || "未知原因"));
          } else {
            ElMessage.success("向量化完成，已可检索");
          }
        }
      } catch (err) {
        // 任务过期或查询失败则停止轮询，依赖列表刷新兜底
        clearInterval(pollTimer);
        await loadDocs();
      }
    }, 1000);
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    busyIds.value = busyIds.value.filter((id) => id !== row.id);
  }
};

// 移除入库：删除文档向量（保留文档记录），状态置为未入库，可重新入库
const handleRemoveVector = async (row) => {
  await ElMessageBox.confirm(
    `确定将文档「${row.name}」移出入库吗？将删除其向量，文档记录保留，之后可重新入库。`,
    "移除入库",
    { type: "warning" },
  );
  await removeDocVector(knowledgeId, row.id);
  ElMessage.success("已移出入库");
  loadDocs();
};

const handleSelectionChange = (rows) => {
  selectedRows.value = rows;
};

// 禁用单个文档（原「删除」：逻辑删除 + 删向量，可在「已删除」中恢复）
const handleDeleteDoc = async (row) => {
  await ElMessageBox.confirm(
    `确定禁用文档「${row.name}」吗？将把它移入「已删除」，可恢复。`,
    "禁用确认",
    { type: "warning" },
  );
  await deleteDoc(knowledgeId, row.id);
  ElMessage.success("已禁用");
  loadDocs();
};

// 彻底删除：逻辑删除 + 标记 purged，前端「已删除」列表不再展示、用户不可自恢复
const handlePurgeDoc = async (row) => {
  await ElMessageBox.confirm(
    `确定彻底删除文档「${row.name}」吗？将把它从知识库移除，不再显示，且无法自行恢复。`,
    "彻底删除",
    { type: "error" },
  );
  await purgeDoc(knowledgeId, row.id);
  ElMessage.success("已彻底删除");
  loadDocs();
};

// 批量移除入库：勾选的已入库文档删除向量、保留记录
const handleBatchRemoveVector = async () => {
  const ids = selectedRows.value.map((r) => r.id);
  if (!ids.length) return;
  await ElMessageBox.confirm(
    `确定将选中的 ${ids.length} 个文档移出入库吗？将删除其向量，文档记录保留。`,
    "批量移除入库",
    {
      type: "warning",
    },
  );
  const count = await batchRemoveVector(knowledgeId, ids);
  ElMessage.success(`已移除 ${count} 个文档的入库`);
  loadDocs();
};

// 批量删除文档（逻辑删除 + 删各文档向量，可恢复）
const handleBatchDelete = async () => {
  const ids = selectedRows.value.map((r) => r.id);
  if (!ids.length) return;
  await ElMessageBox.confirm(
    `确定删除选中的 ${ids.length} 个文档吗？将同时删除其向量，可在「已删除」中恢复。`,
    "批量删除",
    {
      type: "warning",
    },
  );
  const count = await batchDeleteDocs(knowledgeId, ids);
  ElMessage.success(`已删除 ${count} 个文档`);
  loadDocs();
};

// 批量入库：为所选文档提交向量化任务（已入库/已删除自动跳过），轮询至全部完成
const handleBatchVectorize = async () => {
  const ids = selectedRows.value.map((r) => r.id);
  if (!ids.length) return;
  await ElMessageBox.confirm(
    `确定为选中的 ${ids.length} 个文档提交入库吗？（已入库的会自动跳过）`,
    "批量入库",
    { type: "info" },
  );
  const taskIds = await batchVectorizeDoc(knowledgeId, ids);
  if (!taskIds?.length) {
    ElMessage.info("所选文档均已入库，无需重复入库");
    loadDocs();
    return;
  }
  ElMessage.success(`已提交 ${taskIds.length} 个文档入库，正在向量化...`);
  const failed = await pollTasks(taskIds);
  await loadDocs();
  if (failed.length) {
    ElMessage.warning(`${failed.length} 个文档向量化失败，请查看文档状态`);
  } else {
    ElMessage.success("批量入库完成");
  }
};

// 轮询多个任务直至全部结束，返回失败任务的消息列表
const pollTasks = (taskIds) =>
  new Promise((resolve) => {
    const remaining = new Set(taskIds);
    const failed = [];
    const timer = setInterval(async () => {
      for (const id of [...remaining]) {
        try {
          const task = await getTask(id);
          if (task?.status === "SUCCESS" || task?.status === "FAILED") {
            remaining.delete(id);
            if (task.status === "FAILED")
              failed.push(task.message || "未知原因");
          }
        } catch {
          // 任务过期或查询失败则视为结束，依赖列表刷新兜底
          remaining.delete(id);
        }
      }
      if (remaining.size === 0) {
        clearInterval(timer);
        pollTimers.delete(timer);
        resolve(failed);
      }
    }, 1000);
    pollTimers.add(timer);
  });

// 恢复用户自己删除的文档（管理员删除的已锁定不可恢复），恢复后重新入库
const handleRestoreDoc = async (row) => {
  await ElMessageBox.confirm(
    `确定恢复文档「${row.name}」吗？将重新向量化入库。`,
    "恢复确认",
    { type: "info" },
  );
  busyIds.value = [...busyIds.value, row.id];
  try {
    const taskId = await restoreDoc(knowledgeId, row.id);
    ElMessage.success("已恢复，正在重新入库...");
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      try {
        const task = await getTask(taskId);
        const done = task?.status === "SUCCESS" || task?.status === "FAILED";
        if (done) {
          clearInterval(pollTimer);
          await loadDocs();
          if (task.status === "FAILED") {
            ElMessage.warning("向量化失败：" + (task.message || "未知原因"));
          } else {
            ElMessage.success("恢复完成，已可检索");
          }
        }
      } catch (err) {
        // 任务过期或查询失败则停止轮询，依赖列表刷新兜底
        clearInterval(pollTimer);
        await loadDocs();
      }
    }, 1000);
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    busyIds.value = busyIds.value.filter((id) => id !== row.id);
  }
};

// ---------- 查看操作：打开 / 下载 / 向量详情（所有可查看者） ----------

// 后端异常会返回 Result JSON（Blob），解析后提示而非当文件处理
const blobErrorMessage = async (blob) => {
  if (!(blob instanceof Blob) || !blob.type.includes("application/json")) return null;
  try {
    const data = JSON.parse(await blob.text());
    return data && data.code !== 0 ? data.message || "文件操作失败" : null;
  } catch {
    return null;
  }
};

// 下载：拉取原始文件触发浏览器下载（保留原名）
const handleDownloadDoc = async (row) => {
  try {
    const blob = await downloadDocFile(knowledgeId, row.id);
    const err = await blobErrorMessage(blob);
    if (err) {
      ElMessage.error(err);
      return;
    }
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = row.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    // 错误已由拦截器提示
  }
};

// 向量详情：查询文档切片并弹窗展示
const handleViewChunks = async (row) => {
  if (row.vectorStatus !== "SUCCESS") {
    ElMessage.info("该文档未入库，暂无切片数据");
    return;
  }
  chunkDocName.value = row.name;
  chunks.value = [];
  chunkDialogVisible.value = true;
  chunkLoading.value = true;
  try {
    chunks.value = await getDocChunks(knowledgeId, row.id);
  } catch (e) {
    // 错误已由拦截器提示，保持空态
  } finally {
    chunkLoading.value = false;
  }
};

const formatSize = (bytes) => {
  bytes = Number(bytes);
  if (!bytes) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
};

const goBack = () => {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push(isOwner.value || isMember.value ? "/knowledge" : "/public-knowledge");
  }
};

onMounted(() => {
  loadKnowledge();
  loadDocs();
});
onBeforeUnmount(() => {
  clearInterval(pollTimer);
  pollTimers.forEach(clearInterval);
  pollTimers.clear();
});
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.back-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-title h2 {
  font-size: 20px;
}
.head-info {
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.head-cover {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}
.head-cover-letter {
  line-height: 56px;
  text-align: center;
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #66b1ff);
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: #606266;
}
.author-avatar {
  background: #909399;
  color: #fff;
  font-size: 11px;
  flex-shrink: 0;
}
.author-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #909399;
}
.desc-alert {
  margin-bottom: 16px;
}
.hidden-input {
  display: none;
}
.file-icon {
  margin-right: 6px;
  color: #409eff;
  vertical-align: middle;
}
.doc-table {
  margin-top: 4px;
}
.batch-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.docs-toolbar {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}
.cover-preview {
  width: 180px;
  height: 90px;
  object-fit: cover;
  border-radius: 6px;
  display: block;
}
.cover-placeholder {
  width: 180px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: #909399;
  font-size: 13px;
  cursor: pointer;
}
.switch-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.invite-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.member-name {
  margin-left: 6px;
  font-size: 13px;
}
.chunk-list {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chunk-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
}
.chunk-head {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.chunk-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
}
</style>
