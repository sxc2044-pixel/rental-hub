package com.campus.rental;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final Store store;

  public WebConfig(Store store) {
    this.store = store;
  }

  public static Map<String, Object> user(HttpSession session) {
    @SuppressWarnings("unchecked")
    var user = (Map<String, Object>) session.getAttribute("user");
    if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    return user;
  }

  public static long uid(HttpSession s) {
    return Store.id(user(s));
  }

  public static boolean admin(HttpSession s) {
    return "ADMIN".equals(user(s).get("role"));
  }

  public static void student(HttpSession s) {
    if (admin(s)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "此功能仅供学生使用");
  }

  public static String csrf(HttpSession s) {
    if (s.getAttribute("csrf") == null) s.setAttribute("csrf", UUID.randomUUID().toString());
    return s.getAttribute("csrf").toString();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest req, HttpServletResponse res, Object handler) {
                res.setHeader("X-Content-Type-Options", "nosniff");
                res.setHeader("X-Frame-Options", "DENY");
                res.setHeader("Referrer-Policy", "same-origin");
                res.setHeader(
                    "Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline';"
                        + " script-src 'self'; object-src 'none'; frame-ancestors 'none'");
                String path = req.getRequestURI();
                if (!path.startsWith("/api/")) return true;
                res.setHeader("Cache-Control", "no-store");
                var session = req.getSession();
                if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
                    && !csrf(session).equals(req.getHeader("X-CSRF-Token")))
                  throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请求验证失效，请刷新页面");
                if (session.getAttribute("user") != null) {
                  var previous = user(session);
                  var current =
                      store.one(
                          "SELECT id,account,name,role,enabled,session_version FROM t_user WHERE"
                              + " id=?",
                          uid(session));
                  if (!Objects.equals(
                      previous.get("session_version"), current.get("session_version"))) {
                    session.invalidate();
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "密码已修改，请重新登录");
                  }
                  if (!Boolean.TRUE.equals(current.get("enabled"))
                      && !Integer.valueOf(1).equals(current.get("enabled"))) {
                    session.invalidate();
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已禁用");
                  }
                  session.setAttribute("user", current);
                }
                if (path.startsWith("/api/admin/") && !admin(session))
                  throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
                return true;
              }
            })
        .addPathPatterns("/**");
  }
}
