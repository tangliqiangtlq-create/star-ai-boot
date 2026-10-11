package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.stream.entity.StreamVideoPlayHistory;
import org.jeecg.stream.mapper.StreamVideoPlayHistoryMapper;
import org.jeecg.stream.service.IStreamVideoPlayHistoryService;
import org.springframework.stereotype.Service;

@Service
public class StreamVideoPlayHistoryServiceImpl extends ServiceImpl<StreamVideoPlayHistoryMapper, StreamVideoPlayHistory> implements IStreamVideoPlayHistoryService {
}
