package com.wu.pharos.uam.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.wu.pharos.uam.ingestion.controller.EmployeeEventController;
import com.wu.pharos.uam.ingestion.service.EmployeeEventService;
import com.wu.pharos.uam.ingestion.dto.EventResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmployeeEventController.class)
class EmployeeEventControllerTest {
  @Autowired
  MockMvc mvc;
  @MockitoBean
  EmployeeEventService service;

  @Test
  void acceptsRequestsWithoutAuthentication() throws Exception {
    when(service.process(any())).thenReturn(new EventResult(UUID.randomUUID(), "SUCCESS"));
    mvc
      .perform(post("/api/v1/ipass/events").contentType("application/json").content("{}"))
      .andExpect(status().isOk())
      .andExpect(header().string("X-Correlation-ID", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")))
      .andExpect(jsonPath("$.status").value("SUCCESS"))
      .andExpect(jsonPath("$.eventId").isNotEmpty())
      .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(2)));
  }

  @Test
  void generatesANewCorrelationIdForEachRequestAndClearsLoggingContext() throws Exception {
    when(service.process(any())).thenReturn(new EventResult(UUID.randomUUID(), "SUCCESS"));
    var first = mvc.perform(post("/api/v1/ipass/events").contentType("application/json").content("{}")).andReturn();
    var second = mvc.perform(post("/api/v1/ipass/events").contentType("application/json").content("{}")).andReturn();
    org.junit.jupiter.api.Assertions.assertNotEquals(first.getResponse().getHeader("X-Correlation-ID"),
        second.getResponse().getHeader("X-Correlation-ID"));
    org.junit.jupiter.api.Assertions.assertNull(org.slf4j.MDC.get("correlationId"));
  }

  @Test
  void rejectsMalformedJson() throws Exception {
    mvc
      .perform(post("/api/v1/ipass/events").contentType("application/json").content("{"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.details").doesNotExist())
      .andDo(result -> org.junit.jupiter.api.Assertions.assertEquals(result.getResponse().getHeader("X-Correlation-ID"),
          new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("correlationId").asString()));
  }
}
