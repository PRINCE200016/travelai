package com.tripmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerConstraintIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void chat_indore1000_oneDay_spiritual_returnsUjjain_withStrictCosts() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "originCity", "Indore",
                "budget", 1000,
                "duration", 1,
                "travelType", "Spiritual",
                "travelersCount", 1
        ));

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination", is("Ujjain, Madhya Pradesh")))
                .andExpect(jsonPath("$.description", is("")))
                .andExpect(jsonPath("$.justification", containsString("hard constraints")))
                .andExpect(jsonPath("$.costBreakdown.total", lessThanOrEqualTo(1000)))
                .andExpect(jsonPath("$.costBreakdown.stay", is(0)))
                .andExpect(jsonPath("$.costBreakdown.travel", lessThanOrEqualTo(400)))
                .andExpect(jsonPath("$.costBreakdown.recommendedMode", is("bus_local")));
    }

    @Test
    void chat_tooRestrictive_returnsOnlyFallbackMessage() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "originCity", "Indore",
                "budget", 1000,
                "duration", 3,
                "travelType", "Adventure",
                "travelersCount", 2
        ));

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("fail")))
                .andExpect(jsonPath("$.restrictive", is(true)))
                .andExpect(jsonPath("$.alternatives", notNullValue()))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.costBreakdown").doesNotExist())
                .andExpect(jsonPath("$.justification").doesNotExist());
    }
}

