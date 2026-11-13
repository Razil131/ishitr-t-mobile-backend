package ru.tmobile.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.tmobile.backend.entity.ServicePackageEntity;
import ru.tmobile.backend.repository.ServicePackageRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tariffs")
@RequiredArgsConstructor
public class TariffController {

    private final ServicePackageRepository packageRepository;

    @GetMapping
    public List<Map<String, Object>> getTariffs() {
        return packageRepository.findAll().stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("package_id", p.getId());
                    map.put("name", p.getName());
                    map.put("traffic_gb", p.getTrafficGb());
                    map.put("minutes", p.getMinutes());
                    map.put("sms", p.getSms());
                    map.put("price", p.getPrice());
                    map.put("description", p.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTariff(@RequestBody Map<String, Object> body) {
        ServicePackageEntity entity = new ServicePackageEntity();
        entity.setName((String) body.get("name"));
        entity.setTrafficGb(((Number) body.get("traffic_gb")).doubleValue());
        entity.setMinutes(((Number) body.get("minutes")).longValue());
        entity.setSms(body.containsKey("sms") ? ((Number) body.get("sms")).longValue() : 0L);
        entity.setPrice(new BigDecimal(body.get("price").toString()));
        entity.setDescription((String) body.getOrDefault("description", null));

        ServicePackageEntity saved = packageRepository.save(entity);

        Map<String, Object> result = new HashMap<>();
        result.put("package_id", saved.getId());
        result.put("name", saved.getName());
        result.put("traffic_gb", saved.getTrafficGb());
        result.put("minutes", saved.getMinutes());
        result.put("sms", saved.getSms());
        result.put("price", saved.getPrice());
        result.put("description", saved.getDescription());
        return result;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTariff(@PathVariable Long id) {
        try {
            packageRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Невозможно удалить тариф: он используется в активных подписках групп"
            );
        }
    }
}
