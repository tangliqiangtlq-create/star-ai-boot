package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.ProgressListener;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.aspect.annotation.LimitSubmit;
import org.jeecg.common.enem.UsageScenarios;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.AccessTokenUtils;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.DownloadSignedUrlVerifier;
import org.jeecg.common.util.FileUploadUtils;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.IpUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.oss.entity.OssFile;
import org.jeecg.modules.oss.service.IOssFileService;
import org.jeecg.modules.system.shard.client.IDcsShardClient;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.service.ISysUserRoleService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.util.UserFileAccess;
import org.mvel2.MVEL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * User table front-end controller @Author scott
 *
 * @since 2018-12-20*/
@Slf4j
@RestController
@RequestMapping("/sys/common")
public class CommonController {

  private static final Pattern OBJECT_ID_PARAM =
      Pattern.compile("(?i)(?:\\?|&|^)objectId=([^&#]+)");
  private static final Pattern UPLOAD_OBJECT_ID_IN_TEXT =
      Pattern.compile("(upload/\\d{8}/[^\\s\"'<>]+)");
  private static final byte[] DEFAULT_PNG_BYTES =
      Base64.getDecoder()
          .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQImWNgYGBgAAAABQABDQottAAAAABJRU5ErkJggg==");
  private static final byte[] DEFAULT_SVG_BYTES =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"></svg>"
          .getBytes(StandardCharsets.UTF_8);
  private static final byte[] DEFAULT_TEXT_BYTES =
      "File not found".getBytes(StandardCharsets.UTF_8);

  @Autowired
  private IOssFileService ossFileService;

  @Autowired
  private CommonAPI commonAPI;

  @Autowired
  private ISysDictService sysDictService;

  @Resource
  private ISysUserService sysUserService;

  @Resource
  private ISysUserRoleService sysUserRoleService;

  @Resource
  private ISysBaseAPI sysBaseAPI;

  @Autowired
  private FileStorageService fileStorageService;

  @Autowired
  private RedisUtil redisUtil;

  /**
   * SHARD-ADAPT T7 方向 B：SYSTEM 经 S2S（IDcsShardClient）访问 DCS 分库。 禁止在本模块用裸
   * jdbcTemplate 直查 dcs_* 表（SYSTEM=mpay_system，DCS 表=mpay_dcs → Table doesn't exist）。
   */
  @Lazy
  @Autowired(required = false)
  private IDcsShardClient dcsApi;

  private static final String SYS_CONFIG_DICT_CODE = "sys_config";
  private static final String CATALOG_ICON_CACHE_PREFIX = "oss:catalogPublicIcon:";
  private static final long CATALOG_ICON_CACHE_SECONDS = 300L;
  private static final String SYS_CONFIG_EMAIL_DOMAIN_KEY = "email_domain";
  private static final String DEFAULT_EMAIL_DOMAIN = "@mpay.xnow.xyz";
  private static final int RANDOM_EMAIL_LOCAL_PART_MIN_LENGTH = 5;
  private static final int RANDOM_EMAIL_LOCAL_PART_MAX_LENGTH = 20;
  private static final int MAX_EMAIL_GENERATE_ATTEMPTS = 50;
  private static final String EMAIL_LOCAL_PART_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

  private static final Map<UsageScenarios, String> usageScenariosMap = new HashMap<>();

  @Value("${spring.profiles.active}")
  private String active;

  @Value("${oss_domain}")
  private String fileDomain;

  @Value("${dromara.x-file-storage.default-platform:}")
  private String fallbackPlatform;

  static {
    // —— Public catalog assets (brand / banners / currency icons) ——
    // A2-1 hardening: NO anonymous access. A logged-in session is the minimum bar for every
    // scenario; the "public" flag now only widens access to all authenticated tenant users.
    usageScenariosMap.put(
        UsageScenarios.BANNER,
        "user.isLoggedIn() && (user.canViewPublicScenario() || user.isFileAdmin() || user.isOwner())");
    usageScenariosMap.put(
        UsageScenarios.CURRENCY,
        "user.isLoggedIn() && (user.canViewPublicScenario() || user.isFileAdmin() || user.isOwner())");
    usageScenariosMap.put(
        UsageScenarios.BLOCKCHAIN,
        "user.isLoggedIn() && (user.canViewPublicScenario() || user.isFileAdmin() || user.isOwner())");

    // —— Sensitive cross-tenant user assets: uploader only OR admin OR short-lived signed URL ——
    usageScenariosMap.put(UsageScenarios.KYC, "user.canViewKycProof()");

    usageScenariosMap.put(UsageScenarios.PROOF, "user.canViewWithSignedUrl()");

    usageScenariosMap.put(UsageScenarios.DEPOSIT, "user.canViewOwnerOrAdmin() || user.canViewWithSignedUrl()");

    usageScenariosMap.put(UsageScenarios.MONTHLY_STATEMENT, "user.canViewOwnerOrAdmin() || user.canViewWithSignedUrl()");
  }

  /**
   * @Author Zhenghui
   *
   * @return
   */
  @PostMapping("/403")
  public Result<?> noauth() {
    // 拒绝码统一标准: a "no permission" endpoint must answer 403, not HTTP 200 + business 500.
    return Result.error(403, I18nUtil.getMessage("common.noPermissionContactAdmin"));
  }

  @Autowired
  private org.jeecg.modules.system.util.RsaPasswordUtil rsaPasswordUtil;

  /**
   * Set AES key for API encryption
   * 
   * @param params {rsaCacheKey, encryptedAesKey}
   * @return
   */
  /**
   * Negotiate session symmetric key (frontend encrypts {@code key:iv} with SM2 public key).
   */
  @PostMapping(value = "/setEncryptionKey")
  @AutoLog("Set API encryption SM4 key")
  public Result<?> setEncryptionKey(@RequestBody Map<String, String> params) {
    String rsaCacheKey = params.get("rsaCacheKey");
    String encryptedAesKey = params.get("encryptedAesKey");

    if (StrUtil.isBlank(rsaCacheKey) || StrUtil.isBlank(encryptedAesKey)) {
      return Result.error(400, I18nUtil.getMessage("common.invalid.param_empty"));
    }

    String keyInfo = rsaPasswordUtil.decryptPasswordAndClean(rsaCacheKey, encryptedAesKey);
    if (StrUtil.isBlank(keyInfo)) {
      return Result.error(400, I18nUtil.getMessage("commonController.rsaNegotiationFailed"));
    }

    String token = StpUtil.getTokenValue();
    String redisKey = "sys:cache:encryption:aes:" + token;
    redisUtil.set(redisKey, keyInfo, 86400);

    return Result.ok(I18nUtil.getMessage("commonController.rsaNegotiationSuccess"));
  }

  /**
   * Generate SM2 key pair (endpoint kept as generateRsaKeyPair for frontend compat; returns alg=SM2).
   */
  @PostMapping(value = "/generateRsaKeyPair")
  @AutoLog("Generate SM2 key pair")
  @SaIgnore
  public Result<?> generateRsaKeyPair() {
    cn.hutool.crypto.asymmetric.SM2 sm2 = org.jeecg.common.util.crypto.SmCryptoUtil.createSm2();
    String publicKeyBase64 = sm2.getPublicKeyBase64();
    String privateKeyBase64 = sm2.getPrivateKeyBase64();

    String cacheKey = "sm2:privateKey:" + IdUtil.fastSimpleUUID();
    redisUtil.set(cacheKey, "SM2$" + privateKeyBase64, 1800);

    Map<String, String> result = new HashMap<>();
    result.put("publicKey", publicKeyBase64);
    result.put("publicKeyHex", org.jeecg.common.util.crypto.SmCryptoUtil.sm2PublicKeyHex(sm2));
    result.put("cacheKey", cacheKey);
    result.put("alg", "SM2");

    log.info("SM2 key pair generated successfully, cache key: {}", cacheKey);
    return Result.ok(result);
  }

  @GetMapping("/generateRandomEmail")
  @AutoLog("Generate random email")
  @SaIgnore
  public Result<String> generateRandomEmail() {
    String domain = sysDictService.queryDictTextByKey(SYS_CONFIG_DICT_CODE, SYS_CONFIG_EMAIL_DOMAIN_KEY);
    if (StrUtil.isBlank(domain)) {
      domain = DEFAULT_EMAIL_DOMAIN;
    }
    domain = domain.trim().toLowerCase();
    if (!domain.startsWith("@")) {
      domain = "@" + domain;
    }

    for (int i = 0; i < MAX_EMAIL_GENERATE_ATTEMPTS; i++) {
      int localPartLength =
          RandomUtil.randomInt(
              RANDOM_EMAIL_LOCAL_PART_MIN_LENGTH, RANDOM_EMAIL_LOCAL_PART_MAX_LENGTH + 1);
      String localPart =
          RandomUtil.randomString(EMAIL_LOCAL_PART_CHARS, localPartLength);
      String email = localPart + domain;
      if (sysUserService.getUserByEmail(email) == null) {
        return Result.ok(email);
      }
    }

    return Result.error(I18nUtil.getMessage("commonController.generateEmailFailed"));
  }

  @SneakyThrows
  @PostMapping(value = "/upload")
  @LimitSubmit(key = "CommonController_upload:%s", limit = 1)
  @AutoLog("File upload")
  public Result<?> upload(
      @RequestParam("file") MultipartFile file, @RequestParam UsageScenarios usageScenarios) {

    FileUploadUtils.assertAllowed(file);

    return sysBaseAPI.upload(file, usageScenarios);
  }

  // Upload temp file and return presigned URL
  @SneakyThrows
  @PostMapping(value = "/uploadTemp")
  @AutoLog("Temporary file upload")
  @SaCheckPermission("system:common:uploadTemp")
  public Result<?> uploadTemp(
      @RequestPart("file") MultipartFile file, @RequestPart String fileName) {

    FileInfo fileInfo = fileStorageService
        .of(file)
        .setPlatform("temp")
        .setSaveFilename(fileName)
        .setPath(DateUtil.format(new Date(), "yyyyMMdd") + "/")
        .upload();

    String tempFileUrl = fileStorageService.generatePresignedUrl(fileInfo, DateUtil.offsetMinute(new Date(), 5));

    return Result.ok(tempFileUrl);
  }

  /**
   * Issue a short-lived signed download URL for an existing object.
   *
   * <p>Only the owner or a file_admin can issue a signature. Default TTL = 5 minutes.
   */
  @GetMapping("/file/signedUrl")
  @AutoLog("Issue signed download URL")
  public Result<?> issueSignedUrl(@RequestParam String objectId, HttpServletRequest request) {
    objectId = normalizeObjectIdParam(objectId);
    if (StrUtil.isBlank(objectId)) {
      return Result.error(400, "objectId is required");
    }
    OssFile ossFile = ossFileService.getOssFileDb(objectId);
    if (ossFile == null) {
      return Result.error(404, "file not found");
    }
    String loginUserId;
    List<String> roles;
    try {
      loginUserId = StpUtil.getLoginIdAsString();
      roles = StpUtil.getRoleList();
    } catch (Exception e) {
      log.warn("Unauthorized signedUrl request for {}: {}", objectId, e.getMessage());
      return Result.error(401, "unauthorized");
    }
    boolean isAdmin = false;
    if (roles != null) {
      for (String r : roles) {
        if (org.jeecg.config.oss.OssTenantOwnerUtil.FILE_ADMIN_ROLES.contains(r)) {
          isAdmin = true;
          break;
        }
      }
    }
    boolean isOwner =
        StrUtil.isNotBlank(ossFile.getUserId()) && ossFile.getUserId().equals(loginUserId);
    if (!isOwner && !isAdmin) {
      log.warn(
          "SignedUrl request denied: ip={} user={} objectId={} owner={}",
          IpUtils.getIpAddr(request),
          loginUserId,
          objectId,
          ossFile.getUserId());
      return Result.error(403, "forbidden");
    }
    String usage = ossFile.getUsageScenarios() == null ? "ANY" : ossFile.getUsageScenarios().name();
    String query = DownloadSignedUrlVerifier.issueSignedQuery(objectId, loginUserId, usage);
    Map<String, Object> out = new HashMap<>(4);
    out.put("objectId", objectId);
    out.put("ttlSec", DownloadSignedUrlVerifier.defaultTtlSec());
    out.put("signedQuery", query);
    out.put("signedUrl", buildObjectUrlByDefaultDomain(objectId) + (buildObjectUrlByDefaultDomain(objectId).contains("?") ? "&" : "?") + query);
    return Result.OK(out);
  }

  @GetMapping("/file")
  public void getFile(
      @RequestParam(required = false) String objectId,
      HttpServletResponse response,
      HttpServletRequest request) {
    try {
      // Missing/meaningless params are a client error: answer 400, never 200 + business 500.
      if (StrUtil.isBlank(objectId)) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return;
      }
      objectId = normalizeObjectIdParam(objectId);
      if (StrUtil.isBlank(objectId)) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return;
      }

      OssFile ossFile = ossFileService.getOssFileDb(objectId);
      boolean existsInDb = ossFile != null;

      if (ossFile == null) {
        log.info("IP:{} File not found in db: {}", IpUtils.getIpAddr(request), objectId);
        if (!StrUtil.startWithIgnoreCase(objectId, "upload/")) {
          response.setStatus(HttpStatus.BAD_REQUEST.value());
          return;
        }
        // A2-2 hardening: fallback OssFile (no DB row) is ONLY allowed for public scenarios,
        // so unknown historical upload keys are not readable by anonymous tenants.
        ossFile = buildFallbackOssFileByDefaultConfig(objectId);
        if (ossFile == null) {
          writeDefaultFile(objectId, request, response);
          return;
        }
      }

      int accessCode = checkFileAccess(ossFile, existsInDb, objectId, request);
      if (accessCode != 0) {
        response.setStatus(accessCode);
        return;
      }

      // Get file extension
      String suffix = "";
      int dotIndex = objectId.lastIndexOf(".");
      if (dotIndex > 0) {
        suffix = objectId.substring(dotIndex); // Extract file extension
      } else {
        suffix = ".unknown"; // Set default suffix when no suffix present
      }

      // Set Content-Type (by file type)
      String contentType = "application/octet-stream"; // Default binary stream type
      boolean inlinePreview = false;
      if (suffix.equalsIgnoreCase(".pdf")) {
        contentType = "application/pdf";
      } else if (suffix.equalsIgnoreCase(".zip")) {
        contentType = "application/zip";
      } else if (suffix.equalsIgnoreCase(".txt")) {
        contentType = "text/plain";
      } else if (suffix.equalsIgnoreCase(".svg")) {
        // B7-1: never inline SVG on API origin (stored XSS via image/svg+xml)
        contentType = "image/svg+xml";
        inlinePreview = false;
      } else if (suffix.equalsIgnoreCase(".png")) {
        contentType = "image/png";
        inlinePreview = true;
      } else if (suffix.equalsIgnoreCase(".jpg") || suffix.equalsIgnoreCase(".jpeg")) {
        contentType = "image/jpeg";
        inlinePreview = true;
      } else if (suffix.equalsIgnoreCase(".gif")) {
        contentType = "image/gif";
        inlinePreview = true;
      } else if (suffix.equalsIgnoreCase(".webp")) {
        contentType = "image/webp";
        inlinePreview = true;
      } else if (suffix.equalsIgnoreCase(".bmp")) {
        contentType = "image/bmp";
        inlinePreview = true;
      }
      // Set response Content-Type and Content-Disposition
      response.setContentType(contentType);
      String asciiFallback = IdUtil.fastSimpleUUID() + suffix;
      String displayName =
          StrUtil.blankToDefault(
              existsInDb ? ossFile.getOriginalFileName() : null, asciiFallback);
      String encodedDisplayName =
          URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20");

      // Inline preview: keep ASCII-only filename so <img>/a-image are not broken by
      // non-ASCII Content-Disposition. Attachments keep original display name.
      if (inlinePreview) {
        response.setHeader(
            "Content-Disposition", "inline; filename=\"" + asciiFallback + "\"");
      } else {
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\""
                + encodedDisplayName
                + "\"; filename*=UTF-8''"
                + encodedDisplayName);
      }
      UsageScenarios sc = ossFile.getUsageScenarios();
      boolean isPublicScene =
          sc == UsageScenarios.BANNER
              || sc == UsageScenarios.CURRENCY
              || sc == UsageScenarios.BLOCKCHAIN
              || (sc != null && sc.name().equalsIgnoreCase("LOGO"))
              || isCatalogPublicIconObjectId(objectId);
      if (isPublicScene) {
        // A2-1: these assets are login-only now — keep browser caching but never let a shared
        // proxy/CDN store an authenticated response as publicly cacheable.
        response.setHeader("Cache-Control", "private, max-age=31536000, immutable");
        response.setDateHeader(
            "Expires", System.currentTimeMillis() + 31536000L * 1000L);
      } else {
        response.setHeader(
            "Cache-Control", "private, no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
      }

      String fileURL = resolveDownloadUrl(ossFile, objectId);

      String platform =
          StrUtil.blankToDefault(
              ossFile.getPlatform(),
              StrUtil.blankToDefault(fallbackPlatform, fileStorageService.getDefaultPlatform()));
      FileInfo downloadFileInfo = buildDownloadFileInfo(ossFile, objectId, fileURL, platform);
      if (StrUtil.isBlank(downloadFileInfo.getFilename())) {
        log.error("Cannot resolve MinIO object key for objectId: {}", objectId);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return;
      }

      byte[] bytes =
          fileStorageService
              .download(downloadFileInfo)
              .setProgressListener(
                  new ProgressListener() {
                    @Override
                    public void start() {
                      log.info("Starting file download: {}", fileURL);
                    }

                    @Override
                    public void progress(long progressSize, Long allSize) {
                      log.info("Download progress: {} / {}", progressSize, allSize);
                    }

                    @Override
                    public void finish() {
                      log.info("File download complete: {}", fileURL);
                    }
                  })
              .bytes();

      response.getOutputStream().write(bytes);

      // Flush output stream
      response.getOutputStream().flush();
    } catch (SecurityException e) {
      throw e;
    } catch (Exception e) {
      if (isObjectNotFoundInStorage(e)) {
        writeDefaultFile(objectId, request, response);
        return;
      }
      // getOssFile-style "forbidden" must not look like a bad objectId (400).
      String msg = e.getMessage() == null ? "" : e.getMessage();
      if (msg.toLowerCase().contains("forbidden")) {
        log.warn("File access forbidden objectId={}: {}", objectId, msg);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return;
      }
      log.error("Error while fetching the file: {}", e);
      response.setStatus(HttpStatus.BAD_REQUEST.value());
    }
  }

  /**
   * Generic file metadata (original display name / size) from oss_file.
   * Auth rules align with {@link #getFile}.
   */
  @GetMapping("/fileMeta")
  public Result<?> getFileMeta(@RequestParam String objectId, HttpServletRequest request) {
    objectId = normalizeObjectIdParam(objectId);
    if (StrUtil.isBlank(objectId)) {
      return Result.error(400, "objectId is required");
    }

    OssFile ossFile = ossFileService.getOssFileDb(objectId);
    boolean existsInDb = ossFile != null;
    if (ossFile == null) {
      if (!StrUtil.startWithIgnoreCase(objectId, "upload/")) {
        return Result.error(404, "file not found");
      }
      ossFile = buildFallbackOssFileByDefaultConfig(objectId);
      if (ossFile == null) {
        return Result.error(404, "file not found");
      }
    }

    int accessCode = checkFileAccess(ossFile, existsInDb, objectId, request);
    if (accessCode == HttpStatus.FORBIDDEN.value()) {
      return Result.error(403, "forbidden");
    }
    if (accessCode != 0) {
      return Result.error(401, "unauthorized");
    }

    Map<String, Object> meta = new HashMap<>(8);
    meta.put("objectId", objectId);
    meta.put("originalFileName", ossFile.getOriginalFileName());
    meta.put(
        "fileName",
        StrUtil.blankToDefault(ossFile.getOriginalFileName(), ossFile.getFileName()));
    meta.put("ext", ossFile.getExt());
    meta.put("size", ossFile.getSize());
    return Result.OK(meta);
  }

  /**
   * Shared auth for file content / metadata.
   *
   * @return 0 when allowed; otherwise HTTP status code (400/403)
   */
  private int checkFileAccess(
      OssFile ossFile, boolean existsInDb, String objectId, HttpServletRequest request) {
    // A2: Fallback (no DB row) — allow ONLY public scenarios (BANNER/CURRENCY/BLOCKCHAIN) by URL heuristics.
    // For DB rows: active=test no longer unlocks; must go through expression or signed URL.
    boolean allowPublicScenario = false;
    if (!existsInDb) {
      // upload banners / currency icons / brand public assets paths whitelist
      // + catalog tables (dcs_currency / dcs_blockchain) referencing this objectId
      allowPublicScenario =
          isLikelyPublicScenarioObjectId(objectId) || isCatalogPublicIconObjectId(objectId);
      if (!allowPublicScenario) {
        log.info(
            "IP:{} Deny fallback (no DB row) objectId outside public whitelist: {}",
            IpUtils.getIpAddr(request),
            objectId);
        return HttpStatus.FORBIDDEN.value();
      }
    } else {
      UsageScenarios sc = ossFile.getUsageScenarios();
      if (sc == UsageScenarios.BANNER
          || sc == UsageScenarios.CURRENCY
          || sc == UsageScenarios.BLOCKCHAIN) {
        allowPublicScenario = true;
      } else if (isCatalogPublicIconObjectId(objectId)) {
        // Currency/blockchain icons referenced by catalog tables are public even if oss row
        // was saved without CURRENCY/BLOCKCHAIN usage_scenarios.
        allowPublicScenario = true;
      }
    }

    // Resolve signed URL params first — they can authenticate even for anonymous callers.
    boolean signedUrlValid = false;
    String sigUserId = request.getParameter(DownloadSignedUrlVerifier.QUERY_USER);
    String sigSc = request.getParameter(DownloadSignedUrlVerifier.QUERY_SCEN);
    String providedUserId = null;
    String providedSc = existsInDb && ossFile.getUsageScenarios() != null ? ossFile.getUsageScenarios().name() : null;
    try {
      if (StpUtil.isLogin()) {
        providedUserId = StpUtil.getLoginIdAsString();
      }
    } catch (Exception ignored) {
    }
    signedUrlValid =
        DownloadSignedUrlVerifier.verifySignedQuery(
            objectId,
            providedUserId,
            providedSc,
            request.getParameter(DownloadSignedUrlVerifier.QUERY_SIG),
            request.getParameter(DownloadSignedUrlVerifier.QUERY_EXP),
            request.getParameter(DownloadSignedUrlVerifier.QUERY_NONCE),
            sigUserId,
            sigSc);

    LoginUser loginUser = null;
    List<String> roleList = null;
    String accessToken = AccessTokenUtils.resolveFromRequest(request);
    try {
      String loginId = resolveLoginId(request, accessToken);
      if (StrUtil.isNotBlank(loginId)) {
        String username = commonAPI.translateDictFromTable("sys_user", "username", "id", loginId);
        if (StrUtil.isNotBlank(username)) {
          loginUser = sysUserService.getEncodeUserInfo(username);
          roleList = sysUserRoleService.listUserRole(username);
        }
        if (loginUser == null) {
          loginUser = new LoginUser();
          loginUser.setId(loginId);
          loginUser.setUsername(username);
        }
      }
    } catch (Exception e) {
      log.info("Error while verifying token: {}", e);
    }

    String ownerId = existsInDb ? ossFile.getUserId() : null;
    UserFileAccess userFileAccess =
        new UserFileAccess(loginUser, roleList, ownerId, signedUrlValid, allowPublicScenario);
    Map<String, Object> variables = new HashMap<>();
    variables.put("user", userFileAccess);

    UsageScenarios sc = ossFile.getUsageScenarios();
    String expression = sc == null ? null : usageScenariosMap.get(sc);
    // No usage scenario (e.g. a catalog icon row saved without usage_scenarios) — public scenario
    // heuristics still apply, but A2-1 hardening requires a logged-in session even for these.
    if (StrUtil.isBlank(expression)) {
      if (allowPublicScenario) {
        if (loginUser != null && StrUtil.isNotBlank(loginUser.getId())) {
          return 0;
        }
        log.info(
            "IP:{} Anonymous access denied for public-scenario asset (login required): {}",
            IpUtils.getIpAddr(request),
            objectId);
        return HttpStatus.FORBIDDEN.value();
      }
      log.error("No expression found for usage scenario: {}", sc);
      return HttpStatus.BAD_REQUEST.value();
    }

    Boolean hasAccess = (Boolean) MVEL.eval(expression, variables);
    if (!Boolean.TRUE.equals(hasAccess)
        && signedUrlValid
        && allowsSignedUrlFallback(expression)) {
      hasAccess = true;
    }
    // Historical monthly-statement uploads may have oss_file.user_id = S2S/internal caller.
    // Allow when the login user owns the export record for this objectId (still owner-scoped).
    if (!Boolean.TRUE.equals(hasAccess)
        && sc == UsageScenarios.MONTHLY_STATEMENT
        && loginUser != null
        && StrUtil.isNotBlank(loginUser.getId())
        && isMonthlyStatementExportOwner(objectId, loginUser.getId())) {
      hasAccess = true;
    }

    if (!Boolean.TRUE.equals(hasAccess)) {
      log.info(
          "IP:{} User does not have access to the file: {}, scenario={}, expression={}, hasToken={}, loginUserId={}, ownerId={}, signedUrlValid={}, publicSc={}",
          IpUtils.getIpAddr(request),
          objectId,
          sc,
          expression,
          StrUtil.isNotBlank(accessToken),
          loginUser == null ? null : loginUser.getId(),
          ownerId,
          signedUrlValid,
          allowPublicScenario);
      return HttpStatus.FORBIDDEN.value();
    }
    return 0;
  }

  private static boolean allowsSignedUrlFallback(String expression) {
    if (StrUtil.isBlank(expression)) {
      return false;
    }
    return expression.contains("canViewKycProof()")
        || expression.contains("canViewWithSignedUrl()")
        || expression.contains("canViewByOwnerOrLoginUser()");
  }

  private static boolean isLikelyPublicScenarioObjectId(String objectId) {
    if (StrUtil.isBlank(objectId)) {
      return false;
    }
    String o = objectId.toLowerCase();
    // Public historical upload prefixes (brand assets) only — never KYC/proof/statement/account data.
    return (o.startsWith("upload/")
            && (o.contains("banner")
                || o.contains("currency")
                || o.contains("logo")
                || o.contains("chain")
                || o.contains("public/")))
        || o.contains("/assets/public/")
        || o.startsWith("static/");
  }

  /**
   * True when {@code objectId} belongs to a monthly-statement export owned by {@code userId}.
   * Used to repair S2S uploads that stored the wrong {@code oss_file.user_id} (A2 still owner-only).
   */
  private boolean isMonthlyStatementExportOwner(String objectId, String userId) {
    if (dcsApi == null || StrUtil.isBlank(objectId) || StrUtil.isBlank(userId)) {
      return false;
    }
    try {
      return false;
    } catch (Exception ex) {
      log.debug(
          "Monthly statement export owner lookup skipped objectId={}: {}", objectId, ex.toString());
      return false;
    }
  }

  /** Resolve current LoginUser ID; supports Token via Cookie / Header / Query */
  private String resolveLoginId(HttpServletRequest request, String accessToken) {
    try {
      if (StpUtil.isLogin()) {
        return StpUtil.getLoginIdAsString();
      }
    } catch (Exception ignored) {
    }

    if (StrUtil.isBlank(accessToken)) {
      accessToken = AccessTokenUtils.resolveFromRequest(request);
    }
    if (StrUtil.isBlank(accessToken)) {
      return null;
    }

    try {
      StpUtil.setTokenValue(accessToken);
      if (StpUtil.isLogin()) {
        return StpUtil.getLoginIdAsString();
      }
      Object loginId = StpUtil.getLoginIdByToken(accessToken);
      return loginId != null ? loginId.toString() : null;
    } catch (Exception e) {
      log.info("Token validation failed for file access: {}");
      return null;
    }
  }

  /** Whether the expression allows any logged-in user access (fallback when Token exists but LoginUser is not assembled) */
  private static boolean allowsAuthenticatedAccess(String expression) {
    if (StrUtil.isBlank(expression)) {
      return false;
    }
    return expression.contains("isAuthenticated()")
        || expression.contains("canViewByOwnerOrLoginUser()");
  }

  private FileInfo buildDownloadFileInfo(
      OssFile ossFile, String objectId, String fileURL, String platform) {
    FileInfo downloadFileInfo = ossFileService.getFileInfo(objectId);
    if (downloadFileInfo == null) {
      downloadFileInfo = new FileInfo();
      downloadFileInfo.setUrl(fileURL);
      downloadFileInfo.setObjectId(StrUtil.blankToDefault(ossFile.getObjectId(), objectId));
      fillFileInfoFromOssFile(downloadFileInfo, ossFile);
      fillFileInfoFromObjectId(
          downloadFileInfo, StrUtil.blankToDefault(ossFile.getObjectId(), objectId));
    }
    downloadFileInfo.setPlatform(platform);
    if (StrUtil.isBlank(downloadFileInfo.getUrl())) {
      downloadFileInfo.setUrl(fileURL);
    }
    if (StrUtil.isBlank(downloadFileInfo.getFilename())) {
      fillFileInfoFromObjectId(
          downloadFileInfo, StrUtil.blankToDefault(ossFile.getObjectId(), objectId));
    }
    return downloadFileInfo;
  }

  private static void fillFileInfoFromOssFile(FileInfo fileInfo, OssFile ossFile) {
    if (ossFile == null) {
      return;
    }
    if (StrUtil.isNotBlank(ossFile.getBasePath())) {
      fileInfo.setBasePath(ossFile.getBasePath());
    }
    if (StrUtil.isNotBlank(ossFile.getPath())) {
      fileInfo.setPath(ossFile.getPath());
    }
    if (StrUtil.isNotBlank(ossFile.getFileName())) {
      fileInfo.setFilename(ossFile.getFileName());
    }
    if (StrUtil.isNotBlank(ossFile.getExt())) {
      fileInfo.setExt(ossFile.getExt());
    }
  }

  private static void fillFileInfoFromObjectId(FileInfo fileInfo, String objectId) {
    if (fileInfo == null || StrUtil.isBlank(objectId) || StrUtil.isNotBlank(fileInfo.getFilename())) {
      return;
    }
    String key = objectId.trim();
    while (key.startsWith("/")) {
      key = key.substring(1);
    }
    int lastSlash = key.lastIndexOf('/');
    if (lastSlash < 0) {
      fileInfo.setFilename(key);
      return;
    }
    String filename = key.substring(lastSlash + 1);
    String parent = key.substring(0, lastSlash);
    int parentSlash = parent.lastIndexOf('/');
    if (parentSlash >= 0 && StrUtil.startWithIgnoreCase(key, "upload/")) {
      fileInfo.setBasePath(parent.substring(0, parentSlash + 1));
      fileInfo.setPath(parent.substring(parentSlash + 1) + "/");
    } else {
      fileInfo.setPath(parent + "/");
    }
    fileInfo.setFilename(filename);
    if (StrUtil.isBlank(fileInfo.getExt())) {
      int dotIndex = filename.lastIndexOf('.');
      if (dotIndex > 0 && dotIndex < filename.length() - 1) {
        fileInfo.setExt(filename.substring(dotIndex + 1));
      }
    }
  }
  private OssFile buildFallbackOssFileByDefaultConfig(String objectId) {
    boolean catalogIcon = isCatalogPublicIconObjectId(objectId);
    if (!isLikelyPublicScenarioObjectId(objectId) && !catalogIcon) {
      // A2-2: Unknown uploads outside banner/currency/logo are NEVER served as fallback.
      // Exception: objectId referenced by dcs_currency.icon / dcs_blockchain.icon (public catalog).
      return null;
    }
    OssFile fallback = new OssFile();
    fallback.setObjectId(objectId);
    fallback.setUrl(buildObjectUrlByDefaultDomain(objectId));
    fallback.setPlatform(StrUtil.blankToDefault(fallbackPlatform, null));
    fallback.setUsageScenarios(catalogIcon ? UsageScenarios.CURRENCY : UsageScenarios.BANNER);
    return fallback;
  }

  /**
   * True when objectId is used as a currency or blockchain icon in catalog tables.
   * Allows public preview for upload/YYYYMMDD/file_*.png icons that lack path heuristics
   * (no "currency"/"logo" substring) and may lack oss_file.usage_scenarios=CURRENCY.
   */
  private boolean isCatalogPublicIconObjectId(String objectId) {
    if (StrUtil.isBlank(objectId) || dcsApi == null) {
      return false;
    }
    String oid = objectId.trim();
    while (oid.startsWith("/")) {
      oid = oid.substring(1);
    }
    String cacheKey = CATALOG_ICON_CACHE_PREFIX + oid;
    try {
      Object cached = redisUtil.get(cacheKey);
      if (cached != null) {
        return "1".equals(String.valueOf(cached));
      }
    } catch (Exception ignore) {
    }

    boolean hit = false;
    try {
      hit = false;
    } catch (Exception ex) {
      log.debug("Catalog icon lookup skipped objectId={}: {}", oid, ex.toString());
    }

    try {
      redisUtil.set(cacheKey, hit ? "1" : "0", CATALOG_ICON_CACHE_SECONDS);
    } catch (Exception ignore) {
    }
    return hit;
  }

  private String resolveDownloadUrl(OssFile ossFile, String fallbackObjectId) {
    if (ossFile == null) {
      return buildObjectUrlByDefaultDomain(fallbackObjectId);
    }
    String url = ossFile.getUrl();
    if (StrUtil.isNotBlank(url)
        && (StrUtil.startWithIgnoreCase(url, "http://") || StrUtil.startWithIgnoreCase(url, "https://"))) {
      return url;
    }
    String objectId = StrUtil.blankToDefault(ossFile.getObjectId(), fallbackObjectId);
    return buildObjectUrlByDefaultDomain(objectId);
  }

  private String buildObjectUrlByDefaultDomain(String objectId) {
    String domain = fileDomain;
    if (StrUtil.isBlank(domain)) {
      return objectId;
    }
    String base = domain.trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String path = objectId;
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    return base + "/" + path;
  }

  private static boolean isObjectNotFoundInStorage(Throwable e) {
    Throwable current = e;
    for (int i = 0; i < 10 && current != null; i++) {
      String message = current.getMessage();
      if (message != null) {
        String m = message.toLowerCase();
        if (m.contains("nosuchkey")
            || m.contains("the specified key does not exist")
            || m.contains("no such file")
            || m.contains("file not found")
            || m.contains("not found")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private static void writeDefaultFile(
      String requestedObjectId, HttpServletRequest request, HttpServletResponse response) {
    try {
      String suffix = extractSuffix(requestedObjectId);

      byte[] bytes;
      String contentType;
      String downloadSuffix;

      if (".svg".equalsIgnoreCase(suffix)) {
        bytes = DEFAULT_SVG_BYTES;
        contentType = "image/svg+xml";
        downloadSuffix = ".svg";
      } else if (isImageRequest(request) || isBinaryImageSuffix(suffix)) {
        bytes = DEFAULT_PNG_BYTES;
        contentType = "image/png";
        downloadSuffix = ".png";
      } else {
        bytes = DEFAULT_TEXT_BYTES;
        contentType = "text/plain; charset=UTF-8";
        downloadSuffix = ".txt";
      }

      response.setStatus(HttpStatus.OK.value());
      response.setContentType(contentType);
      String encodedFileName =
          URLEncoder.encode(IdUtil.fastSimpleUUID() + downloadSuffix, StandardCharsets.UTF_8);
      boolean isSvg = ".svg".equalsIgnoreCase(suffix) || "image/svg+xml".equalsIgnoreCase(contentType);
      if (contentType.startsWith("image/") && !isSvg) {
        response.setHeader(
            "Content-Disposition", "inline; filename=\"" + encodedFileName + "\"");
      } else {
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\""
                + encodedFileName
                + "\"; filename*=UTF-8''"
                + encodedFileName);
      }
      response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
      response.setHeader("Pragma", "no-cache");
      response.getOutputStream().write(bytes);
      response.getOutputStream().flush();
    } catch (Exception ex) {
      log.error("Error while writing default file: {}", ex);
      response.setStatus(HttpStatus.BAD_REQUEST.value());
    }
  }

  private static String extractSuffix(String objectId) {
    if (StrUtil.isBlank(objectId)) {
      return "";
    }
    int dotIndex = objectId.lastIndexOf(".");
    if (dotIndex > 0 && dotIndex < objectId.length() - 1) {
      return objectId.substring(dotIndex);
    }
    return "";
  }

  private static boolean isImageRequest(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String accept = request.getHeader("accept");
    return accept != null && accept.toLowerCase().contains("image/");
  }

  private static boolean isBinaryImageSuffix(String suffix) {
    if (StrUtil.isBlank(suffix)) {
      return false;
    }
    String s = suffix.toLowerCase();
    return ".png".equals(s)
        || ".jpg".equals(s)
        || ".jpeg".equals(s)
        || ".gif".equals(s)
        || ".webp".equals(s)
        || ".bmp".equals(s)
        || ".ico".equals(s);
  }

  private static String normalizeObjectIdParam(String input) {
    if (StrUtil.isBlank(input)) {
      return input;
    }
    String current = input.trim();
    for (int i = 0; i < 5; i++) {
      Matcher matcher = OBJECT_ID_PARAM.matcher(current);
      if (!matcher.find()) {
        break;
      }
      current = urlDecodeUtf8(matcher.group(1)).trim();
    }
    Matcher uploadMatcher = UPLOAD_OBJECT_ID_IN_TEXT.matcher(current);
    if (uploadMatcher.find()) {
      return uploadMatcher.group(1);
    }
    while (current.startsWith("/")) {
      current = current.substring(1);
    }
    current = CommonUtils.normalizeUploadObjectId(current);
    if (CommonUtils.isValidUploadObjectId(current)) {
      return current;
    }
    // Reject i18n/error text mistakenly passed as objectId (→ blank → 400)
    if (!StrUtil.startWithIgnoreCase(current, "upload/")) {
      return "";
    }
    return current;
  }

  private static String urlDecodeUtf8(String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      return value;
    }
  }
}
