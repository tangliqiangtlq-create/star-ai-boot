package org.jeecg.creator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.creator.entity.CreatorVideoCategory;
import org.jeecg.creator.mapper.CreatorVideoCategoryMapper;
import org.jeecg.creator.service.ICreatorVideoCategoryService;
import org.springframework.stereotype.Service;

@Service
public class CreatorVideoCategoryServiceImpl extends ServiceImpl<CreatorVideoCategoryMapper, CreatorVideoCategory> implements ICreatorVideoCategoryService {
}
