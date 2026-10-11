package org.jeecg.common.api.dto;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Denied-invocation audit record, transferred over the S2S channel.
 *
 * <p>{@code sys_deny_audit} is owned by the system shard ({@code mpay_system}). Under the
 * per-service sharding scheme a service that is <em>not</em> the owner (dcs / processengine) must
 * never keep a local mirror of that table: it hands the record to the owner through
 * {@link org.jeecg.common.api.CommonAPI#recordDenyAudit(DenyAuditDTO)}. In the system service
 * itself the same call resolves to an in-process persistent write.
 *
 * <p>Shard rule: {@code SHARD-BAN T5-06/07} / gate {@code 1.14}. Do not add a JPA/MyBatis mapping
 * for this table in a non-owner module.
 *
 * <p>Field names mirror {@code org.jeecg.modules.system.entity.SysDenyAudit} so the owner can map
 * them one-to-one.
 */
@Data
@Accessors(chain = true)
public class DenyAuditDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Primary key ID (nullable; the owner assigns one when absent). */
  private String id;

  /** Request IP. */
  private String requestIp;

  /** User ID (optional; null when the caller is not logged in). */
  private String userIdOpt;

  /** HTTP method (GET/POST/PUT/DELETE/...). */
  private String httpMethod;

  /** Request URI. */
  private String requestUri;

  /** Deny reason code, e.g. {@code H8_DENIED_NOTLOGIN}, {@code H5_LEVEL_VIOLATION}. */
  private String reasonCode;

  /** Reason detail, already redacted. */
  private String reasonDetailRedacted;

  /** Request body, already redacted (optional). */
  private String requestBodyRedacted;

  /** Trace ID (nullable). */
  private String traceId;

  /** Created at (nullable; the owner fills the current time when absent). */
  private Date createTime;

  /** Tenant ID (optional). */
  private Integer tenantId;
}
