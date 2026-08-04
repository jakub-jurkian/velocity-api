package com.velocity.api.bike.repository;

import com.velocity.api.bike.BikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BikeModelRepository extends JpaRepository<BikeModel, UUID> {
}
