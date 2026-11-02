package com.switchbase.keyswitch;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

import static com.switchbase.keyswitch.QManufacturerAlias.manufacturerAlias;
import static com.switchbase.keyswitch.QSwitchAlias.switchAlias;
import static com.switchbase.keyswitch.QKeyboardSwitch.keyboardSwitch;


@Repository
public class SwitchQueryRepository {

    private final JPAQueryFactory query;

    public SwitchQueryRepository(JPAQueryFactory query) {
        this.query = query;
    }

    public Page<KeyboardSwitch> search(SwitchSearch search, Pageable pageable) {
        BooleanExpression[] conditions = {
                keywordContains(search.keyword()),
                typeEq(search.type()),
                forceGoe(search.minForce()),
                forceLoe(search.maxForce())
        };

        List<KeyboardSwitch> content = query
                .selectFrom(keyboardSwitch)
                .where(conditions)
                .orderBy(keyboardSwitch.spec.manufacturer.asc(), keyboardSwitch.spec.modelName.asc(),
                        keyboardSwitch.spec.variant.asc().nullsFirst())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = query
                .select(keyboardSwitch.count())
                .from(keyboardSwitch)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    // 제조사, 브랜드, 이름, 별칭, 제조사의 다른 표기 중 하나라도 맞으면 찾는다.
    // 이름과 별칭은 띄어쓰기와 대소문자를 무시하고 비교한다.
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String compact = Names.normalize(keyword);
        StringExpression compactModelName = Expressions.stringTemplate(
                "replace(lower({0}), ' ', '')", keyboardSwitch.spec.modelName);

        return keyboardSwitch.spec.manufacturer.containsIgnoreCase(keyword.strip())
                .or(keyboardSwitch.spec.brand.containsIgnoreCase(keyword.strip()))
                .or(compactModelName.contains(compact))
                .or(JPAExpressions.selectOne()
                        .from(switchAlias)
                        .where(switchAlias.keyboardSwitch.eq(keyboardSwitch),
                                switchAlias.normalized.contains(compact))
                        .exists())
                .or(keyboardSwitch.spec.manufacturer.in(
                        JPAExpressions.select(manufacturerAlias.manufacturer)
                                .from(manufacturerAlias)
                                .where(manufacturerAlias.normalized.contains(compact))));
    }

    private BooleanExpression typeEq(SwitchType type) {
        return type == null ? null : keyboardSwitch.spec.switchType.eq(type);
    }

    private BooleanExpression forceGoe(BigDecimal min) {
        return min == null ? null : keyboardSwitch.spec.actuationForceG.goe(min);
    }

    private BooleanExpression forceLoe(BigDecimal max) {
        return max == null ? null : keyboardSwitch.spec.actuationForceG.loe(max);
    }
}
