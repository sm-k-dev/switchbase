package com.switchbase.collections;

import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.keyswitch.KeyboardSwitchRepository;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/** 내 컬렉션 (review-collection-report §4.2, Q7, Q8, Q18). */
@Service
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionItemRepository items;
    private final KeyboardSwitchRepository switches;
    private final MemberRepository members;

    public CollectionService(CollectionItemRepository items, KeyboardSwitchRepository switches,
                             MemberRepository members) {
        this.items = items;
        this.switches = switches;
        this.members = members;
    }

    /** 누르면 켜고, 다시 누르면 끈다(하드 삭제). 돌려주는 값은 켜졌는지. */
    @Transactional
    public boolean toggle(Long memberId, Long switchId, CollectionStatus status) {
        if (!switches.existsById(switchId)) {
            throw new NoSuchElementException("스위치가 없습니다: " + switchId);
        }
        // 삭제는 행이 없어도 정상 처리, 추가는 ON CONFLICT로 중복 클릭을 흡수한다(리뷰 R3)
        if (items.existsByMemberIdAndSwitchIdAndStatus(memberId, switchId, status)) {
            items.deleteOne(memberId, switchId, status);
            return false;
        }
        items.insertIgnore(memberId, switchId, status.name());
        return true;
    }

    public CollectionState state(Long memberId, Long switchId) {
        return new CollectionState(
                items.existsByMemberIdAndSwitchIdAndStatus(memberId, switchId, CollectionStatus.FAVORITE),
                items.existsByMemberIdAndSwitchIdAndStatus(memberId, switchId, CollectionStatus.OWNED),
                items.existsByMemberIdAndSwitchIdAndStatus(memberId, switchId, CollectionStatus.TRIED));
    }

    /** 본인 컬렉션. status가 null이면 전체. */
    public Page<CollectionItemView> mine(Long memberId, CollectionStatus status, Pageable pageable) {
        return view(status == null
                ? items.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                : items.findByMemberIdAndStatusOrderByCreatedAtDesc(memberId, status, pageable));
    }

    /** 타인의 컬렉션: 공개 설정이 켜진 활동 중인 회원만. 아니면 404. */
    public Page<CollectionItemView> publicOf(Long targetMemberId, Long viewerId, CollectionStatus status,
                                             Pageable pageable) {
        Member target = members.findById(targetMemberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + targetMemberId));
        boolean self = Objects.equals(viewerId, targetMemberId);
        if (!self && (target.isWithdrawn() || !target.isCollectionPublic())) {
            throw new NoSuchElementException("컬렉션이 공개되어 있지 않아요");
        }
        return mine(targetMemberId, status, pageable);
    }

    public boolean isPublic(Long memberId) {
        return members.findById(memberId).map(Member::isCollectionPublic).orElse(false);
    }

    @Transactional
    public void setPublic(Long memberId, boolean collectionPublic) {
        Member m = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + memberId));
        m.changeCollectionPublic(collectionPublic);
    }

    private Page<CollectionItemView> view(Page<CollectionItem> page) {
        Map<Long, String> names = switches.findAllById(
                page.getContent().stream().map(CollectionItem::getSwitchId).distinct().toList()).stream()
                .collect(Collectors.toMap(KeyboardSwitch::getId, s -> s.getSpec().modelName()));
        return page.map(i -> new CollectionItemView(
                i.getSwitchId(), names.getOrDefault(i.getSwitchId(), "(삭제된 스위치)"),
                i.getStatus().getLabel(), i.getCreatedAt()));
    }
}
