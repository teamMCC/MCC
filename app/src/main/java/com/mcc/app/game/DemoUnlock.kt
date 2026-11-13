package com.mcc.app.game

import com.mcc.app.trait.Slot

/**
 * 시연용 '모두 해금' (순수 로직, 테스트 대상).
 *
 * 발표·영상에서 도감·악세서리·방을 한 번에 보여 주기 위한 기능입니다. 시연 빌드(SHOW_DEMO_TOOLS)에서만 버튼이 보이고,
 * 누르기 전 진행 상황은 LocalStore 가 따로 백업해 두었다가 '되돌리기'로 그대로 돌려놓습니다.
 *
 *   · 도감 31종(파츠·종·지역 악세서리) 모두 ★★ 횟수로
 *   · 누적 주행 1000km 이상 → 지구본 소품
 *   · 위 둘로 지역 방 8개·골드 방·모든 소품이 열림
 *   · 보관함의 모든 캐릭터에 ★★ 파츠를 입힘 (화면에서 바로 보이도록)
 * 캐릭터·알·주행 기록 자체는 바꾸지 않습니다.
 */
object DemoUnlock {
    /** 이 정도면 누적 거리 해금(지구본 1000km)이 모두 열림 */
    const val SHOWCASE_KM = 1000f

    fun unlockAll(state: GameState) {
        val max = CollectionRules.TIER_COUNTS.last()
        CollectionRules.ENTRIES.forEach { e ->
            if (CollectionRules.count(state.collection, e.asset) < max) state.collection.counts[e.asset] = max
        }
        if (state.lifetime.totalKm < SHOWCASE_KM) state.lifetime.totalKm = SHOWCASE_KM
        state.creatures.forEach { creature ->
            Slot.values().forEach { slot ->
                CollectionRules.equip(state.collection, creature, slot, CollectionRules.MAX_TIER)
            }
        }
    }
}
