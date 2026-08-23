-- ============================================================
-- 学智汇 数据库初始化脚本
-- 用法：
--   方式一（命令行）：mysql -uroot -p < sql/init.sql
--   方式二：在 Navicat / MySQL Workbench 中直接打开并执行
-- 说明：幂等，可重复执行
-- ============================================================

-- 1. 创建数据库（若不存在）
CREATE DATABASE IF NOT EXISTS `xuezhihui`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `xuezhihui`;

-- 2. 创建用户表（若不存在）
-- 如需重建，先手动执行：DROP TABLE IF EXISTS `user`;
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `userAccount`  varchar(256) NOT NULL COMMENT '账号',
    `userPassword` varchar(512) NOT NULL COMMENT '密码',
    `userName`     varchar(256) DEFAULT NULL COMMENT '用户昵称',
    `userAvatar`   varchar(1024) DEFAULT NULL COMMENT '用户头像',
    `userProfile`  varchar(512)  DEFAULT NULL COMMENT '用户简介',
    `userRole`     varchar(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
    `defaultVectorize` tinyint NOT NULL DEFAULT 1 COMMENT '上传文档是否默认入库: 1=是 0=否',
    `collapseRefs` tinyint NOT NULL DEFAULT 1 COMMENT '参考文献默认折叠: 1=折叠 0=展开',
    `editTime`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    `createTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_userAccount` (`userAccount`),
    KEY `idx_userName` (`userName`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户';

-- 3. 创建知识库表（若不存在）
CREATE TABLE IF NOT EXISTS `knowledge`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `name`        varchar(128) NOT NULL COMMENT '知识库名称',
    `description` varchar(512) DEFAULT NULL COMMENT '知识库简介',
    `cover`       varchar(1024) DEFAULT NULL COMMENT '知识库封面',
    `userId`      bigint       NOT NULL COMMENT '所属用户id',
    `createTime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_userId` (`userId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='知识库';

-- 4. 创建文档表（若不存在）
CREATE TABLE IF NOT EXISTS `knowledge_doc`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `knowledgeId`  bigint       NOT NULL COMMENT '所属知识库id',
    `name`         varchar(256) NOT NULL COMMENT '原始文件名',
    `fileUrl`      varchar(1024) DEFAULT NULL COMMENT '文件存储地址',
    `fileSize`     bigint       DEFAULT NULL COMMENT '文件大小(字节)',
    `fileType`     varchar(64)  DEFAULT NULL COMMENT '文件类型',
    `vectorStatus` varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态: PENDING/SUCCESS/FAILED/SKIPPED/REMOVED',
    `errorMsg`     varchar(512) DEFAULT NULL COMMENT '向量化失败原因',
    `fileHash`     varchar(64)  DEFAULT NULL COMMENT '文件内容SHA-256(上传去重用)',
    `deleteSource` varchar(16)  DEFAULT NULL COMMENT '删除来源: user=用户删除可自恢复, admin=管理员删除不可恢复且禁止上传',
    `createTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_knowledgeId` (`knowledgeId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='知识库文档';

-- 5. 创建对话会话表（若不存在）
-- 历史持久化到 MySQL（唯一事实源）；Redis 仅作 LLM 滚动上下文
-- 列名用 camelCase：项目 map-underscore-to-camel-case=false，MyBatis-Plus 按字段名原样映射
CREATE TABLE IF NOT EXISTS `chat_session`
(
    `sessionId`  varchar(64) NOT NULL COMMENT '会话id(UUID)',
    `userId`     bigint      NOT NULL COMMENT '所属用户id',
    `title`      varchar(64) NOT NULL COMMENT '会话标题(首条用户消息截断，Java 写入)',
    `createTime` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sessionId`),
    KEY `idx_user_update` (`userId`, `updateTime`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='对话会话';

-- 6. 创建对话消息表（若不存在）
CREATE TABLE IF NOT EXISTS `chat_message`
(
    `id`         bigint      NOT NULL COMMENT '消息id(雪花)',
    `sessionId`  varchar(64) NOT NULL COMMENT '会话id',
    `userId`     bigint      NOT NULL COMMENT '所属用户id',
    `role`       varchar(16) NOT NULL COMMENT '角色: user/assistant',
    `content`    text        NOT NULL COMMENT '消息内容',
    `createTime` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session` (`sessionId`),
    KEY `idx_user` (`userId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='对话消息';

-- 7. 初始管理员账号（幂等：账号已存在则跳过）
-- 默认账号：admin  默认密码：admin12345（登录后建议修改）
INSERT IGNORE INTO `user` (`userAccount`, `userPassword`, `userName`, `userRole`)
VALUES ('admin', '$2a$10$Db1RZ9oVxgAR4DDhoKY.2uv.OVB/BHkyCN88XCYxG2x1aQKbFLwzO', '管理员', 'admin');

-- 8. 兼容旧库：knowledge_doc 补 fileHash 列（已存在则跳过，幂等）
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_doc' AND COLUMN_NAME = 'fileHash');
SET @ddl = IF(@col = 0,
              'ALTER TABLE knowledge_doc ADD COLUMN fileHash varchar(64) DEFAULT NULL COMMENT ''文件内容SHA-256(上传去重用)''',
              'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 9. 兼容旧库：user 补 defaultVectorize 列（已存在则跳过，幂等）
SET @col2 = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'defaultVectorize');
SET @ddl2 = IF(@col2 = 0,
               'ALTER TABLE `user` ADD COLUMN defaultVectorize tinyint NOT NULL DEFAULT 1 COMMENT ''上传文档是否默认入库: 1=是 0=否''',
               'SELECT 1');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;

-- 10. 兼容旧库：knowledge_doc 补 deleteSource 列（已存在则跳过，幂等）
SET @col3 = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_doc' AND COLUMN_NAME = 'deleteSource');
SET @ddl3 = IF(@col3 = 0,
               'ALTER TABLE knowledge_doc ADD COLUMN deleteSource varchar(16) DEFAULT NULL COMMENT ''删除来源: user=用户删除可自恢复, admin=管理员删除不可恢复且禁止上传''',
               'SELECT 1');
PREPARE s3 FROM @ddl3; EXECUTE s3; DEALLOCATE PREPARE s3;

-- 11. 兼容旧库：user 补 collapseRefs 列（已存在则跳过，幂等）
SET @col4 = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'collapseRefs');
SET @ddl4 = IF(@col4 = 0,
               'ALTER TABLE `user` ADD COLUMN collapseRefs tinyint NOT NULL DEFAULT 1 COMMENT ''参考文献默认折叠: 1=折叠 0=展开''',
               'SELECT 1');
PREPARE s4 FROM @ddl4; EXECUTE s4; DEALLOCATE PREPARE s4;
