package org.jeecg.modules.system.service;

import org.jeecg.modules.system.entity.SysDictItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  Service class
 * </p>
 *
 * @Author zhangweijian
 * @since 2018-12-28
 */
public interface ISysDictItemService extends IService<SysDictItem> {

    /**
     * Query dict items by dict id
     * @param mainId dictionary id
     * @return
     */
    public List<SysDictItem> selectItemsByMainId(String mainId);
}
