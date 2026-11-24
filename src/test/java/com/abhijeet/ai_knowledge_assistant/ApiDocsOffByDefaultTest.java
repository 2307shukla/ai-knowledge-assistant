package com.abhijeet.ai_knowledge_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The endpoint list is not published unless an operator turns it on. */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/docs-${random.uuid}.json")
@AutoConfigureMockMvc
class ApiDocsOffByDefaultTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiDocsAndSwaggerUiAnswerNotFound() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
