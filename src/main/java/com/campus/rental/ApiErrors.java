package com.campus.rental;

import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> business(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(Map.of("message", ex.getReason() == null ? "请求失败" : ex.getReason()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409).body(Map.of("message", "记录重复、时段已占用或存在关联数据，请重新选择"));
  }

  @ExceptionHandler({
    java.time.DateTimeException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(Exception ex) {
    return ResponseEntity.badRequest().body(Map.of("message", "参数格式错误，请检查输入"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> tooLarge(Exception ex) {
    return ResponseEntity.status(413).body(Map.of("message", "文件不能超过5MB"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception ex) {
    LOG.error("服务异常，类型={}", ex.getClass().getSimpleName());
    return ResponseEntity.status(503).body(Map.of("message", "系统繁忙，请稍后重试"));
  }
}
