package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.stream.entity.StreamVideoInteraction;
import org.jeecg.stream.mapper.StreamVideoInteractionMapper;
import org.jeecg.stream.service.IStreamVideoInteractionService;
import org.springframework.stereotype.Service;

@Service
public class StreamVideoInteractionServiceImpl extends ServiceImpl<StreamVideoInteractionMapper, StreamVideoInteraction> implements IStreamVideoInteractionService {
}
