package org.jeecg.modules.system.passwordreset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetLink;

import java.util.Date;
import java.util.List;

public interface SysPasswordResetLinkMapper extends BaseMapper<SysPasswordResetLink> {

    /** Successful consume: USED + consumed_flag=Y + time/ip/ua; optimistic version */
    @Update("UPDATE sys_password_reset_link " +
            "SET status = 'USED', consumed_flag = 'Y', consumed_time = #{consumedTime}, " +
            "    consumed_by_ip = #{clientIp}, consumed_ua = #{ua}, version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND version = #{version}")
    int consumeLinkById(@Param("id") String id,
                        @Param("version") Integer version,
                        @Param("consumedTime") Date consumedTime,
                        @Param("clientIp") String clientIp,
                        @Param("ua") String ua);

    /** Manual revoke: set REVOKED (PENDING only) */
    @Update("UPDATE sys_password_reset_link " +
            "SET status = 'REVOKED', revoked_by = #{revokedBy}, revoked_time = #{revokedTime}, revoke_reason = #{reason}, " +
            "    version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND version = #{version}")
    int revokeLinkById(@Param("id") String id,
                       @Param("version") Integer version,
                       @Param("revokedBy") String revokedBy,
                       @Param("reason") String reason,
                       @Param("revokedTime") Date revokedTime);

    /** Batch revoke all PENDING by account (post-success cleanup / freeze) */
    @Update("UPDATE sys_password_reset_link " +
            "SET status = 'REVOKED', revoked_by = #{revokedBy}, revoked_time = #{revokedTime}, revoke_reason = #{reason}, " +
            "    version = version + 1, update_time = NOW() " +
            "WHERE username = #{username} AND status = 'PENDING'")
    int batchRevokePendingByUsername(@Param("username") String username,
                                     @Param("revokedBy") String revokedBy,
                                     @Param("reason") String reason,
                                     @Param("revokedTime") Date revokedTime);

    /** Lazily mark expired PENDING → EXPIRED */
    @Update("UPDATE sys_password_reset_link " +
            "SET status = 'EXPIRED', version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND version = #{version}")
    int expireLinkById(@Param("id") String id, @Param("version") Integer version);

    /** Too many failures → LOCKED */
    @Update("UPDATE sys_password_reset_link " +
            "SET status = 'LOCKED', locked_at = #{lockedAt}, version = version + 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND version = #{version}")
    int lockLinkById(@Param("id") String id,
                     @Param("version") Integer version,
                     @Param("lockedAt") Date lockedAt);

    /** fail_count +1; set LOCKED at max in one atomic SQL */
    @Update("UPDATE sys_password_reset_link " +
            "SET fail_count = fail_count + 1, " +
            "    status = CASE WHEN fail_count + 1 >= max_fail_attempts THEN 'LOCKED' ELSE status END, " +
            "    locked_at = CASE WHEN fail_count + 1 >= max_fail_attempts THEN COALESCE(locked_at, #{now}) ELSE locked_at END, " +
            "    version = version + 1, update_time = NOW() " +
            "WHERE id = #{id}")
    int incrementFailAndMaybeLock(@Param("id") String id,
                                  @Param("lockIt") boolean lockIt,
                                  @Param("now") Date now);

    /* ================ Job: batch expire / cleanup history ================ */

    /** Fetch a batch of expired PENDING link ids (LIMIT 1000) to avoid long locks */
    @Select("SELECT id FROM sys_password_reset_link " +
            "WHERE status = 'PENDING' AND expire_time < #{now} " +
            "ORDER BY expire_time ASC LIMIT #{limit}")
    List<String> selectPendingExpiredIdsBatch(@Param("now") Date now, @Param("limit") int limit);

    /** Batch UPDATE to EXPIRED by id IN (skip concurrently consumed) */
    @Update("<script>UPDATE sys_password_reset_link " +
            "SET status = 'EXPIRED', version = version + 1, update_time = NOW() " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND status = 'PENDING'</script>")
    int batchMarkExpiredByIds(@Param("ids") List<String> ids);

    /** Delete terminal links older than N days by create_time, limit rows */
    @Delete("DELETE FROM sys_password_reset_link " +
            "WHERE status IN ('USED','REVOKED','LOCKED','EXPIRED') AND create_time < #{before} " +
            "LIMIT #{limit}")
    int deleteTerminalLinksBefore(@Param("before") Date before, @Param("limit") int limit);
}
