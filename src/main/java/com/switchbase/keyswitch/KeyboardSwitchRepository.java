package com.switchbase.keyswitch;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KeyboardSwitchRepository extends JpaRepository<KeyboardSwitch, Long> {

    boolean existsBySpecManufacturerAndSpecModelNameAndSpecVariant(String manufacturer, String modelName, String variant);

    boolean existsBySpecManufacturerAndSpecModelNameAndSpecVariantIsNull(String manufacturer, String modelName);

    @org.springframework.data.jpa.repository.Query("select s.id from KeyboardSwitch s")
    java.util.List<Long> findAllIds();

    /** 수정·되돌리기 직렬화용. 같은 스위치의 수정과 되돌리기는 이 행 잠금을 기다린 뒤 최신 리비전을 본다 (사전 리뷰 R1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from KeyboardSwitch s where s.id = :id")
    Optional<KeyboardSwitch> findForUpdate(@Param("id") Long id);
}
