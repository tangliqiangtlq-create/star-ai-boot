-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 03 · 内容创作域 ai_* (6 张表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策: Q1 独立 ai_asset_folder 支持多级目录 (用户高频操作)
-- =============================================================================
-- 表关系: ai_asset_folder (树形目录) 1:N ai_asset
--         ai_script 1:N ai_storyboard_project 1:N ai_storyboard_shot
--         ai_storyboard_shot 通过 references_json 引用 ai_asset
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- 3.1 ai_asset_folder · 素材文件夹 (独立表, 支持多级目录, Q1 采纳)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_asset_folder`;
CREATE TABLE `ai_asset_folder` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `space_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '**空间ID (演进预留, 指向 ai_space.id, 双轨并存, NULL 时回退查 user_id/team_id)**',
  `space_type`         TINYINT          DEFAULT NULL                                            COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `user_id`            BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '所属用户ID (NULL=团队共享文件夹)',
  `parent_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '父文件夹ID (0=根目录, 树形自关联)',
  `ancestor_ids`       VARCHAR(1000)    DEFAULT NULL                                            COMMENT '祖先路径链 (逗号分隔, 如 "0,5,12", 便于递归查询和删除父级时级联)',
  `library_type`       VARCHAR(30)      NOT NULL DEFAULT 'custom'                               COMMENT '文件夹所属素材库类型: character / scene / prop / frameasset / virtual / custom / mixed',
  `folder_name`        VARCHAR(100)     NOT NULL                                                COMMENT '文件夹名称 (租户内同级同级下唯一)',
  `folder_code`        VARCHAR(64)      DEFAULT NULL                                            COMMENT '文件夹编码 (业务引用用, 可选)',
  `description`        VARCHAR(500)     DEFAULT NULL                                            COMMENT '描述',
  `cover_url`          VARCHAR(500)     DEFAULT NULL                                            COMMENT '文件夹封面 (取子素材第一张图)',
  `is_public`          TINYINT          NOT NULL DEFAULT 0                                      COMMENT '可见范围: 0=私有(仅自己), 1=租户内共享, 2=平台级公开',
  `shared_with_team`   TINYINT          NOT NULL DEFAULT 0                                      COMMENT '是否团队共享 (user_id 团队场景扩展)',
  `child_count`        INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '直接子文件夹数量 (异步维护, 加速列表展示)',
  `asset_count`        INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '文件夹下素材数量 (含子文件夹, 异步维护)',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '同级排序号 (升序)',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展配置 (JSON, 如显示偏好/标签/颜色标记)',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_parent_name` (`tenant_id`, `parent_id`, `folder_name`, `del_flag`),          COMMENT '同租户同父级下名称唯一',
  KEY `idx_parent` (`parent_id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_library_type` (`library_type`),
  KEY `idx_ancestors` (`ancestor_ids`(255))                                               COMMENT '前缀索引, 查祖先链/子树',
  CONSTRAINT `chk_library_type` CHECK (`library_type` IN ('character','scene','prop','frameasset','virtual','custom','mixed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='素材文件夹 (独立表支持多级目录, 角色库/场景库/道具库/抽帧库/虚拟库/通用库 共用此表, 按 library_type 区分)';


-- -----------------------------------------------------------------------------
-- 3.2 ai_asset · 素材 (所有类型通用一张表, 图片/视频/音频/角色/场景/道具)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_asset`;
CREATE TABLE `ai_asset` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `space_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '**空间ID (演进预留, 指向 ai_space.id, 双轨并存)**',
  `space_type`         TINYINT          DEFAULT NULL                                            COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `user_id`            BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '所属用户ID (NULL=团队共享素材)',
  `folder_id`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '所属文件夹ID (ai_asset_folder.id, NULL=根目录)',
  `library_type`       VARCHAR(30)      NOT NULL DEFAULT 'custom'                               COMMENT '素材库类型 (与 folder.library_type 对齐): character / scene / prop / frameasset / virtual / custom / mixed',
  `asset_name`         VARCHAR(200)     NOT NULL                                                COMMENT '素材名称 (展示用)',
  `asset_code`         VARCHAR(64)      DEFAULT NULL                                            COMMENT '素材编码 (业务引用用, 可选, 如 CHAR-001 / SCENE-003)',
  `asset_type`         VARCHAR(30)      NOT NULL                                                COMMENT '素材大类: image / video / audio / document / template',
  `asset_subtype`      VARCHAR(30)      DEFAULT NULL                                            COMMENT '素材子类型 (在 asset_type=image 时存 character / scene / prop / frame 等细分)',
  `mime_type`          VARCHAR(100)     DEFAULT NULL                                            COMMENT 'MIME 类型 (如 image/png / video/mp4 / audio/wav)',
  `file_extension`     VARCHAR(20)      DEFAULT NULL                                            COMMENT '文件扩展名 (小写, 如 png / mp4 / wav)',
  `file_size_bytes`    BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '文件大小 (字节)',
  `duration_ms`        BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '时长 (毫秒, 仅 video / audio 有值)',
  `fps`                INT UNSIGNED     DEFAULT NULL                                            COMMENT '帧率 (仅 video 有值)',
  `width`              INT UNSIGNED     DEFAULT NULL                                            COMMENT '像素宽度 (image/video)',
  `height`             INT UNSIGNED     DEFAULT NULL                                            COMMENT '像素高度 (image/video)',
  `url`                VARCHAR(1000)    NOT NULL                                                COMMENT '内部访问 URL (MinIO/OSS 内部地址, 鉴权后返回给前端)',
  `thumbnail_url`      VARCHAR(1000)    DEFAULT NULL                                            COMMENT '缩略图 URL (前端列表用, 异步生成)',
  `preview_url`        VARCHAR(1000)    DEFAULT NULL                                            COMMENT '预览文件 URL (如视频 HLS 片段地址)',
  `storage_provider`   VARCHAR(30)      NOT NULL DEFAULT 'minio'                                COMMENT '存储供应商: minio / aliyun-s3 / aws-s3 / tencent-cos / qiniu / local',
  `storage_bucket`     VARCHAR(100)     DEFAULT NULL                                            COMMENT '存储桶名',
  `storage_key`        VARCHAR(500)     DEFAULT NULL                                            COMMENT '存储对象 Key (OSS / MinIO 的 object key)',
  `storage_etag`       VARCHAR(100)     DEFAULT NULL                                            COMMENT '存储层 ETag (用于完整性校验)',
  `volc_asset_id`      VARCHAR(255)     DEFAULT NULL                                            COMMENT '火山引擎原生素材 ID (asset:// / engine:// 协议, 仅火山素材有值)',
  `volc_asset_version` VARCHAR(64)      DEFAULT NULL                                            COMMENT '火山素材版本号',
  `placeholder_tag`    VARCHAR(64)      DEFAULT NULL                                            COMMENT 'Prompt 占位符标签 (如 <<<image_char_1>>> 中的 image_char_1, 占位符替换逻辑用)',
  `tags`               JSON             DEFAULT NULL                                            COMMENT '用户自定义标签 (JSON 数组, 如 ["科幻","室内","夜景"])',
  `face_count`         INT UNSIGNED     DEFAULT NULL                                            COMMENT '人脸数量 (仅角色素材有值, 用于自动检测角色图质量)',
  `face_embedding`     MEDIUMBLOB       DEFAULT NULL                                            COMMENT '人脸嵌入向量 (二进制, 用于角色相似度匹配/去重, 可选)',
  `embedding_id`       VARCHAR(64)      DEFAULT NULL                                            COMMENT '语义向量嵌入 ID (向量数据库引用, 用于语义搜索, 可选)',
  `checksum_sha256`    VARCHAR(128)     DEFAULT NULL                                            COMMENT '文件 SHA-256 校验和 (防篡改/秒传)',
  `usage_count`        INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '被引用次数 (分镜/剧本生成使用, 高频素材标记)',
  `status`             TINYINT          NOT NULL DEFAULT 0                                      COMMENT '状态: 0=处理中(上传/转码/生成中), 1=可用, 2=已损坏, 3=存储缺失, 4=已废弃',
  `processing_progress` INT              DEFAULT NULL                                            COMMENT '处理中进度 (0-100, 转码/生成时)',
  `processing_error_msg` VARCHAR(500)   DEFAULT NULL                                            COMMENT '处理失败错误信息',
  `is_temp`            TINYINT          NOT NULL DEFAULT 0                                      COMMENT '是否临时文件 (24h 后自动清理): 0=否, 1=是',
  `expire_time`        DATETIME(3)      DEFAULT NULL                                            COMMENT '过期时间 (临时文件清理/限时素材到期)',
  `uploaded_from`      VARCHAR(30)      DEFAULT NULL                                            COMMENT '上传来源: user-upload / ai-generated / volc-sync / import / api-call',
  `source_url`         VARCHAR(1000)    DEFAULT NULL                                            COMMENT '原始来源 URL (下载/同步场景, 保留溯源)',
  `is_public`          TINYINT          NOT NULL DEFAULT 0                                      COMMENT '可见范围: 0=私有(仅自己), 1=租户共享, 2=平台公开',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '排序号 (同级)',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展配置 (JSON: 拍摄参数/版权信息/授权期限)',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_folder` (`folder_id`),
  KEY `idx_library_type` (`library_type`),
  KEY `idx_asset_type` (`asset_type`),
  KEY `idx_tenant_folder_type` (`tenant_id`, `folder_id`, `asset_type`),
  KEY `idx_volc_asset_id` (`volc_asset_id`),                                              COMMENT '火山素材关联查找',
  KEY `idx_placeholder_tag` (`placeholder_tag`),                                           COMMENT 'Prompt 占位符替换时快速查找',
  KEY `idx_status` (`status`),
  KEY `idx_expire` (`expire_time`),                                                       COMMENT '定时清理临时文件',
  KEY `idx_checksum` (`checksum_sha256`)                                                  COMMENT '秒传/去重校验',
  CONSTRAINT `chk_asset_type` CHECK (`asset_type` IN ('image','video','audio','document','template'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='素材 (通用表, 一张表承载所有类型素材: 角色图/场景图/道具图/抽帧图/视频片段/音频/文件/模板, 按 asset_type + asset_subtype 区分)';


-- -----------------------------------------------------------------------------
-- 3.3 ai_script · AI 剧本
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_script`;
CREATE TABLE `ai_script` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `space_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '**空间ID (演进预留, 双轨并存)**',
  `space_type`         TINYINT          DEFAULT NULL                                            COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `user_id`            BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '所属用户ID (NULL=团队共享剧本)',
  `title`              VARCHAR(200)     NOT NULL                                                COMMENT '剧本标题',
  `summary`            VARCHAR(2000)    DEFAULT NULL                                            COMMENT '故事梗概 (不超过 2000 字)',
  `logline`            VARCHAR(500)     DEFAULT NULL                                            COMMENT '一句话描述 (elevator pitch)',
  `genre`              VARCHAR(50)      DEFAULT NULL                                            COMMENT '题材/类型 (可多选逗号分隔, 如 悬疑/爱情/古装/科幻)',
  `target_duration_sec` INT UNSIGNED    DEFAULT NULL                                            COMMENT '目标成片时长 (秒)',
  `target_style`       VARCHAR(50)      DEFAULT NULL                                            COMMENT '视觉风格 (如 写实/二次元/赛博朋克/中国风)',
  `target_aspect_ratio` VARCHAR(20)    DEFAULT '16:9'                                          COMMENT '目标宽高比',
  `target_resolution`  VARCHAR(20)      DEFAULT '1080p'                                         COMMENT '目标分辨率',
  `target_language`    VARCHAR(10)      NOT NULL DEFAULT 'zh-CN'                                COMMENT '剧本语言',
  `prompt_seed`        MEDIUMTEXT       DEFAULT NULL                                            COMMENT 'AI 生成原始 Prompt (用户输入给 AI 的初始提示)',
  `script_content`     LONGTEXT         NOT NULL                                                COMMENT '剧本正文内容 (JSON 格式推荐: 按场景+镜头结构化, 或 Markdown 纯文本)',
  `script_format`      VARCHAR(20)      NOT NULL DEFAULT 'json'                                 COMMENT '剧本格式: json (结构化, 推荐) / markdown / fountain',
  `word_count`         INT UNSIGNED     DEFAULT NULL                                            COMMENT '字数统计 (异步更新)',
  `char_count`         INT UNSIGNED     DEFAULT NULL                                            COMMENT '预估角色数量',
  `scene_count`        INT UNSIGNED     DEFAULT NULL                                            COMMENT '预估场景数量',
  `shot_count`         INT UNSIGNED     DEFAULT NULL                                            COMMENT '预估镜头数量',
  `estimated_budget`   DECIMAL(14,2)   DEFAULT NULL                                            COMMENT '预估总预算 (Credits, 分镜生成后回填)',
  `cover_url`          VARCHAR(500)     DEFAULT NULL                                            COMMENT '剧本封面 (AI 生成或上传)',
  `status`             TINYINT          NOT NULL DEFAULT 0                                      COMMENT '状态: 0=草稿, 1=已生成分镜, 2=已归档, 3=已删除',
  `shared_with_team`   TINYINT          NOT NULL DEFAULT 0                                      COMMENT '是否团队共享',
  `shared_with_tenant` TINYINT          NOT NULL DEFAULT 0                                      COMMENT '是否租户内公开',
  `version`            INT UNSIGNED     NOT NULL DEFAULT 1                                      COMMENT '版本号 (每次保存 +1)',
  `parent_version_id`  BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '上一个版本 ID (历史版本链, 支持版本回滚)',
  `latest_version_id`  BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '此剧本最新版本 ID (冗余, 加速查询)',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展信息 (JSON: 角色列表/场景列表/素材引用)',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_genre` (`genre`(50)),
  KEY `idx_parent_version` (`parent_version_id`),
  KEY `idx_latest_version` (`latest_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='AI 剧本 (支持 JSON 结构化或 Markdown 纯文本, 含版本历史链)';


-- -----------------------------------------------------------------------------
-- 3.4 ai_storyboard_project · 分镜项目 (时间线容器, 一个项目 = 一条时间线)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_storyboard_project`;
CREATE TABLE `ai_storyboard_project` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `space_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '**空间ID (演进预留, 双轨并存, 指向 ai_space.id)**',
  `space_type`         TINYINT          DEFAULT NULL                                            COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `user_id`            BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '所属用户ID',
  `team_id`            BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '所属团队ID (团队协作时绑定)',
  `script_id`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '关联剧本ID (NULL=独立分镜, 不从剧本生成)',
  `project_name`       VARCHAR(200)     NOT NULL                                                COMMENT '项目名称',
  `description`        VARCHAR(2000)    DEFAULT NULL                                            COMMENT '项目描述',
  `cover_url`          VARCHAR(500)     DEFAULT NULL                                            COMMENT '项目封面 (第一张镜头预览)',
  `aspect_ratio`       VARCHAR(20)      NOT NULL DEFAULT '16:9'                                 COMMENT '宽高比: 16:9 / 9:16 / 1:1 / 21:9',
  `resolution`         VARCHAR(20)      NOT NULL DEFAULT '1080p'                                COMMENT '默认分辨率: 720p / 1080p / 2K / 4K',
  `fps`                INT              NOT NULL DEFAULT 24                                     COMMENT '帧率',
  `default_model_id`   BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '默认视频模型ID (镜头未指定 model_id 时使用)',
  `default_channel_id` BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '默认通道ID (镜头未指定时自动选择)',
  `default_task_type`  VARCHAR(30)      DEFAULT 't2v'                                           COMMENT '默认任务类型',
  `default_negative_prompt` TEXT       DEFAULT NULL                                            COMMENT '全局 Negative Prompt (所有镜头共享, 可被镜头覆盖)',
  `timeline_json`      MEDIUMTEXT       DEFAULT NULL                                            COMMENT '时间线完整序列化 (JSON: 轨道配置/特效/转场/背景音乐/字幕, 编辑器序列化格式)',
  `total_duration_ms`  BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '时间线总时长 (毫秒, 聚合 shot.duration 计算, 异步更新)',
  `shot_count`         INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '镜头总数 (异步维护)',
  `video_count`        INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '已生成视频数',
  `completed_percent`  INT              NOT NULL DEFAULT 0                                      COMMENT '整体完成百分比 (0-100, 所有 shot.status 综合计算)',
  `budget_limit`       DECIMAL(14,2)   DEFAULT NULL                                            COMMENT '预算上限 (Credits, NULL=不限制)',
  `budget_used`        DECIMAL(14,2)   NOT NULL DEFAULT 0                                      COMMENT '已用预算 (异步聚合 shot.cost_actual)',
  `budget_warn_percent` INT             NOT NULL DEFAULT 80                                     COMMENT '预算告警阈值 (百分比, 如 80 表示用了 80% 就告警)',
  `status`             TINYINT          NOT NULL DEFAULT 0                                      COMMENT '状态: 0=草稿, 1=分镜完成, 2=部分生成, 3=全部生成, 4=合成中, 5=已发布',
  `shared_with_team`   TINYINT          NOT NULL DEFAULT 0                                      COMMENT '是否团队共享',
  `template_source_id` BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '来源模板ID (从模板创建时记录)',
  `created_from_batch` BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '由哪个批量任务创建 (ai_generation_batch.id)',
  `published_video_url` VARCHAR(1000)  DEFAULT NULL                                            COMMENT '合成发布后的成片 URL',
  `published_time`     DATETIME(3)      DEFAULT NULL                                            COMMENT '发布时间',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展信息 (JSON: 协作成员/权限配置/标签)',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_team` (`team_id`),
  KEY `idx_script` (`script_id`),
  KEY `idx_status` (`status`),
  KEY `idx_budget_limit` (`budget_limit`, `budget_used`),                                   COMMENT '预算告警扫描',
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4,5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='分镜项目 (时间线容器, 一条时间线 = 一个项目, 聚合多个 shot)';


-- -----------------------------------------------------------------------------
-- 3.5 ai_storyboard_shot · 分镜镜头 (内容创作域 + 视频生成域的交汇点)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_storyboard_shot`;
CREATE TABLE `ai_storyboard_shot` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `space_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '**空间ID (演进预留, 双轨并存, 冗余自 project.space_id, 加速扣费归属查询)**',
  `space_type`         TINYINT          DEFAULT NULL                                            COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `project_id`         BIGINT UNSIGNED  NOT NULL                                                COMMENT '所属分镜项目ID (外键 ai_storyboard_project.id)',
  `script_id`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '关联剧本ID (冗余, 便于直接按剧本查)',
  `shot_index`         INT              NOT NULL                                                COMMENT '镜头序号 (同一 project 内唯一, 从 1 开始, 时间线顺序)',
  `scene_number`       VARCHAR(30)      DEFAULT NULL                                            COMMENT '场景编号 (如 S01-003 / EP01-SC05)',
  `shot_title`         VARCHAR(200)     DEFAULT NULL                                            COMMENT '镜头标题 (简短描述, 如 女主走进破旧的书房)',
  `shot_type`          VARCHAR(30)      DEFAULT NULL                                            COMMENT '镜头类型: wide / medium / close-up / extreme-close / POV / over-shoulder / aerial / drone / tracking / dolly',
  `camera_movement`    VARCHAR(30)      DEFAULT NULL                                            COMMENT '运镜方式: static / pan / tilt / zoom-in / zoom-out / dolly-in / dolly-out / tracking / crane / handheld',
  `transition`         VARCHAR(30)      DEFAULT NULL                                            COMMENT '转场方式: cut / fade / dissolve / wipe / match-cut / j-cut / l-cut',
  `shot_duration_ms`   INT UNSIGNED     NOT NULL DEFAULT 3000                                   COMMENT '目标镜头时长 (毫秒)',
  `start_offset_ms`    BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '时间线起始偏移 (毫秒, 由编辑器写入, 动态值)',
  `end_offset_ms`      BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '时间线结束偏移 (毫秒)',
  `prompt_text`        TEXT             NOT NULL                                                COMMENT 'AI 生成用主 Prompt (已替换素材占位符后的完整文本, 传给 AI 厂商)',
  `prompt_negative`    TEXT             DEFAULT NULL                                            COMMENT 'Negative Prompt',
  `prompt_notes`       VARCHAR(2000)    DEFAULT NULL                                            COMMENT '导演备注 (不进入 Prompt, 仅给人类创作者看)',
  `prompt_style_ref`   VARCHAR(200)     DEFAULT NULL                                            COMMENT '风格参考描述 (如 电影感 / 90年代港片 / 吉卜力风格)',
  `references_json`    JSON             DEFAULT NULL                                            COMMENT '素材引用列表 (JSON 数组, 结构: [{"asset_id":1,"placeholder_tag":"image_char_1","asset_type":"image","role":"character"}])',
  `reference_image_url` VARCHAR(1000)   DEFAULT NULL                                            COMMENT '组合参考图预览 URL (前端展示, 所有素材拼接成一张图)',
  `model_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '指定视频模型 (NULL=用项目 default_model_id)',
  `channel_id`         BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '指定通道 (NULL=路由自动选择)',
  `task_type`          VARCHAR(30)      DEFAULT NULL                                            COMMENT '指定任务类型 (NULL=用项目默认)',
  `resolution`         VARCHAR(20)      DEFAULT NULL                                            COMMENT '指定分辨率 (NULL=用项目默认)',
  `aspect_ratio`       VARCHAR(20)      DEFAULT NULL                                            COMMENT '指定宽高比 (NULL=用项目默认)',
  `fps`                INT              DEFAULT NULL                                            COMMENT '指定帧率 (NULL=用项目默认)',
  `prompt_seed`        BIGINT           DEFAULT NULL                                            COMMENT '随机种子 (用于复现, NULL=每次随机)',
  `preview_image_url`  VARCHAR(1000)    DEFAULT NULL                                            COMMENT 'AI 生成的分镜预览图 (Storyboard Diffusion, 可选步骤)',
  `generation_id`      BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '关联视频生成记录 (ai_generation.id)',
  `batch_id`           BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '关联批量任务 (ai_generation_batch.id, 批量生成时回填)',
  `video_url`          VARCHAR(1000)    DEFAULT NULL                                            COMMENT '最终生成视频 URL (完成后回填)',
  `video_duration_ms`  INT UNSIGNED     DEFAULT NULL                                            COMMENT '实际视频时长 (可能 != shot_duration_ms)',
  `video_width`        INT UNSIGNED     DEFAULT NULL                                            COMMENT '视频实际宽度',
  `video_height`       INT UNSIGNED     DEFAULT NULL                                            COMMENT '视频实际高度',
  `video_file_size`    BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '视频文件大小',
  `video_format`       VARCHAR(20)      DEFAULT NULL                                            COMMENT '视频格式 (mp4 / mov)',
  `voiceover_text`     TEXT             DEFAULT NULL                                            COMMENT '旁白/台词文本 (TTS 生成输入)',
  `voiceover_audio_url` VARCHAR(1000)   DEFAULT NULL                                            COMMENT '旁白音频 URL (TTS 生成后回填)',
  `voiceover_model`    VARCHAR(100)     DEFAULT NULL                                            COMMENT '使用的 TTS 模型',
  `voiceover_duration_ms` INT UNSIGNED   DEFAULT NULL                                            COMMENT '旁白时长',
  `bgm_url`            VARCHAR(1000)    DEFAULT NULL                                            COMMENT '背景音乐 URL',
  `bgm_start_offset_ms` BIGINT UNSIGNED DEFAULT 0                                               COMMENT 'BGM 起始偏移 (毫秒, 截取片段)',
  `bgm_volume`         DECIMAL(3,2)    DEFAULT 1.00                                            COMMENT 'BGM 音量 (0.0-2.0, 相对于视频音轨)',
  `sfx_url`            VARCHAR(1000)    DEFAULT NULL                                            COMMENT '音效 URL',
  `subtitle_text`      MEDIUMTEXT       DEFAULT NULL                                            COMMENT '字幕文本 (SRT/VTT 格式, JSON 或纯文本)',
  `cost_estimate`      DECIMAL(12,4)   DEFAULT NULL                                            COMMENT '预估费用 (Credits, 生成前计算)',
  `cost_actual`        DECIMAL(12,4)   DEFAULT NULL                                            COMMENT '实际费用 (Credits, 生成完成后回填)',
  `status`             TINYINT          NOT NULL DEFAULT 0                                      COMMENT '状态: 0=草稿, 1=待生成, 2=生成中, 3=已生成, 4=已重生成, 5=已跳过, 6=生成失败',
  `status_changed_at`  DATETIME(3)      DEFAULT NULL                                            COMMENT '状态最后变更时间',
  `notes`              VARCHAR(2000)    DEFAULT NULL                                            COMMENT '排期备注/审核意见',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '同 shot_index 的微调排序 (插入镜头场景)',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展 (JSON: 标注/审核历史/评论)',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_shot_index` (`project_id`, `shot_index`, `del_flag`),                      COMMENT '同项目内序号唯一',
  KEY `idx_tenant_project` (`tenant_id`, `project_id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_script` (`script_id`),
  KEY `idx_generation` (`generation_id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_model` (`model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_budget` (`cost_estimate`, `cost_actual`),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4,5,6))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='分镜镜头 (内容创作域 + 视频生成域的核心交汇点, 一条记录 = 一个镜头 = 对应一个视频生成任务)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 6 张表建表完成
-- 依赖顺序: ai_asset_folder → ai_asset
--           ai_script → ai_storyboard_project → ai_storyboard_shot
-- =============================================================================
