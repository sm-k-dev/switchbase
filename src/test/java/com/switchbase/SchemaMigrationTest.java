package com.switchbase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway가 keyboard_switch 테이블을 만든다")
    void createsKeyboardSwitchTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM keyboard_switch", Integer.class);

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("스위치 타입은 LINEAR, TACTILE, CLICKY만 허용한다")
    void rejectsUnknownSwitchType() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO keyboard_switch (manufacturer, model_name, switch_type) VALUES (?, ?, ?)",
                "Gateron", "Test", "SILENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}