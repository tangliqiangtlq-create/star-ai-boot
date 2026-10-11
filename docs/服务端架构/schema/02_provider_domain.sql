-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 02 · AI 供应商域 ai_* (4 张表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策: Q8 单独 ai_asset_virtual_sync 放第 06 部分, 本文件只放 4 张核心表
-- =============================================================================
-- 表关系: ai_provider (平台级, 只读) 1:N ai_model (平台级, 只读)
--         ai_provider 1:N ai_channel (租户级, 可写, 存 AK/SK 加密凭证)
--         ai_channel N:M ai_model (通过 ai_channel_model 中间表, 控制通道可用哪些模型)
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- 2.1 ai_provider · AI 供应商 (平台级只读, 不允许租户修改)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_provider`;
CREATE TABLE `ai_provider` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `provider_code`          VARCHAR(64)      NOT NULL                                              COMMENT '供应商唯一编码: bytedance / aliyun / kling / openai / google / minimax / anthropic',
  `provider_name_cn`       VARCHAR(50)      NOT NULL                                              COMMENT '供应商中文名',
  `provider_name_en`       VARCHAR(50)      DEFAULT NULL                                          COMMENT '供应商英文名',
  `api_base_url`           VARCHAR(500)     NOT NULL                                              COMMENT 'API Base URL (如 https://ark.cn-beijing.volces.com/api/v3)',
  `auth_type`              TINYINT          NOT NULL                                              COMMENT '认证方式: 0=Bearer Token, 1=AK-SK HMAC签名, 2=OAuth 2.0 Client Credentials, 3=自定义',
  `default_http_method`    VARCHAR(10)      NOT NULL DEFAULT 'POST'                               COMMENT '默认 HTTP 方法 (POST 为主)',
  `default_content_type`   VARCHAR(100)     NOT NULL DEFAULT 'application/json'                  COMMENT '默认 Content-Type',
  `default_timeout_ms`     INT UNSIGNED     NOT NULL DEFAULT 60000                                COMMENT '默认请求超时 (毫秒, 60s)',
  `default_connect_timeout_ms` INT UNSIGNED  NOT NULL DEFAULT 10000                                COMMENT '默认连接超时 (毫秒, 10s)',
  `default_read_timeout_ms`   INT UNSIGNED  NOT NULL DEFAULT 60000                                COMMENT '默认读超时 (毫秒)',
  `default_retry`         TINYINT          NOT NULL DEFAULT 2                                    COMMENT '默认重试次数',
  `default_retry_interval_ms` INT UNSIGNED NOT NULL DEFAULT 1000                                 COMMENT '默认重试初始间隔 (毫秒, 指数退避)',
  `default_max_retry_interval_ms` INT UNSIGNED NOT NULL DEFAULT 10000                             COMMENT '最大重试间隔 (毫秒)',
  `retryable_http_codes`  VARCHAR(200)     DEFAULT NULL                                          COMMENT '可重试的 HTTP 状态码列表 (逗号分隔, 如 429,500,502,503,504)',
  `api_docs_url`           VARCHAR(500)     DEFAULT NULL                                          COMMENT '官方 API 文档地址',
  `website_url`            VARCHAR(500)     DEFAULT NULL                                          COMMENT '供应商官网',
  `logo_url`               VARCHAR(500)     DEFAULT NULL                                          COMMENT '供应商 Logo',
  `supports_sse`           TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否支持 SSE 流式响应: 0=否, 1=是',
  `supports_webhook`       TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否支持 Webhook 异步回调: 0=否, 1=是',
  `webhook_verify_type`    TINYINT          DEFAULT NULL                                          COMMENT 'Webhook 验证方式: 1=签名头, 2=HMAC, 3=自定义',
  `webhook_signature_header` VARCHAR(100)   DEFAULT NULL                                          COMMENT 'Webhook 签名 Header 名 (如 X-Signature)',
  `request_signature_algorithm` VARCHAR(50)  DEFAULT NULL                                          COMMENT '请求签名算法 (auth_type=1 时需要, 如 HmacSHA256)',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展配置 (JSON: 请求头模板/路径模板/响应解析规则/SSE 事件名映射/Webhook 路径)',
  `status`                 TINYINT          NOT NULL DEFAULT 1                                    COMMENT '状态: 0=禁用, 1=正常, 2=灰度测试中',
  `sort_no`                INT              NOT NULL DEFAULT 0                                    COMMENT '排序号 (升序, 平台显示顺序)',
  `remark`                 VARCHAR(500)     DEFAULT NULL                                          COMMENT '备注',
  `create_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '创建人ID',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '最后更新人ID',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`               INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_code` (`provider_code`, `del_flag`)                           COMMENT '供应商编码全局唯一',
  KEY `idx_status` (`status`),
  CONSTRAINT `chk_auth_type` CHECK (`auth_type` IN (0,1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='AI 供应商 (平台级只读配置, 由系统管理员维护, 租户不可修改)';


-- -----------------------------------------------------------------------------
-- 2.2 ai_model · AI 模型 (平台级只读, 一个 Provider 下多个 Model)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_model`;
CREATE TABLE `ai_model` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `provider_id`            BIGINT UNSIGNED  NOT NULL                                              COMMENT '所属供应商ID (外键 ai_provider.id)',
  `model_id`               VARCHAR(128)     NOT NULL                                              COMMENT '厂商侧模型ID (如 wan2.6-t2v / seedance-2-5 / kling-v1.6)',
  `model_name_cn`          VARCHAR(100)     NOT NULL                                              COMMENT '模型中文名 (展示用, 如 万相2.6 T2V)',
  `model_name_en`          VARCHAR(100)     DEFAULT NULL                                          COMMENT '模型英文名',
  `model_category`         VARCHAR(30)      NOT NULL                                              COMMENT '模型大类: text=大语言, image=图片生成, video=视频生成, audio=语音合成/识别, embedding=向量嵌入',
  `task_type`              VARCHAR(30)      NOT NULL                                              COMMENT '任务类型: t2v=文生视频, i2v=图生视频, t2i=文生图, i2i=图生图, kf2v=关键帧转视频, inpainting=局部重绘, upscale=超分辨率, subtitle_erase=字幕擦除, tts=语音合成',
  `task_name`              VARCHAR(50)      DEFAULT NULL                                          COMMENT '任务中文名 (展示用, 如 文生视频 / 图生视频)',
  `input_types`            JSON             DEFAULT NULL                                          COMMENT '支持的输入类型列表 (JSON 数组, 如 ["text"] / ["text","image"] / ["text","image","video"])',
  `output_types`           JSON             DEFAULT NULL                                          COMMENT '输出类型列表 (JSON 数组, 如 ["video"] / ["image"]) ',
  `default_resolution`     VARCHAR(20)      DEFAULT NULL                                          COMMENT '默认分辨率 (如 720p / 1080p / 2K / 4K)',
  `supported_resolutions`  JSON             DEFAULT NULL                                          COMMENT '支持的分辨率列表 (JSON 数组, 如 ["720p","1080p","2K","4K"])',
  `aspect_ratios`          JSON             DEFAULT NULL                                          COMMENT '支持的宽高比列表 (JSON 数组, 如 ["16:9","9:16","1:1"]) ',
  `max_duration_sec`       INT UNSIGNED     DEFAULT NULL                                          COMMENT '单条视频最大时长 (秒)',
  `min_duration_sec`       INT UNSIGNED     DEFAULT NULL                                          COMMENT '单条视频最小时长 (秒)',
  `duration_step_sec`      INT UNSIGNED     DEFAULT NULL                                          COMMENT '时长粒度 (秒, 如 5 表示必须 5 的倍数)',
  `max_reference_count`    INT UNSIGNED     DEFAULT NULL                                          COMMENT '最大参考素材数量 (多参考图支持, NULL=不支持多参考)',
  `supports_multi_ref`     TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否支持多参考图: 0=否, 1=是',
  `supports_asset_protocol` TINYINT        NOT NULL DEFAULT 0                                    COMMENT '是否支持火山 engine:// / asset:// 协议 (火山引擎系模型): 0=否, 1=是',
  `supports_negative_prompt` TINYINT        NOT NULL DEFAULT 1                                    COMMENT '是否支持 Negative Prompt: 0=否, 1=是',
  `supports_specific_frame` TINYINT         NOT NULL DEFAULT 0                                    COMMENT '是否支持关键帧指定 (kf2v): 0=否, 1=是',

  -- ====== G3: 调用模式 (之前靠硬编码判断, 现在模型自己声明) ======
  `call_mode`             TINYINT          NOT NULL DEFAULT 2                                    COMMENT '**调用模式**: 1=同步返回 (提交请求后直接返回结果, 如 SDXL 图生图/阿里 CosyVoice TTS), 2=异步轮询 (提交→返回 task_id→轮询, 大部分视频生成), 3=Webhook 回调 (提交后厂商完成时回调, 如火山 Ark/阿里百炼), 4=SSE 流式 (生成进度推流, 如部分 LLM)',
  `submit_method`         VARCHAR(10)      NOT NULL DEFAULT 'POST'                               COMMENT '提交请求 HTTP 方法: POST / GET',
  `submit_path_template`  VARCHAR(500)     DEFAULT NULL                                          COMMENT '提交路径模板 (如 /api/v3/contents/generations/tasks, NULL=用 provider 默认)',
  `poll_path_template`    VARCHAR(500)     DEFAULT NULL                                          COMMENT '轮询路径模板 (如 /api/v3/contents/generations/tasks/{task_id}, {task_id} 占位符)',
  `result_path_template`  VARCHAR(500)     DEFAULT NULL                                          COMMENT '结果查询路径模板 (轮询时用于拉取最终结果)',
  `poll_interval_sec_min` INT UNSIGNED     NOT NULL DEFAULT 5                                    COMMENT '最小轮询间隔 (秒)',
  `poll_interval_sec_max` INT UNSIGNED     NOT NULL DEFAULT 30                                   COMMENT '最大轮询间隔 (秒, 指数退避上限)',
  `poll_timeout_sec`      INT UNSIGNED     DEFAULT NULL                                          COMMENT '轮询超时上限 (秒, NULL=用 estimated_duration_sec × 3)',
  `poll_backoff_type`     TINYINT          NOT NULL DEFAULT 1                                    COMMENT '轮询退避策略: 1=固定间隔, 2=线性递增, 3=指数退避, 4=厂商推荐间隔(由轮询响应里的 retry_after 控制)',
  `result_extract_path`   VARCHAR(300)     DEFAULT NULL                                          COMMENT '从轮询响应 JSON 中提取"结果 URL/ID"的 JSONPath (如 $.output[0].url / $.data.video_url)',
  `status_extract_path`   VARCHAR(300)     DEFAULT NULL                                          COMMENT '从轮询响应 JSON 中提取"任务状态"的 JSONPath (如 $.status / $.data.state)',
  `status_success_value`  VARCHAR(50)      DEFAULT NULL                                          COMMENT '任务完成时 status 字段的值 (如 succeeded / completed / SUCCESS)',
  `status_failed_values`  JSON             DEFAULT NULL                                          COMMENT '任务失败时 status 可能的值列表 (JSON 数组, 如 ["failed","error","canceled"])',
  `cost_extract_path`     VARCHAR(300)     DEFAULT NULL                                          COMMENT '从轮询响应 JSON 中提取"实际消耗/计费时长"的 JSONPath (如 $.usage.duration_sec, 用于精确计费, NULL=按请求参数估算)',
  `webhook_path`          VARCHAR(500)     DEFAULT NULL                                          COMMENT 'Webhook 回调路径 (平台侧, 如 /api/webhook/volcengine/ark, 与 provider 上的 webhook_verify_type 配合使用)',

  `estimated_duration_sec` INT UNSIGNED     DEFAULT NULL                                          COMMENT '预估单任务生成时长 (秒, 用于 Worker 超时和进度估算)',
  `estimated_cost_per_sec` DECIMAL(12,8)   DEFAULT NULL                                          COMMENT '预估每秒成本 (USD, 参考价, 实际以 ai_price_rule 为准)',
  `official_quota_per_day` INT UNSIGNED     DEFAULT NULL                                          COMMENT '厂商官方每日调用配额 (NULL=未公开/不限)',
  `official_quota_per_min` INT UNSIGNED     DEFAULT NULL                                          COMMENT '厂商官方每分钟调用配额',
  `rate_limit_per_min`     INT UNSIGNED     DEFAULT NULL                                          COMMENT '平台侧建议每分钟限流 (NULL=不限)',
  `sla_availability`       DECIMAL(5,4)    DEFAULT NULL                                          COMMENT '厂商 SLA 可用性 (如 0.999)',
  `feature_tags`           JSON             DEFAULT NULL                                          COMMENT '功能标签 (JSON 数组, 如 ["动漫风格","写实","人物一致性好"])',
  `recommended_prompt_template` MEDIUMTEXT DEFAULT NULL                                  COMMENT '推荐 Prompt 模板 (Markdown, 含变量占位符, 前端用户引导)',
  `known_issues`           VARCHAR(500)     DEFAULT NULL                                          COMMENT '已知问题/限制说明 (展示给用户)',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展配置 (JSON: 请求体参数 schema / 响应字段映射 / 支持的高级参数)',
  `status`                 TINYINT          NOT NULL DEFAULT 1                                    COMMENT '状态: 0=下线, 1=正常, 2=灰度测试中',
  `sort_no`                INT              NOT NULL DEFAULT 0                                    COMMENT '排序号 (升序)',
  `remark`                 VARCHAR(500)     DEFAULT NULL                                          COMMENT '备注',
  `create_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '创建人ID',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '最后更新人ID',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`               INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_model_id` (`provider_id`, `model_id`, `del_flag`)                       COMMENT '同一供应商下 model_id 唯一',
  KEY `idx_provider` (`provider_id`),
  KEY `idx_category` (`model_category`),
  KEY `idx_task_type` (`task_type`),
  KEY `idx_status` (`status`),
  CONSTRAINT `chk_model_category` CHECK (`model_category` IN ('text','image','video','audio','embedding')),
  CONSTRAINT `chk_task_type` CHECK (`task_type` IN ('t2v','i2v','t2i','i2i','kf2v','inpainting','upscale','subtitle_erase','tts'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='AI 模型 (平台级只读配置, 包含所有 AI 厂商的完整模型元数据)';


-- -----------------------------------------------------------------------------
-- 2.3 ai_channel · AI 通道 (租户级可写, 存放加密的 AK/SK 凭证, 最敏感表)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_channel`;
CREATE TABLE `ai_channel` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL DEFAULT 0                                    COMMENT '租户ID (0=平台级通道, 所有租户共享; >0=租户专属通道)',
  `provider_id`            BIGINT UNSIGNED  NOT NULL                                              COMMENT '关联供应商ID (外键 ai_provider.id)',
  `channel_name`           VARCHAR(100)     NOT NULL                                              COMMENT '通道名称 (便于区分, 如 火山-主通道 / 火山-备通道 / 测试环境通道)',
  `channel_code`           VARCHAR(64)      DEFAULT NULL                                          COMMENT '通道编码 (业务引用用, 可选)',
  `ak_cipher`              VARCHAR(1024)    NOT NULL                                              COMMENT 'AccessKey **AES-256-GCM-SIV 加密存储** (密文, 包含 IV + Tag + Ciphertext)',
  `sk_cipher`              VARCHAR(2048)    NOT NULL                                              COMMENT 'SecretKey **AES-256-GCM-SIV 加密存储** (SK 通常比 AK 长, 预留 2048)',
  `encryption_key_ref`     VARCHAR(128)     DEFAULT NULL                                          COMMENT '加密密钥引用 (KMS Key ID / 本地 Key 标识 / Hash, 定位解密用哪个密钥)',

  -- ====== OAuth 2.0 凭证 (G2 补项, auth_type=2 时必填) ======
  `client_id`              VARCHAR(200)     DEFAULT NULL                                          COMMENT 'OAuth 2.0 Client ID (auth_type=2 时必填, Google Vertex AI 等用)',
  `client_secret_cipher`   VARCHAR(2048)    DEFAULT NULL                                          COMMENT 'OAuth 2.0 Client Secret **AES-256-GCM-SIV 加密** (auth_type=2 时必填)',
  `access_token_cipher`    VARCHAR(4096)    DEFAULT NULL                                          COMMENT '**OAuth Access Token 加密存储** (运行时自动 refresh, 不要人工填)',
  `access_token_expire_at` DATETIME(3)      DEFAULT NULL                                          COMMENT 'access_token 过期时间 (定时任务提前 5 分钟 refresh)',
  `refresh_token_cipher`   VARCHAR(2048)    DEFAULT NULL                                          COMMENT '**OAuth Refresh Token 加密存储** (长时效, 用于换取新 access_token)',
  `refresh_token_expire_at` DATETIME(3)     DEFAULT NULL                                          COMMENT 'refresh_token 过期时间 (到期后需要用户重新授权)',
  `token_scope`            VARCHAR(500)     DEFAULT NULL                                          COMMENT '授权 scope 列表 (空格分隔, 如 "https://www.googleapis.com/auth/cloud-platform")',
  `token_refresh_url`      VARCHAR(500)     DEFAULT NULL                                          COMMENT 'token 刷新端点 (NULL=用 provider 默认)',
  `token_last_refresh_time` DATETIME(3)     DEFAULT NULL                                          COMMENT '最后一次成功 refresh 时间',
  `token_refresh_fail_count` INT UNSIGNED   NOT NULL DEFAULT 0                                    COMMENT 'refresh 连续失败次数 (达到阈值触发告警/熔断)',
  `api_base_url_override`  VARCHAR(500)     DEFAULT NULL                                          COMMENT '覆盖供应商默认 Base URL (用于自建代理/私有化部署/区域切换)',
  `additional_headers_json` JSON            DEFAULT NULL                                          COMMENT '附加请求头 (JSON, 如 {"X-Custom-Header": "value"}) ',
  `weight`                 INT              NOT NULL DEFAULT 100                                  COMMENT '负载权重 (同一 Provider 下多通道按权重轮询, 越大使用频率越高)',
  `priority`               INT              NOT NULL DEFAULT 0                                    COMMENT '优先级 (数值越小越优先选择, 0=最高)',
  `quota_per_day`          INT UNSIGNED     DEFAULT NULL                                          COMMENT '每日调用配额 (NULL=不限, 超过后自动切换下一个通道)',
  `quota_per_minute`       INT UNSIGNED     DEFAULT NULL                                          COMMENT '每分钟调用配额 (NULL=不限)',
  `quota_used_today`       INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '今日已使用配额 (每日 0 点定时重置)',
  `quota_reset_time`       DATETIME(3)      DEFAULT NULL                                          COMMENT '配额下次重置时间',
  `health_status`          TINYINT          NOT NULL DEFAULT 1                                    COMMENT '健康状态: 0=offline, 1=healthy, 2=degraded(部分失败), 3=circuit_breaker(熔断中)',
  `health_last_check`      DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次健康检查时间',
  `health_latency_ms`      INT UNSIGNED     DEFAULT NULL                                          COMMENT '健康检查平均延迟 (毫秒, 最近 N 次平均)',
  `consecutive_failures`   INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '连续失败次数 (达到阈值自动熔断, 熔断恢复后重置为 0)',
  `consecutive_successes`  INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '连续成功次数 (用于判断从 degraded 恢复到 healthy)',
  `circuit_breaker_until`  DATETIME(3)      DEFAULT NULL                                          COMMENT '熔断到期时间 (超过后进入半开状态尝试恢复)',
  `enabled`                TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否启用: 0=禁用 (不参与选路), 1=启用',
  `expire_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '凭证过期时间 (AK/SK 轮换到期提醒)',
  `last_used_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次实际使用时间',
  `last_used_ip_hash`      VARCHAR(255)     DEFAULT NULL                                          COMMENT '最后一次使用方 IP 哈希 (安全审计)',
  `monthly_cost_limit`     DECIMAL(14,2)   DEFAULT NULL                                          COMMENT '月度费用上限 (USD, 超过后自动降级/关闭)',
  `monthly_cost_used`      DECIMAL(14,2)   NOT NULL DEFAULT 0                                    COMMENT '本月已产生费用 (USD, 异步更新)',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展配置 (JSON: 代理配置/超时覆盖/签名参数覆盖)',
  `remark`                 VARCHAR(500)     DEFAULT NULL                                          COMMENT '备注',
  `create_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '创建人ID',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '最后更新人ID',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`               INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_provider` (`tenant_id`, `provider_id`),                             COMMENT '租户查询某供应商下的所有通道',
  KEY `idx_provider` (`provider_id`),
  KEY `idx_health` (`health_status`),
  KEY `idx_tenant_enabled` (`tenant_id`, `enabled`, `health_status`),                   COMMENT '路由选择时快速过滤: 启用+健康+属于我的租户或平台',
  KEY `idx_quota_reset` (`quota_reset_time`),                                          COMMENT '定时任务扫描重置配额',
  KEY `idx_expire` (`expire_time`),                                                    COMMENT '定时任务扫描凭证过期提醒',
  KEY `idx_monthly_cost_limit` (`monthly_cost_limit`, `monthly_cost_used`)              COMMENT '月度控费扫描',
  CONSTRAINT `chk_health_status` CHECK (`health_status` IN (0,1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='AI 通道 (存放加密的 AK/SK 凭证, 每个租户可为同一 Provider 配置多通道实现主备和负载均衡, 最敏感表)';


-- -----------------------------------------------------------------------------
-- 2.4 ai_channel_model · 通道-模型绑定 (N:M 中间表, 控制通道可用哪些模型 + 价格覆盖)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_channel_model`;
CREATE TABLE `ai_channel_model` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `channel_id`             BIGINT UNSIGNED  NOT NULL                                              COMMENT '通道ID (外键 ai_channel.id)',
  `model_id`               BIGINT UNSIGNED  NOT NULL                                              COMMENT '模型ID (外键 ai_model.id)',
  `custom_price_per_sec`   DECIMAL(12,8)   DEFAULT NULL                                          COMMENT '通道专属每秒价格 (Credits, NULL=用平台 ai_price_rule 默认价)',
  `custom_resolution_coef` DECIMAL(8,4)    DEFAULT NULL                                          COMMENT '通道分辨率系数 (NULL=用模型默认, 可临时加价或打折)',
  `enabled`                TINYINT          NOT NULL DEFAULT 1                                    COMMENT '此通道是否启用此模型: 0=禁用 (该通道不支持此模型), 1=启用',
  `quota_per_day`          INT UNSIGNED     DEFAULT NULL                                          COMMENT '此通道此模型单独的每日配额 (NULL=用通道级默认)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_model` (`channel_id`, `model_id`),
  KEY `idx_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='通道-模型绑定 (N:M 中间表, 控制哪些通道可以调用哪些模型, 并支持通道级价格覆盖)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 4 张表建表完成
-- 执行顺序: provider → model → channel → channel_model
-- 外键逻辑依赖: channel 依赖 provider, channel_model 依赖 channel + model
-- =============================================================================
