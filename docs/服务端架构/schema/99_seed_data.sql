-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 种子数据脚本
-- Part 99 · 初始种子数据 (平台级配置, 非必须但建议执行)
-- 执行时机: 所有建表脚本执行完毕后, 立即执行此脚本
-- 注意: 种子数据使用 id=1-9999, 雪花 ID 真正发号从 10000 开始, 避免冲突
-- =============================================================================
-- 内容:
--   1. 平台超级管理员 (sys_user user_type=1, tenant_id=0)
--   2. 平台级内置角色 (sys_role tenant_id=0)
--   3. 核心权限定义 (sys_permission)
--   4. 基础字典项 (sys_dict + sys_dict_item)
--   5. AI 供应商默认配置 (ai_provider)
--   6. AI 核心模型默认配置 (ai_model)
--   7. 平台级默认通道 (ai_channel, tenant_id=0, 凭证占位等运维配置)
--   8. 平台级默认定价规则 (ai_price_rule, tenant_id=0)
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- 1. 平台超级管理员 (id=1, tenant_id=0, user_type=1)
-- =============================================================================
-- 密码: admin123  (PBKDF2WithHmacSHA256, 600000 iterations, 实际 Hash 值需后端生成)
-- 以下 password 为占位 Hash, 请在部署时用 PasswordUtil.hash("admin123") 替换
INSERT INTO `sys_user` (`id`, `tenant_id`, `username`, `realname`, `password`, `salt`, `user_type`, `status`, `mfa_enabled`, `create_by`, `create_time`, `revision`)
VALUES (
  1,                                          -- id
  0,                                          -- tenant_id = 0 (平台管理员不属于任何租户)
  'admin',                                    -- username
  '平台超级管理员',                            -- realname
  '$pbkdf2$16bytesaltwillbereplaced$replacedbyactualhash$600000',  -- password (请用 PasswordUtil.hash() 生成)
  'placeholder_salt_replace_me_16byte',       -- salt
  1,                                          -- user_type = 1 (平台管理员)
  1,                                          -- status = 1 (正常)
  0,                                          -- mfa_enabled = 0 (生产环境建议开启)
  NULL, NULL, 0
);


-- =============================================================================
-- 2. 平台级内置角色 (tenant_id=0, 所有租户继承可用)
-- =============================================================================
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `description`, `role_type`, `data_scope`, `sort_no`, `status`, `revision`) VALUES
(1001, 0, 'platform_admin',   '平台超级管理员',  '拥有平台所有权限, 可管理所有租户',                                0, 0, 1, 1, 0),
(1002, 0, 'tenant_admin',     '租户管理员',      '可管理本租户下所有用户、角色、素材、计费',                          0, 1, 2, 1, 0),
(1003, 0, 'finance',          '财务人员',        '可查看/导出账单、充值/退款、订阅管理, 不可操作业务',                0, 3, 3, 1, 0),
(1004, 0, 'operator',         '运营人员',        '可管理字典/公告/供应商通道/模型/定价, 不可管理用户/资金',          0, 2, 4, 1, 0),
(1005, 0, 'content_editor',   '内容创作者',      '可创建剧本/分镜/素材/视频生成, 不可修改计费和团队配置',            1, 3, 5, 1, 0),
(1006, 0, 'viewer',           '只读成员',        '可查看项目和生成结果, 不可创建/修改任何资源',                      1, 3, 6, 1, 0);

-- 用户-角色关联: admin 拥有 platform_admin
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `tenant_id`) VALUES (1, 1, 1001, 0);


-- =============================================================================
-- 3. 核心权限定义 (平台级, 菜单 + 按钮 + API 合一)
-- =============================================================================
-- 层级结构: system / video / billing / provider / team / content / task
-- 注意: 此 seed 只列出顶层和关键二级, 完整权限树由 Flyway 迁移脚本或后端初始化填充
INSERT INTO `sys_permission` (`id`, `parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `api_method`, `api_path`, `icon`, `sort_no`, `visible`, `status`) VALUES
-- 一级目录
(10001, 0, 'system',        '系统管理', 0, '/system',                    NULL,   NULL, NULL,              'setting', 1, 1, 1),
(10002, 0, 'video',         '视频生成', 0, '/video',                     NULL,   NULL, NULL,              'video-camera', 2, 1, 1),
(10003, 0, 'content',       '内容创作', 0, '/content',                   NULL,   NULL, NULL,              'edit', 3, 1, 1),
(10004, 0, 'provider',      'AI 供应商', 0, '/provider',                  NULL,   NULL, NULL,              'api', 4, 1, 1),
(10005, 0, 'billing',       '计费订阅', 0, '/billing',                   NULL,   NULL, NULL,              'wallet', 5, 1, 1),
(10006, 0, 'team',          '团队管理', 0, '/team',                      NULL,   NULL, NULL,              'team', 6, 1, 1),

-- system 菜单 + API
(10101, 10001, 'system:user',          '用户管理',    1, '/system/user',          '/system/user/index',    NULL,                NULL,              'user',     1, 1, 1),
(10102, 10001, 'system:role',          '角色管理',    1, '/system/role',          '/system/role/index',    NULL,                NULL,              'lock',     2, 1, 1),
(10103, 10001, 'system:permission',    '权限管理',    1, '/system/permission',    '/system/permission/index', NULL,             NULL,              'safe',     3, 1, 1),
(10104, 10001, 'system:tenant',         '租户管理',    1, '/system/tenant',         '/system/tenant/index',  NULL,                NULL,              'office',   4, 1, 1),
(10105, 10001, 'system:dict',           '字典管理',    1, '/system/dict',           '/system/dict/index',    NULL,                NULL,              'collection-tag', 5, 1, 1),
(10106, 10001, 'system:log',            '日志审计',    1, '/system/log',            '/system/log/index',     NULL,                NULL,              'file-list', 6, 1, 1),

-- video API 权限
(10201, 10002, 'video:generate',        '视频生成',    3, NULL, NULL, 'POST', '/videos/generate',          NULL, 1, 0, 1),
(10202, 10002, 'video:batch-generate',  '批量生成',    3, NULL, NULL, 'POST', '/videos/batch-generate',    NULL, 2, 0, 1),
(10203, 10002, 'video:generate-list',   '生成记录查询',3, NULL, NULL, 'GET',  '/generations',             NULL, 3, 0, 1),
(10204, 10002, 'video:tool-upscale',    '视频超分',    3, NULL, NULL, 'POST', '/tools/video-upscale',     NULL, 4, 0, 1),
(10205, 10002, 'video:tool-subtitle-erase', '字幕擦除',3, NULL, NULL, 'POST', '/tools/subtitle-erase',    NULL, 5, 0, 1),

-- content 菜单
(10301, 10003, 'content:script',              '剧本管理',  1, '/content/script',              '/content/script/index',     NULL, NULL, 'document', 1, 1, 1),
(10302, 10003, 'content:storyboard',          '分镜管理',  1, '/content/storyboard',          '/content/storyboard/index', NULL, NULL, 'film', 2, 1, 1),
(10303, 10003, 'content:asset-library',       '素材库',    1, '/content/asset-library',       '/content/asset-library/index', NULL, NULL, 'picture', 3, 1, 1),
(10304, 10003, 'content:character-library',    '角色库',    1, '/content/character-library',    '/content/character-library/index', NULL, NULL, 'avatar', 4, 1, 1),
(10305, 10003, 'content:scene-library',        '场景库',    1, '/content/scene-library',        '/content/scene-library/index', NULL, NULL, 'map-location', 5, 1, 1),
(10306, 10003, 'content:prop-library',         '道具库',    1, '/content/prop-library',         '/content/prop-library/index', NULL, NULL, 'box', 6, 1, 1),

-- provider 菜单 + API
(10401, 10004, 'provider:config',         '供应商配置',    1, '/provider/config',          '/provider/config/index',    NULL, NULL, 'connection', 1, 1, 1),
(10402, 10004, 'provider:model',          '模型管理',      1, '/provider/model',           '/provider/model/index',     NULL, NULL, 'cpu', 2, 1, 1),
(10403, 10004, 'provider:channel',         '通道管理',      1, '/provider/channel',          '/provider/channel/index',   NULL, NULL, 'link', 3, 1, 1),

-- billing 菜单
(10501, 10005, 'billing:account',         '账户概览',    1, '/billing/account',          '/billing/account/index',    NULL, NULL, 'dashboard', 1, 1, 1),
(10502, 10005, 'billing:usage',           '消费明细',    1, '/billing/usage',            '/billing/usage/index',      NULL, NULL, 'list', 2, 1, 1),
(10503, 10005, 'billing:recharge',        '充值管理',    1, '/billing/recharge',         '/billing/recharge/index',   NULL, NULL, 'credit-card', 3, 1, 1),
(10504, 10005, 'billing:subscription',     '订阅管理',    1, '/billing/subscription',      '/billing/subscription/index', NULL, NULL, 'gift', 4, 1, 1),
(10505, 10005, 'billing:price-rule',      '定价规则',    1, '/billing/price-rule',        '/billing/price-rule/index', NULL, NULL, 'money', 5, 1, 1),

-- team 菜单
(10601, 10006, 'team:member',             '团队成员',    1, '/team/member',              '/team/member/index',        NULL, NULL, 'user', 1, 1, 1),
(10602, 10006, 'team:budget',             '团队预算',    1, '/team/budget',              '/team/budget/index',        NULL, NULL, 'wallet', 2, 1, 1),
(10603, 10006, 'team:permission',         '团队权限',    1, '/team/permission',          '/team/permission/index',    NULL, NULL, 'lock', 3, 1, 1),
(10604, 10006, 'team:impersonation',      '代登录审计',  1, '/team/impersonation',       '/team/impersonation/index', NULL, NULL, 'eye', 4, 1, 1);


-- =============================================================================
-- 4. 基础字典项 (平台级 tenant_id=0)
-- =============================================================================

-- 用户状态字典
INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `has_color`, `sort_no`, `status`) VALUES (10001, 0, 'user_status', '用户状态', 1, 1, 1);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_code`, `item_text`, `item_value`, `item_color`, `sort_no`, `status`) VALUES
(1000101, 10001, 'enabled',  '正常',  '1', 'green',  1, 1),
(1000102, 10001, 'disabled', '禁用',  '0', 'red',    2, 1),
(1000103, 10001, 'pending',  '待审核', '2', 'orange', 3, 1),
(1000104, 10001, 'locked',   '锁定中', '3', 'red',    4, 1);

-- AI 模型分类字典
INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `sort_no`, `status`) VALUES (10002, 0, 'ai_model_category', 'AI 模型分类', 2, 1);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_code`, `item_text`, `item_value`, `sort_no`, `status`) VALUES
(1000201, 10002, 'text',      '大语言模型', 'text',      1, 1),
(1000202, 10002, 'image',     '图片生成',   'image',     2, 1),
(1000203, 10002, 'video',     '视频生成',   'video',     3, 1),
(1000204, 10002, 'audio',     '语音合成',   'audio',     4, 1),
(1000205, 10002, 'embedding', '向量嵌入',   'embedding', 5, 1);

-- 视频生成任务类型字典
INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `sort_no`, `status`) VALUES (10003, 0, 'video_task_type', '视频生成任务类型', 3, 1);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_code`, `item_text`, `item_value`, `sort_no`, `status`) VALUES
(1000301, 10003, 't2v',              '文生视频',     't2v',              1, 1),
(1000302, 10003, 'i2v',              '图生视频',     'i2v',              2, 1),
(1000303, 10003, 'kf2v',             '关键帧转视频', 'kf2v',             3, 1),
(1000304, 10003, 't2i',              '文生图',       't2i',              4, 1),
(1000305, 10003, 'i2i',              '图生图',       'i2i',              5, 1),
(1000306, 10003, 'inpainting',       '局部重绘',     'inpainting',       6, 1),
(1000307, 10003, 'upscale',          '视频超分',     'upscale',          7, 1),
(1000308, 10003, 'subtitle_erase',   '字幕擦除',     'subtitle_erase',   8, 1),
(1000309, 10003, 'tts',              '语音合成',     'tts',              9, 1);

-- 分辨率字典
INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `sort_no`, `status`) VALUES (10004, 0, 'video_resolution', '视频分辨率', 4, 1);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_code`, `item_text`, `item_value`, `sort_no`, `status`) VALUES
(1000401, 10004, '480p', '480p (854×480)',  '480p', 1, 1),
(1000402, 10004, '720p', '720p (1280×720)', '720p', 2, 1),
(1000403, 10004, '1080p','1080p (1920×1080)','1080p', 3, 1),
(1000404, 10004, '2K',   '2K (2560×1440)',  '2K',   4, 1),
(1000405, 10004, '4K',   '4K (3840×2160)',  '4K',   5, 1);

-- 宽高比字典
INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `sort_no`, `status`) VALUES (10005, 0, 'aspect_ratio', '视频宽高比', 5, 1);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_code`, `item_text`, `item_value`, `sort_no`, `status`) VALUES
(1000501, 10005, '16:9', '横屏 16:9', '16:9', 1, 1),
(1000502, 10005, '9:16', '竖屏 9:16', '9:16', 2, 1),
(1000503, 10005, '1:1',  '方形 1:1',  '1:1',  3, 1),
(1000504, 10005, '21:9', '超宽 21:9', '21:9', 4, 1);

-- 计费交易类型字典
INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `sort_no`, `status`) VALUES (10006, 0, 'billing_transaction_type', '计费交易类型', 6, 1);
INSERT INTO `sys_dict_item` (`id`, `dict_id`, `item_code`, `item_text`, `item_value`, `sort_no`, `status`) VALUES
(1000601, 10006, 'recharge',        '充值',        '1', 1, 1),
(1000602, 10006, 'video_generation', '视频生成扣费', '2', 2, 1),
(1000603, 10006, 'tool_usage',      '工具链扣费',   '3', 3, 1),
(1000604, 10006, 'refund',          '退款',        '4', 4, 1),
(1000605, 10006, 'gift',            '活动赠送',     '5', 5, 1),
(1000606, 10006, 'manual_adjust',   '人工调整',     '6', 6, 1),
(1000607, 10006, 'subscription',    '订阅扣费',     '7', 7, 1),
(1000608, 10006, 'expire_revoke',   '到期作废',     '8', 8, 1);


-- =============================================================================
-- 5. AI 供应商默认配置 (ai_provider, 平台级 tenant_id=0)
-- =============================================================================
INSERT INTO `ai_provider` (`id`, `provider_code`, `provider_name_cn`, `provider_name_en`, `api_base_url`, `auth_type`, `default_timeout_ms`, `default_retry`, `retryable_http_codes`, `status`, `sort_no`, `revision`) VALUES
(20001, 'bytedance', '字节跳动 (火山引擎)', 'ByteDance (VolcEngine)', 'https://ark.cn-beijing.volces.com/api/v3', 1, 120000, 2, '429,500,502,503,504', 1, 1, 0),
(20002, 'aliyun',    '阿里巴巴 (DashScope)', 'Alibaba (DashScope)',   'https://dashscope.aliyuncs.com/api/v3',   0, 120000, 2, '429,500,502,503,504', 1, 2, 0),
(20003, 'kling',     '快手可灵',            'Kuaishou Kling',        'https://api.klingai.com/v1',              1, 120000, 2, '429,500,502,503,504', 1, 3, 0),
(20004, 'openai',    'OpenAI',              'OpenAI',                'https://api.openai.com/v1',               0, 180000, 2, '429,500,502,503,504', 1, 4, 0),
(20005, 'google',    'Google (Vertex AI)',  'Google (Vertex AI)',    'https://aiplatform.googleapis.com/v1',    1, 180000, 2, '429,500,502,503,504', 1, 5, 0);


-- =============================================================================
-- 6. AI 核心模型默认配置 (ai_model, 平台级)
-- =============================================================================
-- 火山引擎 豆包+即梦 联合模型
INSERT INTO `ai_model` (`id`, `provider_id`, `model_id`, `model_name_cn`, `model_category`, `task_type`, `input_types`, `default_resolution`, `supported_resolutions`, `supports_multi_ref`, `max_duration_sec`, `estimated_duration_sec`, `status`, `sort_no`, `revision`) VALUES
(21001, 20001, 'doubao-seedance-1-0-pro-250528', '豆包+即梦 联合模型', 'video', 't2v',  '["text"]',           '1080p', '["720p","1080p","2K","4K"]', 0, 60, 60, 1, 1, 0),
(21002, 20001, 'seedance-2-5',                    '即梦 2.5',          'video', 't2v',  '["text"]',           '1080p', '["720p","1080p","2K"]',       0, 30, 60, 1, 2, 0),
(21003, 20001, 'seedance-2-0',                    '即梦 2.0',          'video', 't2v',  '["text"]',           '1080p', '["720p","1080p"]',            0, 30, 45, 1, 3, 0),
(21004, 20001, 'seedance-picture-n',              '即梦多参考图',       'video', 'i2v',  '["text","image"]',    '1080p', '["720p","1080p","2K"]',       1, 30, 60, 1, 4, 0),
(21005, 20001, 'doubao-seedream-4-5-251128',      'Seedream 图片生成',  'image', 't2i',  '["text"]',           '1024p', '["1024p","2048p"]',           0, NULL, 20, 1, 5, 0);

-- 阿里万相系列
INSERT INTO `ai_model` (`id`, `provider_id`, `model_id`, `model_name_cn`, `model_category`, `task_type`, `input_types`, `default_resolution`, `supported_resolutions`, `supports_multi_ref`, `supports_asset_protocol`, `max_duration_sec`, `estimated_duration_sec`, `status`, `sort_no`, `revision`) VALUES
(22001, 20002, 'wan2.6-t2v', '万相 2.6 文生视频', 'video', 't2v', '["text"]',                    '1080p', '["720p","1080p","2K","4K"]', 0, 0, 30, 90, 1, 1, 0),
(22002, 20002, 'wan2.6-i2v', '万相 2.6 图生视频', 'video', 'i2v', '["text","image"]',            '1080p', '["720p","1080p","2K","4K"]', 1, 0, 30, 90, 1, 2, 0),
(22003, 20002, 'wan2.5-t2v', '万相 2.5 文生视频', 'video', 't2v', '["text"]',                    '1080p', '["720p","1080p","2K"]',       0, 0, 30, 90, 1, 3, 0),
(22004, 20002, 'wan2.2-t2v', '万相 2.2 文生视频', 'video', 't2v', '["text"]',                    '1080p', '["720p","1080p"]',            0, 0, 15, 60, 1, 4, 0),
(22005, 20002, 'wan2.2-i2v', '万相 2.2 图生视频', 'video', 'i2v', '["text","image"]',            '1080p', '["720p","1080p"]',            1, 0, 15, 60, 1, 5, 0),
(22006, 20002, 'wan2.2-kf2v','万相 2.2 关键帧',   'video', 'kf2v','["text","image"]',            '1080p', '["720p","1080p"]',            1, 0, 15, 90, 1, 6, 0),
(22007, 20002, 'wanx-v1',    '万相 V1 图片',      'image', 't2i', '["text"]',                    '1024p', '["512p","1024p"]',            0, 0, NULL, 20, 1, 7, 0);

-- 快手可灵 / OpenAI Sora / Google Veo
INSERT INTO `ai_model` (`id`, `provider_id`, `model_id`, `model_name_cn`, `model_category`, `task_type`, `input_types`, `default_resolution`, `supports_multi_ref`, `max_duration_sec`, `estimated_duration_sec`, `status`, `sort_no`, `revision`) VALUES
(23001, 20003, 'kling-v1.6',  '可灵 V1.6',  'video', 't2v', '["text"]',        '1080p', 0, 60, 90, 1, 1, 0),
(23002, 20003, 'kling-v1.6-i2v', '可灵 V1.6 图生', 'video', 'i2v', '["text","image"]', '1080p', 1, 60, 120, 1, 2, 0),
(24001, 20004, 'sora-1',     'Sora V1',    'video', 't2v', '["text"]',        '1080p', 0, 60, 120, 1, 1, 0),
(25001, 20005, 'veo-2',      'Veo 2',      'video', 't2v', '["text"]',        '1080p', 0, 60, 120, 1, 1, 0);


-- =============================================================================
-- 7. 平台级默认通道 (ai_channel, tenant_id=0, 占位凭证, 运维部署时替换)
-- =============================================================================
-- 注意: 以下 ak_cipher / sk_cipher 为占位符, 部署时用 aes-256-gcm-siv 加密实际 AK/SK 后替换
-- encryption_key_ref 指向 KMS Key ID 或本地 Key 标识
INSERT INTO `ai_channel` (`id`, `tenant_id`, `provider_id`, `channel_name`, `channel_code`, `ak_cipher`, `sk_cipher`, `encryption_key_ref`, `weight`, `priority`, `enabled`, `health_status`, `revision`) VALUES
(30001, 0, 20001, '火山引擎-主通道', 'volcengine-primary', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_AK', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_SK', 'kms://volcengine-key-1', 100, 0, 1, 1, 0),
(30002, 0, 20001, '火山引擎-备通道', 'volcengine-backup',  'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_AK', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_SK', 'kms://volcengine-key-2', 50,  1, 1, 1, 0),
(30003, 0, 20002, '阿里云 DashScope-主通道', 'dashscope-primary', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_AK', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_SK', 'kms://aliyun-key-1', 100, 0, 1, 1, 0),
(30004, 0, 20003, '快手可灵-主通道', 'kling-primary',      'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_AK', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_SK', 'kms://kling-key-1',   100, 0, 1, 1, 0),
(30005, 0, 20004, 'OpenAI-主通道',  'openai-primary',    'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_AK', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_SK', 'kms://openai-key-1',  100, 0, 1, 1, 0),
(30006, 0, 20005, 'Google Veo-主通道','google-primary',     'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_AK', 'REPLACE_WITH_AES256_GCM_SIV_ENCRYPTED_SK', 'kms://google-key-1',   100, 0, 1, 1, 0);

-- 通道-模型绑定 (ai_channel_model): 所有通道默认绑定自家供应商的所有模型
-- 此处只作占位, 实际绑定列表较长, 可在运维后台批量配置或用 Flyway 迁移脚本


-- =============================================================================
-- 8. 平台级默认定价规则 (ai_price_rule, tenant_id=0)
-- =============================================================================
-- 兜底价优先级最低 (priority=0), 具体模型价 priority 更大
INSERT INTO `ai_price_rule` (`id`, `tenant_id`, `model_id`, `task_type`, `resolution`, `duration_bucket_start_sec`, `duration_bucket_end_sec`, `unit_price_per_sec`, `resolution_coef`, `min_price`, `rule_code`, `rule_name`, `priority`, `enabled`, `revision`) VALUES
-- 万相 2.6 T2V (最高优先级, 最热门模型)
(40001, 0, 22001, 't2v', '720p',  NULL, NULL, 0.5000, 0.8000, NULL, 'PRD-WAN26-T2V-720P',  '万相2.6 文生视频 720p',   100, 1, 0),
(40002, 0, 22001, 't2v', '1080p', NULL, NULL, 0.8000, 1.0000, NULL, 'PRD-WAN26-T2V-1080P', '万相2.6 文生视频 1080p',  100, 1, 0),
(40003, 0, 22001, 't2v', '2K',    NULL, NULL, 1.2000, 1.5000, NULL, 'PRD-WAN26-T2V-2K',    '万相2.6 文生视频 2K',     100, 1, 0),
(40004, 0, 22001, 't2v', '4K',    NULL, NULL, 2.0000, 2.5000, NULL, 'PRD-WAN26-T2V-4K',    '万相2.6 文生视频 4K',     100, 1, 0),

-- 即梦 2.5 T2V
(40005, 0, 21002, 't2v', '720p',  NULL, NULL, 0.4000, 0.8000, NULL, 'PRD-SEED25-720P',     '即梦2.5 720p',            90,  1, 0),
(40006, 0, 21002, 't2v', '1080p', NULL, NULL, 0.7000, 1.0000, NULL, 'PRD-SEED25-1080P',    '即梦2.5 1080p',           90,  1, 0),
(40007, 0, 21002, 't2v', '2K',    NULL, NULL, 1.0000, 1.4000, NULL, 'PRD-SEED25-2K',       '即梦2.5 2K',              90,  1, 0),

-- 可灵 V1.6
(40008, 0, 23001, 't2v', '1080p', NULL, NULL, 0.9000, 1.0000, NULL, 'PRD-KLING16-1080P',   '可灵 V1.6 1080p',         80,  1, 0),
(40009, 0, 23002, 'i2v', '1080p', NULL, NULL, 1.0000, 1.0000, NULL, 'PRD-KLING16-I2V',     '可灵 V1.6 图生视频',      80,  1, 0),

-- Sora / Veo (高端, 高定价)
(40010, 0, 24001, 't2v', '1080p', NULL, NULL, 3.0000, 1.0000, 5.0000, 'PRD-SORA-1080P',    'Sora V1 1080p',          70,  1, 0),
(40011, 0, 25001, 't2v', '1080p', NULL, NULL, 2.5000, 1.0000, 4.0000, 'PRD-VEO2-1080P',    'Veo 2 1080p',            70,  1, 0),

-- 图片生成 (封顶)
(40012, 0, 21005, 't2i', NULL, NULL, NULL, 0.5000, 1.0000, 0.5000, 'PRD-SEEDREAM',    'Seedream 图片生成 (每张)',  60,  1, 0),

-- 工具链 (单独定价, 按任务)
(40013, 0, NULL, 'upscale',        NULL, NULL, NULL, 0.2000, 1.0000, NULL, 'PRD-UPSCALE',         '视频超分 (每秒)',           50,  1, 0),
(40014, 0, NULL, 'subtitle_erase', NULL, NULL, NULL, 0.1000, 1.0000, NULL, 'PRD-SUBTITLE-ERASE',  '字幕擦除 (每秒)',           50,  1, 0),

-- 兜底价 (最低优先级 priority=0, 匹配不到规则时用这个)
(49999, 0, NULL, NULL, NULL, NULL, NULL, 0.5000, 1.0000, NULL, 'PRD-FALLBACK-DEFAULT', '兜底默认价', 0, 1, 0);


-- =============================================================================
-- 执行完毕验证清单
-- =============================================================================
SELECT '建表完成' AS result;
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'ai_%';
SELECT COUNT(*) AS sys_table_count FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'sys_%';
SELECT '验证 admin 账号' AS check_point, id, username, tenant_id, user_type FROM sys_user WHERE id = 1;
SELECT '验证供应商种子' AS check_point, id, provider_code FROM ai_provider;
SELECT '验证模型种子' AS check_point, COUNT(*) AS model_count FROM ai_model;
SELECT '验证定价种子' AS check_point, COUNT(*) AS price_count FROM ai_price_rule;


-- =============================================================================
-- 🆕 Stream 域种子数据 (v1.2.1)
-- 18+ 视频付费分润平台 · 初始会员方案 + 平台级配置
-- =============================================================================

-- ========== 会员订阅方案 (多 Region + 多周期 + 会员权益配置) ==========
INSERT INTO stream_subscription_plan
(plan_code, plan_name, duration_days, price_amount, currency, region,
 pool_fee_rate, creator_payout_rate, contribution_mode, min_effective_sec, completion_weight,
 gift_points, gift_points_bonus, free_episode_extra, preview_sec_extra,
 enabled, sort_order, trial_consumable, tenant_id, create_by) VALUES

-- ======== 中国大陆 cn ========
('monthly',    '月会员',    30,  19.90, 'CNY', 'cn', 0.9000, 0.7000, 'play_duration', 10, 0.3000, 100,  0, 2,  30, 1, 1, 0, 0, 1),
('quarterly',  '季会员',    90,  49.90, 'CNY', 'cn', 0.9000, 0.7000, 'play_duration', 10, 0.3000, 300,  0, 3,  45, 1, 2, 0, 0, 1),
('yearly',     '年会员',    365, 168.00, 'CNY', 'cn', 0.9000, 0.7000, 'play_duration', 10, 0.3000, 1200, 200, 5, -1, 1, 3, 0, 0, 1),
('trial_7d',   '7天免费试用', 7,   0.00, 'CNY', 'cn', 0.8000, 0.5000, 'play_duration', 10, 0.3000,   0,   0, 1,   0, 1, 4, 1, 0, 1),

-- ======== 新加坡 sg (东南亚市场, 价格略高) ========
('monthly',    'Monthly',   30, SGD 12.90, 'CNY', 'sg', 0.9000, 0.7000, 'play_duration', 10, 0.3000, 100,  0, 2,  30, 1, 1, 0, 0, 1),
('quarterly',  'Quarterly', 90, SGD 34.90, 'CNY', 'sg', 0.9000, 0.7000, 'play_duration', 10, 0.3000, 300,  0, 3,  45, 1, 2, 0, 0, 1),
('yearly',     'Yearly',    365, SGD 118.00, 'CNY', 'sg', 0.9000, 0.7000, 'play_duration', 10, 0.3000, 1200, 200, 5, -1, 1, 3, 0, 0, 1),
('trial_7d',   '7-Day Trial', 7,   0.00, 'CNY', 'sg', 0.8000, 0.5000, 'play_duration', 10, 0.3000,   0,   0, 1,   0, 1, 4, 1, 0, 1),

-- ======== 美国 us (竞争激烈, 价格最高 + 权益最优) ========
('monthly',    'Monthly',   30, USD 9.99,  'CNY', 'us', 0.8500, 0.7500, 'hybrid',        15, 0.4000, 150,  0, 3,  45, 1, 1, 0, 0, 1),
('quarterly',  'Quarterly', 90, USD 24.99, 'CNY', 'us', 0.8500, 0.7500, 'hybrid',        15, 0.4000, 400,  0, 4,  60, 1, 2, 0, 0, 1),
('yearly',     'Yearly',    365, USD 79.99, 'CNY', 'us', 0.8500, 0.7500, 'hybrid',        15, 0.4000, 1500, 300, 6, -1, 1, 3, 0, 0, 1),
('trial_7d',   '7-Day Trial', 7,   0.00, 'CNY', 'us', 0.8000, 0.5000, 'hybrid',        15, 0.4000,    0,   0, 2,  15, 1, 4, 1, 0, 1);

-- ========== 平台级配置 (stream_platform_config, 按 Region) ==========
INSERT INTO stream_platform_config (config_key, config_value, config_type, region, description, tenant_id, update_by) VALUES
('free_episodes_per_series', '3', 'int',    'cn', '全局免费集数: 每部剧集前 3 集对所有人免费开放 (非会员也能看)', 0, 1),
('free_episodes_per_series', '3', 'int',    'sg', '同上', 0, 1),
('free_episodes_per_series', '5', 'int',    'us', '美国市场竞争激烈, 给更多免费集数做拉新', 0, 1),

-- 订阅池防刷: 单用户月度贡献度占比上限 (超过视为异常)
('pool_contribution_cap',    '0.05', 'decimal', 'cn', '单用户在订阅池月度贡献度占比上限 5% (超过触发告警, 防刷)', 0, 1),
('pool_contribution_cap',    '0.05', 'decimal', 'sg', '同上', 0, 1),
('pool_contribution_cap',    '0.03', 'decimal', 'us', '美国市场用户多, 上限更低', 0, 1),

-- 最低结算门槛: 创作者累计收益超过多少才能提现
('min_withdrawal_amount',    '50.00', 'decimal', 'cn', '创作者最低提现门槛 ¥50', 0, 1),
('min_withdrawal_amount',    '50.00', 'decimal', 'sg', 'S$50 等价', 0, 1),
('min_withdrawal_amount',    '10.00', 'decimal', 'us', '美国门槛 $10', 0, 1),

-- 直播最大码率 / HLS 分片大小等后续可扩展配置预留
('hls_segment_target_sec',   '4', 'int', 'cn', 'HLS 目标分片时长 4s (越小切换越快, 但请求更多)', 0, 1),
('hls_segment_target_sec',   '4', 'int', 'sg', '同上', 0, 1),
('hls_segment_target_sec',   '4', 'int', 'us', '同上', 0, 1);

-- ========== 验证种子数据 ==========
SELECT '验证会员方案种子' AS check_point, COUNT(*) AS plan_count FROM stream_subscription_plan WHERE enabled = 1;
SELECT '验证平台配置种子' AS check_point, COUNT(*) AS config_count FROM stream_platform_config;
SELECT '验证 cn 区方案权益' AS check_point, plan_code, gift_points, free_episode_extra, preview_sec_extra FROM stream_subscription_plan WHERE region='cn' ORDER BY sort_order;


-- =============================================================================
-- 全部执行完毕后下一步:
--   1. 替换 ai_channel 表中的 AK/SK 占位密文为真实 AES-256-GCM-SIV 加密值
--   2. 用 PasswordUtil.hash("你要的密码") 生成 admin 密码, 替换 sys_user.password
--   3. 启动后端应用, 验证所有 @Dict 注解字典翻译正常
--   4. 通过后台 API 创建租户 + 租户管理员
-- =============================================================================
