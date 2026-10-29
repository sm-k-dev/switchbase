package com.switchbase.keyswitch;

import com.switchbase.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminAccessTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anyoneCanSeeSwitchList() throws Exception {
        mvc.perform(get("/switches")).andExpect(status().isOk());
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mvc.perform(get("/admin/switches/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenForm() throws Exception {
        mvc.perform(get("/admin/switches/new")).andExpect(status().isOk());
    }
}