package com.tripmind.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Base64;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes());

    @Test
    void planReturnsCostedTripsWithinBudget() throws Exception {
        String body = """
                {"origin":"Delhi","days":3,"travellers":2,"budget":30000,"vibes":["hills"],"startDate":"%s"}
                """.formatted(LocalDate.now().plusDays(30));
        mvc.perform(post("/api/plan").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.recommendations", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.recommendations[0].cost.total", lessThanOrEqualTo(30000)))
                .andExpect(jsonPath("$.recommendations[0].dayPlan", hasSize(3)))
                .andExpect(jsonPath("$.query.originLabel").value("Delhi"));
    }

    @Test
    void planValidatesInput() throws Exception {
        mvc.perform(post("/api/plan").contentType(MediaType.APPLICATION_JSON).content("{\"origin\":\"Delhi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("budget")));
        mvc.perform(post("/api/plan").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void catalogueEndpoints() throws Exception {
        mvc.perform(get("/api/origins")).andExpect(status().isOk()).andExpect(jsonPath("$", hasItem("Pune")));
        mvc.perform(get("/api/destinations")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(greaterThan(30))));
        mvc.perform(get("/api/destinations/goa")).andExpect(status().isOk()).andExpect(jsonPath("$.highlights").isArray());
        mvc.perform(get("/api/destinations/nowhere")).andExpect(status().isNotFound());
    }

    @Test
    void adminRoutesRequireCredentials() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/admin/stats").header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:wrong".getBytes())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.users").exists());
        mvc.perform(get("/api/admin/leads/export").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void formerlyPublicDataIsNoLongerExposed() throws Exception {
        mvc.perform(get("/api/leads")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/itinerary-requests")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/packages/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/payment").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void accountLifecycle() throws Exception {
        String email = "trav" + System.nanoTime() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"email\":\"" + email + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"email\":\"" + email + "\",\"password\":\"long-enough-pw\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(res).get("token").asText();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha\",\"email\":\"" + email.toUpperCase() + "\",\"password\":\"long-enough-pw\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        // A signed-in search shows up in history
        mvc.perform(post("/api/plan").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\"Pune\",\"days\":2,\"travellers\":2,\"budget\":20000}"))
                .andExpect(status().isOk());
        JsonNode history = mapper.readTree(mvc.perform(get("/api/me/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(1, history.size());

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicFormsValidate() throws Exception {
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"not-an-email\",\"message\":\"Hello there\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"a@example.com\",\"message\":\"Hello there\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/itinerary-request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"a@example.com\",\"destination\":\"Goa\",\"status\":\"sent\",\"days\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void csvCellsAreSafe() {
        org.junit.jupiter.api.Assertions.assertEquals("\"'=HYPERLINK(1)\"", AdminController.cell("=HYPERLINK(1)"));
        org.junit.jupiter.api.Assertions.assertEquals("\"a \"\"b\"\"\"", AdminController.cell("a \"b\""));
    }
}
