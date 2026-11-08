package com.switchbase.keyswitch;

import com.switchbase.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
        KeyboardSwitch sw = switchService.create(spec("Yellow", SwitchType.LINEAR, "50"), List.of(), "처음 등록", null, "tester", null);

        assertThat(switchService.revisionsOf(sw.getId()))
                .extracting(SwitchRevision::getRevisionNo)
                .containsExactly(1);
    }

    @Test
    void updateKeepsOldSpecInHistory() {
        KeyboardSwitch sw = switchService.create(spec("Yellow", SwitchType.LINEAR, "50"), List.of(), "처음 등록", null, "tester", null);
        em.flush();

        switchService.update(sw.getId(), 0L, spec("Yellow", SwitchType.LINEAR, "55"), List.of(), "입력압 수정", null, "tester",
                "https://example.com/spec");
        em.flush();

        var revisions = switchService.revisionsOf(sw.getId());
        assertThat(revisions).extracting(SwitchRevision::getRevisionNo).containsExactly(2, 1);
        assertThat(revisions.get(1).getSpec().actuationForceG()).isEqualByComparingTo("50");
        assertThat(switchService.get(sw.getId()).getSpec().actuationForceG()).isEqualByComparingTo("55");
        assertThat(switchService.sourcesOf(sw.getId()))
                .extracting(SpecSource::getSourceType)
                .containsExactly(SourceType.COMMUNITY);
    }

    @Test
    void staleVersionIsRejected() {
        KeyboardSwitch sw = switchService.create(spec("Yellow", SwitchType.LINEAR, "50"), List.of(), "처음 등록", null, "tester", null);
        em.flush();
        switchService.update(sw.getId(), 0L, spec("Yellow", SwitchType.LINEAR, "55"), List.of(), "먼저 고침", null, "a", null);
        em.flush();  // 여기서 version이 1이 된다

        assertThatThrownBy(() ->
                switchService.update(sw.getId(), 0L, spec("Yellow", SwitchType.LINEAR, "60"), List.of(), "늦게 고침", null, "b", null))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void searchFiltersByTypeAndForce() {
        switchService.create(spec("Light Linear", SwitchType.LINEAR, "35"), List.of(), "등록", null, "tester", null);
        switchService.create(spec("Heavy Linear", SwitchType.LINEAR, "67"), List.of(), "등록", null, "tester", null);
        switchService.create(spec("Heavy Tactile", SwitchType.TACTILE, "67"), List.of(), "등록", null, "tester", null);

        Page<KeyboardSwitch> result = switchService.search(
                new SwitchSearch("test", SwitchType.LINEAR, new BigDecimal("60"), null), PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(sw -> sw.getSpec().modelName())
                .containsExactly("Heavy Linear");
    }

    @Test
    void searchFindsSwitchByAliasIgnoringSpaces() {
        switchService.create(spec("Silent Test White", SwitchType.LINEAR, "45"),
                List.of("저월백", "저소음 월백", "저 월백"), "등록", null, "tester", null);
        em.flush();

        assertThat(searchNames("저 월백")).containsExactly("Silent Test White");
        assertThat(searchNames("저소음월백")).containsExactly("Silent Test White");
        assertThat(searchNames("silenttest")).containsExactly("Silent Test White");
        // "저월백"과 "저 월백"은 같은 이름이라 하나만 남는다
        assertThat(switchService.aliasesOf(searchId("저월백"))).hasSize(2);
    }

    @Test
    void koreanManufacturerNameIsSavedAsOfficialName() {
        KeyboardSwitch sw = switchService.create(
                new SwitchSpec("게이트론", null, "Test Yellow", null, SwitchType.LINEAR, null, null, null, null, null, null),
                List.of(), "등록", null, "tester", null);
        em.flush();

        assertThat(sw.getSpec().manufacturer()).isEqualTo("Gateron");
        assertThat(switchService.exists("게이트론", "Test Yellow", null)).isTrue();
        assertThat(searchNames("게이트론")).contains("Test Yellow");
    }

    @Test
    void sameNameWithDifferentVariantsCanCoexist() {
        switchService.create(variantSpec("Y3", "45g", "45"), List.of(), "등록", null, "tester", null);
        switchService.create(variantSpec("Y3", "55g", "55"), List.of(), "등록", null, "tester", null);
        em.flush();

        assertThat(switchService.exists("Test Co", "Y3", "45g")).isTrue();
        assertThat(switchService.exists("Test Co", "Y3", "62g")).isFalse();
        assertThat(switchService.exists("Test Co", "Y3", null)).isFalse();
        assertThat(searchNames("y3")).hasSize(2);

        // 제조사 + 이름 + 변형이 모두 같으면 DB가 막는다
        assertThatThrownBy(() ->
                switchService.create(variantSpec("Y3", "45g", "45"), List.of(), "등록", null, "tester", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SwitchSpec variantSpec(String model, String variant, String force) {
        return new SwitchSpec("Test Co", null, model, variant, SwitchType.LINEAR, new BigDecimal(force),
                null, null, null, null, null);
    }

    private List<String> searchNames(String keyword) {
        return switchService.search(new SwitchSearch(keyword, null, null, null), PageRequest.of(0, 20))
                .getContent().stream().map(sw -> sw.getSpec().modelName()).toList();
    }

    private Long searchId(String keyword) {
        return switchService.search(new SwitchSearch(keyword, null, null, null), PageRequest.of(0, 20))
                .getContent().getFirst().getId();
    }

    private SwitchSpec spec(String model, SwitchType type, String force) {
        return new SwitchSpec("Test Co", null, model, null, type, new BigDecimal(force),
                null, null, null, null, null);
    }
}
