package ru.tmobile.backend.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.tmobile.backend.entity.enums.ResourceType;

@Entity
@Table(name = "user_usage")
@Getter @Setter
@IdClass(UserUsageEntity.UserUsageId.class)
public class UserUsageEntity {
    
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

    @Column(name = "used_value", nullable = false)
    private Double usedValue;

    @Id
    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Getter @Setter @EqualsAndHashCode 
    public static class UserUsageId implements java.io.Serializable {
        private Long userId;
        private Long groupId;
        private ResourceType resourceType;
        private Long periodId;
    }

}
