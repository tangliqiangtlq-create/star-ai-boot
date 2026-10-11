package org.jeecg.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
public class CspReportController {

  @PostMapping(path = "/_/csp-report", consumes = {"application/csp-report", "application/reports+json", "*/*"})
  public Mono<ResponseEntity<Void>> receive(@RequestBody(required = false) String body) {
    String payload = body == null ? "" : body.trim();
    if (payload.length() > 4096) {
      payload = payload.substring(0, 4096) + "...(truncated)";
    }
    log.warn("Received CSP violation report: {}", payload);
    return Mono.just(ResponseEntity.noContent().build());
  }
}
