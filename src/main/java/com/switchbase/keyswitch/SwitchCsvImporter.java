package com.switchbase.keyswitch;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CSV 한 줄 = 스위치 하나.
 * manufacturer,brand,model_name,variant,switch_type,actuation_force_g,bottom_out_force_g,pre_travel_mm,total_travel_mm,release_year,aliases,source_url
 * aliases는 | 로 구분한다. 예: 저월백|저소음 월백
 * 이미 있는 스위치(제조사+이름+변형)는 건너뛴다. 한 줄이 틀려도 나머지는 계속 넣는다.
 */
@Component
public class SwitchCsvImporter {

    private static final int COLUMNS = 12;

    private final SwitchService switchService;
    private final Validator validator;

    public SwitchCsvImporter(SwitchService switchService, Validator validator) {
        this.switchService = switchService;
        this.validator = validator;
    }

    public record Result(int created, int skipped, List<String> errors) {
    }

    public Result importCsv(InputStream in, Long editorMemberId, String editorNickname) throws IOException {
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 || line.isBlank()) {
                    continue; // 첫 줄은 제목 줄
                }
                try {
                    SwitchForm form = parse(line);
                    Set<ConstraintViolation<SwitchForm>> violations = validator.validate(form);
                    if (!violations.isEmpty()) {
                        ConstraintViolation<SwitchForm> v = violations.iterator().next();
                        errors.add(lineNo + "번째 줄: " + v.getPropertyPath() + " " + v.getMessage());
                        continue;
                    }
                    if (switchService.exists(form.getManufacturer(), form.getModelName(), form.getVariant())) {
                        skipped++;
                        continue;
                    }
                    switchService.create(form.toSpec(), form.aliasList(), form.getEditSummary(),
                            editorMemberId, editorNickname, form.getSourceUrl());
                    created++;
                } catch (NumberFormatException e) {
                    errors.add(lineNo + "번째 줄: 숫자 칸에 숫자가 아닌 값이 있어요");
                } catch (IllegalArgumentException e) {
                    errors.add(lineNo + "번째 줄: " + e.getMessage());
                }
            }
        }
        return new Result(created, skipped, errors);
    }

    private SwitchForm parse(String line) {
        String[] c = line.split(",", -1);
        if (c.length != COLUMNS) {
            throw new IllegalArgumentException("칸이 " + COLUMNS + "개여야 하는데 " + c.length + "개예요");
        }
        SwitchForm f = new SwitchForm();
        f.setManufacturer(c[0].strip());
        f.setBrand(c[1].strip());
        f.setModelName(c[2].strip());
        f.setVariant(c[3].strip());
        f.setSwitchType(type(c[4]));
        f.setActuationForceG(decimal(c[5]));
        f.setBottomOutForceG(decimal(c[6]));
        f.setPreTravelMm(decimal(c[7]));
        f.setTotalTravelMm(decimal(c[8]));
        f.setReleaseYear(c[9].isBlank() ? null : Short.valueOf(c[9].strip()));
        f.setAliases(c[10]);
        f.setSourceUrl(c[11].strip());
        f.setEditSummary("CSV 일괄 등록");
        return f;
    }

    private SwitchType type(String value) {
        try {
            return SwitchType.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("switch_type은 LINEAR, TACTILE, CLICKY 중 하나여야 해요");
        }
    }

    private BigDecimal decimal(String value) {
        return value.isBlank() ? null : new BigDecimal(value.strip());
    }
}
