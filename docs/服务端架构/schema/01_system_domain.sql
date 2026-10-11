-- =============================================================================
-- Star AI Boot · AI 生产力平台 · 数据库建表脚本
-- Part 01 · 系统管理域 sys_* (13 张表)
-- 版本: v1.0 | MySQL: 8.4.0 | Charset: utf8mb4_unicode_ci
-- 设计决策: Q13 暂不预留独立库扩展点, Q2 保留 dict+dict_item 两张表
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1.1 sys_tenant · 租户
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_name`        VARCHAR(100)     NOT NULL                                                COMMENT '租户名称 (展示名)',
  `tenant_code`        VARCHAR(64)      NOT NULL                                                COMMENT '租户编码 (唯一, URL slug, 字母数字下划线)',
  `contact_name`       VARCHAR(50)      DEFAULT NULL                                            COMMENT '联系人姓名',
  `contact_phone`      VARCHAR(128)     DEFAULT NULL                                            COMMENT '联系电话 (AES-256-GCM-SIV 加密存储)',
  `contact_email`      VARCHAR(255)     DEFAULT NULL                                            COMMENT '联系邮箱 (AES-256-GCM-SIV 加密存储)',
  `industry`           VARCHAR(50)      DEFAULT NULL                                            COMMENT '所属行业',
  `logo_url`           VARCHAR(500)     DEFAULT NULL                                            COMMENT '租户 Logo 地址',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '状态: 0=禁用, 1=正常, 2=试用, 3=过期',
  `expire_time`        DATETIME(3)      DEFAULT NULL                                            COMMENT '订阅到期时间 (超过后 status 自动置 3)',
  `max_users`          INT UNSIGNED     NOT NULL DEFAULT 10                                     COMMENT '最大用户数上限',
  `max_storage_mb`     BIGINT UNSIGNED  NOT NULL DEFAULT 1024                                   COMMENT '最大存储空间 (MB)',
  `used_storage_mb`    BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '已用存储空间 (MB, 异步更新)',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展配置 (JSON: 自定义域名/SSO元数据/功能开关)',
  `remark`             VARCHAR(500)     DEFAULT NULL                                            COMMENT '备注',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间 (毫秒精度)',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记: 0=存在, 1=已删除',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号 (CAS 更新时使用)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`, `del_flag`)                           COMMENT '租户编码全局唯一 (含 del_flag 防删除后冲突)',
  KEY `idx_status` (`status`),
  KEY `idx_expire_time` (`expire_time`)                                             COMMENT '定时任务扫描过期租户',
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='租户 (SaaS 客户顶层隔离单位)';


-- -----------------------------------------------------------------------------
-- 1.2 sys_user · 用户
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID (雪花ID)',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '租户ID (平台管理员=0, 普通用户必须>0)',
  `username`           VARCHAR(64)      NOT NULL                                                COMMENT '登录账号 (租户内唯一)',
  `realname`           VARCHAR(50)      DEFAULT NULL                                            COMMENT '真实姓名',
  `nickname`           VARCHAR(50)      DEFAULT NULL                                            COMMENT '昵称',
  `avatar`             VARCHAR(500)     DEFAULT NULL                                            COMMENT '头像地址',
  `password`           VARCHAR(255)     NOT NULL                                                COMMENT '密码 (PBKDF2WithHmacSHA256, 600000 iterations, 格式: ${pbkdf2}$salt$hash$iter)',
  `salt`               VARCHAR(64)      DEFAULT NULL                                            COMMENT '密码盐 (随机生成, 与 password 配合验证)',
  `password_expire`    DATETIME(3)      DEFAULT NULL                                            COMMENT '密码强制过期时间 (NULL=永不过期)',
  `phone`              VARCHAR(255)     DEFAULT NULL                                            COMMENT '手机号 (AES-256-GCM-SIV 加密存储, 前3后4脱敏展示)',
  `phone_verified`     TINYINT          NOT NULL DEFAULT 0                                      COMMENT '手机号是否已验证: 0=否, 1=是',
  `email`              VARCHAR(255)     DEFAULT NULL                                            COMMENT '邮箱 (AES-256-GCM-SIV 加密存储)',
  `email_verified`     TINYINT          NOT NULL DEFAULT 0                                      COMMENT '邮箱是否已验证: 0=否, 1=是',
  `gender`             TINYINT          NOT NULL DEFAULT 0                                      COMMENT '性别: 0=未知, 1=男, 2=女',
  `birthday`           DATE             DEFAULT NULL                                            COMMENT '生日',
  `country_code`       VARCHAR(10)      DEFAULT NULL                                            COMMENT '国家代码 (ISO 3166-1 alpha-2, 如 CN/US/GB)',
  `timezone`           VARCHAR(32)      NOT NULL DEFAULT 'Asia/Shanghai'                        COMMENT 'IANA 时区标识',
  `locale`             VARCHAR(16)      NOT NULL DEFAULT 'zh-CN'                                COMMENT 'BCP 47 语言代码',
  `user_type`          TINYINT          NOT NULL DEFAULT 0                                      COMMENT '用户类型: 0=普通用户, 1=平台管理员, 2=租户管理员',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '账号状态: 0=禁用, 1=正常, 2=待审核, 3=锁定中',
  `kyc_status`         TINYINT          NOT NULL DEFAULT 0                                      COMMENT 'KYC 状态: 0=未认证, 1=待审核, 2=已认证, 3=已拒绝',
  `last_login_time`    DATETIME(3)      DEFAULT NULL                                            COMMENT '最后登录时间',
  `last_login_ip_hash` VARCHAR(255)     DEFAULT NULL                                            COMMENT '最后登录IP (哈希存储, 不可反查, 用于异常检测)',
  `last_login_device`  VARCHAR(500)     DEFAULT NULL                                            COMMENT '最后登录设备指纹 (Hash)',
  `login_fail_count`   INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '连续登录失败次数 (达到阈值自动 lock_until)',
  `lock_until`         DATETIME(3)      DEFAULT NULL                                            COMMENT '账号锁定到期时间 (到达后自动解锁)',
  `mfa_enabled`        TINYINT          NOT NULL DEFAULT 0                                      COMMENT '是否启用 MFA: 0=否, 1=是',
  `default_team_id`    BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '默认团队ID (快速进入)',
  `ext_json`           JSON             DEFAULT NULL                                            COMMENT '扩展信息 (JSON: 偏好设置/主题/通知开关)',
  `remark`             VARCHAR(500)     DEFAULT NULL                                            COMMENT '备注',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记: 0=存在, 1=已删除',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `del_flag`)                      COMMENT '租户内账号唯一',
  KEY `idx_phone_hash` (`phone`(64))                                                         COMMENT '手机号哈希前缀索引 (加密后查询)',
  KEY `idx_email_hash` (`email`(64))                                                         COMMENT '邮箱哈希前缀索引',
  KEY `idx_tenant_status` (`tenant_id`, `status`),
  KEY `idx_status` (`status`),
  KEY `idx_lock_until` (`lock_until`)                                                        COMMENT '定时任务扫描待解锁账号',
  KEY `idx_login_fail` (`login_fail_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户';


-- -----------------------------------------------------------------------------
-- 1.3 sys_user_tenant · 用户-租户关联 (支持一人多租户)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_tenant`;
CREATE TABLE `sys_user_tenant` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `user_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '用户ID',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `status`             TINYINT          NOT NULL DEFAULT 2                                      COMMENT '状态: 0=已移除, 1=已加入, 2=待接受邀请',
  `join_time`          DATETIME(3)      DEFAULT NULL                                            COMMENT '实际加入时间 (接受邀请后)',
  `invite_code`        VARCHAR(64)      DEFAULT NULL                                            COMMENT '邀请码 (接受邀请时校验, 一次性使用)',
  `invited_by`         BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '邀请人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`, `del_flag`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户-租户关联 (支持一人属于多个租户, 含邀请流程)';


-- -----------------------------------------------------------------------------
-- 1.4 sys_role · 角色
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '租户ID (0=平台级角色, 所有租户共享)',
  `role_code`          VARCHAR(64)      NOT NULL                                                COMMENT '角色编码 (租户内唯一, 如 team_admin / content_editor)',
  `role_name`          VARCHAR(100)     NOT NULL                                                COMMENT '角色显示名称',
  `description`        VARCHAR(500)     DEFAULT NULL                                            COMMENT '角色描述',
  `role_type`          TINYINT          NOT NULL DEFAULT 0                                      COMMENT '角色类型: 0=系统内置 (不可删除), 1=租户自定义',
  `data_scope`         TINYINT          NOT NULL DEFAULT 3                                      COMMENT '数据权限范围: 0=全部, 1=本部门及以下, 2=本部门, 3=仅本人',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '排序号 (升序)',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '状态: 0=禁用, 1=正常',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  `revision`           INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`, `del_flag`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='角色 (平台级 tenant_id=0 全局可用, 租户级 tenant_id>0 租户内生效)';


-- -----------------------------------------------------------------------------
-- 1.5 sys_permission · 权限 (目录 + 菜单 + 按钮 + API 合一)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `parent_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '父级权限ID (0=顶级, 树形结构)',
  `permission_code`    VARCHAR(128)     NOT NULL                                                COMMENT '权限编码 (全局唯一, 冒号分隔, 如 system:user:create / video:generate)',
  `permission_name`    VARCHAR(100)     NOT NULL                                                COMMENT '权限显示名称',
  `permission_type`    TINYINT          NOT NULL                                                COMMENT '权限类型: 0=目录, 1=菜单, 2=按钮, 3=API',
  `path`               VARCHAR(200)     DEFAULT NULL                                            COMMENT '前端路由路径 (type=0/1 目录和菜单使用)',
  `component`          VARCHAR(200)     DEFAULT NULL                                            COMMENT '前端组件路径 (type=1 菜单使用, 如 /system/user/index)',
  `api_method`         VARCHAR(10)      DEFAULT NULL                                            COMMENT 'HTTP 方法 (type=3 API 使用, GET/POST/PUT/DELETE)',
  `api_path`           VARCHAR(300)     DEFAULT NULL                                            COMMENT 'API 路径 (type=3 API 使用, 如 /videos/generate)',
  `api_match_mode`     TINYINT          DEFAULT 0                                               COMMENT 'API 路径匹配模式: 0=精确, 1=前缀, 2=正则',
  `icon`               VARCHAR(64)      DEFAULT NULL                                            COMMENT '菜单图标 (Element Plus / Ant Design 图标名)',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '同级排序号 (升序)',
  `visible`            TINYINT          NOT NULL DEFAULT 1                                      COMMENT '菜单是否显示: 0=隐藏 (仅权限), 1=显示',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '状态: 0=禁用, 1=正常',
  `remark`             VARCHAR(500)     DEFAULT NULL                                            COMMENT '备注',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`, `del_flag`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_type` (`permission_type`),
  KEY `idx_api_method_path` (`api_method`, `api_path`)                                   COMMENT 'API 权限快速匹配',
  CONSTRAINT `chk_permission_type` CHECK (`permission_type` IN (0,1,2,3)),
  CONSTRAINT `chk_api_method` CHECK (`api_method` IS NULL OR `api_method` IN ('GET','POST','PUT','DELETE','PATCH','OPTIONS','HEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='权限 (目录 / 菜单 / 按钮 / API 四类统一存储)';


-- -----------------------------------------------------------------------------
-- 1.6 sys_user_role · 用户-角色关联 (N:N, 带 tenant_id 区分租户归属)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `user_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '用户ID',
  `role_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '角色ID',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID (用户在不同租户下可以有不同角色)',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_tenant` (`user_id`, `role_id`, `tenant_id`),
  KEY `idx_role` (`role_id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_user_tenant` (`user_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户-角色关联 (同一用户在不同租户下可以拥有不同角色)';


-- -----------------------------------------------------------------------------
-- 1.7 sys_role_permission · 角色-权限关联 (N:N)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `role_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '角色ID',
  `permission_id`      BIGINT UNSIGNED  NOT NULL                                                COMMENT '权限ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='角色-权限关联';


-- -----------------------------------------------------------------------------
-- 1.8 sys_permission_data_rule · 数据权限规则 (行级 WHERE 自动注入)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission_data_rule`;
CREATE TABLE `sys_permission_data_rule` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `role_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '角色ID (必填)',
  `permission_id`      BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '权限ID (NULL=该角色全局所有权限生效)',
  `rule_name`          VARCHAR(100)     NOT NULL                                                COMMENT '规则名称 (便于管理)',
  `rule_column`        VARCHAR(100)     NOT NULL                                                COMMENT '过滤字段名 (SQL WHERE 的列名, 如 create_by / tenant_id / dept_id)',
  `rule_operator`      VARCHAR(10)      NOT NULL                                                COMMENT '操作符: = / != / IN / NOT IN / LIKE / NOT LIKE / BETWEEN / > / < / >= / <=',
  `rule_value`         VARCHAR(1000)    NOT NULL                                                COMMENT '过滤值 (支持变量占位符: ${current_user_id} ${current_dept_id} ${current_tenant_id})',
  `priority`           INT              NOT NULL DEFAULT 0                                      COMMENT '优先级 (数值越小越先匹配)',
  `effect_scope`       TINYINT          NOT NULL DEFAULT 1                                      COMMENT '影响范围: 0=仅 type=1 菜单, 1=type=3 API, 2=全部',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '状态: 0=禁用, 1=正常',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_role` (`role_id`),
  KEY `idx_permission` (`permission_id`),
  CONSTRAINT `chk_rule_operator` CHECK (`rule_operator` IN ('=','!=','IN','NOT IN','LIKE','NOT LIKE','BETWEEN','>','<','>=','<='))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='数据权限规则 (MyBatis 拦截器自动注入 WHERE 条件, 实现行级隔离)';


-- -----------------------------------------------------------------------------
-- 1.9 sys_user_mfa · 用户 MFA 配置 (TOTP / Email OTP / WebAuthn / SMS)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_mfa`;
CREATE TABLE `sys_user_mfa` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `user_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '用户ID',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `mfa_type`           TINYINT          NOT NULL                                                COMMENT 'MFA 类型: 1=TOTP (Google Authenticator), 2=Email OTP, 3=WebAuthn/FIDO2, 4=SMS OTP',
  `secret_hash`        VARCHAR(512)     NOT NULL                                                COMMENT '密钥 Hash (OTPHasher 不可逆 Hash, 绝不明文存)',
  `backup_codes_hash`  VARCHAR(4000)    DEFAULT NULL                                            COMMENT '备用码 Hash (JSON 数组, 每个码独立 SHA-256 Hash, 使用后从数组移除)',
  `recovery_email`     VARCHAR(255)     DEFAULT NULL                                            COMMENT '恢复邮箱 (MFA 丢失时通过此邮箱重置)',
  `webauthn_credential_id` VARCHAR(512) DEFAULT NULL                                        COMMENT 'WebAuthn 凭证 ID (Base64, type=3 时必填)',
  `webauthn_public_key` MEDIUMBLOB      DEFAULT NULL                                            COMMENT 'WebAuthn 公钥 (COSE DER 格式, type=3 时必填)',
  `webauthn_sign_count` BIGINT UNSIGNED DEFAULT 0                                          COMMENT 'WebAuthn 签名计数 (防重放)',
  `last_verify_time`   DATETIME(3)      DEFAULT NULL                                            COMMENT '最后一次成功验证时间',
  `enabled`            TINYINT          NOT NULL DEFAULT 1                                      COMMENT '是否启用: 0=禁用, 1=启用',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_mfa_type` (`user_id`, `mfa_type`, `del_flag`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `chk_mfa_type` CHECK (`mfa_type` IN (1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户 MFA 配置 (密钥不可逆 Hash 存储, 绝不明文)';


-- -----------------------------------------------------------------------------
-- 1.10 sys_user_agent · 用户可信设备 (WebAuthn / 浏览器指纹 / IP 异常检测)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_agent`;
CREATE TABLE `sys_user_agent` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `user_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '用户ID',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL                                                COMMENT '租户ID',
  `device_name`        VARCHAR(100)     DEFAULT NULL                                            COMMENT '设备名称 (用户可自定义, 如张三的MacBook)',
  `device_type`        VARCHAR(30)      DEFAULT NULL                                            COMMENT '设备类型: mobile / pc / tablet / tv / unknown',
  `user_agent`         VARCHAR(500)     DEFAULT NULL                                            COMMENT '完整 User-Agent 字符串 (原始, 仅审计用)',
  `fingerprint_hash`   VARCHAR(255)     DEFAULT NULL                                            COMMENT '浏览器设备指纹 (Hash 存储, 不可反查)',
  `webauthn_id`        VARCHAR(512)     DEFAULT NULL                                            COMMENT '关联的 WebAuthn 凭证 ID (sys_user_mfa)',
  `ip_hash`            VARCHAR(255)     DEFAULT NULL                                            COMMENT 'IP 哈希 (HMAC-SHA256, 不可反查, 仅用于相同 IP 匹配)',
  `is_trusted`         TINYINT          NOT NULL DEFAULT 0                                      COMMENT '信任状态: 0=未验证, 1=已信任, 2=已拒绝',
  `verify_method`      TINYINT          DEFAULT NULL                                            COMMENT '验证方式: 1=邮箱 OTP, 2=短信 OTP, 3=TOTP, 4=WebAuthn',
  `verify_time`        DATETIME(3)      DEFAULT NULL                                            COMMENT '验证通过时间',
  `last_use_time`      DATETIME(3)      DEFAULT NULL                                            COMMENT '最后使用时间 (每次登录成功更新)',
  `last_use_ip_hash`   VARCHAR(255)     DEFAULT NULL                                            COMMENT '最后使用 IP 哈希',
  `use_count`          INT UNSIGNED     NOT NULL DEFAULT 0                                      COMMENT '累计使用次数',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_trusted` (`user_id`, `is_trusted`),
  KEY `idx_fingerprint` (`fingerprint_hash`),
  KEY `idx_webauthn_id` (`webauthn_id`),
  KEY `idx_last_use_time` (`last_use_time`)                                               COMMENT '定时任务清理长期未用设备'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户可信设备 (首次登录触发验证, 之后自动信任)';


-- -----------------------------------------------------------------------------
-- 1.11 sys_log · 操作审计日志 (@AutoLog 自动记录)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `tenant_id`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '租户ID (平台级操作可能为 NULL)',
  `user_id`            BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '操作人用户ID',
  `username`           VARCHAR(64)      DEFAULT NULL                                            COMMENT '操作人账号 (冗余, 便于直接查询, 不 JOIN user 表)',
  `realname`           VARCHAR(50)      DEFAULT NULL                                            COMMENT '操作人真实姓名 (冗余)',
  `module_name`        VARCHAR(100)     DEFAULT NULL                                            COMMENT '模块名称 (如 system / video / billing)',
  `operation`          VARCHAR(500)     NOT NULL                                                COMMENT '操作描述 (如 删除用户 admin / 生成视频 wan2.6-t2v)',
  `method`             VARCHAR(300)     DEFAULT NULL                                            COMMENT '请求方法 (全限定类名#方法, 如 org.jeecg.controller.UserController#deleteUser)',
  `request_method`     VARCHAR(10)      DEFAULT NULL                                            COMMENT 'HTTP 方法 (GET/POST/PUT/DELETE)',
  `request_url`        VARCHAR(500)     DEFAULT NULL                                            COMMENT '请求 URL (不含 query 中的 Token)',
  `request_param`      MEDIUMTEXT       DEFAULT NULL                                            COMMENT '请求参数 (脱敏后, JSON 格式, 密码/Token 已替换为 ****)',
  `response_result`    MEDIUMTEXT       DEFAULT NULL                                            COMMENT '响应结果 (脱敏后, JSON, 大结果截断)',
  `ip`                 VARCHAR(64)      DEFAULT NULL                                            COMMENT '客户端 IP',
  `location`           VARCHAR(200)     DEFAULT NULL                                            COMMENT 'IP 解析地理位置 (异步填充)',
  `device`             VARCHAR(500)     DEFAULT NULL                                            COMMENT '客户端设备 (User-Agent 缩写)',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '执行状态: 0=失败, 1=成功',
  `cost_ms`            INT UNSIGNED     DEFAULT NULL                                            COMMENT '接口耗时 (毫秒)',
  `exception_msg`      VARCHAR(4000)    DEFAULT NULL                                            COMMENT '异常堆栈 (脱敏后, 生产环境截断)',
  `trace_id`           VARCHAR(64)      DEFAULT NULL                                            COMMENT '链路追踪 ID (SkyWalking/Micrometer)',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_module_time` (`module_name`, `create_time`),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='操作审计日志 (@AutoLog 注解自动切面记录, 建议按月分区归档)';


-- -----------------------------------------------------------------------------
-- 1.12 sys_data_log · 数据变更日志 (@DataLog 自动记录)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_data_log`;
CREATE TABLE `sys_data_log` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `tenant_id`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '租户ID',
  `data_source`        VARCHAR(100)     NOT NULL                                                COMMENT '数据源标识 (如 jeecg-boot / star-ai-module-generate)',
  `table_name`         VARCHAR(100)     NOT NULL                                                COMMENT '变更的物理表名',
  `pk_value`           VARCHAR(200)     NOT NULL                                                COMMENT '变更记录的主键值 (字符串化, 支持联合主键用逗号分隔)',
  `action`             TINYINT          NOT NULL                                                COMMENT '变更类型: 1=INSERT, 2=UPDATE, 3=DELETE',
  `old_value`          MEDIUMTEXT       DEFAULT NULL                                            COMMENT '变更前数据 (JSON 完整快照, UPDATE/DELETE 时存在)',
  `new_value`          MEDIUMTEXT       DEFAULT NULL                                            COMMENT '变更后数据 (JSON 完整快照, INSERT/UPDATE 时存在)',
  `diff_json`          JSON             DEFAULT NULL                                            COMMENT '差异字段 (仅 UPDATE 有值, 格式: {"field_name": {"old": x, "new": y}})',
  `operator_id`        BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '操作人ID',
  `operator_name`      VARCHAR(64)      DEFAULT NULL                                            COMMENT '操作人账号 (冗余)',
  `operator_ip`        VARCHAR(64)      DEFAULT NULL                                            COMMENT '操作人 IP',
  `trace_id`           VARCHAR(64)      DEFAULT NULL                                            COMMENT '链路追踪 ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_table` (`tenant_id`, `table_name`),
  KEY `idx_table_pk` (`table_name`, `pk_value`),
  KEY `idx_action` (`action`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_operator` (`operator_id`),
  CONSTRAINT `chk_action` CHECK (`action` IN (1,2,3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='数据变更日志 (字段级审计, UPDATE 时只记录 diff)';


-- -----------------------------------------------------------------------------
-- 1.13 sys_dict · 字典类型
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `tenant_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0                                      COMMENT '租户ID (0=平台级字典, 所有租户共享)',
  `dict_code`          VARCHAR(100)     NOT NULL                                                COMMENT '字典编码 (全局唯一, 如 user_status / ai_model_category)',
  `dict_name`          VARCHAR(100)     NOT NULL                                                COMMENT '字典显示名称',
  `description`        VARCHAR(500)     DEFAULT NULL                                            COMMENT '字典说明',
  `has_color`          TINYINT          NOT NULL DEFAULT 0                                      COMMENT '字典项是否有颜色: 0=否, 1=是 (前端 Badge/Tag 用)',
  `has_icon`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '字典项是否有图标: 0=否, 1=是',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '排序号',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '状态: 0=禁用, 1=正常',
  `create_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '创建人ID',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED  DEFAULT NULL                                            COMMENT '最后更新人ID',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_dict_code` (`tenant_id`, `dict_code`, `del_flag`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='字典类型 (@Dict 注解自动翻译, 支持平台级租户级双轨)';


-- -----------------------------------------------------------------------------
-- 1.14 sys_dict_item · 字典项
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id`                 BIGINT UNSIGNED  NOT NULL                                                COMMENT '主键ID',
  `dict_id`            BIGINT UNSIGNED  NOT NULL                                                COMMENT '字典ID (外键 sys_dict.id)',
  `item_code`          VARCHAR(100)     NOT NULL                                                COMMENT '字典项编码 (同一字典内唯一)',
  `item_text`          VARCHAR(200)     NOT NULL                                                COMMENT '字典项显示文本',
  `item_value`         VARCHAR(200)     DEFAULT NULL                                            COMMENT '字典项实际值 (存 DB 的值)',
  `item_color`         VARCHAR(30)      DEFAULT NULL                                            COMMENT '字典项颜色值 (十六进制, 如 #FF4D4F)',
  `item_icon`          VARCHAR(64)      DEFAULT NULL                                            COMMENT '字典项图标名',
  `parent_item_code`   VARCHAR(100)     DEFAULT NULL                                            COMMENT '父项编码 (支持二级字典, 如 省份-城市)',
  `sort_no`            INT              NOT NULL DEFAULT 0                                      COMMENT '排序号 (同级升序)',
  `status`             TINYINT          NOT NULL DEFAULT 1                                      COMMENT '状态: 0=禁用, 1=正常',
  `remark`             VARCHAR(500)     DEFAULT NULL                                            COMMENT '备注',
  `create_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)                   COMMENT '创建时间',
  `update_time`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag`           TINYINT          NOT NULL DEFAULT 0                                      COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_item_code` (`dict_id`, `item_code`, `del_flag`),
  KEY `idx_dict_id` (`dict_id`),
  KEY `idx_parent_item` (`parent_item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='字典项 (支持 parent_item_code 实现二级树形字典)';


SET FOREIGN_KEY_CHECKS = 1;
-- =============================================================================
-- 本域 14 张表建表完成
-- 执行顺序说明: 本脚本已按外键依赖排序, 直接执行即可
-- =============================================================================
