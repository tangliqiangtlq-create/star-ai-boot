-- =============================================================================
-- Star AI Boot · 18+ 视频付费分润平台 · 数据库建表脚本
-- Part 09 · 支付公共服务域 (ai_payment_*) · 共 4 张表
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策:
--   D1  支付模块为公共服务，所有业务的支付单都进入 ai_payment_order
--   D2  支持多渠道 (wechat/alipay/stripe/paddle) + 多 Region (cn/sg/us)
--   D3  渠道敏感配置 (app_id/app_secret/private_key) 加密存储
--   D4  第三方回调幂等: 内部使用 order_no 唯一约束防重
--   D5  每日对账 (XXL-JOB) 拉取云厂商账单，差异告警
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- 9.1 支付订单（公共服务，所有业务的支付单都在这里）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_payment_order`;
CREATE TABLE `ai_payment_order` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`            VARCHAR(32) NOT NULL COMMENT '内部支付订单号 (雪花ID，唯一)',
  `business_order_no`   VARCHAR(32) NOT NULL COMMENT '业务侧订单号 (stream/billing/creator)',
  `business_type`       VARCHAR(30) NOT NULL COMMENT '业务类型: stream_video_purchase / billing_recharge / creator_withdrawal / creator_earnings_payout',
  `user_id`             BIGINT UNSIGNED NOT NULL COMMENT '付款用户',
  `creator_id`          BIGINT UNSIGNED DEFAULT NULL COMMENT '收款创作者 (仅提现/分润打款场景)',

  -- 渠道
  `channel_type`        VARCHAR(30) NOT NULL COMMENT '渠道: wechat_native / wechat_jsapi / alipay_pc / alipay_h5 / stripe_checkout / stripe_card / paddle / bank_transfer',
  `channel_trade_no`    VARCHAR(128) DEFAULT NULL COMMENT '第三方流水号 (支付成功后回填)',
  `pay_url`             VARCHAR(1024) DEFAULT NULL COMMENT '支付参数 (二维码URL / Stripe Session URL / JSAPI 参数 JSON)',

  -- 金额
  `amount`              DECIMAL(14,2) NOT NULL COMMENT '订单金额 (原币)',
  `currency`            VARCHAR(3) NOT NULL DEFAULT 'CNY',
  `fee_amount`          DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '渠道手续费 (支付完成后回填)',
  `net_amount`          DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '净收入 = amount - fee_amount',

  -- 状态机
  `status`              VARCHAR(20) NOT NULL DEFAULT 'created' COMMENT 'created/paying/paid/closed/refunding/refunded/failed',
  `paid_at`             DATETIME(3) DEFAULT NULL,
  `closed_at`           DATETIME(3) DEFAULT NULL,
  `expires_at`          DATETIME(3) NOT NULL COMMENT '订单过期时间 (默认 30min)',
  `close_reason`        VARCHAR(200) DEFAULT NULL COMMENT '关闭原因: timeout/user_cancel/refund_all',

  -- 回调
  `notify_url`          VARCHAR(512) NOT NULL COMMENT '业务方回调地址 (支付成功/退款成功后 POST)',
  `notify_count`        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已通知次数',
  `last_notify_at`      DATETIME(3) DEFAULT NULL,
  `last_notify_result`  VARCHAR(200) DEFAULT NULL COMMENT '上次通知 HTTP 状态 + 响应体摘要',

  -- 扩展
  `region`              VARCHAR(10) NOT NULL DEFAULT 'cn' COMMENT 'cn/sg/us',
  `remark`              VARCHAR(500) DEFAULT NULL,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`           BIGINT UNSIGNED DEFAULT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`           BIGINT UNSIGNED DEFAULT NULL,
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  `revision`            INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_business` (`business_type`, `business_order_no`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_channel_trade` (`channel_type`, `channel_trade_no`),
  KEY `idx_expires` (`status`, `expires_at`),
  KEY `idx_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='支付订单 (公共服务)';


-- -----------------------------------------------------------------------------
-- 9.2 支付渠道配置
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_payment_channel`;
CREATE TABLE `ai_payment_channel` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel_code`        VARCHAR(30) NOT NULL COMMENT '渠道唯一编码: wechat_native / stripe_checkout / paddle / bank_transfer',
  `channel_name`        VARCHAR(64) NOT NULL COMMENT '渠道展示名: 微信支付 Native / Stripe Checkout',
  `channel_category`    VARCHAR(20) NOT NULL COMMENT '支付类型: pay_in / pay_out (收款 / 打款)',
  `region`              VARCHAR(10) NOT NULL COMMENT '该渠道可用的 Region: cn / sg / us / *',

  `enabled`             TINYINT NOT NULL DEFAULT 1,
  `sort_order`          INT NOT NULL DEFAULT 0,
  `fee_rate`            DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '渠道手续费率 (小数，如 0.006 = 0.6%)',
  `fee_fixed`           DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '固定手续费 (叠加在 fee_rate 上)',
  `min_amount`          DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '最小收款金额',
  `max_amount`          DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '0=不限',
  `supported_currency`  VARCHAR(200) DEFAULT NULL COMMENT '支持的币种列表 (逗号分隔，NULL=不限)',

  -- 加密存储的敏感配置 (AES-GCM 加密后存入)
  `config_json`         TEXT DEFAULT NULL COMMENT '加密存储的渠道配置: app_id / app_secret / private_key / webhook_secret 等',

  -- 回调安全
  `signature_field`     VARCHAR(64) DEFAULT NULL COMMENT '第三方签名头名称 (如 Wechatpay-Signature / Stripe-Signature)',
  `webhook_url`         VARCHAR(512) DEFAULT NULL COMMENT '平台在第三方配置的 webhook 地址',

  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`           BIGINT UNSIGNED DEFAULT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`           BIGINT UNSIGNED DEFAULT NULL,
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  `revision`            INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_region` (`channel_code`, `region`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='支付渠道配置';


-- -----------------------------------------------------------------------------
-- 9.3 退款单
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_payment_refund`;
CREATE TABLE `ai_payment_refund` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `refund_no`           VARCHAR(32) NOT NULL COMMENT '退款单号 (雪花ID)',
  `payment_order_no`    VARCHAR(32) NOT NULL COMMENT '关联 ai_payment_order.order_no',
  `business_order_no`   VARCHAR(32) NOT NULL COMMENT '业务侧原订单号',
  `business_type`       VARCHAR(30) NOT NULL,

  `channel_type`        VARCHAR(30) NOT NULL COMMENT '与支付单一致的渠道',
  `channel_refund_no`   VARCHAR(128) DEFAULT NULL COMMENT '第三方退款流水号',
  `refund_amount`       DECIMAL(14,2) NOT NULL COMMENT '本次退款金额 (原币)',
  `currency`            VARCHAR(3) NOT NULL,
  `total_refunded`      DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '该支付单累计已退款金额 (含本次)',

  `reason`              VARCHAR(500) DEFAULT NULL COMMENT '退款原因',
  `refund_type`         VARCHAR(20) NOT NULL DEFAULT 'full' COMMENT 'full 全额 / partial 部分 / auto 自动 (系统触发)',

  -- 状态
  `status`              VARCHAR(20) NOT NULL DEFAULT 'created' COMMENT 'created/refunding/success/failed',
  `requested_by`        BIGINT UNSIGNED DEFAULT NULL COMMENT '申请退款的用户 (业务方)',
  `reviewed_by`         BIGINT UNSIGNED DEFAULT NULL COMMENT '审核退款的管理员',
  `reviewed_at`         DATETIME(3) DEFAULT NULL,
  `success_at`          DATETIME(3) DEFAULT NULL,
  `failure_reason`      VARCHAR(500) DEFAULT NULL COMMENT '退款失败原因 (渠道返回)',

  -- 回调
  `notify_url`          VARCHAR(512) NOT NULL COMMENT '业务方退款回调地址',
  `notify_count`        INT UNSIGNED NOT NULL DEFAULT 0,

  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_by`           BIGINT UNSIGNED DEFAULT NULL,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_by`           BIGINT UNSIGNED DEFAULT NULL,
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `del_flag`            TINYINT NOT NULL DEFAULT 0,
  `revision`            INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_payment` (`payment_order_no`),
  KEY `idx_business` (`business_type`, `business_order_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='退款单';


-- -----------------------------------------------------------------------------
-- 9.4 每日对账 (XXL-JOB 跑批 + 云厂商账单拉取)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_payment_settlement`;
CREATE TABLE `ai_payment_settlement` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `settle_date`         DATE NOT NULL COMMENT '对账日期 (T+1 跑 T 的数据)',
  `channel_type`        VARCHAR(30) NOT NULL COMMENT '按渠道分表统计',
  `region`              VARCHAR(10) NOT NULL DEFAULT 'cn',
  `currency`            VARCHAR(3) NOT NULL DEFAULT 'CNY',

  -- 汇总数据
  `total_orders`        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已支付订单数',
  `total_amount`        DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '当日收款总额',
  `total_fee`           DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '当日手续费总额',
  `net_amount`          DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '当日净收入 = total_amount - total_fee',
  `refund_count`        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日退款单数',
  `refund_amount`       DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '当日退款总额',

  -- 云厂商账单拉取
  `cloud_bill_id`       VARCHAR(128) DEFAULT NULL COMMENT '云厂商账单文件ID (如 Stripe report_run_id)',
  `cloud_total_amount`  DECIMAL(14,2) DEFAULT NULL COMMENT '云厂商账单的收款总额 (拉取后回填)',
  `cloud_total_fee`     DECIMAL(14,2) DEFAULT NULL COMMENT '云厂商账单的手续费总额',

  -- 对账结果
  `status`              VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/done/discrepancy',
  `discrepancy_amount`  DECIMAL(14,2) DEFAULT NULL COMMENT '差异金额 (仅 discrepancy 时有值)',
  `discrepancy_detail`  JSON DEFAULT NULL COMMENT '差异详情: [{order_no, system_amount, cloud_amount}]',

  `executed_at`         DATETIME(3) DEFAULT NULL,
  `executed_by`         BIGINT UNSIGNED DEFAULT NULL COMMENT '定时任务固定填 system',
  `remark`              VARCHAR(500) DEFAULT NULL,
  `tenant_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settle_key` (`settle_date`, `channel_type`, `region`, `currency`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4_unicode_ci COMMENT='每日对账';


SET FOREIGN_KEY_CHECKS = 1;
