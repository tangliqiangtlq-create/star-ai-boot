package org.jeecg.modules.system.shard.client;

import java.util.List;
import java.util.Map;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.CustomFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cross-module shard client (SYSTEM to DCS).
 *
 * <p>S2S: must declare {@code configuration = CustomFeignConfig.class}, otherwise the outbound Feign
 * call carries no {@code X-Internal-Call-Secret} and the downstream {@code GatewaySignatureFilter}
 * rejects it with 40102 (missing or invalid gateway signature). Spring Cloud OpenFeign 4.x does not
 * inherit parent-context RequestInterceptor beans by default, so declaring the configuration here is
 * mandatory.
 */
@FeignClient(
    value = "jeecg-dcs",
    contextId = "dcsShardClient",
    configuration = CustomFeignConfig.class)
public interface IDcsShardClient {

  @PostMapping(value = "/dcs/api/shard/listDepositBankUserIds")
  Result<List<String>> listDepositBankUserIds();

  @PostMapping(value = "/dcs/api/shard/listActiveBeneficiarySenderIds")
  Result<List<String>> listActiveBeneficiarySenderIds();

  @PostMapping(value = "/dcs/api/shard/listAccountTypesByGroup")
  Result<List<String>> listAccountTypesByGroup(@RequestParam("accountGroup") String accountGroup);

  @PostMapping(value = "/dcs/api/shard/ensureOpsAccountsOpen")
  Result<Boolean> ensureOpsAccountsOpen(
      @RequestParam("userId") String userId,
      @RequestParam("heldTypes") List<String> heldTypes,
      @RequestParam("openTypes") List<String> openTypes);

  @PostMapping(value = "/dcs/api/shard/countBeneficiaryByUserIds")
  Result<Map<String, Integer>> countBeneficiaryByUserIds(@RequestBody List<String> userIds);

  @PostMapping(value = "/dcs/api/shard/countDepositBankByUserIds")
  Result<Map<String, Integer>> countDepositBankByUserIds(@RequestBody List<String> userIds);
}