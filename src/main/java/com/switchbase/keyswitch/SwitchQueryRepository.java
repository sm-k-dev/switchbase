package com.switchbase.keyswitch;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

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
                .orderBy(keyboardSwitch.spec.manufacturer.asc(), keyboardSwitch.spec.modelName.asc())
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

    // 조건이 비어 있으면 null을 돌려준다. QueryDSL은 where 안의 null을 무시한다.
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyboardSwitch.spec.manufacturer.containsIgnoreCase(keyword)
                .or(keyboardSwitch.spec.modelName.containsIgnoreCase(keyword));
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
