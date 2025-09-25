
-- 创建库
create database if not exists yu_ai_code_mother;

-- 切换库
use yu_ai_code_mother;

-- 用户表
-- 以下是建表语句

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 应用表
create table app
(
    id           bigint auto_increment comment 'id' primary key,
    appName      varchar(256)                       null comment '应用名称',
    cover        varchar(512)                       null comment '应用封面',
    initPrompt   text                               null comment '应用初始化的 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey    varchar(64)                        null comment '部署标识',
    deployedTime datetime                           null comment '部署时间',
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;

-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;


-- 应用版本表
CREATE TABLE `app_version`
(
    `id`            bigint auto_increment COMMENT 'id' PRIMARY KEY,
    `appId`         bigint                             NOT NULL COMMENT '应用id',
    `version`       int                                NOT NULL COMMENT '版本号，例如 1, 2, 3...',
    `content`       longtext                           NULL COMMENT '存储内容（全量代码或差异 patch）',
    `storageType`   varchar(10) DEFAULT 'full'         NOT NULL COMMENT '存储类型 (full, diff)',
    `message`       varchar(512)                       NULL COMMENT '版本说明，类似于 git commit message',
    `chatHistoryId` bigint                             NULL COMMENT '关联的对话id，用于追溯版本来源',
    `createTime`    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    `updateTime`    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`      tinyint  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY `uk_appId_version` (`appId`, `version`),
    INDEX `idx_appId` (`appId`),
    INDEX `idx_chatHistoryId` (`chatHistoryId`)
) COMMENT '应用版本' COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `app_version`
    ADD COLUMN `cover` varchar(512) NULL COMMENT '版本封面截图URL' AFTER `message`;
ALTER TABLE `app`
    ADD COLUMN `currentVersion` int NULL COMMENT '当前应用版本' ;
