package org.jeecg.creator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.creator.entity.CreatorVideoTag;
import org.jeecg.creator.mapper.CreatorVideoTagMapper;
import org.jeecg.creator.service.ICreatorVideoTagService;
import org.springframework.stereotype.Service;

@Service
public class CreatorVideoTagServiceImpl extends ServiceImpl<CreatorVideoTagMapper, CreatorVideoTag> implements ICreatorVideoTagService {
}
