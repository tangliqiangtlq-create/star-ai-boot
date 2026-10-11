package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.stream.entity.StreamVideoCollection;
import org.jeecg.stream.mapper.StreamVideoCollectionMapper;
import org.jeecg.stream.service.IStreamVideoCollectionService;
import org.springframework.stereotype.Service;

@Service
public class StreamVideoCollectionServiceImpl extends ServiceImpl<StreamVideoCollectionMapper, StreamVideoCollection> implements IStreamVideoCollectionService {
}
