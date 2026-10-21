-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 06 · 火山虚拟库同步 ai_asset_virtual_sync (Q8 采纳, 轻量同步元数据表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- =============================================================================
-- 设计说明:
--   - ai_asset 表承载实际素材 (图片/视频/音频二进制), 是"仓库"
--   - ai_asset_virtual_sync 承载"远程同步遥控器", 只存同步元数据
--   - 火山引擎素材被官方下架/更新版本时, 本地需要感知 → 这张表就是感知点
--   - 同步失败要记录重试状态 → Queue 扫描重试
--   - 所有其他 AI 厂商 (OpenAI/Google/Kling) 如有类似同步需求, 也用这张表
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


DROP TABLE IF EXISTS `ai_asset_virtual_sync`;
CREATE TABLE `ai_asset_virtual_sync` (
  `id`                     BIGINT UNSIGNED  NOT NULL                                              COMMENT '主键ID (雪花ID)',
  `tenant_id`              BIGINT UNSIGNED  NOT NULL                                              COMMENT '租户ID',
  `provider_code`          VARCHAR(64)      NOT NULL                                              COMMENT '供应商编码 (如 bytedance-volcengine / minimax / kling)',
  `external_asset_id`      VARCHAR(255)     NOT NULL                                              COMMENT '外部素材 ID (火山 asset-123456 / OpenAI file-xxx)',
  `external_asset_type`    VARCHAR(30)      NOT NULL                                              COMMENT '外部素材类型: virtual_character / virtual_scene / virtual_prop / virtual_video / virtual_audio',
  `local_asset_id`         BIGINT UNSIGNED  DEFAULT NULL                                          COMMENT '本地 ai_asset.id (同步成功后回填, 建立双向映射)',
  `external_version`       VARCHAR(64)      DEFAULT NULL                                          COMMENT '外部素材版本号',
  `external_status`        VARCHAR(30)      DEFAULT NULL                                          COMMENT '外部侧原始状态 (active / offline / updating / deleted)',
  `sync_status`            TINYINT          NOT NULL DEFAULT 0                                    COMMENT '**同步状态**: 0=pending(等待首次拉取), 1=synced(已同步), 2=sync_failed(同步失败待重试), 3=syncing(同步中), 4=external_deleted(外部已下架), 5=external_updated(外部已更新, 待本地同步)',
  `sync_direction`         TINYINT          NOT NULL DEFAULT 0                                    COMMENT '同步方向: 0=从外部拉到本地, 1=双向, 2=仅索引不同步文件',
  `sync_method`            TINYINT          DEFAULT 0                                             COMMENT '同步方式: 0=API 拉取, 1=Callback Webhook, 2=手动触发',
  `sync_progress_percent`  INT              NOT NULL DEFAULT 0                                    COMMENT '同步进度 (0-100, 大文件分块同步时)',
  `last_sync_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次成功同步时间',
  `next_sync_retry_time`   DATETIME(3)      DEFAULT NULL                                          COMMENT '下次重试时间 (失败退避)',
  `consecutive_failures`   INT UNSIGNED     NOT NULL DEFAULT 0                                    COMMENT '连续失败次数 (用于熔断, 达阈值后降频)',
  `max_failures_before_pause` INT UNSIGNED   NOT NULL DEFAULT 5                                    COMMENT '连续失败超过此值后暂停自动同步 (等人工介入)',
  `external_metadata_raw`  JSON             DEFAULT NULL                                          COMMENT '外部原始元数据 (JSON, 外部 API 返回的完整对象, 审计 + 排查)',
  `last_sync_raw_request`  VARCHAR(1000)    DEFAULT NULL                                          COMMENT '最后一次同步请求摘要 (URL + 参数, 脱敏)',
  `last_sync_raw_response` VARCHAR(2000)    DEFAULT NULL                                          COMMENT '最后一次同步响应摘要 (脱敏, 错误时保留详情)',
  `last_sync_error_code`   VARCHAR(64)      DEFAULT NULL                                          COMMENT '最后一次同步错误码',
  `last_sync_error_msg`    VARCHAR(2000)    DEFAULT NULL                                          COMMENT '最后一次同步错误信息',
  `local_placeholder_tag`  VARCHAR(64)      DEFAULT NULL                                          COMMENT '本地生成的占位符 (如 vasset_image_1 / vasset_video_1, 与 ai_asset.placeholder_tag 一致)',
  `auto_update`            TINYINT          NOT NULL DEFAULT 1                                    COMMENT '是否自动跟随外部更新: 0=手动, 1=自动',
  `poll_interval_minutes`  INT UNSIGNED     DEFAULT 60                                            COMMENT '轮询间隔 (分钟, auto_update=1 时生效)',
  `last_poll_time`         DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次轮询时间',
  `last_external_checked`  DATETIME(3)      DEFAULT NULL                                          COMMENT '最后一次确认外部状态时间',
  `external_deleted_at`    DATETIME(3)      DEFAULT NULL                                          COMMENT '外部确认已下架时间 (sync_status=4 时)',
  `archived_locally`       TINYINT          NOT NULL DEFAULT 0                                    COMMENT '本地是否已归档 (外部下架后, 本地素材可保留/归档/删除): 0=正常可用, 1=已归档, 2=已物理删除',
  `ext_json`               JSON             DEFAULT NULL                                          COMMENT '扩展 (JSON: 火山 engine:// 协议参数/回调历史/重试策略覆盖)',
  `create_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                 COMMENT '创建时间',
  `update_time`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`               TINYINT          NOT NULL DEFAULT 0                                    COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_external` (`provider_code`, `external_asset_id`, `del_flag`),
  KEY `idx_local_asset` (`local_asset_id`),
  KEY `idx_sync_status` (`sync_status`),
  KEY `idx_retry` (`next_sync_retry_time`)                                             COMMENT '失败重试扫描',
  KEY `idx_poll` (`last_poll_time`)                                                    COMMENT '轮询扫描',
  KEY `idx_tenant_provider` (`tenant_id`, `provider_code`),
  KEY `idx_provider_status` (`provider_code`, `sync_status`),
  CHECK (`sync_status` IN (0,1,2,3,4,5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='外部虚拟素材同步遥控器 (轻量元数据表, 跟踪火山/OpenAI/Minimax 等外部素材的生命周期: 同步/更新/下架/重试)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 1 张表建表完成
-- 这张表是 Q8 采纳的设计: 素材本身存在 ai_asset, 这张表只存同步元数据
-- 所有 AI 厂商的外部素材同步需求都统一用这张表, 不同 provider_code 区分
-- =============================================================================
