package com.medrassi.webapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void greetsByName() throws Exception {
        mvc.perform(get("/api/hello?name=Wasseel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome, Wasseel!"));
    }

    @Test
    void greetsTheWorldByDefault() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome, world!"));
    }

    @Test
    void reportsWhichHostAnswered() throws Exception {
        mvc.perform(get("/api/whoami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").isNotEmpty());
    }

    @Test
    void exposesTheHealthEndpointKubernetesWillUse() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
