package org.jeecg.creator.util;
import org.jeecg.creator.entity.CreatorAccount;
import org.jeecg.stream.entity.StreamVideo;
import org.springframework.util.Assert;
import java.math.BigDecimal;

public class PricingValidator {
    public static void validate(CreatorAccount account, StreamVideo video) {
        Assert.isTrue(account.getPricingEnabled() == 1, "创作者未开启自助定价权限");
        BigDecimal price = video.getPriceAmount() == null ? BigDecimal.ZERO : video.getPriceAmount();
        if ("free".equals(video.getPriceType()) || price.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal min = account.getPriceAmountMin() != null ? account.getPriceAmountMin() : new BigDecimal("9.99");
        BigDecimal max = account.getPriceAmountMax() != null ? account.getPriceAmountMax() : new BigDecimal("99.99");
        Assert.isTrue(
            price.compareTo(min) >= 0 && price.compareTo(max) <= 0,
            String.format("定价 ¥%s 不在创作者允许区间 [¥%s, ¥%s] 内, 请调整或提交人工审核", price, min, max)
        );
    }
}
