package ru.tmobile.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// DEBUG
@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    @GetMapping
    public String check() {
        return "backend is running";
    }
}