package org.jeecg.modules.base.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
public class CustomErrorController implements ErrorController {

  /** Accept any method — Tomcat error dispatch for WS/filter sendError uses GET. */
  @RequestMapping("/error")
  public String handleError(HttpServletRequest request) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

    log.error("request URI:{} status: {}", status, request.getRequestURI());

    if (status != null) {
      int statusCode = Integer.parseInt(status.toString());
      if (statusCode == HttpStatus.NOT_FOUND.value()) {
        return "404";
      } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
        return "403";
      } else {
        return "500";
      }
    }
    return "500";
  }
}
