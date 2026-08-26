<template>
  <div class="profile-page" v-loading="saving">
    <!-- 未登录兜底：直达 /profile 时引导去聊天页登录 -->
    <el-empty v-if="!authStore.isLogin" description="请先登录后编辑个人资料">
      <el-button type="primary" @click="router.push('/chat')">去登录</el-button>
    </el-empty>

    <div v-else class="profile-card">
      <h2 class="card-title">我的</h2>

      <!-- 头像：点击上传，支持预览 -->
      <div class="avatar-row">
        <el-upload :show-file-list="false" :http-request="handleAvatarUpload" accept="image/*">
          <div class="avatar-wrap">
            <el-avatar :size="96" class="avatar" :src="form.userAvatar || undefined">
              {{ avatarText }}
            </el-avatar>
            <div class="avatar-mask"><el-icon><Camera /></el-icon>更换头像</div>
          </div>
        </el-upload>
      </div>

      <el-form :model="form" label-width="70px" class="profile-form">
        <el-form-item label="账号">
          <el-input :model-value="authStore.user?.userAccount" disabled />
        </el-form-item>
        <el-form-item label="昵称" required>
          <el-input
            v-model="form.userName"
            maxlength="30"
            show-word-limit
            placeholder="请输入昵称"
          />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="form.userProfile"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="一句话介绍自己（可选）"
          />
        </el-form-item>
      </el-form>

      <div class="save-row">
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Camera } from "@element-plus/icons-vue";
import { uploadAvatar } from "../api/auth";
import { useAuthStore } from "../stores/auth";

const authStore = useAuthStore();
const router = useRouter();

const saving = ref(false);
const form = reactive({ userName: "", userProfile: "", userAvatar: "" });

const avatarText = computed(() => {
  const name = authStore.user?.userName || authStore.user?.userAccount || "?";
  return name.charAt(0).toUpperCase();
});

const syncFromUser = () => {
  form.userName = authStore.user?.userName || "";
  form.userProfile = authStore.user?.userProfile || "";
  form.userAvatar = authStore.user?.userAvatar || "";
};

// 上传头像：拿到 COS/本地可公网访问 URL 后写入表单，随「保存」一并提交
const handleAvatarUpload = async (options) => {
  try {
    const url = await uploadAvatar(options.file);
    form.userAvatar = url;
    ElMessage.success("头像上传成功，点击保存生效");
  } catch (err) {
    // 错误已由拦截器提示
  }
};

const handleSave = async () => {
  if (!form.userName.trim()) {
    ElMessage.warning("请输入昵称");
    return;
  }
  saving.value = true;
  try {
    await authStore.updateProfile({
      userName: form.userName.trim(),
      userAvatar: form.userAvatar || undefined,
      userProfile: form.userProfile.trim() || undefined,
    });
    ElMessage.success("保存成功");
    syncFromUser(); // 用刷新后的用户信息回填（如后端规范化后的昵称）
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  if (authStore.isLogin) syncFromUser();
});
</script>

<style scoped>
.profile-page {
  max-width: 560px;
  margin: 0 auto;
}
.profile-card {
  background: #fff;
  border-radius: 8px;
  padding: 28px 32px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.card-title {
  font-size: 20px;
  margin: 0 0 20px;
}
.avatar-row {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}
.avatar-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  line-height: 0;
}
.avatar {
  display: block;
  font-size: 36px;
  font-weight: 600;
  background: #409eff;
  color: #fff;
}
.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}
.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}
.profile-form {
  max-width: 420px;
  margin: 0 auto;
}
.save-row {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}
</style>
