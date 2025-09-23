package ru.rpovetkin.notifications.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.notifications.dto.NotificationRequest;
import ru.rpovetkin.notifications.dto.NotificationResponse;
import ru.rpovetkin.notifications.service.EmailNotificationService;
import ru.rpovetkin.notifications.service.NotificationService;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private EmailNotificationService emailNotificationService;

    @Test
    @DisplayName("GET /api/notifications/health returns OK")
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/notifications/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/notifications/send returns 200 on success")
    void sendNotification_success() throws Exception {
        NotificationResponse resp = NotificationResponse.builder()
                .success(true)
                .message("ok")
                .build();
        given(notificationService.sendNotification(any(NotificationRequest.class))).willReturn(resp);

        NotificationRequest req = NotificationRequest.builder()
                .userId("u1")
                .type("INFO")
                .title("t")
                .message("m")
                .source("test")
                .build();

        mockMvc.perform(post("/api/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
