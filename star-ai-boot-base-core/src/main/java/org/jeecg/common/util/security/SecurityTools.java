package org.jeecg.common.util.security;

import cn.hutool.core.codec.Base64Encoder;
import com.alibaba.fastjson.JSONObject;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.jeecg.common.util.security.entity.MyKeyPair;
import org.jeecg.common.util.security.entity.SecurityReq;
import org.jeecg.common.util.security.entity.SecurityResp;
import org.jeecg.common.util.security.entity.SecuritySignReq;
import org.jeecg.common.util.security.entity.SecuritySignResp;

/** SecurityTools: National secret SM2 signature + SM4 payload. */
@Slf4j
public class SecurityTools {

  public static final String ALGORITHM = "SM4/CBC/PKCS7Padding";

  public static SecurityResp valid(SecurityReq req) {
    SecurityResp resp = new SecurityResp();
    try {
      String keyHex =
          new String(
              cn.hutool.core.codec.Base64Decoder.decode(req.getAesKey()), StandardCharsets.UTF_8);
      String plain = SmCryptoUtil.sm4Decrypt(req.getData(), keyHex);
      resp.setData(JSONObject.parseObject(plain));
      boolean verify =
          SmCryptoUtil.sm2Verify(
              plain.getBytes(StandardCharsets.UTF_8),
              cn.hutool.core.codec.Base64Decoder.decode(req.getSignData()),
              req.getPubKey());
      resp.setSuccess(verify);
    } catch (Exception e) {
      log.warn("SM2/SM4 valid failed", e);
      resp.setSuccess(false);
    }
    return resp;
  }

  public static SecuritySignResp sign(SecuritySignReq req) {
    String keyHex = SmCryptoUtil.generateSm4KeyHex();
    String data = req.getData();
    String enc = SmCryptoUtil.sm4Encrypt(data, keyHex);
    byte[] signed = SmCryptoUtil.sm2Sign(data.getBytes(StandardCharsets.UTF_8), req.getPrikey());

    SecuritySignResp resp = new SecuritySignResp();
    resp.setAesKey(Base64Encoder.encode(keyHex.getBytes(StandardCharsets.UTF_8)));
    resp.setData(enc);
    resp.setSignData(Base64Encoder.encode(signed));
    return resp;
  }

  public static MyKeyPair generateKeyPair() {
    cn.hutool.crypto.asymmetric.SM2 sm2 = SmCryptoUtil.createSm2();
    MyKeyPair resp = new MyKeyPair();
    resp.setPriKey(sm2.getPrivateKeyBase64());
    resp.setPubKey(sm2.getPublicKeyBase64());
    return resp;
  }
}
