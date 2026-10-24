package com.campus.rental;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.web.server.ResponseStatusException;

class RulesTest {
  @ParameterizedTest
  @ValueSource(strings = {"abc123", "Student123!", "中文abc123"})
  void acceptsStrongPasswords(String p) {
    assertEquals(p, Rules.password(Map.of("password", p)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"123456", "abcdef", "a1", "", "中文中文12"})
  void rejectsWeakPasswords(String p) {
    assertThrows(ResponseStatusException.class, () -> Rules.password(Map.of("password", p)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"13800138000", "19912345678"})
  void acceptsPhone(String p) {
    assertEquals(p, Rules.phone(Map.of("phone", p)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "12800138000", "1380013800a", "138001380000"})
  void rejectsPhone(String p) {
    assertThrows(ResponseStatusException.class, () -> Rules.phone(Map.of("phone", p)));
  }

  @ParameterizedTest
  @CsvSource({
    "PENDING,CONFIRMED,true",
    "CONFIRMED,COMPLETED,true",
    "PENDING,CANCELLED,false",
    "CONFIRMED,CANCELLED,true"
  })
  void acceptsTransitions(String current, String next, boolean admin) {
    assertDoesNotThrow(() -> Rules.reservationTransition(current, next, admin));
  }

  @ParameterizedTest
  @CsvSource({
    "PENDING,COMPLETED,true",
    "COMPLETED,CONFIRMED,true",
    "CONFIRMED,CANCELLED,false",
    "CANCELLED,PENDING,true",
    "PENDING,CONFIRMED,false"
  })
  void rejectsTransitions(String current, String next, boolean admin) {
    assertThrows(
        ResponseStatusException.class, () -> Rules.reservationTransition(current, next, admin));
  }

  @Test
  void acceptsFutureWholeHour() {
    var time = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
    assertEquals(time, Rules.visit(time.toString()));
  }

  @Test
  void rejectsPast() {
    assertThrows(
        ResponseStatusException.class,
        () -> Rules.visit(LocalDateTime.now().minusDays(1).toString()));
  }

  @Test
  void rejectsHalfHour() {
    assertThrows(
        ResponseStatusException.class,
        () -> Rules.visit(LocalDateTime.now().plusDays(1).withHour(10).withMinute(30).toString()));
  }

  @Test
  void rejectsTooFarFuture() {
    assertThrows(
        ResponseStatusException.class,
        () -> Rules.visit(LocalDateTime.now().plusDays(40).toString()));
  }

  @Test
  void rejectsInvalidDate() {
    assertThrows(ResponseStatusException.class, () -> Rules.visit("bad-date"));
  }

  @Test
  void preservesDecimal() {
    assertEquals(
        new BigDecimal("123.45"),
        Rules.decimal(Map.of("rent", "123.45"), "rent", new BigDecimal("1000")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1001", "12.345", "NaN"})
  void rejectsInvalidDecimal(String value) {
    assertThrows(
        ResponseStatusException.class,
        () -> Rules.decimal(Map.of("rent", value), "rent", new BigDecimal("1000")));
  }

  @Test
  void rejectsTextLengthAndNul() {
    assertThrows(ResponseStatusException.class, () -> Rules.text(Map.of("x", ""), "x", 1, 5));
    assertThrows(
        ResponseStatusException.class, () -> Rules.text(Map.of("x", "a\u0000"), "x", 1, 5));
  }
}
