package com.switchbase.keyswitch;

import com.switchbase.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional  // 테스트가 끝나면 넣은 데이터를 되돌린다
class SwitchServiceTest {

    @Autowired
    SwitchService switchService;

    @Autowired
    EntityManager em;

    @Test
    void createWritesFirstRevision() {
        KeyboardSwitch sw = switchService.create(spec("Yellow", SwitchType.LINEAR, "50"), "처음 등록", "tester", null);

        assertThat(switchService.revisionsOf(sw.getId()))
                .extracting(SwitchRevision::getRevisionNo)
                .containsExactly(1);
    }

    @Test
    void updateKeepsOldSpecInHistory() {
        KeyboardSwitch sw = switchService.create(spec("Yellow", SwitchType.LINEAR, "50"), "처음 등록", "tester", null);
        em.flush();

        switchService.update(sw.getId(), 0L, spec("Yellow", SwitchType.LINEAR, "55"), "입력압 수정", "tester",
                "https://example.com/spec");
        em.flush();

        var revisions = switchService.revisionsOf(sw.getId());
        assertThat(revisions).extracting(SwitchRevision::getRevisionNo).containsExactly(2, 1);
        assertThat(revisions.get(1).getSpec().actuationForceG()).isEqualByComparingTo("50");
        assertThat(switchService.get(sw.getId()).getSpec().actuationForceG()).isEqualByComparingTo("55");
        assertThat(switchService.sourcesOf(sw.getId())).hasSize(1);
    }

    @Test
    void staleVersionIsRejected() {
        KeyboardSwitch sw = switchService.create(spec("Yellow", SwitchType.LINEAR, "50"), "처음 등록", "tester", null);
        em.flush();
        switchService.update(sw.getId(), 0L, spec("Yellow", SwitchType.LINEAR, "55"), "먼저 고침", "a", null);
        em.flush();  // 여기서 version이 1이 된다

        assertThatThrownBy(() ->
                switchService.update(sw.getId(), 0L, spec("Yellow", SwitchType.LINEAR, "60"), "늦게 고침", "b", null))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void searchFiltersByTypeAndForce() {
        switchService.create(spec("Light Linear", SwitchType.LINEAR, "35"), "등록", "tester", null);
        switchService.create(spec("Heavy Linear", SwitchType.LINEAR, "67"), "등록", "tester", null);
        switchService.create(spec("Heavy Tactile", SwitchType.TACTILE, "67"), "등록", "tester", null);

        Page<KeyboardSwitch> result = switchService.search(
                new SwitchSearch("test", SwitchType.LINEAR, new BigDecimal("60"), null), PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(sw -> sw.getSpec().modelName())
                .containsExactly("Heavy Linear");
    }

    private SwitchSpec spec(String model, SwitchType type, String force) {
        return new SwitchSpec("Test Co", model, type, new BigDecimal(force),
                null, null, null, null, null);
    }
}