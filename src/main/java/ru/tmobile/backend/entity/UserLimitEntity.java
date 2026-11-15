package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.tmobile.backend.entity.enums.ResourceType;

@Entity
@Table(name = "user_limits")
@Getter @Setter
@IdClass(UserLimitEntity.UserLimitId.class)
public class UserLimitEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "resource_type", nullable = false, columnDefinition = "E_RESOURCE_TYPE")
    private ResourceType resourceType;

    @Column(name = "limit_value", nullable = false)
    private Double limitValue;

    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked = false;

    @Getter @Setter @EqualsAndHashCode 
    public static class UserLimitId implements java.io.Serializable {
        private Long userId;
        private Long groupId;
        private ResourceType resourceType;
    }
}