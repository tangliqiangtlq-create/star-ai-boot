package org.jeecg.stream.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.stream.entity.StreamWithdrawalRequest;
import org.jeecg.stream.mapper.StreamWithdrawalRequestMapper;
import org.jeecg.stream.service.IStreamWithdrawalRequestService;
import org.springframework.stereotype.Service;

@Service
public class StreamWithdrawalRequestServiceImpl extends ServiceImpl<StreamWithdrawalRequestMapper, StreamWithdrawalRequest> implements IStreamWithdrawalRequestService {
}
