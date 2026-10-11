-- =============================================================================
-- Star AI Boot · 18+ 视频付费分润平台 · 数据库建表脚本
-- Part 08 · 视频消费域 + 创作者中心域 (creator_* / stream_*) · 共 13 张表
-- Part 11 · 用户年龄验证扩展 (sys_user_age_verification)
-- Part 10 (引用) · 会员订阅池扩展: schema/10_subscription_pool_domain.sql (4 张表)
-- 版本: v1.2 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策:
--   D1  全平台 18+，不需要分级 safe/adult 双 bucket
--   D2  存储按 region (cn/sg/us) 路由，MinIO / AWS S3 切换
--   D3  自动审核阈值: nsfw > 0.90 直接驳回, 0.85-0.90 人工复核
--   D4  分润双轨: 交易驱动 (单视频付费, revenue_rate=30%) + 订阅池驱动 (会员月度分摊, subscription_revenue_rate=30%)
--   D5  签名 URL 策略: 国内 MinIO presigned, 国际 CloudFront Signed URL
--   D6  stream_revenue_record.revenue_source 标记分润来源 (DIRECT / SUBSCRIPTION_POOL)
--   D7  v1.2 核心: 每集定价 + 定价权开放给创作者 + 平台固定点数抽成 + 抽成比例创作者独立配置
--   D8  抽成快照: 视频发布时把 creator_account.revenue_rate 固化到 stream_video.revenue_rate_snapshot, 后续改抽成不追溯
--   D9  定价审核: 创作者定价在允许区间内 + auto_publish_price=1 → 自动生效; 超出区间 → price_status=pending 人工审核
--   D10 剧集解锁: stream_video_collection = 剧集 Series (加 series_code/season), 创作者自己定价 + 抽成快照
--   D11 全局解锁: stream_creator_unlock 独立表 + creator_account.global_unlock_price, 价格由创作者自己制定, 解锁后该创作者所有视频 (含未来新发布的) 永久可看
--   D12 三级定价层级: episode (单集 stream_video) + series (剧集 stream_video_collection) + creator (全局 creator_account.global_unlock), 三级定价区间独立配置
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- Part 11 · 用户年龄验证扩展 (sys_* 域, 系统模块)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_age_verification`;
CREATE TABLE `sys_user_age_verification` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '关联 sys_user.id',
  `age_verified`        TINYINT NOT NULL DEFAULT 0 COMMENT '0=未验证, 1=已成年',
  `age_verify_method`   VARCHAR(30) DEFAULT NULL COMMENT 'id_card/kyc/sumsum/jumio/creditcard_preauth',
  `age_verified_at`     DATETIME(3) DEFAULT NULL COMMENT '验证时间',
  `age_verify_ref`      VARCHAR(128) DEFAULT NULL COMMENT '第三方 KYC 引用ID',
  `region`              VARCHAR(10) NOT NULL DEFAULT 'cn' COMMENT 'cn/sg/us',
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_verified` (`age_verified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='用户年龄验证 (注册强制 18+ 合规核心)';


-- -----------------------------------------------------------------------------
-- Part 08 · 创作者中心域 (creator_*) · 3 张表
-- -----------------------------------------------------------------------------

-- 8.1 创作者账户
DROP TABLE IF EXISTS `creator_account`;
CREATE TABLE `creator_account` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '关联 sys_user.id',
  `nickname`            VARCHAR(64) NOT NULL COMMENT '创作者昵称',
  `avatar_url`          VARCHAR(512) DEFAULT NULL,
  `bio`                 VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  `region`              VARCHAR(10) NOT NULL DEFAULT 'cn' COMMENT 'cn/sg/us',
  `status`              VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/active/frozen/rejected',
  `level`               VARCHAR(10) NOT NULL DEFAULT 'L1' COMMENT '创作者等级 L1-L5',

  -- ============ 分润抽成 (固定点数, 不同创作者独立配置) ============
  -- 设计: 平台抽成 = order_amount × (1 - revenue_rate/100), 创作者拿 revenue_rate%
  -- 不同创作者抽成点数独立配置 (L1 默认 30%, 爆款创作者可谈 50% 甚至更高)
  -- 生效时点: 视频发布时把 revenue_rate 快照到 stream_video.revenue_rate_snapshot
  --          后续改 creator_account.revenue_rate 不追溯已发布视频
  `revenue_rate`        DECIMAL(5,2) NOT NULL DEFAULT 30.0000 COMMENT '交易驱动分润比例% (单视频付费时创作者拿多少, 默认 30%)',
  `subscription_revenue_rate` DECIMAL(5,2) NOT NULL DEFAULT 30.0000 COMMENT '订阅池分润比例% (订阅池场景独立配置, 可不同于 revenue_rate)',

  -- ============ 定价权限 (开放给创作者) ============
  -- 设计: 定价权开放给创作者, 平台设置定价区间 + 自动风控审核
  -- L1/L2 创作者: 定价在平台允许区间内 → 自动生效; 超出区间 → 提交人工审核
  -- L3+ 创作者:   在区间内自动生效, 平台抽检
  -- 区间本身由平台运营后台维护, 不写死在代码里
  `pricing_enabled`     TINYINT NOT NULL DEFAULT 1 COMMENT '是否开放定价权给该创作者 (0=平台统一定价, 1=创作者自助定价)',
  `pricing_mode`        VARCHAR(20) NOT NULL DEFAULT 'self' COMMENT 'self=自助定价 / platform=平台统一定价',
  `price_amount_min`    DECIMAL(10,2) DEFAULT NULL COMMENT '该创作者单视频最低定价 (NULL=走平台全局默认值)',
  `price_amount_max`    DECIMAL(10,2) DEFAULT NULL COMMENT '该创作者单视频最高定价 (NULL=走平台全局默认值)',
  -- 剧集定价区间 (与单集独立, 剧集打包价通常比单集逐集全买便宜 30-50%)
  `series_price_min`    DECIMAL(10,2) DEFAULT NULL COMMENT '该创作者剧集打包最低定价 (NULL=走平台全局默认值)',
  `series_price_max`    DECIMAL(10,2) DEFAULT NULL COMMENT '该创作者剧集打包最高定价 (NULL=走平台全局默认值)',
  -- 全局解锁定价区间 (与剧集独立, 全局解锁 = 创作者所有视频打包, 通常比剧集逐集解锁更便宜)
  `global_unlock_price_min` DECIMAL(10,2) DEFAULT NULL COMMENT '该创作者全局解锁最低定价',
  `global_unlock_price_max` DECIMAL(10,2) DEFAULT NULL COMMENT '该创作者全局解锁最高定价',
  `auto_publish_price`  TINYINT NOT NULL DEFAULT 1 COMMENT '定价在允许区间内是否自动生效 (0=需人工审核, 1=自动生效)',

  -- ============ 全局解锁定价 (创作者级: 解锁该创作者所有视频) ============
  -- 设计: 创作者可设置"解锁我全部视频多少钱", 用户付一笔钱永久解锁该创作者所有视频 (含未来新发布的)
  -- 与剧集的区别: 剧集是具体几部剧的打包; 全局解锁是创作者身份维度的所有内容
  -- 价格由创作者自己制定 (跟每集定价独立, 也走 pricing_enabled + 区间审核)
  -- 权限判断优先级: path ② 会员免费 > ②.5 创作者全局解锁 > ③ 剧集解锁 > ④ 单集购买
  `global_unlock_enabled`   TINYINT NOT NULL DEFAULT 0 COMMENT '是否开启全局解锁 (0=关闭, 1=开启并定价)',
  `global_unlock_price`     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '全局解锁定价 (0=免费解锁所有视频, 不推荐)',
  `global_unlock_currency`  VARCHAR(3) NOT NULL DEFAULT 'CNY',
  `global_unlock_discount_pct` DECIMAL(5,2) DEFAULT NULL COMMENT '前端展示用: "全局解锁比逐集买省 XX%" (NULL=动态计算, 实际不参与定价逻辑)',

  `subscriber_count`    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订阅/关注该创作者的粉丝数 (订阅场景下影响贡献度算法)',
  `kyc_status`          VARCHAR(20) NOT NULL DEFAULT 'unverified',
  `total_views`         BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `total_favorites`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `videos_published`    INT UNSIGNED NOT NULL DEFAULT 0,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`           BIGINT UNSIGNED DEFAULT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`           BIGINT UNSIGNED DEFAULT NULL,
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  `revision`            INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='创作者账户';

-- 8.2 视频分类（平台维护）
DROP TABLE IF EXISTS `creator_video_category`;
CREATE TABLE `creator_video_category` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `name`                VARCHAR(64) NOT NULL,
  `icon_url`            VARCHAR(512) DEFAULT NULL,
  `sort_order`          INT NOT NULL DEFAULT 0,
  `enabled`             TINYINT NOT NULL DEFAULT 1,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='视频分类';

-- 8.3 视频标签
DROP TABLE IF EXISTS `creator_video_tag`;
CREATE TABLE `creator_video_tag` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`                VARCHAR(32) NOT NULL,
  `usage_count`         INT UNSIGNED NOT NULL DEFAULT 0,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='视频标签';


-- -----------------------------------------------------------------------------
-- Part 08 · 视频消费域 (stream_*) · 10 张表
-- -----------------------------------------------------------------------------

-- 8.4 视频（核心）
DROP TABLE IF EXISTS `stream_video`;
CREATE TABLE `stream_video` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `creator_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联 creator_account.id',
  `title`               VARCHAR(200) NOT NULL,
  `description`         TEXT,
  `cover_url`           VARCHAR(512) DEFAULT NULL,
  `duration_sec`        INT UNSIGNED NOT NULL DEFAULT 0,
  `category_id`         BIGINT UNSIGNED DEFAULT NULL,

  -- 存储
  `region`              VARCHAR(10) NOT NULL DEFAULT 'cn' COMMENT 'cn/sg/us',
  `storage_bucket`      VARCHAR(64) NOT NULL COMMENT 'stream-published / stream-creator-draft',
  `original_key`        VARCHAR(256) NOT NULL COMMENT 'MinIO / S3 Key (draft 或 published)',
  `original_size`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `mime_type`           VARCHAR(50) DEFAULT NULL,

  -- 转码产物
  `hls_master_key`      VARCHAR(256) DEFAULT NULL COMMENT 'HLS master.m3u8 Key',
  `hls_presets`         VARCHAR(200) DEFAULT NULL COMMENT '1080p:720p:480p',
  `transcode_status`    VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/completed/failed',
  `transcode_job_id`    VARCHAR(128) DEFAULT NULL,

  -- 发布状态
  `publish_status`      VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending_review/published/rejected/unpublished',
  `review_note`         VARCHAR(500) DEFAULT NULL COMMENT '驳回原因',
  `reviewed_by`         BIGINT UNSIGNED DEFAULT NULL,
  `reviewed_at`         DATETIME(3) DEFAULT NULL,
  `published_at`        DATETIME(3) DEFAULT NULL,

  -- 定价 (创作者自助定价 + 平台抽成)
  -- 价格来源: creator_account.pricing_mode=self → 创作者设 price_amount
  --          creator_account.pricing_mode=platform → 平台运营后台定价
  -- 定价审核: price_amount 在 creator_account.price_amount_min/max 区间内 + auto_publish_price=1 → 自动生效
  --          超出区间 或 auto_publish_price=0 → price_status=pending → 人工审核
  `price_type`          VARCHAR(20) NOT NULL DEFAULT 'free' COMMENT 'free=免费 / paid=每集付费 / member_only=会员免费看',
  `price_amount`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单集定价 (price_type=paid 时生效)',
  `currency`            VARCHAR(3) NOT NULL DEFAULT 'CNY',
  `preview_sec`         INT UNSIGNED NOT NULL DEFAULT 30 COMMENT '试看秒数 (0=不允许试看, 付费内容建议 30-60s)',
  `price_status`        VARCHAR(20) NOT NULL DEFAULT 'approved'
  COMMENT '定价状态: approved=已生效 / pending=待审核 (超出创作者允许区间) / rejected=已驳回',
  `price_audit_by`      BIGINT UNSIGNED DEFAULT NULL COMMENT '定价审核人 (price_status=pending 时)',
  `price_audit_at`      DATETIME(3) DEFAULT NULL,
  `price_audit_remark`  VARCHAR(500) DEFAULT NULL COMMENT '驳回原因',

  -- ============ 抽成快照 (发布时固化, 后续 creator_account.revenue_rate 改了不追溯) ============
  -- 设计: 订单成功 → stream_revenue_record.revenue_rate 取自这里, 不是实时查 creator_account
  -- 这样创作者升级/降级导致抽成率变化时, 历史订单分润逻辑稳定可审计
  `revenue_rate_snapshot` DECIMAL(5,2) NOT NULL DEFAULT 30.0000
  COMMENT '交易驱动抽成比例快照 (发布时从 creator_account.revenue_rate 固化)',
  `subscription_revenue_rate_snapshot` DECIMAL(5,2) NOT NULL DEFAULT 30.0000
  COMMENT '订阅池抽成比例快照 (发布时从 creator_account.subscription_revenue_rate 固化)',

  -- 自动审核
  `auto_review_status`  VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/passed/flagged/rejected',
  `auto_review_score`   DECIMAL(5,4) DEFAULT NULL COMMENT 'NSFW 总分 0-1',
  `auto_review_detail`  JSON DEFAULT NULL,

  -- 地理屏蔽（video 级覆盖，全局 CDN Geo-Restriction 由平台配置）
  `geo_block_countries` VARCHAR(500) DEFAULT NULL COMMENT '额外屏蔽的国家 ISO2 码 (逗号分隔)',

  -- 统计
  `play_count`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `unique_view_count`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `like_count`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `favorite_count`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `purchase_count`      INT UNSIGNED NOT NULL DEFAULT 0,
  `total_revenue`       DECIMAL(14,2) NOT NULL DEFAULT 0.00,

  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`           BIGINT UNSIGNED DEFAULT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`           BIGINT UNSIGNED DEFAULT NULL,
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  `revision`            INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_publish_status` (`publish_status`),
  KEY `idx_category` (`category_id`),
  KEY `idx_auto_review` (`auto_review_status`),
  KEY `idx_price_type` (`price_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='视频资源 (核心)';

-- 8.5 视频-标签 多对多
DROP TABLE IF EXISTS `stream_video_tag_rel`;
CREATE TABLE `stream_video_tag_rel` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `video_id`            BIGINT UNSIGNED NOT NULL,
  `tag_id`              BIGINT UNSIGNED NOT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_tag` (`video_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='视频标签关联';

-- 8.5 剧集 (Series: 短剧/连续剧, 每部剧含若干集, 支持剧集级打包解锁)
-- 视频层级 (三级, 均可独立定价):
--   episode  → stream_video (单集, price_type + price_amount, 创作者自己定价)
--   series   → stream_video_collection (整部剧打包, price_amount, 创作者自己定价)
--   creator  → creator_account.global_unlock (创作者全局, global_unlock_price, 创作者自己定价)
--
-- 剧集定价流程 (创作者自己设置):
--   创作者后台 → "我的剧集" → 选择某剧集 → 设置 price_amount (比如 ¥199)
--   审核: creator_account.pricing_enabled=1 + price_amount 在 [series_price_min, series_price_max] 区间内
--         + auto_publish_price=1 → 自动生效; 超出区间 → price_status=pending 人工审核
--
-- 剧集解锁权限判断:
--   用户请求播放某 video → 先查该 video 所属剧集 (collection_id via stream_video_collection_rel)
--   → stream_video_purchase 有 user_id + collection_id + unlock_scope='collection' → 放行
--   → 剧集解锁后, 该剧集下所有集 (含未来新增) 自动解锁
--
-- 抽成: 剧集解锁支付成功 → 固化 revenue_rate_snapshot (创建剧集时从 creator_account.revenue_rate 快照)
DROP TABLE IF EXISTS `stream_video_collection`;
CREATE TABLE `stream_video_collection` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `creator_id`          BIGINT UNSIGNED NOT NULL,

  -- 剧集基本信息
  `series_code`         VARCHAR(32) NOT NULL COMMENT '剧集编码 (同一部剧跨季用相同 series_code, 不同 season 区分)',
  `season`              INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '第几季 (短剧通常 season=1, 多季连续剧 season 递增)',
  `episode_total`       INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本剧总集数 (冗余, 与 episode_count 等价)',
  `title`               VARCHAR(200) NOT NULL,
  `cover_url`           VARCHAR(512) DEFAULT NULL,
  `description`         TEXT,

  -- 剧集定价 (创作者自己设置, 走 creator_account.pricing_enabled + series_price_min/max 区间审核)
  `price_type`          VARCHAR(20) NOT NULL DEFAULT 'free' COMMENT 'free=剧集免费 / paid=剧集打包付费 / member_only=会员免费看',
  `price_amount`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '剧集打包解锁价 (创作者自己设置, 通常比单集逐集买便宜)',
  `episode_unit_price`  DECIMAL(10,2) DEFAULT NULL COMMENT '单集参考价 (展示用: "单集 ¥15 / 全集 ¥199 (共 16 集, 省 ¥41)", 不参与实际定价逻辑)',
  `currency`            VARCHAR(3) NOT NULL DEFAULT 'CNY',

  -- 定价审核 (剧集定价也走审核流, 与 stream_video 单集定价对齐)
  `price_status`        VARCHAR(20) NOT NULL DEFAULT 'approved' COMMENT 'approved=已生效 / pending=待审核 / rejected=已驳回',
  `price_audit_by`      BIGINT UNSIGNED DEFAULT NULL,
  `price_audit_at`      DATETIME(3) DEFAULT NULL,
  `price_audit_remark`  VARCHAR(500) DEFAULT NULL,

  -- ============ 抽成快照 (剧集创建时从 creator_account.revenue_rate 固化) ============
  -- 剧集解锁成功时, stream_revenue_record.revenue_rate 取这里的快照
  `revenue_rate_snapshot` DECIMAL(5,2) NOT NULL DEFAULT 30.0000,

  `episode_count`       INT UNSIGNED NOT NULL DEFAULT 0,
  `total_duration_sec`  INT UNSIGNED NOT NULL DEFAULT 0,
  `play_count`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_series_season` (`series_code`, `season`, `tenant_id`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='剧集 Series (短剧/连续剧, 创作者自己定价支持剧集级打包解锁)';

DROP TABLE IF EXISTS `stream_video_collection_rel`;
CREATE TABLE `stream_video_collection_rel` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `collection_id`       BIGINT UNSIGNED NOT NULL COMMENT '剧集 ID',
  `video_id`            BIGINT UNSIGNED NOT NULL,
  `episode_number`      INT UNSIGNED NOT NULL COMMENT '第几集 (该剧集内的集序号)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_episode` (`collection_id`, `episode_number`),
  UNIQUE KEY `uk_video` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='剧集-视频关联 (每集只属于一部剧集)';

-- 8.7 观看权限
-- 权限获取路径 (5 种消费模式, 按权限判断优先级从上到下):
--   ① 单视频购买:            video_id 填值, collection_id NULL → 该单视频观看权限
--   ② 剧集打包解锁 (全集):   collection_id 填值, video_id NULL → 该剧集下所有集自动解锁
--                              代码层判断: 用户要播某视频时, 除了查 video_id 精确匹配, 还要查
--                              "该视频所属的 collection_id 是否在本用户的 collection 购买记录里"
--   ③ 创作者全局解锁 🆕 v1.2  stream_creator_unlock 表 (独立表, 权限判断时优先匹配)
--                              用户付一笔钱 → 永久解锁 creator_id 所有视频 (含未来新发布的)
--                              价格由创作者自己在 creator_account.global_unlock_price 设定
--   ④ 会员权益:              access_type='member' → active 订阅会员时, 所有 price_type='member_only' 视频可看
--                              不需要在本表插记录, 实时校验 stream_user_subscription 状态
--   ⑤ 赠送/活动:             access_type='gift' → 运营后台赠送
--
-- 幂等逻辑: 同一用户同一视频/剧集只保留一条有效记录 (expires_at IS NULL 或 > 当前时间)
DROP TABLE IF EXISTS `stream_video_purchase`;
CREATE TABLE `stream_video_purchase` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT UNSIGNED NOT NULL,
  `video_id`            BIGINT UNSIGNED DEFAULT NULL COMMENT '单视频购买时填值, 剧集解锁 NULL',
  `collection_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '剧集解锁时填值, 单视频购买 NULL',
  `unlock_scope`        VARCHAR(20) NOT NULL DEFAULT 'episode'
  COMMENT '解锁范围: episode=单集 | collection=全集解锁 (剧集解锁后, 代码层自动授予该剧集下所有 video 的观看权限)',
  `order_no`            VARCHAR(32) NOT NULL COMMENT 'stream 业务订单号',
  `payment_order_no`    VARCHAR(32) DEFAULT NULL COMMENT 'payment 模块订单号 (gift/member 场景可 NULL)',
  `access_type`         VARCHAR(20) NOT NULL DEFAULT 'purchase' COMMENT 'purchase=付费购买 / gift=赠送 / member=会员权益 (member 一般实时校验, 不一定插本表)',
  `paid_amount`         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '用户实际支付金额 (0=免费/赠送)',
  `currency`            VARCHAR(3) NOT NULL DEFAULT 'CNY',
  `expires_at`          DATETIME(3) DEFAULT NULL COMMENT 'NULL=永久有效 (付费购买默认永久, 会员权益通常有期限)',
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  -- MySQL NULL != NULL, 所以 collection 解锁时 video_id 都是 NULL, 用 COALESCE 去重
  -- 同一用户同一 video 只保留一条; 同一用户同一 collection 也只保留一条
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  UNIQUE KEY `uk_user_collection` (`user_id`, `collection_id`),
  KEY `idx_user` (`user_id`, `unlock_scope`),
  KEY `idx_payment_order` (`payment_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='视频观看权限 (已购/解锁记录)';

-- 8.8 播放历史
DROP TABLE IF EXISTS `stream_video_play_history`;
CREATE TABLE `stream_video_play_history` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT UNSIGNED NOT NULL,
  `video_id`            BIGINT UNSIGNED NOT NULL,
  `position_sec`        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '断点位置',
  `total_watched_sec`   INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计观看时长',
  `play_count`          INT UNSIGNED NOT NULL DEFAULT 1,
  `is_effective`        TINYINT NOT NULL DEFAULT 0 COMMENT '是否算有效播放 (total_watched_sec >= subscription_plan.min_effective_sec 时为 1, 订阅池贡献计算入口)',
  `completion_rate`     DECIMAL(5,4) DEFAULT NULL COMMENT '完播率 (= total_watched_sec / video.duration_sec, NULL=还没看完首段)',
  `is_member_view`      TINYINT NOT NULL DEFAULT 0 COMMENT '观看时用户是否是 active 订阅会员 (订阅池贡献计算入口)',
  `last_played_at`      DATETIME(3) NOT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  KEY `idx_user_played` (`user_id`, `last_played_at`),
  KEY `idx_member_effective` (`is_member_view`, `is_effective`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='播放历史 (断点续播 + 订阅池贡献埋点)';

-- 8.9 点赞收藏
DROP TABLE IF EXISTS `stream_video_interaction`;
CREATE TABLE `stream_video_interaction` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT UNSIGNED NOT NULL,
  `video_id`            BIGINT UNSIGNED NOT NULL,
  `interaction_type`    VARCHAR(20) NOT NULL COMMENT 'like/favorite',
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video_type` (`user_id`, `video_id`, `interaction_type`),
  KEY `idx_video_type` (`video_id`, `interaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='点赞收藏';

-- 8.10 分润收益流水（交易驱动 + 订阅池两种来源）
DROP TABLE IF EXISTS `stream_revenue_record`;
CREATE TABLE `stream_revenue_record` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `revenue_source`      VARCHAR(30) NOT NULL DEFAULT 'DIRECT' COMMENT '分润来源: DIRECT=单视频付费 (交易驱动) / SUBSCRIPTION_POOL=会员订阅池分摊 (月度跑批)',
  `pool_batch_no`       VARCHAR(32) DEFAULT NULL COMMENT '订阅池分摊批次号 (SUBSCRIPTION_POOL 时必填, 格式 YYYY-MM_region; DIRECT 时 NULL)',
  `payment_order_no`    VARCHAR(32) DEFAULT NULL COMMENT '关联 ai_payment_order (DIRECT 时必填, SUBSCRIPTION_POOL 时 NULL)',
  `business_order_no`   VARCHAR(32) NOT NULL COMMENT 'stream 业务订单号 (DIRECT 时填 stream_order; SUBSCRIPTION_POOL 时填 pool_batch_no)',
  `video_id`            BIGINT UNSIGNED DEFAULT NULL COMMENT '关联视频 (DIRECT 时必填; SUBSCRIPTION_POOL 时 NULL, 订阅池分摊不按单视频粒度)',
  `creator_id`          BIGINT UNSIGNED NOT NULL,
  `total_amount`        DECIMAL(14,2) NOT NULL COMMENT '订单总额 (DIRECT=用户实付; SUBSCRIPTION_POOL=该创作者从池中分得金额)',
  `platform_share`      DECIMAL(14,2) NOT NULL COMMENT '平台抽成',
  `creator_share`       DECIMAL(14,2) NOT NULL COMMENT '创作者分润 (= total_amount - platform_share)',
  `revenue_rate`        DECIMAL(5,2) NOT NULL COMMENT '当时使用的分润比例 (DIRECT 取 creator_account.revenue_rate; SUBSCRIPTION_POOL 取 creator_account.subscription_revenue_rate)',
  `contribution_pct`    DECIMAL(10,4) DEFAULT NULL COMMENT '订阅池贡献百分比 (SUBSCRIPTION_POOL 时填 creator_monthly_contribution.contribution_pct; DIRECT 时 NULL)',
  `settle_status`       VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/settled/withdrawn',
  `settle_batch_no`     VARCHAR(32) DEFAULT NULL,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_settle_status` (`settle_status`),
  KEY `idx_payment` (`payment_order_no`),
  KEY `idx_source_pool` (`revenue_source`, `pool_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='分润收益流水 (交易驱动 + 订阅池双来源)';

-- 8.10.1 创作者全局解锁记录 (v1.2 新增)
-- 用户付一笔钱解锁某创作者的所有视频 (含未来新发布的)
-- 与 stream_video_purchase 的关系: 全局解锁单独存一张表, 权限判断时优先匹配
-- 分润: 全局解锁支付成功 → payment.success MQ → stream_revenue_record (revenue_source=DIRECT, 但 video_id=NULL, 代表创作者维度)
DROP TABLE IF EXISTS `stream_creator_unlock`;
CREATE TABLE `stream_creator_unlock` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT UNSIGNED NOT NULL,
  `creator_id`          BIGINT UNSIGNED NOT NULL COMMENT '解锁哪个创作者的全部视频',
  `order_no`            VARCHAR(32) NOT NULL COMMENT 'stream 业务订单号',
  `payment_order_no`    VARCHAR(32) DEFAULT NULL COMMENT 'payment 模块订单号 (gift 可 NULL)',
  `access_type`         VARCHAR(20) NOT NULL DEFAULT 'purchase' COMMENT 'purchase=付费解锁 / gift=运营赠送',
  `paid_amount`         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `currency`            VARCHAR(3) NOT NULL DEFAULT 'CNY',
  `expires_at`          DATETIME(3) DEFAULT NULL COMMENT 'NULL=永久 (付费解锁默认永久)',
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_creator` (`user_id`, `creator_id`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_payment` (`payment_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='创作者全局解锁记录 (v1.2 新增)';

-- 8.11 创作者提现申请
DROP TABLE IF EXISTS `stream_withdrawal_request`;
CREATE TABLE `stream_withdrawal_request` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_no`          VARCHAR(32) NOT NULL COMMENT '提现单号',
  `creator_id`          BIGINT UNSIGNED NOT NULL,
  `amount`              DECIMAL(14,2) NOT NULL,
  `bank_name`           VARCHAR(64) NOT NULL,
  `bank_account`        VARCHAR(200) NOT NULL COMMENT '脱敏存储',
  `account_name`        VARCHAR(64) NOT NULL,
  `region`              VARCHAR(10) NOT NULL DEFAULT 'cn',
  `status`              VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/paid/failed',
  `reviewed_by`         BIGINT UNSIGNED DEFAULT NULL,
  `reviewed_at`         DATETIME(3) DEFAULT NULL,
  `paid_at`             DATETIME(3) DEFAULT NULL,
  `payment_ref_no`      VARCHAR(64) DEFAULT NULL COMMENT '打款关联的 payment 模块订单号',
  `failure_reason`      VARCHAR(500) DEFAULT NULL,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`           BIGINT UNSIGNED DEFAULT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`           BIGINT UNSIGNED DEFAULT NULL,
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_creator_status` (`creator_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='创作者提现申请';

-- 8.12 创作者收益账本（汇总表，加速看板查询）
DROP TABLE IF EXISTS `stream_creator_earnings`;
CREATE TABLE `stream_creator_earnings` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `creator_id`          BIGINT UNSIGNED NOT NULL,
  `total_earned`        DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '累计总收入',
  `available_balance`   DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '可提现余额',
  `frozen_balance`      DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '冻结中 (已申请提现未打款)',
  `total_withdrawn`     DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '累计已提现',
  `last_updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='创作者收益账本 (汇总表)';

SET FOREIGN_KEY_CHECKS = 1;
