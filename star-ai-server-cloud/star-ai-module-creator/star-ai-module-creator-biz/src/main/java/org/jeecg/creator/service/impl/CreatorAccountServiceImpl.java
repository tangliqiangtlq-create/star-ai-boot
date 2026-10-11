package org.jeecg.creator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.creator.entity.CreatorAccount;
import org.jeecg.creator.mapper.CreatorAccountMapper;
import org.jeecg.creator.service.ICreatorAccountService;
import org.springframework.stereotype.Service;

@Service
public class CreatorAccountServiceImpl extends ServiceImpl<CreatorAccountMapper, CreatorAccount> implements ICreatorAccountService {
}
