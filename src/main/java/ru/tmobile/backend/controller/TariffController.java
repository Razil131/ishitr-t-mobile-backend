package ru.tmobile.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.tmobile.backend.entity.ServicePackageEntity;
import ru.tmobile.backend.repository.ServicePackageRepository;

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
                .map(p -> Map.<String, Object>of(
                        "package_id", p.getId(),
                        "name", p.getName(),
                        "total_traffic", p.getTotalTraffic(),
                        "total_minutes", p.getTotalMinutes()
                ))
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTariff(@RequestBody Map<String, Object> body) {
        ServicePackageEntity entity = new ServicePackageEntity();
        entity.setName((String) body.get("name"));
        entity.setTotalTraffic(((Number) body.get("total_traffic")).doubleValue());
        entity.setTotalMinutes(((Number) body.get("total_minutes")).longValue());

        ServicePackageEntity saved = packageRepository.save(entity);

        return Map.of(
                "package_id", saved.getId(),
                "name", saved.getName(),
                "total_traffic", saved.getTotalTraffic(),
                "total_minutes", saved.getTotalMinutes()
        );
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
