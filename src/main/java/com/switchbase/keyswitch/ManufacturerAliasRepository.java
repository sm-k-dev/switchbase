package com.switchbase.keyswitch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManufacturerAliasRepository extends JpaRepository<ManufacturerAlias, Long> {

    Optional<ManufacturerAlias> findByNormalized(String normalized);
}