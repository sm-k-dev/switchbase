package com.switchbase.ratings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModTagRepository extends JpaRepository<ModTag, Long> {

    Optional<ModTag> findByNormalized(String normalized);

    List<ModTag> findTop10ByNormalizedStartingWithOrderByNormalizedAsc(String prefix);
}
