package ru.tmobile.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.tmobile.backend.entity.ServicePackageEntity;
import ru.tmobile.backend.repository.ServicePackageRepository;

class TariffControllerTest {
    MockMvc mockMvc;
    ServicePackageRepository repo;
    ObjectMapper json;

    @BeforeEach void up() {
        repo = mock(ServicePackageRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TariffController(repo)).build();
        json = new ObjectMapper();
    }

    private ServicePackageEntity pkg(long id, String name, double traffic, long minutes) {
        ServicePackageEntity p = new ServicePackageEntity();
        p.setId(id); p.setName(name); p.setTotalTraffic(traffic); p.setTotalMinutes(minutes);
        return p;
    }

    @Test void getTariffsReturnsMappedList() throws Exception {
        when(repo.findAll()).thenReturn(List.of(pkg(1L, "Basic", 10.0, 500L), pkg(2L, "Pro", 30.5, 1500L)));
        mockMvc.perform(get("/api/v1/tariffs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].package_id").value(1))
            .andExpect(jsonPath("$[0].name").value("Basic"))
            .andExpect(jsonPath("$[0].total_traffic").value(10.0))
            .andExpect(jsonPath("$[0].total_minutes").value(500))
            .andExpect(jsonPath("$[1].package_id").value(2));
    }

    @Test void getTariffsReturnsEmptyArrayWhenNone() throws Exception {
        when(repo.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/tariffs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test void createTariffReturns201WithSavedPayload() throws Exception {
        ServicePackageEntity saved = pkg(9L, "New", 20.0, 1000L);
        when(repo.save(any(ServicePackageEntity.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/tariffs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"total_traffic\":20.0,\"total_minutes\":1000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.package_id").value(9))
            .andExpect(jsonPath("$.name").value("New"))
            .andExpect(jsonPath("$.total_traffic").value(20.0))
            .andExpect(jsonPath("$.total_minutes").value(1000));
    }

    @Test void deleteTariffReturns204WhenSuccessful() throws Exception {
        mockMvc.perform(delete("/api/v1/tariffs/42"))
            .andExpect(status().isNoContent());
        verify(repo).deleteById(42L);
    }

    @Test void deleteTariffReturns409OnDataIntegrityViolation() throws Exception {
        doThrow(new DataIntegrityViolationException("fk"))
            .when(repo).deleteById(eq(42L));

        mockMvc.perform(delete("/api/v1/tariffs/42"))
            .andExpect(status().isConflict());
    }
}
