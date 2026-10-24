package com.campus.rental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 本机 Web 服务入口，数据库可通过 profile 切换到 Kali。 */
@SpringBootApplication
public class RentalApplication {
  public static void main(String[] args) {
    SpringApplication.run(RentalApplication.class, args);
  }
}
