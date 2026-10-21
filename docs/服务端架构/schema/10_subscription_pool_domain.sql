-- =============================================================================
-- Star AI Boot · 18+ 视频付费分润平台 · 数据库建表脚本
-- Part 10 · 会员订阅池域 (stream_subscription_* + stream_creator_monthly_contribution)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 前置依赖: schema/08_stream_creator_domain.sql (已含 v1.1 扩展字段)
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 订阅池模式设计背景
-- -----------------------------------------------------------------------------
--
-- 本 schema 覆盖 Netflix 式"全平台会员 → 每月总池 → 按播放量贡献分摊"的
-- 分润模式，与 08 文件的"单视频付费 → 交易驱动分润"模式并存。
--
-- 两种模式的边界:
--
--   price_type = 'paid'        → 交易驱动 (DIRECT)
--                                 用户付费买单视频 → payment.success MQ →
--                                 stream_revenue_record.revenue_source=DIRECT
--
--   price_type = 'member_only' → 订阅池驱动 (SUBSCRIPTION_POOL)
--                                 用户买会员 → 月度 XXL-JOB:
--                                   汇总当月订阅收入 → 进 stream_subscription_pool
--                                   从 play_history 聚合每位创作者贡献
--                                   按贡献占比分摊 pool_amount
--                                   → stream_revenue_record.revenue_source=SUBSCRIPTION_POOL
--
-- 关键数据流:
--
--   实时层:
--     用户观看 member_only 视频 → stream_video_play_history
--       is_effective=1 (有效播放) + is_member_view=1 (会员观看)
--       → 异步增量 Redis 计数 video.member_watched
--
--   月度批处理层 (每月 1 日 XXL-JOB subscription.pool.aggregate):
--     ① 汇总 ai_payment_order (business_type LIKE 'stream_subscription%')
--        → stream_subscription_pool.total_subscription_amount
--     ② pool_amount = total_subscription_amount × pool_fee_rate (平台抽成后)
--     ③ 从 play_history 聚合每位创作者贡献
--        → stream_creator_monthly_contribution.contribution_score
--     ④ creator_share = pool_amount
--                      × contribution_pct
--                      × creator_account.subscription_revenue_rate
--     ⑤ 生成 stream_revenue_record (revenue_source=SUBSCRIPTION_POOL)
--     ⑥ 更新 stream_creator_earnings.available_balance
--
-- -----------------------------------------------------------------------------

-- 10.1 会员订阅方案（平台运营后台配置，多 region 多周期）
DROP TABLE IF EXISTS `stream_subscription_plan`;
CREATE TABLE `stream_subscription_plan` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `plan_code`             VARCHAR(32) NOT NULL COMMENT '方案唯一编码: monthly/quarterly/yearly/trial_7d',
  `plan_name`             VARCHAR(64) NOT NULL COMMENT '显示名: 月会员/年会员/7天试用',
  `duration_days`         INT UNSIGNED NOT NULL COMMENT '30/90/365/7',
  `price_amount`          DECIMAL(10,2) NOT NULL COMMENT '方案价格 (0 表示免费试用)',
  `currency`              VARCHAR(3) NOT NULL DEFAULT 'CNY',
  `region`                VARCHAR(10) NOT NULL COMMENT '适用 region: cn/sg/us',

  -- 订阅池财务参数 (快照到 pool)
  `pool_fee_rate`         DECIMAL(5,4) NOT NULL DEFAULT 1.0000
  COMMENT '进入订阅池的比例 (1.0000=100% 进池; 0.9000=90%进池10%平台抽成)',
  `creator_payout_rate`   DECIMAL(5,4) NOT NULL DEFAULT 0.7000
  COMMENT '池中分配给创作者分润的比例 (0.7000=70%给创作者, 30%留平台)',

  -- 贡献度算法参数
  `contribution_mode`     VARCHAR(20) NOT NULL DEFAULT 'play_duration'
  COMMENT '贡献分计算模式: play_duration=有效播放时长 / completion_score=完播率加权 / hybrid=时长×(1-cw)+完播×cw',
  `min_effective_sec`     INT UNSIGNED NOT NULL DEFAULT 10
  COMMENT '有效播放最低秒数 (短于此值视为误触, 不计入贡献)',
  `completion_weight`     DECIMAL(5,2) NOT NULL DEFAULT 0.3000
  COMMENT 'completion_mode=hybrid 时完播率权重 (时长权重=1-completion_weight)',

  -- 展示与控制
  `enabled`               TINYINT NOT NULL DEFAULT 1,
  `sort_order`            INT NOT NULL DEFAULT 0,
  `trial_consumable`      TINYINT NOT NULL DEFAULT 0 COMMENT '试用期是否一人仅可领一次',

  -- ============ 会员权益配置 (v1.2 新增, 全部平台方配置) ============
  -- 权益 1: 充会员送积分
  --   积分用于后续平台消费 (兑换/抵扣等, 积分体系由独立模块承载, 这里只配置赠送规则)
  --   生效时点: 订阅支付成功 → 写积分账户 (由积分模块消费 MQ)
  `gift_points`            INT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '充该方案送积分数量 (0=不送; 例: monthly=100, yearly=1200+200 bonus)',
  `gift_points_bonus`     INT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '额外赠送积分 (比如年会员额外送 200, total_gift = gift_points + gift_points_bonus)',

  -- 权益 2: 集级免费扩展 (叠加平台全局配置)
  --   全局免费: 每部剧集前 N 集免费 (非会员也能看, 由 stream_platform_config.free_episodes_per_series 配置)
  --   会员扩展: 在全局基础上额外 +X 集 (会员专属多免费几集)
  --   例: 全局免费 3 集, monthly 会员 +2, yearly 会员 +5
  --       → 非会员免费 3 集, 月会员免费 5 集, 年会员免费 8 集
  `free_episode_extra`    INT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '会员额外免费集数 (叠加 stream_platform_config.free_episodes_per_series; 0=无扩展)',

  -- 权益 3: 秒级试看扩展 (对付费内容的试看秒数加权)
  --   全局试看: 每集视频 preview_sec (创作者可设, 默认 30s)
  --   会员扩展: 在 preview_sec 基础上再多 X 秒; 或 -1 表示试看秒数 = 视频总时长 (即完整免费看)
  --   例: 非会员 30s 试看, 月会员 +30s = 60s, 年会员完整看 (-1)
  `preview_sec_extra`     INT NOT NULL DEFAULT 0
  COMMENT '会员额外试看秒数 (叠加 video.preview_sec; -1=完整免费看该付费视频; 0=无扩展)',


  `tenant_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`             BIGINT UNSIGNED DEFAULT NULL,
  `create_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`             BIGINT UNSIGNED DEFAULT NULL,
  `update_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`              TINYINT NOT NULL DEFAULT 0,
  `revision`              INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code_region` (`plan_code`, `region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅方案 (平台配置)';

-- 10.2 用户订阅记录（谁、什么时候、什么方案、是否自动续费）
DROP TABLE IF EXISTS `stream_user_subscription`;
CREATE TABLE `stream_user_subscription` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`               BIGINT UNSIGNED NOT NULL,
  `plan_id`               BIGINT UNSIGNED NOT NULL,
  `plan_code_snapshot`    VARCHAR(32) NOT NULL COMMENT '快照: 订阅当时的 plan_code (方案变更后不影响历史)',
  `plan_name_snapshot`    VARCHAR(64) DEFAULT NULL,
  `payment_order_no`      VARCHAR(32) DEFAULT NULL COMMENT '关联 ai_payment_order (付费订阅时有, 免费试用为 NULL)',

  `status`                VARCHAR(20) NOT NULL DEFAULT 'active'
  COMMENT '订阅状态: active=有效 / trial=试用 / pending=支付中 / paused=暂停 / canceled=已取消 / expired=已过期',

  `started_at`            DATETIME(3) NOT NULL,
  `expires_at`            DATETIME(3) NOT NULL,
  `auto_renew`            TINYINT NOT NULL DEFAULT 0 COMMENT '是否自动续费 (Paddle/Stripe 自动扣款场景)',
  `canceled_at`           DATETIME(3) DEFAULT NULL,
  `cancel_reason`         VARCHAR(200) DEFAULT NULL,
  `canceled_by`           BIGINT UNSIGNED DEFAULT NULL,

  `trial_consumed`        TINYINT NOT NULL DEFAULT 0 COMMENT '标记试用是否已使用过 (trial_consumable=1 时用来阻断重复领取)',
  `region`                VARCHAR(10) NOT NULL DEFAULT 'cn',

  `tenant_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`             BIGINT UNSIGNED DEFAULT NULL,
  `create_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`             BIGINT UNSIGNED DEFAULT NULL,
  `update_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`              TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_expires` (`status`, `expires_at`),
  KEY `idx_payment` (`payment_order_no`),
  KEY `idx_user_plan` (`user_id`, `plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅记录';

-- 10.3 每月订阅总池（XXL-JOB subscription.pool.aggregate 产出的核心对账锚点）
DROP TABLE IF EXISTS `stream_subscription_pool`;
CREATE TABLE `stream_subscription_pool` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `pool_month`            DATE NOT NULL COMMENT '池子所属月份 (YYYY-MM-01, 如 2026-10-01 表示 10 月池)',
  `region`                VARCHAR(10) NOT NULL COMMENT 'cn/sg/us (分 region 独立池子, 因为币种和创作者分润比例不同)',

  -- 1. 汇总: 订阅收入 (从 ai_payment_order 聚合)
  `total_subscription_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00
  COMMENT '当月所有订阅收入总额 (business_type LIKE stream_subscription% AND status=paid)',
  `pool_amount`           DECIMAL(14,2) NOT NULL DEFAULT 0.00
  COMMENT '实际进入订阅池的金额 (= total_subscription_amount × pool_fee_rate)',
  `creator_payout_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00
  COMMENT '创作者分润总额 (= pool_amount × creator_payout_rate)',
  `platform_fee_amount`   DECIMAL(14,2) NOT NULL DEFAULT 0.00
  COMMENT '平台抽成 (= pool_amount - creator_payout_amount)',

  -- 2. 汇总: 播放贡献度 (从 stream_video_play_history 聚合, is_effective=1 AND is_member_view=1)
  `total_active_subscribers` INT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '当月有效订阅用户数 (至少产生 1 次有效播放)',
  `total_plays`             BIGINT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '全平台 member_only 视频累计有效播放次数',
  `total_play_duration_sec` BIGINT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '全平台 member_only 视频累计有效播放时长 (秒)',
  `total_contribution_score` DECIMAL(20,4) NOT NULL DEFAULT 0.0000
  COMMENT '全平台总贡献分 (∑ 所有创作者的 contribution_score, 按 plan.contribution_mode 算法计算)',

  -- 3. 方案参数快照 (防止中途改配置导致分润不一致, 月度跑批时从 plan 读取后固化)
  `plan_code_snapshot`        VARCHAR(32) DEFAULT NULL,
  `pool_fee_rate_snapshot`    DECIMAL(5,4) DEFAULT NULL,
  `creator_payout_rate_snapshot` DECIMAL(5,4) DEFAULT NULL,
  `contribution_mode_snapshot` VARCHAR(20) DEFAULT NULL,
  `min_effective_sec_snapshot` INT UNSIGNED DEFAULT NULL,
  `completion_weight_snapshot` DECIMAL(5,2) DEFAULT NULL,

  -- 4. 池子状态机 (对账流)
  `status`                VARCHAR(20) NOT NULL DEFAULT 'pending'
  COMMENT '池子状态: pending=待计算 → calculating=计算中 → allocated=已分摊 (revenue_record 已生成) → settled=已结算 / discrepancy=对账异常',
  `calculated_at`         DATETIME(3) DEFAULT NULL,
  `allocated_at`          DATETIME(3) DEFAULT NULL,
  `settled_at`            DATETIME(3) DEFAULT NULL,
  `discrepancy_reason`    VARCHAR(500) DEFAULT NULL,

  `remark`                VARCHAR(500) DEFAULT NULL,
  `tenant_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`             BIGINT UNSIGNED DEFAULT NULL,
  `create_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`             BIGINT UNSIGNED DEFAULT NULL,
  `update_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`              TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_month_region` (`pool_month`, `region`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每月订阅分润总池 (核心对账锚点)';

-- 10.4 每月创作者订阅贡献度（池子 → 创作者的分润计算明细）
DROP TABLE IF EXISTS `stream_creator_monthly_contribution`;
CREATE TABLE `stream_creator_monthly_contribution` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `pool_id`               BIGINT UNSIGNED NOT NULL COMMENT '关联 stream_subscription_pool.id',
  `creator_id`            BIGINT UNSIGNED NOT NULL,
  `pool_month`            DATE NOT NULL,
  `region`                VARCHAR(10) NOT NULL,

  -- 原始播放汇总 (从 play_history 聚合, is_effective=1 AND is_member_view=1)
  `total_plays`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `total_unique_viewers`  INT UNSIGNED NOT NULL DEFAULT 0
  COMMENT '观看过该创作者视频的唯一 active 订阅用户数',
  `total_play_duration_sec` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `weighted_completion_rate` DECIMAL(5,4) NOT NULL DEFAULT 0.0000
  COMMENT '按播放时长加权的平均完播率 (= Σ(play_duration_sec / video_duration_sec) × play_duration_sec) / total_play_duration_sec)',

  -- 贡献分 (按 pool.contribution_mode 计算, 详见方案文档公式章节)
  `contribution_score`    DECIMAL(20,4) NOT NULL DEFAULT 0.0000
  COMMENT '贡献分: play_duration 模式=total_play_duration_sec / completion_score 模式=total_plays × weighted_completion_rate / hybrid 模式=时长项×(1-cw)+完播项×cw',
  `contribution_pct`      DECIMAL(10,4) NOT NULL DEFAULT 0.0000
  COMMENT '占全平台总贡献分的百分比 (= contribution_score / pool.total_contribution_score)',

  -- 分润金额
  `creator_share_amount`  DECIMAL(14,2) NOT NULL DEFAULT 0.00
  COMMENT '从订阅池中分得的金额 (= pool.creator_payout_amount × contribution_pct × creator.subscription_revenue_rate / 100)',
  `creator_revenue_rate_snapshot` DECIMAL(5,2) NOT NULL DEFAULT 30.0000
  COMMENT '计算当时使用的 creator.subscription_revenue_rate 快照 (防止中途改比例导致追溯混乱)',

  `tenant_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`              TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_creator` (`pool_id`, `creator_id`),
  KEY `idx_creator_month` (`creator_id`, `pool_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每月创作者订阅贡献度';

-- 10.5 平台级配置 (全局免费集数等平台方统一配置, v1.2 新增)
-- 为什么不用 sys_dict? 因为 sys_dict 是 JeecgBoot 通用字典, 这里是 stream 业务域专属配置
-- 后续 stream 相关的全局配置都放这里, 避免污染通用字典表
DROP TABLE IF EXISTS `stream_platform_config`;
CREATE TABLE `stream_platform_config` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_key`            VARCHAR(64) NOT NULL COMMENT '配置键, 如 free_episodes_per_series',
  `config_value`          TEXT NOT NULL COMMENT '配置值 (INT 或 JSON 都存字符串, 按 config_type 解析)',
  `config_type`           VARCHAR(20) NOT NULL DEFAULT 'int' COMMENT '值类型: int/decimal/json/string',
  `region`                VARCHAR(10) NOT NULL DEFAULT 'cn' COMMENT '适用 region: cn/sg/us (不同 region 可不同配置)',
  `description`           VARCHAR(200) DEFAULT NULL COMMENT '配置说明 (给运营看)',
  `update_by`             BIGINT UNSIGNED DEFAULT NULL,
  `update_time`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `tenant_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_region` (`config_key`, `region`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='stream 业务域平台级配置';

-- ======== 初始配置数据 (平台方上线时写入) ========
-- INSERT INTO stream_platform_config (config_key, config_value, config_type, region, description) VALUES
--   ('free_episodes_per_series', '3',  'int',    'cn', '全局免费集数: 每部剧集前 N 集免费 (非会员也能看)'),
--   ('free_episodes_per_series', '3',  'int',    'sg', '同上'),
--   ('free_episodes_per_series', '5',  'int',    'us', '美国市场给更多免费集数 (竞争更激烈)');
--
-- 会员最终可免费看的集数:
--   free_episodes_per_series + plan.free_episode_extra
--   例: 全局 3 + yearly 会员 +5 = 8 集免费

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 附: 08_stream_creator_domain.sql 的 v1.1 upgrade migration (历史已建 v1.0 表的环境)
--     新部署环境忽略本节, 直接用 08 文件的 v1.1 DDL
-- =============================================================================
--
-- ALTER TABLE `creator_account`
--   ADD COLUMN `subscription_revenue_rate` DECIMAL(5,2) NOT NULL DEFAULT 30.0000 COMMENT '订阅池分润比例%' AFTER `revenue_rate`,
--   ADD COLUMN `subscriber_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订阅/关注粉丝数' AFTER `subscription_revenue_rate`;
--
-- ALTER TABLE `stream_video_play_history`
--   ADD COLUMN `is_effective` TINYINT NOT NULL DEFAULT 0 COMMENT '是否有效播放' AFTER `play_count`,
--   ADD COLUMN `completion_rate` DECIMAL(5,4) DEFAULT NULL COMMENT '完播率' AFTER `is_effective`,
--   ADD COLUMN `is_member_view` TINYINT NOT NULL DEFAULT 0 COMMENT '会员观看' AFTER `completion_rate`,
--   ADD KEY `idx_member_effective` (`is_member_view`, `is_effective`);
--
-- ALTER TABLE `stream_revenue_record`
--   ADD COLUMN `revenue_source` VARCHAR(30) NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT/SUBSCRIPTION_POOL' AFTER `id`,
--   ADD COLUMN `pool_batch_no` VARCHAR(32) DEFAULT NULL COMMENT '订阅池分摊批次号 YYYY-MM_region' AFTER `revenue_source`,
--   MODIFY COLUMN `payment_order_no` VARCHAR(32) DEFAULT NULL COMMENT 'DIRECT 时必填, SUBSCRIPTION_POOL 时 NULL',
--   MODIFY COLUMN `video_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'DIRECT 时必填, SUBSCRIPTION_POOL 时 NULL',
--   ADD COLUMN `contribution_pct` DECIMAL(10,4) DEFAULT NULL COMMENT '订阅池贡献百分比' AFTER `revenue_rate`,
--   ADD KEY `idx_source_pool` (`revenue_source`, `pool_batch_no`);
--
