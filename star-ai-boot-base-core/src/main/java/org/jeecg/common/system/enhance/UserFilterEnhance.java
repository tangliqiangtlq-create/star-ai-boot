package org.jeecg.common.system.enhance;

import java.util.List;

/**
 * User enhancement
 */
public interface UserFilterEnhance {
    
    /**
     * Get user id
     * @param loginUserId currently logged in user id
     * 
     * @return List<String> Returns multiple user IDs
     */
    default List<String> getUserIds(String loginUserId) {
        return null;
    }
}
