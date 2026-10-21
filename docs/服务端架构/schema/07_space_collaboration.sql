-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 07 · 空间与协作域 (演进预留, 2 张表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策:
--   空间概念 (ai_space) 统一承载"个人空间"和"团队空间"
--   与 ai_team (团队) 以及 asset/project/script 等业务表采用**双轨并存**策略:
--     - 新表 ai_space + ai_space_collaborator 是空间统一模型 (演进版)
--     - ai_team + asset/project/script 原有的 user_id / team_id 字段**暂保留**
--     - 业务表新增 space_id + space_type (space_type=1个人/2团队) 指向空间
--     - 迁移期同时写两套字段, 等全部业务迁移完成后再清理旧字段
-- =============================================================================
-- 为什么不直接废弃 user_id / team_id 全换 space_id?
--   1. 现有数据 (user_id / team_id) 不需要立刻全量迁移, 先跑起来
--   2. 后端 Service 可以用 Adapter 模式同时支持新旧两种归属查找
--   3. 双轨期可以做 A/B 验证, 确认 space 模型解决了真实痛点再全量切换
-- =============================================================================
-- 表关系:
--   ai_space (空间统一实体) 1:N ai_space_collaborator (跨空间协作成员)
--   ai_space.team_id → ai_team (空间类型=2 团队空间时关联团队)
--   asset / script / project / generation 通过 space_id + space_type 挂靠到空间
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- 7.1 ai_space · 空间统一实体 (个人空间 + 团队空间)
-- -----------------------------------------------------------------------------
-- 空间是产品层的顶层容器概念, 比 team 更通用:
--   type=1 (personal): 对应 user_id (张三的个人空间, 一用户一空间)
--   type=2 (team):     对应 team_id (团队空间, 一个 team 一个空间, 但 team 表继续承载团队配置)
--
-- 空间表**不承载业务明细**, 只承载空间级配置:
--   - 空间身份 (owner / type / 关联的 user 或 team)
--   - 空间级默认值 (默认模型 / 默认分辨率 / 默认扣费策略 / 默认 Prompt)
--   - 空间级计费偏好 (billing_priority 等字段**迁移到这里**, team 上同名字段逐步废弃)
--   - 空间级功能开关 (是否允许外部协作 / 是否允许发布 / 是否允许 API 调用)
--
-- 迁移策略:
--   初期: ai_space 只对 team (type=2) 全量生效, personal 空间不创建 ai_space 记录
--         业务表 space_id/space_type=NULL, 查归属回退到 user_id/team_id
--   中期: personal 空间也创建 ai_space 记录, 业务表全部填 space_id
--   后期: 旧字段 user_id/team_id 在业务表中逐步废弃, 仅保留在 ai_space
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_space`;
CREATE TABLE `ai_space` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',

  -- ====== 空间身份 ======
  `space_name`             VARCHAR(100)     NOT NULL                                              COMMENT '空间名称 (展示给用户, 如 张三的空间 / 古风短剧组)',
  `space_code`             VARCHAR(64)      DEFAULT NULL                                          COMMENT '空间编码 (业务引用, URL slug)',
  `space_type`             TINYINT          NOT NULL                                              COMMENT '空间类型: 1=个人空间, 2=团队空间',
  `owner_user_id`          BIGINT UNSIGNED  NOT NULL                                              COMMENT '空间 owner 用户ID (个人空间=用户自己, 团队空间=team.leader_user_id)',
  `team_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联团队ID (仅 type=2 团队空间有值, 指向 ai_team.id, type=1 时为 NULL)',
  `avatar`                 VARCHAR(500)     DEFAULT NULL                                          COMMENT '空间头像',
  `cover_url`              VARCHAR(500)     DEFAULT NULL                                          COMMENT '空间封面图',
  `description`            VARCHAR(1000)    DEFAULT NULL                                          COMMENT '空间描述/简介',

  -- ====== 空间级默认值 (业务表未指定时使用空间默认) ======
  `default_model_id`       BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '默认视频模型ID (项目未指定时用这个)',
  `default_task_type`      VARCHAR(30)      DEFAULT 't2v'                                         COMMENT '默认任务类型',
  `default_resolution`     VARCHAR(20)      DEFAULT '1080p'                                       COMMENT '默认分辨率',
  `default_aspect_ratio`   VARCHAR(20)      DEFAULT '16:9'                                        COMMENT '默认宽高比',
  `default_fps`            INT              DEFAULT 24                                            COMMENT '默认帧率',
  `default_negative_prompt` VARCHAR(2000)   DEFAULT NULL                                          COMMENT '空间共享 Negative Prompt (所有项目默认继承)',
  `default_camera_movement` VARCHAR(30)     DEFAULT NULL                                          COMMENT '默认运镜方式 (storyboard shot 默认值)',

  -- ====== 空间级计费偏好 (**取代 ai_team 上的 billing_* 字段**) ======
  `billing_priority`       TINYINT          DEFAULT NULL                                          COMMENT '扣费优先级策略 (NULL=回退到 ai_team.billing_priority): 0=仅团队, 1=团队优先个人兜底, 2=仅个人, 3=个人优先团队兜底',
  `allow_mixed_billing`    TINYINT          DEFAULT NULL                                          COMMENT '是否允许分扣 (NULL=回退到 ai_team)',
  `mixed_billing_threshold` DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '分扣阈值',
  `billing_confirm_mode`   TINYINT          DEFAULT NULL                                          COMMENT '扣费前确认模式 (NULL=回退到 ai_team)',
  `billing_confirm_threshold` DECIMAL(14,4) DEFAULT NULL                                          COMMENT '扣费确认阈值',
  `billing_notify_member`  TINYINT          DEFAULT NULL                                          COMMENT '扣费通知成员开关',
  `billing_notify_threshold` DECIMAL(14,4)  DEFAULT NULL                                          COMMENT '扣费通知阈值',
  `budget_limit_month`     DECIMAL(16,4)   DEFAULT NULL                                          COMMENT '空间月度预算上限 (Credits, NULL=不限制, 统一承载 team.budget_limit + owner 自己的预算)',
  `budget_used_month`      DECIMAL(16,4)   NOT NULL DEFAULT 0                                    COMMENT '本月已使用预算',
  `budget_reset_time`      DATETIME(3)      DEFAULT NULL                                          COMMENT '下次重置时间',
  `budget_alert_percent`   INT              DEFAULT NULL                                          COMMENT '预算告警阈值 (NULL=80%)',

  -- ====== 空间级功能开关 ======
  `allow_asset_upload`     TINYINT          NOT NULL DEFAULT 1                                    COMMENT '成员是否允许上传素材',
  `allow_script_create`    TINYINT          NOT NULL DEFAULT 1                                    COMMENT '成员是否允许创建剧本',
  `allow_generate`         TINYINT          NOT NULL DEFAULT 1                                    COMMENT '成员是否允许发起视频生成',
  `allow_publish`          TINYINT          NOT NULL DEFAULT 0                                    COMMENT '成员是否允许发布成片',
  `allow_api_access`       TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否允许 API Key 调用 (空间级 API 访问开关)',
  `allow_external_collab`  TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否允许外部协作 (邀请非团队成员进空间临时工作)',
  `max_external_collaborators` INT UNSIGNED DEFAULT 0                               COMMENT '允许的外部协作者数量上限',
  `watermark_enabled`      TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否给所有输出视频加水印 (空间级开关)',
  `watermark_text`         VARCHAR(100)     DEFAULT NULL                                          COMMENT '水印文字',

  -- ====== 空间使用统计 (冗余聚合, 异步更新) ======
  `project_count`          INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '空间下分镜项目数',
  `script_count`           INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '空间下剧本数',
  `asset_count`            INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '空间下素材数',
  `generation_count_month` INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '本月视频生成次数',
  `generation_duration_month` INT UNSIGNED NOT NULL DEFAULT 0                                    COMMENT '本月累计生成时长 (秒)',
  `member_count`           INT UNSIGNED     NOT NULL DEFAULT 1                                    COMMENT '空间成员数 (owner + collaborators, 团队空间冗余自 team.member_count)',

  -- ====== 空间状态 ======
  `status`                 TINYINT          NOT NULL DEFAULT 1                                    COMMENT '状态: 0=禁用, 1=正常, 2=已归档, 3=迁移中',
  `visibility`             TINYINT          NOT NULL DEFAULT 0                                    COMMENT '可见性: 0=私有(仅成员可见), 1=租户内公开, 2=平台级公开',
  `tags_json`              JSON             DEFAULT NULL                                          COMMENT '空间标签 (JSON 数组, 如 ["古风", "短剧", "高价值客户"])',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展配置 (JSON: 主题/通知偏好/API Key 引用/集成配置)',

  -- ====== 迁移追踪 ======
  `migration_from_team_id` BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '迁移来源 team_id (历史追溯, 迁移完成后可保留)',
  `migration_status`       TINYINT          NOT NULL DEFAULT 0                                    COMMENT '迁移状态: 0=未迁移, 1=双轨并行, 2=已完成迁移, 3=回退中',
  `migration_finished_time` DATETIME(3)      DEFAULT NULL                                          COMMENT '迁移完成时间',

  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`               INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),

  -- ====== 唯一约束 ======
  UNIQUE KEY `uk_personal_per_user` (`tenant_id`, `space_type`, `owner_user_id`, `del_flag`)       COMMENT '个人空间: 一租户一用户只有一个 type=1 的空间',
  UNIQUE KEY `uk_team_per_space`    (`tenant_id`, `team_id`, `del_flag`)                            COMMENT '团队空间: 一租户一 team 只有一个 type=2 的空间',
  UNIQUE KEY `uk_space_code`        (`tenant_id`, `space_code`, `del_flag`),

  -- ====== 查询索引 ======
  KEY `idx_owner` (`owner_user_id`),
  KEY `idx_space_type` (`space_type`),
  KEY `idx_team` (`team_id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_budget_limit` (`budget_limit_month`, `budget_used_month`)                                    COMMENT '预算告警扫描',
  KEY `idx_migration_status` (`migration_status`)                                                      COMMENT '迁移进度扫描',

  CHECK (`space_type` IN (1,2)),
  CHECK (`billing_priority` IS NULL OR `billing_priority` IN (0,1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='空间统一实体 (演进预留, 统一承载个人空间 type=1 和团队空间 type=2, 业务表通过 space_id+space_type 双轨挂靠, 未来逐步取代 user_id/team_id 分散归属)';


-- -----------------------------------------------------------------------------
-- 7.2 ai_space_collaborator · 空间协作成员 (跨空间/个人外部协作者)
-- -----------------------------------------------------------------------------
-- 场景:
--   1. 外部协作 (ai_space.allow_external_collab=1):
--      张三是"古风"团队成员, 同时被邀请到"仙侠"团队空间临时工作 → 外部协作
--   2. 个人空间临时邀请:
--      张三在个人空间做项目, 需要让李四帮忙做一段分镜 → 临时邀请
--   3. 多空间身份:
--      一个用户可以同时是 N 个空间的成员 (owner/admin/member/viewer/external)
--      ai_team_member 继续承载团队空间内的正式成员身份, 此表承载**跨空间/临时**身份
--
-- 与 ai_team_member 的关系:
--   - type=2 (团队空间) + 正式成员: 两边都有记录 (ai_team_member 承载团队配置级信息, 此表承载空间协作级信息)
--   - type=1 (个人空间) + 临时邀请/共享: 只有此表有记录
--   - 外部协作者: 只有此表有记录, ai_team_member 没有
--
-- 一个用户在某个空间的最终权限 = max(此表权限, ai_team_member 权限), 或按更严格的来
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_space_collaborator`;
CREATE TABLE `ai_space_collaborator` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `space_id`               BIGINT UNSIGNED  NOT NULL                                              COMMENT '空间ID (ai_space.id)',
  `space_type`             TINYINT          NOT NULL                                              COMMENT '空间类型冗余 (1=个人, 2=团队, 避免每次 JOIN ai_space)',
  `user_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '协作用户ID',

  -- ====== 协作身份 ======
  `collab_role`            TINYINT          NOT NULL DEFAULT 4                                    COMMENT '协作角色: 1=owner(空间创建者, 只有1人), 2=admin(管理员), 3=member(成员), 4=viewer(只读), 5=external(外部协作者)',
  `is_owner`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否 owner (冗余, 加速判断, 一空间只有 1 人 is_owner=1)',
  `is_primary_member`      TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否空间正式成员 (永久): 0=外部/临时协作者, 1=正式成员',
  `is_team_member`         TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否也是 ai_team_member (冗余, 避免 JOIN 团队表判断)',

  -- ====== 协作来源 ======
  `invited_by`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '邀请人用户ID',
  `invite_reason`          VARCHAR(500)     DEFAULT NULL                                          COMMENT '邀请理由/备注',
  `invite_code`            VARCHAR(64)      DEFAULT NULL                                          COMMENT '邀请码 (接受邀请时校验, 一次性使用后清除)',
  `invited_at`             DATETIME(3)      DEFAULT NULL                                          COMMENT '邀请时间',
  `accepted_at`            DATETIME(3)      DEFAULT NULL                                          COMMENT '接受邀请时间',
  `expire_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '协作到期时间 (NULL=永久; 外部协作者建议设置 30-90 天)',

  -- ====== 协作状态 ======
  `status`                 TINYINT          NOT NULL DEFAULT 2                                    COMMENT '状态: 0=已移除, 1=已加入/生效, 2=待接受邀请, 3=已到期自动移除',
  `joined_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '实际加入时间',
  `removed_time`           DATETIME(3)      DEFAULT NULL                                          COMMENT '被移除时间 (status=0 时回填)',
  `removed_by`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '移除操作人ID',

  -- ====== 协作行为追踪 ======
  `projects_count`         INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '在本空间参与的项目数',
  `assets_count_uploaded`  INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '在本空间上传的素材数',
  `generations_count`      INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '在本空间发起的视频生成次数',
  `generations_duration_total` BIGINT UNSIGNED NOT NULL DEFAULT 0                               COMMENT '在本空间累计生成时长 (秒)',
  `cost_accumulated`       DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '在本空间累计消耗 (Credits, 聚合 usage_record)',
  `cost_month_used`        DECIMAL(14,4)   NOT NULL DEFAULT 0.0000                               COMMENT '本月在本空间消耗 (Credits, 每月重置)',
  `last_active_time`       DATETIME(3)      DEFAULT NULL                                          COMMENT '最后活跃时间 (生成视频/上传素材等)',
  `last_active_ip_hash`    VARCHAR(255)     DEFAULT NULL                                          COMMENT '最后活跃 IP 哈希 (安全审计)',

  -- ====== 协作偏好 ======
  `nickname_in_space`      VARCHAR(50)      DEFAULT NULL                                          COMMENT '在本空间内的昵称 (不同于账号昵称)',
  `notify_on_new_project`  TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否接收新项目通知',
  `notify_on_comment`      TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否接收评论通知',
  `notify_on_cost_threshold` TINYINT        NOT NULL DEFAULT 1                                    COMMENT '是否接收空间扣费超额通知',

  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展配置',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_space_user` (`space_id`, `user_id`, `del_flag`),
  KEY `idx_user` (`user_id`),
  KEY `idx_space_type` (`space_id`, `space_type`),
  KEY `idx_status` (`status`),
  KEY `idx_expire` (`expire_time`)                                                   COMMENT '到期协作者清理扫描',
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_collab_role` (`collab_role`),
  KEY `idx_owner` (`is_owner`),

  CHECK (`collab_role` IN (1,2,3,4,5)),
  CHECK (`space_type` IN (1,2)),
  CHECK (`status` IN (0,1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='空间协作成员 (跨空间协作 + 外部协作者 + 个人空间临时邀请, 与 ai_team_member 双轨并存; 正式团队成员两边都有记录, 外部/跨空间协作者只有此表记录)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 2 张表建表完成
-- ai_space 统一空间实体, 承载空间级配置和计费偏好
-- ai_space_collaborator 承载所有跨空间协作身份
-- =============================================================================
-- 双轨迁移策略:
--   1. 新表 ai_space 对所有新创建的 team 自动 INSERT 一条 type=2 的空间
--   2. 新用户首次进入系统时, 自动 INSERT 一条 type=1 的个人空间
--   3. 业务表新增 space_id + space_type 字段 (见 03/04 文件修改)
--   4. 后端 Service 层: 有 space_id 时优先查 space, 无则回退查 user_id/team_id
--   5. 定时迁移脚本: 把老数据的 user_id/team_id 补填 space_id
--   6. 全部迁移完成后, 业务表旧字段标记废弃但保留 3 个版本再物理删除
-- =============================================================================
