package org.jeecg.stream.service;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.stream.dto.CreateOrderReq;
import org.jeecg.stream.entity.StreamVideoPurchase;

public interface IStreamPurchaseService extends IService<StreamVideoPurchase> {
    StreamVideoPurchase createEpisodeOrder(CreateOrderReq req);
    StreamVideoPurchase getByOrderNo(String orderNo);
    boolean hasPurchased(Long userId, Long videoId);
}
