package com.example.web.security;

import com.example.web.controller.TimeslotReadController;
import com.example.web.repository.BlockTimeslotRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/timeslots (unlike /api/admin/timeslots) is readable by any
 * authenticated role, since it backs dropdowns like the Assignments form
 * which WRITERs (not just ADMINs) use.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(TimeslotReadController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class TimeslotReadSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockTimeslotRepository timeslotRepository;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/timeslots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_canRead() throws Exception {
        when(timeslotRepository.findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc()).thenReturn(List.of());
        mockMvc.perform(get("/api/timeslots"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canRead() throws Exception {
        when(timeslotRepository.findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc()).thenReturn(List.of());
        mockMvc.perform(get("/api/timeslots"))
                .andExpect(status().isOk());
    }
}
