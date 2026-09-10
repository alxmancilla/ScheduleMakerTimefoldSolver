package com.example.web.controller;

import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.repository.BlockTimeslotRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link TimeslotReadController}: read-only listing
 * available to any authenticated role, used to populate dropdowns elsewhere
 * (e.g. the Assignments form) without requiring ADMIN.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(TimeslotReadController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TimeslotReadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockTimeslotRepository timeslotRepository;

    private BlockTimeslotEntity timeslot;

    @Before
    public void setUp() {
        timeslot = new BlockTimeslotEntity(1, 7, 2);
        timeslot.setId("block_abc123");
    }

    @Test
    public void getAllTimeslots_returnsList() throws Exception {
        when(timeslotRepository.findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc())
                .thenReturn(List.of(timeslot));
        mockMvc.perform(get("/api/timeslots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("block_abc123"));
    }

    @Test
    public void getTimeslotById_notFound_returns404() throws Exception {
        when(timeslotRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/timeslots/nope"))
                .andExpect(status().isNotFound());
    }
}
