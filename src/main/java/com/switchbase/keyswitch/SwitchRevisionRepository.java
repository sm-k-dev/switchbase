package com.switchbase.keyswitch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SwitchRevisionRepository extends JpaRepository<SwitchRevision, Long> {

    List<SwitchRevision> findByKeyboardSwitchIdOrderByRevisionNoDesc(Long switchId);

    Optional<SwitchRevision> findByKeyboardSwitchIdAndRevisionNo(Long switchId, int revisionNo);

    int countByKeyboardSwitchId(Long switchId);

    /** 최근 변경 목록. 스위치를 함께 가져와 목록에서 N+1이 생기지 않게 한다. */
    @Query(value = "select r from SwitchRevision r join fetch r.keyboardSwitch order by r.createdAt desc, r.id desc",
            countQuery = "select count(r) from SwitchRevision r")
    Page<SwitchRevision> findAllWithSwitch(Pageable pageable);
}
