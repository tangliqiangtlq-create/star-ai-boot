-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 05 · 计费订阅域 + 团队管理域 ai_* (9 张表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策:
--   Q10 团队预算双维度 (team 总限额 + member 个人限额)
--   Q11 ai_recharge.order_no 唯一约束做幂等 (已足够)
--   Q12 ai_billing 余额更新用 version CAS (无锁高吞吐)
-- =============================================================================
-- 表关系:
--   ai_billing 钱包 (user_id 或 team_id 二选一)
--     1:N ai_usage_record (所有余额变动流水)
--     1:N ai_recharge     (充值订单)
--     1:N ai_subscription (订阅套餐)
--   ai_price_rule 多维度定价 (被 billing 在扣费计算时引用)
--
--   ai_team 团队 (= 租户内组织单元, 不是租户)
--     1:N ai_team_member
--     1:N ai_team_role_permission (团队自定义 RBAC)
--     1:N ai_team_budget_log
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- -----------------------------------------------------------------------------
-- 5.1 ai_price_rule · 多维度定价规则 (一张表覆盖所有定价维度)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_price_rule`;
CREATE TABLE `ai_price_rule` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL DEFAULT 0                                    COMMENT '租户ID (0=平台级默认价, 所有租户共享; >0=租户自定义价)',
  `channel_id`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '通道ID (NULL=所有通道通用, 可做通道差异定价)',
  `provider_id`            BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '供应商ID (冗余自 channel, 或直接指定)',
  `model_id`               BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '模型ID (NULL=所有模型通用, 精确到模型定价)',
  `task_type`              VARCHAR(30)      DEFAULT NULL                                          COMMENT '任务类型 (t2v/i2v/upscale 等, NULL=所有通用)',
  `resolution`             VARCHAR(20)      DEFAULT NULL                                          COMMENT '分辨率 (NULL=所有通用, 不同分辨率定价不同)',
  `aspect_ratio`           VARCHAR(20)      DEFAULT NULL                                          COMMENT '宽高比 (NULL=所有通用, 9:16 可能更贵)',
  `duration_bucket_start_sec` INT UNSIGNED   DEFAULT NULL                                          COMMENT '时长桶起点 (秒, NULL=不限起点)',
  `duration_bucket_end_sec`   INT UNSIGNED   DEFAULT NULL                                          COMMENT '时长桶终点 (秒, NULL=不限终点)',
  `duration_bucket_label`    VARCHAR(50)    DEFAULT NULL                                          COMMENT '时长桶标签 (展示用, 如 "0-10s / 10-30s / 30-60s / 60s+")',

  -- ====== **计费单位灵活支持 (G1 关键补项, 之前硬编码 per_sec)** ======
  `billing_unit`             VARCHAR(20)    NOT NULL DEFAULT 'per_second'                         COMMENT '**计费单位**: per_second=按秒 (wan2.6/可灵), per_call=按次 (Runway Gen-3 固定每条), per_token=按 token (LLM), per_pixel_mp=按像素/百万 (Stability AI), per_character=按字符 (部分 TTS), per_minute=按分钟 (长音频)',
  `price_value`              DECIMAL(14,6) NOT NULL                                               COMMENT '**单价 (含义取决于 billing_unit)**: per_second 时 = 每秒 Credits, per_call 时 = 每次 Credits, per_token 时 = 每千 token Credits, per_pixel_mp 时 = 每百万像素 Credits, per_character 时 = 每千字符 Credits, per_minute 时 = 每分钟 Credits',
  `pixel_coef`               DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT '像素系数 (per_pixel_mp 模式下: 实际像素/1,000,000, 由后端根据 output_width × output_height 自动计算, 写在规则里只是兜底)',
  `token_coef`               DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT 'token 系数 (per_token 模式下: 实际 token 数/1000, 兜底用)',
  `price_formula_hint`       VARCHAR(200)   DEFAULT NULL                                          COMMENT '价格计算公式提示 (后端计费引擎按此公式计算, 如 "price_value × duration_sec × resolution_coef × discount_rate" / "price_value × pixel_coef × discount_rate" / "price_value × discount_rate")',

  -- ====== 以下字段保留向后兼容, 新写入统一用上面 billing_unit + price_value ======
  `unit_price_per_sec`       DECIMAL(12,6) DEFAULT NULL                                          COMMENT '**[Deprecated] 每秒基础价格 (Credits)** → 迁移到 price_value + billing_unit=per_second, 保留 3 个版本后物理删除',

  `resolution_coef`          DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT '分辨率系数 (乘到 price_value 上, 720p=0.8, 4K=2.0, 所有 billing_unit 模式通用)',
  `aspect_ratio_coef`        DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT '宽高比系数 (9:16 竖屏可能 1.2)',
  `ref_image_coef`           DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT '参考图数量系数 (每多一张参考图加价, 如 2 张=1.3, 3 张=1.5)',
  `audio_input_coef`         DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT '音频输入系数 (t2v 带音频 track 时加价)',
  `style_coef`               DECIMAL(8,4)  NOT NULL DEFAULT 1.0000                                COMMENT '风格系数 (某些风格/模型变体溢价, 如写实=1.2, 动漫=0.9)',

  `min_price`                DECIMAL(14,4) DEFAULT NULL                                          COMMENT '最低消费 (Credits, 一次生成的计费金额不低于此值)',
  `max_price`                DECIMAL(14,4) DEFAULT NULL                                          COMMENT '最高消费 (Credits, 封顶保护, 异常生成不会让用户破产)',
  `price_cap_basis`          VARCHAR(30)    DEFAULT NULL                                          COMMENT '封顶基准: generation_total=按单次生成封顶, shot_total=按镜头累计封顶 (批量时)',

  `discount_rate`            DECIMAL(5,4)  NOT NULL DEFAULT 1.0000                                COMMENT '折扣率 (0.8000=8折, 1.0000=不打折)',
  `discount_type`            TINYINT       NOT NULL DEFAULT 0                                    COMMENT '折扣类型: 0=无条件折扣, 1=批量折扣(>=N次触发), 2=时长折扣(>=Ns触发)',
  `discount_threshold`       INT UNSIGNED  DEFAULT NULL                                          COMMENT '折扣阈值 (discount_type=1 时为次数, =2 时为秒数)',
  `effective_from`           DATETIME(3)   DEFAULT NULL                                          COMMENT '生效起始时间 (NULL=立即生效)',
  `effective_to`             DATETIME(3)   DEFAULT NULL                                          COMMENT '生效截止时间 (NULL=永久生效)',
  `priority`                 INT           NOT NULL DEFAULT 0                                    COMMENT '优先级 (数值越大越优先匹配, 0=兜底默认价)',
  `match_examples`           VARCHAR(1000) DEFAULT NULL                                          COMMENT '匹配示例 (JSON, 如 [{"model":"wan2.6-t2v","resolution":"1080p","duration":30,"price":30}])',
  `rule_code`                VARCHAR(64)   DEFAULT NULL                                          COMMENT '规则编码 (业务引用, 如 2026春节促销-万相系列)',
  `rule_name`                VARCHAR(200)  NOT NULL                                              COMMENT '规则名称 (展示给运营/财务看)',
  `rule_description`         VARCHAR(2000) DEFAULT NULL                                          COMMENT '规则描述 (运营注释, 解释为什么这么定价)',
  `enabled`                  TINYINT       NOT NULL DEFAULT 1                                    COMMENT '是否启用: 0=禁用, 1=启用',
  `create_by`                BIGINT UNSIGNED DEFAULT NULL                                        COMMENT '创建人ID',
  `create_time`              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_by`                BIGINT UNSIGNED DEFAULT NULL                                        COMMENT '最后更新人ID',
  `update_time`              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`                 TINYINT       NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`                 INT UNSIGNED  NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_model` (`tenant_id`, `model_id`),
  KEY `idx_model_resolution` (`model_id`, `resolution`),
  KEY `idx_task_type_resolution` (`task_type`, `resolution`),
  KEY `idx_effective` (`effective_from`, `effective_to`),
  KEY `idx_priority_enabled` (`priority`, `enabled`),
  KEY `idx_channel` (`channel_id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_billing_unit` (`billing_unit`),                                                              COMMENT '按计费单位扫描',
  CONSTRAINT `chk_billing_unit` CHECK (`billing_unit` IN ('per_second','per_call','per_token','per_pixel_mp','per_character','per_minute'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='多维度定价规则 (v1.2 支持 6 种计费单位: per_second/per_call/per_token/per_pixel_mp/per_character/per_minute; 一张表覆盖所有维度, 支持阶梯价/促销折扣/时间窗/分辨率/宽高比/参考图数量/音频输入/风格差异定价)';


-- -----------------------------------------------------------------------------
-- 5.2 ai_billing · 账户钱包 (核心资金账户, 一个 user 或 team 对应一条)
-- -----------------------------------------------------------------------------
-- 高并发余额更新: version CAS + 3 次重试
--   UPDATE ai_billing
--   SET credits_balance = credits_balance - ?,
--       credits_frozen  = credits_frozen  + ?,
--       version = version + 1
--   WHERE id = ? AND version = ? AND credits_balance >= ?;
--   -- 影响 1 行 = 成功; 影响 0 行 = 被并发冲, Java 层重试
-- 双向归属约束: user_id 和 team_id 二选一 (一个 Billing 只能属于一个主体)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_billing`;
CREATE TABLE `ai_billing` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `user_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '归属用户ID (个人账户: 有值; 团队账户: NULL, team_id 必填)',
  `team_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '归属团队ID (团队共享账户: 有值; 个人账户: NULL, user_id 必填)',
  `credits_balance`        DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '**可用余额 (Credits)**',
  `credits_frozen`         DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '**冻结金额** (生成任务预扣后冻结, 结算后转到 consumed 或 refunded)',
  `credits_total_recharged`  DECIMAL(16,4) NOT NULL DEFAULT 0.0000                              COMMENT '历史累计充值',
  `credits_total_consumed`  DECIMAL(16,4) NOT NULL DEFAULT 0.0000                              COMMENT '历史累计消费 (实扣, 不含预扣)',
  `credits_total_refunded`  DECIMAL(16,4) NOT NULL DEFAULT 0.0000                              COMMENT '历史累计退款',
  `credits_total_bonus`     DECIMAL(16,4) NOT NULL DEFAULT 0.0000                              COMMENT '历史累计赠送 (充值赠送/活动赠送)',
  `credits_total_revoked`   DECIMAL(16,4) NOT NULL DEFAULT 0.0000                              COMMENT '历史累计作废 (订阅到期/赠送过期)',
  `overdraft_limit`       DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '透支上限 (默认 0, 即禁止透支; 大客户可开放小额透支)',
  `currency`               VARCHAR(10)      NOT NULL DEFAULT 'CREDITS'                            COMMENT '记账单位 (平台统一用 Credits, 对外展示可换算成 USD/CNY)',
  `wallet_status`          TINYINT          NOT NULL DEFAULT 1                                    COMMENT '钱包状态: 0=禁用(冻结所有扣费), 1=正常, 2=欠费锁定 (余额为负且超透支限额)',
  `threshold_warn`         DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '余额阈值告警线 (低于此值触发邮件/Webhook 告警)',
  `threshold_critical`     DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '余额严重告警线 (低于此值禁用生成任务)',
  `last_alert_time`        DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次告警时间 (防骚扰, 两次告警间隔 >= 30min)',
  `alert_cooldown_minutes` INT UNSIGNED    NOT NULL DEFAULT 30                                    COMMENT '告警冷却时间 (分钟, 防刷屏)',
  `monthly_consumed_this`  DECIMAL(14,4)   NOT NULL DEFAULT 0.0000                               COMMENT '本月已消费 (Credits, 每月 1 号 0 点重置)',
  `monthly_reset_time`     DATETIME(3)      DEFAULT NULL                                          COMMENT '月度重置时间 (cron 重置后更新)',
  `total_transaction_count` BIGINT UNSIGNED NOT NULL DEFAULT 0                                     COMMENT '累计交易笔数 (充值+扣费+退款, 对账参考)',
  `last_transaction_time`  DATETIME(3)      DEFAULT NULL                                          COMMENT '最后交易时间',
  `version`                INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '**余额版本号 (CAS 更新关键, 每次余额变动 +1)**',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展 (JSON: 开户时间/客户经理/结算周期/自动充值配置)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user_or_team` (`tenant_id`, `user_id`, `team_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_team` (`team_id`),
  KEY `idx_status` (`wallet_status`),
  KEY `idx_balance_threshold` (`credits_balance`, `threshold_warn`),                          COMMENT '余额告警扫描',
  CONSTRAINT `chk_wallet_status` CHECK (`wallet_status` IN (0,1,2)),
  CONSTRAINT `chk_currency` CHECK (`currency` = 'CREDITS')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='账户钱包 (核心资金账户, user_id 或 team_id 二选一, 高并发余额更新用 version CAS + 3 次重试)';


-- -----------------------------------------------------------------------------
-- 5.3 ai_usage_record · 计费流水 (所有余额变动的不可变账本)
-- -----------------------------------------------------------------------------
-- 不可变: 插入后永不 UPDATE, 退款会新增一条反向流水 (direction=-1)
-- 双向关联业务: ref_type + ref_id 指向具体业务对象
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_usage_record`;
CREATE TABLE `ai_usage_record` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `billing_id`             BIGINT UNSIGNED  NOT NULL                                              COMMENT '钱包账户ID',
  `user_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '归属用户ID (冗余自 billing, 便于直接查询)',
  `team_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '归属团队ID (冗余自 billing)',
  `transaction_no`         VARCHAR(64)      NOT NULL                                              COMMENT '**交易流水号 (全局唯一, 幂等键, 如 TXN-20261007-00001)**',
  `transaction_type`       TINYINT          NOT NULL                                              COMMENT '交易类型: 1=充值, 2=生成扣费, 3=工具链扣费, 4=退款, 5=活动赠送, 6=人工调整, 7=订阅扣费, 8=订阅到期作废, 9=批量扣费合并',
  `direction`              TINYINT          NOT NULL                                              COMMENT '资金方向: 1=入账(余额增加), -1=出账(余额减少)',
  `amount`                 DECIMAL(16,4)   NOT NULL                                              COMMENT '变动金额 (Credits, 正数, 实际增减看 direction)',
  `balance_before`         DECIMAL(16,4)   NOT NULL                                              COMMENT '变动前余额',
  `balance_after`          DECIMAL(16,4)   NOT NULL                                              COMMENT '变动后余额',
  `frozen_before`          DECIMAL(16,4)   NOT NULL DEFAULT 0                                    COMMENT '变动前冻结额',
  `frozen_after`           DECIMAL(16,4)   NOT NULL DEFAULT 0                                    COMMENT '变动后冻结额',
  `ref_type`               VARCHAR(30)      DEFAULT NULL                                          COMMENT '业务来源类型: generation / recharge / subscription / batch / gift / manual_adjust / system_correct / expire_revoke',
  `ref_id`                 BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '业务来源 ID (ai_generation.id / ai_recharge.id / ai_subscription.id / ai_generation_batch.id 等)',
  `related_transaction_no` VARCHAR(64)      DEFAULT NULL                                          COMMENT '关联流水号 (退款时指向原始扣费流水, 冲正指向被冲正流水)',
  `description`            VARCHAR(500)     NOT NULL                                              COMMENT '交易摘要 (如 视频生成 wan2.6-t2v×30s / 充值 $100 / 退款 generation-12345)',
  `detail_json`            JSON             DEFAULT NULL                                          COMMENT '详细信息 (JSON: 价格明细 单价×分辨率系数×时长秒×折扣/供应商/模型/通道/分辨率/时长秒数)',
  `model_id`               BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联模型ID (扣费类)',
  `channel_id`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联通道ID (扣费类)',
  `generation_id`          BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联 generation ID (扣费类, 冗余自 ref_id 便于直接查)',
  `batch_id`               BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联 batch ID (批量扣费合并类)',
  `created_by_system`      TINYINT          NOT NULL DEFAULT 1                                    COMMENT '创建来源: 1=系统自动 (扣费/退款), 0=人工调整',
  `operator_id`            BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '操作人ID (人工调整时必填, 系统自动时为 NULL)',
  `operator_ip`            VARCHAR(64)      DEFAULT NULL                                          COMMENT '操作人 IP (人工调整时审计)',
  `status`                 TINYINT          NOT NULL DEFAULT 1                                    COMMENT '状态: 0=已撤销(极少用, 退款走反向流水), 1=已入账',
  `occurred_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '**业务发生时间** (不是 INSERT 时间, 可能和 create_time 有偏差, 如凌晨批量定时扣款)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间 (INSERT 时间)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  KEY `idx_billing_time` (`billing_id`, `occurred_at`),
  KEY `idx_billing_type_time` (`billing_id`, `transaction_type`, `occurred_at`),
  KEY `idx_ref` (`ref_type`, `ref_id`),
  KEY `idx_user_team` (`user_id`, `team_id`),
  KEY `idx_occurred_at` (`occurred_at`),
  KEY `idx_model` (`model_id`),
  KEY `idx_related` (`related_transaction_no`),
  CONSTRAINT `chk_direction` CHECK (`direction` IN (-1, 1)),
  CONSTRAINT `chk_transaction_type` CHECK (`transaction_type` IN (1,2,3,4,5,6,7,8,9))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='计费流水 (不可变账本, 所有余额变动必写一条, 双向关联具体业务对象, 对账源数据)';


-- -----------------------------------------------------------------------------
-- 5.4 ai_recharge · 充值订单
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_recharge`;
CREATE TABLE `ai_recharge` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `user_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '充值方用户ID',
  `team_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '充值方团队ID (团队账户充值)',
  `order_no`               VARCHAR(64)      NOT NULL                                              COMMENT '**充值订单号 (全局唯一, 幂等键)**',
  `pay_method`             TINYINT          NOT NULL                                              COMMENT '支付方式: 1=微信支付, 2=支付宝, 3=对公打款, 4=API Key 预付费, 5=人工赠送, 6=订阅自动扣费, 7=Apple IAP, 8=Stripe',
  `pay_method_label`       VARCHAR(30)      DEFAULT NULL                                          COMMENT '支付方式展示名 (冗余: 微信支付/支付宝/对公转账)',
  `amount_money`           DECIMAL(12,2)   NOT NULL                                              COMMENT '实际支付金额 (CNY/USD/原币种)',
  `money_currency`         VARCHAR(3)       NOT NULL DEFAULT 'CNY'                                COMMENT '货币代码 (ISO 4217: CNY/USD/EUR)',
  `credits_granted`        DECIMAL(16,4)   NOT NULL                                              COMMENT '**到账 Credits (已含赠送)**',
  `credits_bonus`          DECIMAL(16,4)   NOT NULL DEFAULT 0                                    COMMENT '本次赠送 Credits (不含基础到账)',
  `credits_base`           DECIMAL(16,4)   NOT NULL                                              COMMENT '基础到账 Credits (纯兑换, 不含赠送)',
  `exchange_rate`          DECIMAL(12,6)   NOT NULL                                              COMMENT '兑换比率 (1 money_currency = ? Credits, 精确到小数点 6 位)',
  `discount_code`          VARCHAR(64)      DEFAULT NULL                                          COMMENT '优惠码 (使用过则记录, 便于审计)',
  `discount_amount`        DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '优惠金额 (Credits)',
  `payment_status`         TINYINT          NOT NULL DEFAULT 0                                    COMMENT '支付状态: 0=待支付, 1=支付成功, 2=支付失败, 3=已取消, 4=已全额退款, 5=已部分退款',
  `payment_time`           DATETIME(3)      DEFAULT NULL                                          COMMENT '支付完成时间',
  `payment_expire_time`    DATETIME(3)      DEFAULT NULL                                          COMMENT '支付截止时间 (超过后自动关单)',
  `third_party_order_no`   VARCHAR(128)     DEFAULT NULL                                          COMMENT '第三方支付单号 (微信/支付宝/Stripe 的 transaction_id)',
  `third_party_status`     VARCHAR(64)      DEFAULT NULL                                          COMMENT '第三方原始状态码',
  `third_party_notify_raw` LONGTEXT         DEFAULT NULL                                          COMMENT '第三方回调原文 (加密后存储, 审计用, 可能多次回调均保留最新)',
  `third_party_notify_time` DATETIME(3)     DEFAULT NULL                                          COMMENT '最后一次回调时间',
  `refund_amount_money`    DECIMAL(12,2)   NOT NULL DEFAULT 0.00                                 COMMENT '已退款金额 (money_currency)',
  `refund_amount_credits`  DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '已退款 Credits',
  `refund_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '最后退款时间',
  `invoice_status`         TINYINT          NOT NULL DEFAULT 0                                    COMMENT '发票状态: 0=不开票, 1=待开票, 2=已开票, 3=已红冲',
  `invoice_info_json`      JSON             DEFAULT NULL                                          COMMENT '发票信息 (JSON: 抬头/税号/开票主体/地址电话/开票金额)',
  `invoice_file_url`       VARCHAR(1000)    DEFAULT NULL                                          COMMENT '电子发票 PDF 地址',
  `notes`                  VARCHAR(500)     DEFAULT NULL                                          COMMENT '备注 (对公打款的汇款单号/人工赠送的审批单号)',
  `created_by_system`      TINYINT          NOT NULL DEFAULT 1                                    COMMENT '创建来源: 1=系统, 0=人工后台创建',
  `operator_id`            BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '操作人ID (人工创建/调整时)',
  `related_subscription_id` BIGINT UNSIGNED DEFAULT NULL                                        COMMENT '关联订阅ID (pay_method=6 自动扣费时)',
  `transaction_no`         VARCHAR(64)      DEFAULT NULL                                          COMMENT '关联到 ai_usage_record 的 transaction_no (支付成功写入后回填)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_third_party_order_no` (`third_party_order_no`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_tenant_team` (`tenant_id`, `team_id`),
  KEY `idx_payment_status` (`payment_status`),
  KEY `idx_payment_expire` (`payment_expire_time`),                                         COMMENT '定时关单扫描',
  KEY `idx_invoice_status` (`invoice_status`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `chk_pay_method` CHECK (`pay_method` IN (1,2,3,4,5,6,7,8)),
  CONSTRAINT `chk_payment_status` CHECK (`payment_status` IN (0,1,2,3,4,5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='充值订单 (第三方回调唯一约束天然幂等, 原始回调 JSON 完整保留审计)';


-- -----------------------------------------------------------------------------
-- 5.5 ai_subscription · 订阅套餐
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_subscription`;
CREATE TABLE `ai_subscription` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `user_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '订阅人用户ID',
  `team_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '订阅人团队ID',
  `plan_code`              VARCHAR(64)      NOT NULL                                              COMMENT '套餐编码 (平台预设, 如 BASIC-MONTHLY / PRO-YEARLY)',
  `plan_name`              VARCHAR(100)     NOT NULL                                              COMMENT '套餐展示名 (如 Pro 年付)',
  `plan_description`       VARCHAR(1000)    DEFAULT NULL                                          COMMENT '套餐描述 (展示给用户)',
  `billing_cycle`          TINYINT          NOT NULL                                              COMMENT '计费周期: 1=月付, 2=季付, 3=年付, 4=一次性(PPE)',
  `price_money`            DECIMAL(12,2)   NOT NULL                                              COMMENT '周期价格 (money_currency)',
  `money_currency`         VARCHAR(3)       NOT NULL DEFAULT 'CNY'                                COMMENT '货币',
  `credits_per_cycle`      DECIMAL(16,4)   NOT NULL                                              COMMENT '每周期到账 Credits',
  `credits_bonus`          DECIMAL(16,4)   NOT NULL DEFAULT 0                                    COMMENT '首充/新用户赠送 Credits (仅首个周期)',
  `features_json`          JSON             DEFAULT NULL                                          COMMENT '套餐权益 (JSON: 角色数/项目数/API 并发数/专属通道/优先队列等)',
  `status`                 TINYINT          NOT NULL DEFAULT 0                                    COMMENT '状态: 0=待生效, 1=生效中, 2=已过期, 3=已取消, 4=支付失败, 5=宽限期',
  `start_time`             DATETIME(3)      DEFAULT NULL                                          COMMENT '生效起始时间',
  `end_time`               DATETIME(3)      DEFAULT NULL                                          COMMENT '当前周期到期时间',
  `auto_renew`             TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否自动续费: 0=手动, 1=自动',
  `last_renew_time`        DATETIME(3)      DEFAULT NULL                                          COMMENT '上次续费时间',
  `next_renew_time`        DATETIME(3)      DEFAULT NULL                                          COMMENT '下次续费时间',
  `renew_count`            INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '已续费次数',
  `last_payment_time`      DATETIME(3)      DEFAULT NULL                                          COMMENT '上次扣费时间',
  `last_recharge_id`       BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '上次扣费对应的 recharge.id',
  `grace_until`            DATETIME(3)      DEFAULT NULL                                          COMMENT '宽限期截止 (到期后有 3-7 天宽限期, 期间功能降级但不立刻锁账户)',
  `grace_days`             INT UNSIGNED     NOT NULL DEFAULT 3                                    COMMENT '宽限期天数',
  `cancel_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '取消时间',
  `cancel_reason`          VARCHAR(500)     DEFAULT NULL                                          COMMENT '取消原因 (用户填/客服记录)',
  `cancel_at_period_end`   TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否到期取消: 0=立即取消, 1=周期结束后取消',
  `trial_start_time`       DATETIME(3)      DEFAULT NULL                                          COMMENT '试用开始时间 (新用户 7 天免费)',
  `trial_end_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '试用结束时间',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`               INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_user` (`tenant_id`, `user_id`),
  KEY `idx_tenant_team` (`tenant_id`, `team_id`),
  KEY `idx_status_end` (`status`, `end_time`),
  KEY `idx_next_renew` (`next_renew_time`),
  KEY `idx_grace_until` (`grace_until`),
  KEY `idx_trial_end` (`trial_end_time`),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2,3,4,5)),
  CONSTRAINT `chk_billing_cycle` CHECK (`billing_cycle` IN (1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='订阅套餐 (支持月/季/年付 + 自动续费 + 宽限期 + 试用 + 到期取消)';


-- -----------------------------------------------------------------------------
-- 5.6 ai_team · 团队 (租户内组织单元, 不是租户本身)
-- -----------------------------------------------------------------------------
-- 租户 = 一个 SaaS 客户 (可能是一家公司)
-- 团队 = 租户内的部门/小组/项目组 (一个租户下可以多个团队)
-- 一个用户可以同时属于多个团队
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_team`;
CREATE TABLE `ai_team` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `team_name`              VARCHAR(100)     NOT NULL                                              COMMENT '团队名称 (租户内唯一)',
  `team_code`              VARCHAR(64)      DEFAULT NULL                                          COMMENT '团队编码 (业务引用)',
  `description`            VARCHAR(1000)    DEFAULT NULL                                          COMMENT '团队描述',
  `icon`                   VARCHAR(100)     DEFAULT NULL                                          COMMENT '图标名 (emoji 或组件图标)',
  `cover_url`              VARCHAR(500)     DEFAULT NULL                                          COMMENT '封面图',
  `leader_user_id`         BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '团队负责人 (owner, 必有全量权限)',
  `member_count`           INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '成员数量 (异步维护)',
  `budget_limit`           DECIMAL(16,2)   DEFAULT NULL                                          COMMENT '团队月度预算上限 (Credits, NULL=不限制)',
  `budget_used_month`      DECIMAL(16,4)   NOT NULL DEFAULT 0                                    COMMENT '本月已用预算 (Credits, 每月 1 号重置)',
  `budget_reset_time`      DATETIME(3)      DEFAULT NULL                                          COMMENT '预算下次重置时间',
  `budget_alert_percent`   INT              NOT NULL DEFAULT 80                                    COMMENT '预算告警阈值 (百分比)',
  `budget_warn_last_time`  DATETIME(3)      DEFAULT NULL                                          COMMENT '预算告警上次触发时间 (防骚扰)',
  `sharing_mode`           TINYINT          NOT NULL DEFAULT 0                                    COMMENT '协作模式: 0=独立账户 (每人独立 billing), 1=共享钱包 (团队统一 billing)',
  `billing_id`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '团队共享钱包 (sharing_mode=1 时必填, 指向 ai_billing)',
  `billing_priority`       TINYINT          NOT NULL DEFAULT 2                                    COMMENT '**扣费优先级策略** (团队内生成视频时扣谁的钱): 0=仅团队钱包(团队没钱则拒绝), 1=团队优先 个人兜底(团队没钱自动回退到个人), 2=仅个人钱包(团队共享钱包模式也只扣个人), 3=个人优先 团队兜底(先扣个人, 不足再扣团队)',
  `allow_mixed_billing`    TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否允许**一笔生成跨两个钱包分扣**: 0=不允许(不足则拒单), 1=允许(个人出多少+团队出多少凑够)',
  `mixed_billing_threshold` DECIMAL(14,4)  DEFAULT NULL                                          COMMENT '分扣阈值 (Credits, 仅 allow_mixed_billing=1 时生效: 差额>此阈值才允许分扣, 否则拒单)',
  `billing_confirm_mode`   TINYINT          NOT NULL DEFAULT 1                                    COMMENT '扣费前是否弹窗确认: 0=不确认(直接扣), 1=金额>阈值时确认, 2=每次都确认',
  `billing_confirm_threshold` DECIMAL(14,4) DEFAULT 100.0000                                    COMMENT '弹窗确认金额阈值 (默认单次超过 100 Credits 弹确认框)',
  `billing_notify_member`  TINYINT          NOT NULL DEFAULT 1                                    COMMENT '扣费后是否给成员发通知: 0=不发, 1=超阈值时发, 2=每次都发',
  `billing_notify_threshold` DECIMAL(14,4) DEFAULT 50.0000                                     COMMENT '扣费通知金额阈值',
  `default_role_code`      VARCHAR(64)      DEFAULT 'member'                                      COMMENT '新成员默认角色 (team_role 定义)',
  `allow_member_invite`    TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否允许普通成员邀请: 0=仅 admin/owner, 1=所有成员',
  `allow_member_billing`   TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否允许普通成员充值: 0=仅 admin/owner, 1=所有成员',
  `allow_member_create_asset` TINYINT        NOT NULL DEFAULT 1                                    COMMENT '是否允许普通成员上传素材',
  `allow_member_view_budget` TINYINT         NOT NULL DEFAULT 0                                    COMMENT '是否允许普通成员查看团队预算',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展 (JSON: 协作偏好/通知设置/标签)',
  `status`                 TINYINT          NOT NULL DEFAULT 1                                    COMMENT '状态: 0=禁用, 1=正常, 2=已归档',
  `sort_no`                INT              NOT NULL DEFAULT 0                                    COMMENT '排序号',
  `create_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '创建人ID',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '最后更新人ID',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  `revision`               INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_team_name` (`tenant_id`, `team_name`, `del_flag`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_leader` (`leader_user_id`),
  KEY `idx_budget_limit` (`budget_limit`, `budget_used_month`),
  KEY `idx_billing` (`billing_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='团队 (租户内组织单元, 支持独立/共享钱包两种协作模式, 自带预算管控和自定义 RBAC)';


-- -----------------------------------------------------------------------------
-- 5.7 ai_team_member · 团队成员
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_team_member`;
CREATE TABLE `ai_team_member` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `team_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '团队ID',
  `user_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '用户ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID (冗余自 team, 便于快速查询)',
  `member_role`            TINYINT          NOT NULL DEFAULT 3                                    COMMENT '成员角色 (内置): 1=owner(所有者, 只有1人), 2=admin(管理员), 3=member(普通成员), 4=viewer(只读)',
  `custom_role_code`       VARCHAR(64)      DEFAULT NULL                                          COMMENT '团队自定义角色编码 (ai_team_role_permission.role_code, 与 member_role 可叠加)',
  `nickname_in_team`       VARCHAR(50)      DEFAULT NULL                                          COMMENT '团队内昵称 (可选, 不同于账号昵称)',
  `position`               VARCHAR(50)      DEFAULT NULL                                          COMMENT '团队内职位 (如 导演/摄像/后期/制片人)',
  `cost_limit_per_month`   DECIMAL(14,2)   DEFAULT NULL                                          COMMENT '**个人月度消费限额** (Credits, Q10 双维度预算的成员侧, NULL=不限制)',
  `cost_used_month`        DECIMAL(14,4)   NOT NULL DEFAULT 0.0000                               COMMENT '本月已消费 (Credits, 每月 1 号重置)',
  `cost_reset_time`        DATETIME(3)      DEFAULT NULL                                          COMMENT '限额下次重置时间',
  `cost_limit_alert_percent` INT            NOT NULL DEFAULT 80                                    COMMENT '个人限额告警阈值',
  `cost_alert_last_time`   DATETIME(3)      DEFAULT NULL                                          COMMENT '上次触发个人限额告警时间',
  `status`                 TINYINT          NOT NULL DEFAULT 1                                    COMMENT '成员状态: 0=已踢出, 1=正常, 2=待接受邀请',
  `joined_time`            DATETIME(3)      DEFAULT NULL                                          COMMENT '加入时间 (接受邀请后)',
  `invited_by`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '邀请人用户ID',
  `invite_code`            VARCHAR(64)      DEFAULT NULL                                          COMMENT '邀请码 (接受时校验)',
  `impersonation_enabled`  TINYINT          NOT NULL DEFAULT 0                                    COMMENT '是否允许对此成员代登录 (owner/admin 可以开关)',
  `impersonation_count`    INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '累计被代登录次数 (审计)',
  `last_active_time`       DATETIME(3)      DEFAULT NULL                                          COMMENT '最后活跃时间',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展 (JSON: 标签/备注)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_user` (`team_id`, `user_id`, `del_flag`),
  KEY `idx_user` (`user_id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_cost_limit` (`cost_limit_per_month`, `cost_used_month`),
  KEY `idx_cost_reset` (`cost_reset_time`),
  CONSTRAINT `chk_member_role` CHECK (`member_role` IN (1,2,3,4)),
  CONSTRAINT `chk_status` CHECK (`status` IN (0,1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='团队成员 (含双维度预算: 团队总额 + 个人限额, 含代登录开关和审计)';


-- -----------------------------------------------------------------------------
-- 5.8 ai_team_role_permission · 团队自定义 RBAC (与平台 sys_role 无关)
-- -----------------------------------------------------------------------------
-- 团队可以定义自己的角色和权限点, 不影响平台级 sys_role
-- 权限点复用 sys_permission.permission_code (避免重复定义)
-- owner 永远有全量权限, 此表不包含 owner
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_team_role_permission`;
CREATE TABLE `ai_team_role_permission` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `team_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '团队ID',
  `role_code`              VARCHAR(64)      NOT NULL                                              COMMENT '团队自定义角色编码 (如 director / editor / finance)',
  `role_name`              VARCHAR(100)     NOT NULL                                              COMMENT '角色展示名',
  `role_type`              TINYINT          NOT NULL DEFAULT 1                                    COMMENT '角色类型: 0=团队内置 (不可删除), 1=团队自定义',
  `permission_code`        VARCHAR(128)     NOT NULL                                              COMMENT '权限编码 (复用 sys_permission.permission_code, 不重复造轮子)',
  `description`            VARCHAR(500)     DEFAULT NULL                                          COMMENT '角色描述',
  `granted_by`             BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '授权人用户ID (审计)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_role_permission` (`team_id`, `role_code`, `permission_code`),
  KEY `idx_team_role` (`team_id`, `role_code`),
  KEY `idx_permission` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='团队自定义 RBAC (权限编码复用 sys_permission, owner/admin 默认全量, 此表管理自定义角色和精细权限点)';


-- -----------------------------------------------------------------------------
-- 5.9 ai_team_budget_log · 团队预算流水 (月度重置/成员扣费合计)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_team_budget_log`;
CREATE TABLE `ai_team_budget_log` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `team_id`                BIGINT UNSIGNED  NOT NULL                                              COMMENT '团队ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `user_id`                BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联成员用户ID (member_consume 类型)',
  `log_type`               TINYINT          NOT NULL                                              COMMENT '类型: 1=月度重置, 2=成员扣费合计, 3=预算调整, 4=告警触发',
  `amount`                 DECIMAL(14,4)   NOT NULL                                              COMMENT '变动金额 (Credits, 正负看 direction)',
  `before_value`           DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '变动前 (总额或成员限额已用)',
  `after_value`            DECIMAL(14,4)   DEFAULT NULL                                          COMMENT '变动后',
  `description`            VARCHAR(500)     NOT NULL                                              COMMENT '描述 (如 2026年9月预算重置 / 张三本月消费 500 Credits / 预算从1000调到2000)',
  `ref_type`               VARCHAR(30)      DEFAULT NULL                                          COMMENT '关联业务类型',
  `ref_id`                 BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '关联业务 ID',
  `operator_id`            BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '操作人 (系统自动时为 NULL)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_team_time` (`team_id`, `create_time`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `chk_log_type` CHECK (`log_type` IN (1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='团队预算流水 (月度重置/成员扣费合计/预算调整/告警触发, 审计团队资金动向)';


-- -----------------------------------------------------------------------------
-- 5.10 ai_tenant_billing_summary · 租户级聚合统计 (高频访问, 事件驱动异步更新)
-- -----------------------------------------------------------------------------
-- 归属关系: 钱包主体是 user/team, 租户只是隔离单位
-- 但运营后台/租户控制台需要高频查询"租户总余额/总充值/总消费"
-- 实时聚合 ai_billing 太慢 (租户 1000+ 用户时), 故加此聚合表
--
-- 更新时机:
--   方案 A (推荐): ai_usage_record INSERT 后, Java 层事件驱动更新此表
--                  事务内: 先写 usage_record + billing, 再异步更新 summary
--   方案 B: 定时任务 (每分钟) 重新聚合, 简单但有 1min 延迟
--
-- 对账机制: 每日凌晨 cron 从 ai_billing 全量重算, 修正可能的不一致
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_tenant_billing_summary`;
CREATE TABLE `ai_tenant_billing_summary` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID (UNIQUE, 一租户一行)',

  -- ====== 钱包账户数 ======
  `wallet_user_count`      INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '个人钱包账户数 (ai_billing.user_id IS NOT NULL)',
  `wallet_team_count`      INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '团队共享钱包数 (ai_billing.team_id IS NOT NULL)',
  `wallet_total_count`     INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '钱包总数 (user + team)',

  -- ====== 当前余额 ======
  `balance_total`          DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '**租户总可用余额** (所有 billing.credits_balance 之和)',
  `balance_user`           DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '个人钱包合计余额',
  `balance_team`           DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '团队钱包合计余额',
  `frozen_total`           DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '租户总冻结金额',

  -- ====== 历史累计 (从 usage_record 聚合, 不可变账本) ======
  `recharged_total`        DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '历史累计充值',
  `consumed_total`         DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '历史累计消费 (所有 transaction_type=2,3,7 的出账合计)',
  `refunded_total`         DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '历史累计退款',
  `bonus_total`            DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '历史累计赠送',
  `revoked_total`          DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '历史累计作废 (订阅到期/赠送过期)',
  `transaction_count_total` BIGINT UNSIGNED NOT NULL DEFAULT 0                                     COMMENT '累计交易笔数',

  -- ====== 本月统计 (每月 1 号 0 点 cron 重置) ======
  `recharged_this_month`   DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '本月充值',
  `consumed_this_month`    DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '本月消费',
  `refunded_this_month`    DECIMAL(16,4)   NOT NULL DEFAULT 0.0000                               COMMENT '本月退款',
  `transaction_count_this_month` BIGINT UNSIGNED NOT NULL DEFAULT 0                                COMMENT '本月交易笔数',
  `monthly_reset_time`     DATETIME(3)      DEFAULT NULL                                          COMMENT '本月统计下次重置时间 (下月 1 号 00:00:00)',

  -- ====== 日均消费 (运营看板用, 滑动 7 天窗口) ======
  `consumed_daily_avg_7d`  DECIMAL(14,4)   NOT NULL DEFAULT 0.0000                               COMMENT '近 7 天日均消费',
  `consumed_daily_max_7d`  DECIMAL(14,4)   NOT NULL DEFAULT 0.0000                               COMMENT '近 7 天单日最高消费',
  `recharged_daily_avg_7d` DECIMAL(14,4)   NOT NULL DEFAULT 0.0000                               COMMENT '近 7 天日均充值',

  -- ====== 租户等级 (AI 自动评估, 运营参考) ======
  `tenant_level`           TINYINT          NOT NULL DEFAULT 0                                    COMMENT '租户等级 (自动/手动): 0=免费, 1=基础, 2=专业, 3=企业, 4=VIP',
  `monthly_band`           VARCHAR(30)      DEFAULT NULL                                          COMMENT '月消费档位 (运营分群标签, 如 <1k / 1k-5k / 5k-20k / 20k+)',
  `auto_upgrade_next_month` TINYINT         NOT NULL DEFAULT 0                                    COMMENT '下月是否自动升级档位',

  -- ====== 对账与更新元数据 ======
  `last_event_update_time` DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次事件驱动更新时间 (ai_usage_record INSERT 时)',
  `last_full_rebuild_time` DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次全量重算时间 (每日对账 cron)',
  `last_full_rebuild_checksum` VARCHAR(64) DEFAULT NULL                                          COMMENT '全量重算时的 balance_total SHA-256 (用于发现不一致)',
  `data_consistent_flag`   TINYINT          NOT NULL DEFAULT 1                                    COMMENT '数据是否自洽: 1=事件更新与全量对账一致, 0=不一致需告警',
  `inconsistent_detail`    VARCHAR(1000)    DEFAULT NULL                                          COMMENT '不一致详情 (发现偏差时记录, 运营排查用)',

  -- ====== 运营标签 (人工标记) ======
  `ops_tags_json`          JSON             DEFAULT NULL                                          COMMENT '运营标签 (JSON 数组, 如 ["高价值", "付费意愿强", "需跟进"])',
  `ops_owner`              VARCHAR(50)      DEFAULT NULL                                          COMMENT '负责运营人员姓名',
  `ops_remark`             VARCHAR(1000)    DEFAULT NULL                                          COMMENT '运营备注',

  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间 (租户开通时首次 INSERT)',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant` (`tenant_id`)                                                   COMMENT '一租户一行',
  KEY `idx_balance_total` (`balance_total`),                                             COMMENT '余额分布统计',
  KEY `idx_consumed_this_month` (`consumed_this_month`),                                  COMMENT '月消费排行',
  KEY `idx_tenant_level` (`tenant_level`),
  KEY `idx_monthly_reset` (`monthly_reset_time`),                                         COMMENT '月度重置扫描',
  KEY `idx_data_consistent` (`data_consistent_flag`)                                      COMMENT '数据一致性告警扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='租户级聚合统计 (高频访问, 事件驱动异步更新, 每日全量对账, 解决实时聚合慢的问题)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 10 张表建表完成 (新增 ai_tenant_billing_summary)
-- 依赖顺序: price_rule (独立)
--           billing → usage_record / recharge / subscription / billing_summary
--           team → team_member / team_role_permission / team_budget_log
--           tenant_billing_summary (独立, 由 usage_record/billing 异步聚合)
-- =============================================================================
-- 余额更新: ai_billing.version CAS, 3 次重试, 金融级标准
-- 支付幂等: ai_recharge.order_no 唯一约束, DB 层最硬幂等
-- 不可变账本: ai_usage_record INSERT only, 退款走反向流水
-- 租户聚合: ai_tenant_billing_summary 事件驱动 + 每日全量对账双保险
-- =============================================================================
