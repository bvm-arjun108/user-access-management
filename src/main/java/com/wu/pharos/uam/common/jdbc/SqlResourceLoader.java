package com.wu.pharos.uam.common.jdbc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SqlResourceLoader {
  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public String load(String name) {
    return cache.computeIfAbsent(name, key -> {
      try {
        return new ClassPathResource("sql/" + key + ".sql").getContentAsString(StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException("Missing SQL resource: " + key, e);
      }
    });
  }
}
