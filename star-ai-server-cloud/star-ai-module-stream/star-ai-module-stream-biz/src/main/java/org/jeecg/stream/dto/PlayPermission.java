package org.jeecg.stream.dto;
import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;

@Data
@Builder
public class PlayPermission {
    private boolean granted;
    private String permissionType;
    private String priceType;
    private BigDecimal priceAmount;
    private Integer previewSec;
    private String hlsMasterUrl;

    public static PlayPermission granted(String type) {
        return PlayPermission.builder().granted(true).permissionType(type).build();
    }

    public static PlayPermission locked(String priceType, BigDecimal priceAmount, Integer previewSec) {
        return PlayPermission.builder()
            .granted(false)
            .permissionType("locked")
            .priceType(priceType)
            .priceAmount(priceAmount)
            .previewSec(previewSec)
            .build();
    }
}
