package org.jeecg.stream.service;
import org.jeecg.stream.dto.PlayPermission;

public interface IPlayPermissionService {
    PlayPermission check(Long userId, Long videoId);
}
