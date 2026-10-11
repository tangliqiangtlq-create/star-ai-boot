package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.stream.entity.StreamVideo;
import org.jeecg.stream.mapper.StreamVideoMapper;
import org.jeecg.stream.service.IStreamVideoService;
import org.springframework.stereotype.Service;

@Service
public class StreamVideoServiceImpl extends ServiceImpl<StreamVideoMapper, StreamVideo> implements IStreamVideoService {
}
