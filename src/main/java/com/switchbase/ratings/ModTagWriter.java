package com.switchbase.ratings;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 한 건을 별도 트랜잭션(REQUIRES_NEW)으로 넣는다.
 * 같은 키가 동시에 만들어지면 여기서 유니크 위반이 나고, 이 트랜잭션만 롤백된다.
 * 호출한 평가 트랜잭션은 살아 있으므로 바깥에서 다시 조회할 수 있다(RatingService.resolveTags).
 */
@Component
class ModTagWriter {

    private final ModTagRepository tags;

    ModTagWriter(ModTagRepository tags) {
        this.tags = tags;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ModTag insert(String name, String normalized) {
        return tags.saveAndFlush(new ModTag(name, normalized));
    }
}
