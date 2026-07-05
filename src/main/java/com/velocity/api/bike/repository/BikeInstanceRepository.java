package com.velocity.api.bike.repository;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BikeInstanceRepository extends JpaRepository<BikeInstance, UUID> {
    public Page<BikeInstance> findByStatus(BikeStatus status, Pageable pageable);
}
