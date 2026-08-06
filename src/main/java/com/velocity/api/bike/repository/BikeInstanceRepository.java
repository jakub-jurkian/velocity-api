package com.velocity.api.bike.repository;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.projection.AvailableModelProjection;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BikeInstanceRepository extends JpaRepository<BikeInstance, UUID> {
    Page<BikeInstance> findByStatus(BikeStatus status, Pageable pageable);

    @Query(nativeQuery = true, value = """
                SELECT DISTINCT ON (m.id) i.id AS bookableInstanceId, m.name AS modelName, m.description AS modelDescription, m.speed AS modelSpeed, m.range AS modelRange, m.capacity AS modelCapacity, m.category AS modelCategory FROM bike_instances i JOIN bike_models m ON i.bike_model_id = m.id WHERE i.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM reservations r WHERE r.bike_instance_id = i.id
                AND r.status IN ('PENDING', 'CONFIRMED')
                AND daterange(CAST(:startDate AS date), CAST(:endDate AS date), '[)') && daterange(r.start_date, r.end_date, '[)')
                );
            """)
    List<AvailableModelProjection> findAvailableModels(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
