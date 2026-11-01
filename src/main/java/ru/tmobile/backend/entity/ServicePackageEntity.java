package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "service_packages")
@Getter @Setter
public class ServicePackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_traffic", nullable = false)
    private Double totalTraffic;

    @Column(name = "total_minutes", nullable = false)
    private Long totalMinutes;
}
