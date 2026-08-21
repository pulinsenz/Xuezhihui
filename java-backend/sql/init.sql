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
    `vectorStatus` varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态: PENDING/SUCCESS/FAILED',
    `errorMsg`     varchar(512) DEFAULT NULL COMMENT '向量化失败原因',
    `createTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_knowledgeId` (`knowledgeId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='知识库文档';
