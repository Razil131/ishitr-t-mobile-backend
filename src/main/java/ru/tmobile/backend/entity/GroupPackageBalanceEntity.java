package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "group_package_balances")
@Getter @Setter
@IdClass(GroupPackageBalanceEntity.GroupPackageBalanceId.class)
public class GroupPackageBalanceEntity {

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "remaining_minutes", nullable = false)
    private Long remainingMinutes;

    @Column(name = "remaining_traffic", nullable = false)
    private Double remainingTraffic;

    @Getter @Setter @EqualsAndHashCode 
    public static class GroupPackageBalanceId implements java.io.Serializable {
        private Long groupId;
        private Long periodId;
    }
}