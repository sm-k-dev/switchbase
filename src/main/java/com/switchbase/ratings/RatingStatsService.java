package com.switchbase.ratings;

import com.switchbase.keyswitch.KeyboardSwitchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 집계 캐시 (stats-design §5-1). 평가가 바뀔 때마다 해당 스위치를 rating에서 다시 계산한다(증분 계산 안 함).
 * recompute는 호출한 트랜잭션 안에서 실행되므로 평가 저장과 함께 성공하거나 실패한다(Q1).
 */
@Service
public class RatingStatsService {

    private final RatingRepository ratings;
    private final SwitchRatingStatsRepository stats;
    private final KeyboardSwitchRepository switches;

    public RatingStatsService(RatingRepository ratings, SwitchRatingStatsRepository stats,
                              KeyboardSwitchRepository switches) {
        this.ratings = ratings;
        this.stats = stats;
        this.switches = switches;
    }

    @Transactional
    public void recompute(Long switchId) {
        stats.ensureRow(switchId);
        SwitchRatingStats row = stats.findForUpdate(switchId)
                .orElseThrow(() -> new IllegalStateException("집계 행이 없습니다: " + switchId));
        row.replace(ratings.summaryOf(switchId));
    }

    /** 관리용 전체 재계산. 모든 스위치를 같은 트랜잭션에서 다시 계산한다(멱등). 처리한 스위치 수를 돌려준다. */
    @Transactional
    public int recomputeAll() {
        List<Long> ids = switches.findAllIds();
        for (Long id : ids) {
            recompute(id);
        }
        return ids.size();
    }

    /** 상세 요약은 집계 행에서 읽는다. 행이 없으면 평가 없음이다(Q6). */
    @Transactional(readOnly = true)
    public RatingSummary current(Long switchId) {
        return stats.findById(switchId)
                .map(SwitchRatingStats::toSummary)
                .orElseGet(() -> new RatingSummary(null, null, null, 0L, 0L));
    }
}
