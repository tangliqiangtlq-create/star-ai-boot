package org.jeecg.handler.swagger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Swagger aggregation endpoints; all three are required by doc.html
 *
 * @author zyf
 * @date: 2022/4/21 10:55
 */
@RestController
@RequestMapping("/swagger-resources")
public class SwaggerResourceController {
  private MySwaggerResourceProvider swaggerResourceProvider;

  /** Production environment: disable Swagger documentation */
  @Value("${knife4j.production:#{null}}")
  private Boolean production;

  @Autowired
  public SwaggerResourceController(MySwaggerResourceProvider swaggerResourceProvider) {
    this.swaggerResourceProvider = swaggerResourceProvider;
  }

  @RequestMapping(value = "/configuration/security", method = RequestMethod.POST)
  public ResponseEntity<SwaggerSecurityConfiguration> securityConfiguration() {
    return new ResponseEntity<>(new SwaggerSecurityConfiguration(), HttpStatus.OK);
  }

  @RequestMapping(value = "/configuration/ui", method = RequestMethod.POST)
  public ResponseEntity<SwaggerUiConfiguration> uiConfiguration() {
    return new ResponseEntity<>(new SwaggerUiConfiguration(), HttpStatus.OK);
  }

  @RequestMapping(method = RequestMethod.POST)
  public ResponseEntity<List<MySwaggerResourceProvider.GatewaySwaggerResource>> swaggerResources() {
    // Whether to disable Swagger in production
    if (production != null && production) {
      return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
    }
    return new ResponseEntity<>(swaggerResourceProvider.get(), HttpStatus.OK);
  }

  public static class SwaggerSecurityConfiguration {
    private String apiKeyVehicle = "header";
    private String apiKeyName = "Authorization";
    private String clientId;
    private String clientSecret;
    private String realm;
    private String appName;
    private String scopeSeparator = ",";
    private String additionalQueryStringParams;
    private boolean useBasicAuthenticationWithAccessCodeGrant;
    private boolean enableCsrfSupport;

    public String getApiKeyVehicle() {
      return apiKeyVehicle;
    }

    public void setApiKeyVehicle(String apiKeyVehicle) {
      this.apiKeyVehicle = apiKeyVehicle;
    }

    public String getApiKeyName() {
      return apiKeyName;
    }

    public void setApiKeyName(String apiKeyName) {
      this.apiKeyName = apiKeyName;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public String getRealm() {
      return realm;
    }

    public void setRealm(String realm) {
      this.realm = realm;
    }

    public String getAppName() {
      return appName;
    }

    public void setAppName(String appName) {
      this.appName = appName;
    }

    public String getScopeSeparator() {
      return scopeSeparator;
    }

    public void setScopeSeparator(String scopeSeparator) {
      this.scopeSeparator = scopeSeparator;
    }

    public String getAdditionalQueryStringParams() {
      return additionalQueryStringParams;
    }

    public void setAdditionalQueryStringParams(String additionalQueryStringParams) {
      this.additionalQueryStringParams = additionalQueryStringParams;
    }

    public boolean isUseBasicAuthenticationWithAccessCodeGrant() {
      return useBasicAuthenticationWithAccessCodeGrant;
    }

    public void setUseBasicAuthenticationWithAccessCodeGrant(boolean useBasicAuthenticationWithAccessCodeGrant) {
      this.useBasicAuthenticationWithAccessCodeGrant = useBasicAuthenticationWithAccessCodeGrant;
    }

    public boolean isEnableCsrfSupport() {
      return enableCsrfSupport;
    }

    public void setEnableCsrfSupport(boolean enableCsrfSupport) {
      this.enableCsrfSupport = enableCsrfSupport;
    }
  }

  public static class SwaggerUiConfiguration {
    private String deepLinking = "true";
    private String displayOperationId = "false";
    private String defaultModelsExpandDepth = "1";
    private String defaultModelExpandDepth = "1";
    private String defaultModelRendering = "example";
    private String displayRequestDuration = "false";
    private String docExpansion = "none";
    private String filter = "false";
    private Integer maxDisplayedTags;
    private String operationsSorter = "alpha";
    private String showExtensions = "false";
    private String tagsSorter = "alpha";
    private String validatorUrl;
    private String apisSorter = "alpha";
    private String jsonEditor = "false";
    private String showRequestHeaders = "false";
    private String supportedSubmitMethods = "[get,put,post,delete,options,head,patch,trace]";
    private String requestTimeout;
    private String withCredentials = "false";

    public String getDeepLinking() {
      return deepLinking;
    }

    public void setDeepLinking(String deepLinking) {
      this.deepLinking = deepLinking;
    }

    public String getDisplayOperationId() {
      return displayOperationId;
    }

    public void setDisplayOperationId(String displayOperationId) {
      this.displayOperationId = displayOperationId;
    }

    public String getDefaultModelsExpandDepth() {
      return defaultModelsExpandDepth;
    }

    public void setDefaultModelsExpandDepth(String defaultModelsExpandDepth) {
      this.defaultModelsExpandDepth = defaultModelsExpandDepth;
    }

    public String getDefaultModelExpandDepth() {
      return defaultModelExpandDepth;
    }

    public void setDefaultModelExpandDepth(String defaultModelExpandDepth) {
      this.defaultModelExpandDepth = defaultModelExpandDepth;
    }

    public String getDefaultModelRendering() {
      return defaultModelRendering;
    }

    public void setDefaultModelRendering(String defaultModelRendering) {
      this.defaultModelRendering = defaultModelRendering;
    }

    public String getDisplayRequestDuration() {
      return displayRequestDuration;
    }

    public void setDisplayRequestDuration(String displayRequestDuration) {
      this.displayRequestDuration = displayRequestDuration;
    }

    public String getDocExpansion() {
      return docExpansion;
    }

    public void setDocExpansion(String docExpansion) {
      this.docExpansion = docExpansion;
    }

    public String getFilter() {
      return filter;
    }

    public void setFilter(String filter) {
      this.filter = filter;
    }

    public Integer getMaxDisplayedTags() {
      return maxDisplayedTags;
    }

    public void setMaxDisplayedTags(Integer maxDisplayedTags) {
      this.maxDisplayedTags = maxDisplayedTags;
    }

    public String getOperationsSorter() {
      return operationsSorter;
    }

    public void setOperationsSorter(String operationsSorter) {
      this.operationsSorter = operationsSorter;
    }

    public String getShowExtensions() {
      return showExtensions;
    }

    public void setShowExtensions(String showExtensions) {
      this.showExtensions = showExtensions;
    }

    public String getTagsSorter() {
      return tagsSorter;
    }

    public void setTagsSorter(String tagsSorter) {
      this.tagsSorter = tagsSorter;
    }

    public String getValidatorUrl() {
      return validatorUrl;
    }

    public void setValidatorUrl(String validatorUrl) {
      this.validatorUrl = validatorUrl;
    }

    public String getApisSorter() {
      return apisSorter;
    }

    public void setApisSorter(String apisSorter) {
      this.apisSorter = apisSorter;
    }

    public String getJsonEditor() {
      return jsonEditor;
    }

    public void setJsonEditor(String jsonEditor) {
      this.jsonEditor = jsonEditor;
    }

    public String getShowRequestHeaders() {
      return showRequestHeaders;
    }

    public void setShowRequestHeaders(String showRequestHeaders) {
      this.showRequestHeaders = showRequestHeaders;
    }

    public String getSupportedSubmitMethods() {
      return supportedSubmitMethods;
    }

    public void setSupportedSubmitMethods(String supportedSubmitMethods) {
      this.supportedSubmitMethods = supportedSubmitMethods;
    }

    public String getRequestTimeout() {
      return requestTimeout;
    }

    public void setRequestTimeout(String requestTimeout) {
      this.requestTimeout = requestTimeout;
    }

    public String getWithCredentials() {
      return withCredentials;
    }

    public void setWithCredentials(String withCredentials) {
      this.withCredentials = withCredentials;
    }
  }
}
