-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 04 · 视频生成域 + 异步任务域 ai_* (8 张表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策:
--   Q5 独立 ai_generation_batch 表
--   Q6 ai_generation.task_id 单一外键 (不做 N:M)
--   Q14 ai_generation 使用 MySQL RANGE COLUMNS 按月分区, 主键含 create_time
-- =============================================================================
-- 表关系:
--   ai_generation_batch (批量) 1:N ai_generation
--   ai_generation 1:N ai_generation_file (多文件: 主视频/预览/缩略图/字幕)
--   ai_generation 1:1 ai_generation_param (完整请求快照, 单独表避免膨胀)
--   ai_task 1:N ai_task_step 1:N ai_task_log
--   ai_generation N:1 ai_channel / ai_model
--   ai_generation.task_id → ai_task.id
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- 4.1 ai_generation_batch · 视频生成批量任务 (Q5 采纳, 独立 batch 表)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_generation_batch`;
CREATE TABLE `ai_generation_batch` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `space_id`               BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '**空间ID (演进预留, 双轨并存)**',
  `space_type`             TINYINT          DEFAULT NULL                                          COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `user_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '发起人用户ID',
  `project_id`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联分镜项目ID (批量镜头生成时)',
  `batch_name`             VARCHAR(200)     NOT NULL                                              COMMENT '批量名称 (如 项目A-第一轮生成 / 张三的批量-001)',
  `description`            VARCHAR(1000)    DEFAULT NULL                                          COMMENT '描述',
  `trigger_type`           TINYINT          NOT NULL DEFAULT 1                                    COMMENT '触发方式: 1=用户手动点击, 2=脚本/定时任务, 3=API 调用, 4=模板一键生成',
  `total_count`            INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '本批总 generation 数量',
  `pending_count`          INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '待处理数量',
  `processing_count`       INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '执行中数量',
  `success_count`          INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '成功数量',
  `failed_count`           INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '失败数量',
  `cancelled_count`        INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '已取消数量',
  `partial_failed_count`   INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '部分成功部分失败时的失败数',
  `total_cost_estimate`    DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '预估总费用 (Credits)',
  `total_cost_actual`      DECIMAL(14,4)   NOT NULL DEFAULT 0                                    COMMENT '实际总费用 (Credits, 所有 generation.cost_actual 聚合)',
  `succeeded_at`           DATETIME(3)      DEFAULT NULL                                          COMMENT '全部成功时间',
  `failed_at`              DATETIME(3)      DEFAULT NULL                                          COMMENT '全部/部分失败时间',
  `status`                 TINYINT          NOT NULL DEFAULT 0                                    COMMENT '状态: 0=queued(等待入队), 1=running(执行中), 2=completed(全部成功), 3=partial_failed(部分成功), 4=all_failed(全部失败), 5=cancelled(已取消)',
  `progress_percent`       INT              NOT NULL DEFAULT 0                                    COMMENT '整体进度 (0-100, 成功+失败 / total)',
  `strategy_concurrency`   INT              NOT NULL DEFAULT 5                                    COMMENT '并发策略: 同时执行多少个 generation (避免打爆 AI 厂商)',
  `strategy_channel_prefer` JSON            DEFAULT NULL                                          COMMENT '通道偏好 (JSON, 如优先走哪些 channel_id, NULL=路由自动)',
  `strategy_retry_failed`  TINYINT         NOT NULL DEFAULT 0                                    COMMENT '是否自动重试失败的 generation: 0=不重试, 1=重试(最多3次)',
  `source_shot_ids`        JSON             DEFAULT NULL                                          COMMENT '来源镜头 ID 列表 (JSON 数组, 项目批量时记录哪些 shot)',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展 (JSON: 执行策略/参数覆盖/错误汇总)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_status` (`status`),
  KEY `idx_status_progress` (`status`, `progress_percent`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4,5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='视频生成批量任务 (一个 batch 下挂 N 个 generation, 支持进度页/取消整体/并发控制/失败重试策略)';


-- -----------------------------------------------------------------------------
-- 4.2 ai_generation · 视频生成记录 (核心业务主表, MySQL RANGE COLUMNS 按月分区)
-- -----------------------------------------------------------------------------
-- Q14 采纳: 使用 MySQL 8.4 RANGE COLUMNS (create_time) 分区
-- 分区键必须在主键中, 故 PRIMARY KEY 为 (id, create_time)
-- DROP PARTITION 是元数据操作, 1 秒内完成, 远快于 DELETE
-- 规划: 按月分区, 保留近 6 个月在线, 更早分区直接 DROP
-- 注意: MyBatis-Plus selectById(id) 需加 WHERE id = ? LIMIT 1 (避免跨分区扫描)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_generation`;
CREATE TABLE `ai_generation` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `space_id`               BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '**空间ID (演进预留, 双轨并存, 直接从 shot/project.space_id 冗余, 扣费归属查询一步搞定)**',
  `space_type`             TINYINT          DEFAULT NULL                                          COMMENT '**空间类型 (演进预留, 1=个人空间, 2=团队空间)**',
  `user_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '发起人用户ID',
  `team_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '所属团队ID (团队共享账户时)',
  `shot_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联分镜镜头ID (独立生成=NULL)',
  `batch_id`               BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联批量任务ID (ai_generation_batch.id)',
  `channel_id`             BIGINT UNSIGNED  NOT NULL                                              COMMENT '实际使用的 AI 通道ID (记录路由结果, 审计用)',
  `provider_id`            BIGINT UNSIGNED  NOT NULL                                              COMMENT '供应商ID (冗余自 channel, 加速查询)',
  `model_id`               BIGINT UNSIGNED  NOT NULL                                              COMMENT '使用的模型ID',
  `task_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联异步任务ID (ai_task.id, 99% 场景 1:1)',
  `generation_type`        TINYINT          NOT NULL                                              COMMENT '生成类型: 1=独立生成, 2=分镜镜头生成, 3=批量生成子任务, 4=工具链任务(超分/字幕擦除)',
  `task_type`              VARCHAR(30)      NOT NULL                                              COMMENT '任务类型 (与 ai_model.task_type 对齐): t2v / i2v / t2i / i2i / kf2v / inpainting / upscale / subtitle_erase / tts',
  `input_prompt`           TEXT             DEFAULT NULL                                          COMMENT '输入 Prompt (已替换素材占位符后的完整文本, 传给 AI 厂商)',
  `input_negative_prompt`  TEXT             DEFAULT NULL                                          COMMENT 'Negative Prompt',
  `input_text`             MEDIUMTEXT       DEFAULT NULL                                          COMMENT '纯文本输入 (工具链类/语音合成类任务, 无 Prompt)',
  `input_references_json`  JSON             DEFAULT NULL                                          COMMENT '输入参考素材列表 (JSON, 结构: [{"asset_id":1,"url":"...","role":"character","type":"image"}])',
  `input_params_json`      JSON             DEFAULT NULL                                          COMMENT '完整输入参数快照 (JSON, 构建厂商 payload 时的所有参数: 分辨率/时长/种子/步数/温度 等, 审计用)',
  `external_task_id`       VARCHAR(255)     DEFAULT NULL                                          COMMENT 'AI 厂商返回的任务ID (轮询/回调的 key, 如火山的 VideoGenTaskId)',
  `external_task_url`      VARCHAR(500)     DEFAULT NULL                                          COMMENT '厂商任务查询 URL (回调/Webhook 场景可用)',
  `external_status`        VARCHAR(50)      DEFAULT NULL                                          COMMENT '厂商侧原始状态 (如 RUNNING / SUCCESS / FAILED)',
  `external_error_code`    VARCHAR(128)     DEFAULT NULL                                          COMMENT '厂商原始错误码',
  `resolution_requested`   VARCHAR(20)      DEFAULT NULL                                          COMMENT '请求分辨率',
  `resolution_actual`      VARCHAR(20)      DEFAULT NULL                                          COMMENT '实际输出分辨率 (可能降级)',
  `output_format`          VARCHAR(20)      DEFAULT NULL                                          COMMENT '输出格式: mp4 / mov / png / jpg / wav',
  `output_url`             VARCHAR(1000)    DEFAULT NULL                                          COMMENT '主输出文件 URL (MinIO/OSS 内部地址)',
  `output_thumbnail_url`   VARCHAR(1000)    DEFAULT NULL                                          COMMENT '缩略图 URL',
  `output_preview_url`     VARCHAR(1000)    DEFAULT NULL                                          COMMENT '预览文件 URL (视频用, 通常是低码率 mp4 或 HLS)',
  `output_public_url`      VARCHAR(1000)    DEFAULT NULL                                          COMMENT '公开访问 URL (CDN 签名 URL, 带过期时间, 前端展示用)',
  `output_width`           INT UNSIGNED     DEFAULT NULL                                          COMMENT '实际输出宽度 (像素)',
  `output_height`          INT UNSIGNED     DEFAULT NULL                                          COMMENT '实际输出高度 (像素)',
  `output_duration_ms`     BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '实际输出时长 (毫秒)',
  `output_file_size_bytes` BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '输出文件大小 (字节)',
  `output_fps`             INT UNSIGNED     DEFAULT NULL                                          COMMENT '输出帧率',
  `output_checksum_sha256` VARCHAR(128)     DEFAULT NULL                                          COMMENT '输出文件 SHA-256',
  `status`                 TINYINT          NOT NULL DEFAULT 0                                    COMMENT '状态: 0=pending, 1=processing, 2=completed, 3=failed, 4=cancelled, 5=timeout, 6=retrying',
  `error_code`             VARCHAR(64)      DEFAULT NULL                                          COMMENT '内部错误码 (自拟, 如 ERR_PROVIDER_TIMEOUT / ERR_QUOTA_EXCEEDED)',
  `error_type`             VARCHAR(30)      DEFAULT NULL                                          COMMENT '错误类型: provider_error / network_error / quota_exceeded / validation_error / internal_error / timeout',
  `error_message`          VARCHAR(4000)    DEFAULT NULL                                          COMMENT '错误详细信息 (脱敏后, 生产环境截断)',
  `error_solved`           TINYINT          NOT NULL DEFAULT 0                                    COMMENT '错误是否已处理 (人工介入后标记): 0=未处理, 1=已忽略, 2=已补偿重试成功',
  `error_solved_by`        BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '处理人用户ID',
  `error_solved_time`      DATETIME(3)      DEFAULT NULL                                          COMMENT '处理时间',
  `progress_percent`       INT              NOT NULL DEFAULT 0                                    COMMENT '进度 (0-100, 轮询或回调时更新)',
  `progress_message`       VARCHAR(500)     DEFAULT NULL                                          COMMENT '进度描述 (如 "正在合成" / "等待厂商队列")',
  `billing_reserved`       DECIMAL(14,4)   NOT NULL DEFAULT 0                                    COMMENT '**预扣金额** (任务创建时从 billing.credits_frozen 冻结)',
  `billing_actual`         DECIMAL(14,4)   NOT NULL DEFAULT 0                                    COMMENT '**实扣金额** (完成后回填, 失败=0, 退款时可能负数)',
  `billing_status`         TINYINT          NOT NULL DEFAULT 0                                    COMMENT '结算状态: 0=未结算, 1=已结算(实扣), 2=已退款, 3=已预扣未结算',
  -- ====== 扣费归属记录 (记清楚这次到底扣了谁, 财务/用户对账时不迷糊) ======
  `billing_strategy_source` TINYINT         DEFAULT NULL                                          COMMENT '使用的扣费策略来源: 0=默认(非团队场景), 1=用户显式指定, 2=团队策略自动决策',
  `billing_bearer_mode`    TINYINT          DEFAULT NULL                                          COMMENT '实际扣费归属模式: 0=仅个人钱包, 1=仅团队钱包, 2=分扣(个人+团队)',
  `billing_billing_id`     BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '主扣费钱包 ai_billing.id (单笔扣费场景)',
  `billing_bearer_user_id` BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '扣费归属用户 (billing_billing_id 对应的 user_id, 冗余便于直接查)',
  `billing_bearer_team_id` BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '扣费归属团队 (billing_billing_id 对应的 team_id, 冗余便于直接查)',
  `billing_split_json`     JSON             DEFAULT NULL                                          COMMENT '分扣明细 (billing_bearer_mode=2 时必填: [{"billing_id":1,"amount":20},{"billing_id":2,"amount":10}])',
  `billing_split_transaction_nos` JSON      DEFAULT NULL                                          COMMENT '分扣时各笔流水号 (JSON 数组, 对应上面 split 的每个元素)',
  `billing_decision_trace` VARCHAR(2000)    DEFAULT NULL                                          COMMENT '扣费决策日志 (审计: 团队策略=1, 团队余额=0, 个人余额=500, 决策=回退到个人)',
  `billing_user_confirm`   TINYINT          NOT NULL DEFAULT 0                                    COMMENT '用户是否已确认扣费 (billing_confirm_mode 触发弹窗时: 0=未确认, 1=已确认)',
  `billing_confirm_time`   DATETIME(3)      DEFAULT NULL                                          COMMENT '用户确认时间',
  `started_time`           DATETIME(3)      DEFAULT NULL                                          COMMENT '任务开始执行时间 (进入 processing)',
  `completed_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '成功/失败完成时间',
  `cancelled_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '用户取消时间',
  `external_callback_time` DATETIME(3)      DEFAULT NULL                                          COMMENT '厂商 Webhook 回调到达时间',
  `provider_response_time_ms` INT UNSIGNED  DEFAULT NULL                                          COMMENT '厂商单次调用响应时间 (毫秒, 用于 SLA 监控)',
  `total_provider_calls`   INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '累计调用厂商 API 次数 (含重试)',
  `retry_count`            INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '已自动重试次数',
  `retry_limit`            INT UNSIGNED     NOT NULL DEFAULT 3                                    COMMENT '最大重试次数',
  `request_idempotency_key` VARCHAR(128)    DEFAULT NULL                                          COMMENT '请求幂等 Hash (SHA-256(prompt+model+references), 防重复提交)',
  `trace_id`               VARCHAR(64)      DEFAULT NULL                                          COMMENT '链路追踪 ID',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间 (**分区键, 必须在主键中**)',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`, `create_time`),
  UNIQUE KEY `uk_idempotency` (`request_idempotency_key`, `tenant_id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_tenant_user_status` (`tenant_id`, `user_id`, `status`),
  KEY `idx_shot` (`shot_id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_channel` (`channel_id`),
  KEY `idx_model` (`model_id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_external_task_id` (`external_task_id`),                                          COMMENT '厂商回调时反查',
  KEY `idx_billing_status` (`billing_status`),                                              COMMENT '账单对账扫描',
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4,5,6)),
  CONSTRAINT `chk_generation_type` CHECK (`generation_type` IN (1,2,3,4)),
  CONSTRAINT `chk_task_type` CHECK (`task_type` IN ('t2v','i2v','t2i','i2i','kf2v','inpainting','upscale','subtitle_erase','tts'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='视频生成记录 (核心业务主表, MySQL RANGE COLUMNS 按月分区, DROP PARTITION 秒级清理历史数据)'
  PARTITION BY RANGE COLUMNS (`create_time`) (
    PARTITION p2026_10 VALUES LESS THAN ('2026-11-01'),
    PARTITION p2026_11 VALUES LESS THAN ('2026-12-01'),
    PARTITION p2026_12 VALUES LESS THAN ('2027-01-01'),
    PARTITION p2027_01 VALUES LESS THAN ('2027-02-01'),
    PARTITION p2027_02 VALUES LESS THAN ('2027-03-01'),
    PARTITION p2027_03 VALUES LESS THAN ('2027-04-01'),
    PARTITION p_future  VALUES LESS THAN MAXVALUE
  );
-- 后续分区: ALTER TABLE ai_generation REORGANIZE PARTITION p_future INTO (PARTITION p2027_04 VALUES LESS THAN ('2027-05-01'), PARTITION p_future VALUES LESS THAN MAXVALUE);


-- -----------------------------------------------------------------------------
-- 4.3 ai_generation_file · 多文件 (主视频 / 预览 / 缩略图 / 字幕 / 音频 / 日志)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_generation_file`;
CREATE TABLE `ai_generation_file` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `generation_id`          BIGINT UNSIGNED  NOT NULL                                              COMMENT '所属 generation ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID (冗余, 避免 JOIN)',
  `file_type`              VARCHAR(30)      NOT NULL                                              COMMENT '文件类型: video(主视频) / preview(预览低码率) / thumbnail(缩略图) / subtitle(字幕文件) / audio(音轨/旁白) / log(厂商返回的日志) / original(原始未处理输出) / intermediate(中间产物)',
  `file_label`             VARCHAR(100)     DEFAULT NULL                                          COMMENT '文件标签 (展示用, 如 "原始4K输出" / "压缩后mp4")',
  `url`                    VARCHAR(1000)    NOT NULL                                              COMMENT '内部存储 URL',
  `public_url`             VARCHAR(1000)    DEFAULT NULL                                          COMMENT '公开访问 URL (CDN 签名)',
  `storage_provider`       VARCHAR(30)      DEFAULT NULL                                          COMMENT '存储供应商',
  `storage_bucket`        VARCHAR(100)     DEFAULT NULL                                          COMMENT '存储桶',
  `storage_key`            VARCHAR(500)     DEFAULT NULL                                          COMMENT '存储对象 Key',
  `mime_type`              VARCHAR(100)     DEFAULT NULL                                          COMMENT 'MIME',
  `file_extension`         VARCHAR(20)      DEFAULT NULL                                          COMMENT '扩展名',
  `file_size_bytes`        BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '文件大小',
  `duration_ms`            BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '时长 (video/audio)',
  `width`                  INT UNSIGNED     DEFAULT NULL                                          COMMENT '像素宽',
  `height`                 INT UNSIGNED     DEFAULT NULL                                          COMMENT '像素高',
  `fps`                    INT UNSIGNED     DEFAULT NULL                                          COMMENT '帧率',
  `bitrate_kbps`           INT UNSIGNED     DEFAULT NULL                                          COMMENT '码率',
  `checksum_sha256`        VARCHAR(128)     DEFAULT NULL                                          COMMENT 'SHA-256',
  `is_primary`             TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否主文件 (video 类型=主视频, 其他类型=子文件)',
  `is_public`              TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否允许公开访问',
  `sort_no`                INT              NOT NULL DEFAULT 0                                    COMMENT '同 generation 内排序',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_generation` (`generation_id`),
  KEY `idx_tenant_generation` (`tenant_id`, `generation_id`),
  KEY `idx_file_type` (`file_type`),
  CONSTRAINT `chk_file_type` CHECK (`file_type` IN ('video','preview','thumbnail','subtitle','audio','log','original','intermediate'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='视频生成多文件 (一条 generation 可产出多个文件: 主视频/预览/缩略图/字幕/音轨等)';


-- -----------------------------------------------------------------------------
-- 4.4 ai_generation_param · 完整请求快照 (单独表避免 generation 表膨胀)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_generation_param`;
CREATE TABLE `ai_generation_param` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `generation_id`          BIGINT UNSIGNED  NOT NULL                                              COMMENT '所属 generation ID (UNIQUE, 1:1)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `curl_command`           MEDIUMTEXT       DEFAULT NULL                                          COMMENT '等价 curl 命令 (运维调试用, 脱敏后)',
  `request_payload`        LONGTEXT         DEFAULT NULL                                          COMMENT '**完整请求体 JSON 原文** (传给 AI 厂商的最终 payload, 含 header/body/query, 排查问题用)',
  `request_signature`      VARCHAR(255)     DEFAULT NULL                                          COMMENT '请求签名摘要 (SHA-256, 防篡改审计)',
  `response_raw`           LONGTEXT         DEFAULT NULL                                          COMMENT '厂商原始响应 JSON (raw body, 审计用, 含错误详情)',
  `response_http_status`   SMALLINT UNSIGNED DEFAULT NULL                                        COMMENT '厂商响应 HTTP 状态码',
  `request_headers_masked` MEDIUMTEXT       DEFAULT NULL                                          COMMENT '请求头 (脱敏后, AK/SK 等敏感头已替换)',
  `response_headers_masked` MEDIUMTEXT      DEFAULT NULL                                          COMMENT '响应头 (脱敏后)',
  `request_sent_at`        DATETIME(3)      DEFAULT NULL                                          COMMENT '请求发送时间',
  `response_received_at`   DATETIME(3)      DEFAULT NULL                                          COMMENT '响应接收时间',
  `response_latency_ms`    INT UNSIGNED     DEFAULT NULL                                          COMMENT '单次请求耗时 (毫秒)',
  `is_polling_response`    TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否轮询的中间响应 (0=首次请求, 1=轮询中间态)',
  `poll_index`             INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '第几次轮询 (is_polling_response=1 时递增)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generation` (`generation_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='视频生成完整请求快照 (单独表存储大体积 payload/response, 避免 generation 表膨胀, 问题排查时 JOIN)';


-- -----------------------------------------------------------------------------
-- 4.5 ai_task · 异步任务 (持久化副本, Bull 队列只存轻量引用, 真正 payload 在本表)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_task`;
CREATE TABLE `ai_task` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `task_type`              VARCHAR(30)      NOT NULL                                              COMMENT '任务类型: generation / upscale / subtitle_erase / asset_import / script_generate / storyboard_generate / cleanup / notify / billing',
  `ref_type`               VARCHAR(30)      DEFAULT NULL                                          COMMENT '关联业务类型 (与 task_type 相同或更细, 如 generation 下的 t2v / i2v)',
  `ref_id`                 BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联业务 ID (ai_generation.id / ai_asset.id / ai_script.id 等)',
  `queue_name`             VARCHAR(100)     NOT NULL                                              COMMENT 'Bull 队列名 (如 video-generate / upscale / import-asset)',
  `queue_job_id`           VARCHAR(128)     DEFAULT NULL                                          COMMENT 'Bull Job ID (Redis 端的 jobId, 双向定位)',
  `worker_host`            VARCHAR(100)     DEFAULT NULL                                          COMMENT '执行此任务的 Worker 节点标识 (IP / hostname / pod name)',
  `worker_pid`             INT              DEFAULT NULL                                          COMMENT 'Worker 进程 PID',
  `payload_json`           MEDIUMTEXT       DEFAULT NULL                                          COMMENT 'Bull 入队时的原始 payload (JSON, Redis 丢了也能从 MySQL 恢复)',
  `status`                 TINYINT          NOT NULL DEFAULT 0                                    COMMENT '状态: 0=queued(等待Worker取), 1=processing(执行中), 2=completed(成功), 3=failed(最终失败), 4=cancelled, 5=timeout, 6=waiting_child(等待子任务完成)',
  `priority`               INT              NOT NULL DEFAULT 0                                    COMMENT '优先级 (数值越大越优先执行, Bull 支持)',
  `retry_count`            INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '已自动重试次数',
  `retry_limit`            INT UNSIGNED     NOT NULL DEFAULT 3                                    COMMENT '最大重试次数',
  `next_retry_time`        DATETIME(3)      DEFAULT NULL                                          COMMENT '下次重试时间 (失败退避, NULL=不重试)',
  `progress_percent`       INT              NOT NULL DEFAULT 0                                    COMMENT '进度 (0-100, Worker 运行中更新)',
  `progress_message`       VARCHAR(500)     DEFAULT NULL                                          COMMENT '进度描述',
  `started_time`           DATETIME(3)      DEFAULT NULL                                          COMMENT '开始执行时间',
  `completed_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '完成时间',
  `failed_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '最终失败时间',
  `timeout_at`             DATETIME(3)      DEFAULT NULL                                          COMMENT '超时截止时间 (超过后自动置 status=5)',
  `lock_until`             DATETIME(3)      DEFAULT NULL                                          COMMENT 'Worker 分布式锁到期 (防多实例重复消费同一任务, Redis 锁也有但这里留底)',
  `lock_owner`             VARCHAR(100)     DEFAULT NULL                                          COMMENT '当前持锁者 (worker_host)',
  `error_code`             VARCHAR(64)      DEFAULT NULL                                          COMMENT '内部错误码',
  `error_type`             VARCHAR(30)      DEFAULT NULL                                          COMMENT '错误类型',
  `error_message`          VARCHAR(4000)    DEFAULT NULL                                          COMMENT '错误信息 (脱敏)',
  `result_summary_json`    JSON             DEFAULT NULL                                          COMMENT '任务结果摘要 (不存大文件 URL, 只存关键统计: 成功数/失败数/耗时等)',
  `trace_id`               VARCHAR(64)      DEFAULT NULL                                          COMMENT '链路追踪 ID',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_queue_job` (`queue_name`, `queue_job_id`),
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_queue_status` (`queue_name`, `status`),
  KEY `idx_next_retry` (`next_retry_time`)                                               COMMENT '调度器扫描待重试任务',
  KEY `idx_timeout_at` (`timeout_at`),                                                     COMMENT '超时扫描',
  KEY `idx_ref` (`ref_type`, `ref_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4,5,6))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='异步任务持久化副本 (Bull 队列只存轻量 {task_id}, 真实 payload/状态全在 MySQL, Redis 重启后补偿扫描可恢复; 是整个任务系统的 Source of Truth)';


-- -----------------------------------------------------------------------------
-- 4.6 ai_task_step · 任务分步日志
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_task_step`;
CREATE TABLE `ai_task_step` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `task_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '所属 task ID',
  `step_index`             INT              NOT NULL                                              COMMENT '步骤序号 (同一 task 内从 0 或 1 递增)',
  `step_name`              VARCHAR(100)     NOT NULL                                              COMMENT '步骤名称 (如 call_provider_api / poll_status / download_result / upload_minio / post_billing)',
  `step_description`       VARCHAR(500)     DEFAULT NULL                                          COMMENT '步骤描述',
  `status`                 TINYINT          NOT NULL DEFAULT 0                                    COMMENT '步骤状态: 0=pending, 1=running, 2=success, 3=failed, 4=skipped',
  `started_time`           DATETIME(3)      DEFAULT NULL                                          COMMENT '开始时间',
  `completed_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '完成时间',
  `cost_ms`                INT UNSIGNED     DEFAULT NULL                                          COMMENT '耗时 (毫秒)',
  `request_summary`        VARCHAR(2000)    DEFAULT NULL                                          COMMENT '请求摘要 (如 POST /api/v3/video/generate, 不存完整 payload)',
  `response_summary`       VARCHAR(2000)    DEFAULT NULL                                          COMMENT '响应摘要 (HTTP 200, taskId=xxx, 不存完整 body)',
  `error_message`          VARCHAR(4000)    DEFAULT NULL                                          COMMENT '步骤错误信息',
  `retry_count`            INT              NOT NULL DEFAULT 0                                    COMMENT '此步骤重试次数',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展 (JSON: HTTP 状态码/厂商错误码/上下文)',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_task_index` (`task_id`, `step_index`),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='异步任务分步 (复杂任务拆多步, 每步一行, 让用户/运维看到"现在卡在哪一步")';


-- -----------------------------------------------------------------------------
-- 4.7 ai_task_log · Worker 运行时详细日志
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_task_log`;
CREATE TABLE `ai_task_log` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID, 或用 AUTO_INCREMENT + time 作为 log)',
  `task_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '所属 task ID',
  `step_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '所属 step ID (可选, 归属到具体步骤)',
  `log_level`              VARCHAR(10)      NOT NULL                                              COMMENT '日志级别: TRACE / DEBUG / INFO / WARN / ERROR / FATAL',
  `log_message`            VARCHAR(4000)    NOT NULL                                              COMMENT '日志内容',
  `log_time`               DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '日志时间',
  `worker_host`            VARCHAR(100)     DEFAULT NULL                                          COMMENT '产生此日志的 Worker',
  `extra_json`             JSON             DEFAULT NULL                                          COMMENT '结构化上下文 (JSON: HTTP 状态码/厂商错误码/耗时/线程名)',
  PRIMARY KEY (`id`),
  KEY `idx_task_time` (`task_id`, `log_time`),
  KEY `idx_step` (`step_id`),
  KEY `idx_level` (`log_level`),
  KEY `idx_time` (`log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Worker 运行时详细日志 (Worker 主动上报, 便于前端展示"实时日志流"和运维排查, 高频写入建议按月分区)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 7 张表建表完成 (generation + generation_file + generation_param + generation_batch + task + task_step + task_log)
-- 依赖顺序: generation_batch → generation → generation_file / generation_param
--           task → task_step → task_log
-- =============================================================================
-- ai_generation 已使用 MySQL 分区: DROP PARTITION p2026_10 秒级清理, 历史数据不单独归档
-- 未来可扩展: generation_file / generation_param / task_log 可同步按 create_time 分区
-- =============================================================================
