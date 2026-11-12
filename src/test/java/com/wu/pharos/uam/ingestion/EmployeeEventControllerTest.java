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
      .andExpect(jsonPath("$.status").value("SUCCESS"))
      .andExpect(jsonPath("$.eventId").isNotEmpty())
      .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(2)));
  }

  @Test
  void rejectsMalformedJson() throws Exception {
    mvc.perform(post("/api/v1/ipass/events").contentType("application/json").content("{")).andExpect(status().isBadRequest());
  }
}
