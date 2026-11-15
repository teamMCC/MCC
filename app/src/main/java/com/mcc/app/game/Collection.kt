package com.mcc.app.game

import com.mcc.app.region.Region
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.TraitCatalog

/**
 * 도감.
 *
 * 알이 부화할 때마다 아기가 가진 파츠(종족·형질 4개·지역 아이템)를 1회씩 셉니다.
 * 같은 형질을 여러 번 얻으면 그 형질의 다음 단계 그림이 열립니다.
 *
 *   1회  등록      — 기본 그림
 *   3회  ★         — 색이 바뀐 그림     (그림 이름_s1, 없으면 앱이 색을 바꿔 그림)
 *   6회  ★★        — 반짝이는 특별 그림 (그림 이름_s2, 없으면 앱이 금빛 + 반짝임)
 *
 * 놓아줘도 기록은 줄지 않습니다. 단계는 보관함에서 그 형질을 가진 캐릭터에게 골라 입힙니다.
 * 형질·부화 확률·유전 코드는 바뀌지 않습니다 (단계는 내 기기 안의 꾸밈 보상).
 *
 * @param counts   그림 이름 → 얻은 횟수
 * @param equipped 캐릭터 id → 슬롯 → 입힌 단계 (0 기본, 1 ★, 2 ★★)
 */
class Collection(
    val counts: MutableMap<String, Int> = mutableMapOf(),
    val equipped: MutableMap<String, MutableMap<Slot, Int>> = mutableMapOf(),
)

object CollectionRules {

    /** 단계별로 필요한 횟수. index = 단계 (0 등록, 1 ★, 2 ★★) */
    val TIER_COUNTS = listOf(1, 3, 6)
    const val MAX_TIER = 2

    /** 도감에 나오는 칸 하나. */
    data class Entry(val asset: String, val label: String, val group: String, val tiered: Boolean)

    /** 도감 전체 목록. 종족 3 · 모자 5 · 눈 5 · 입 5 · 의상 5 · 지역 8 = 31칸 */
    val ENTRIES: List<Entry> = buildList {
        Species.values().forEach { add(Entry(it.asset, it.label, "종족", tiered = false)) }
        Slot.values().forEach { slot ->
            TraitCatalog.of(slot).forEach { add(Entry(it.asset, it.label, slot.label, tiered = true)) }
        }
        Region.values().forEach { add(Entry(it.asset, it.accessory, "지역", tiered = false)) }
    }

    fun count(c: Collection, asset: String): Int = c.counts[asset] ?: 0

    /** 열린 단계. 아직 한 번도 못 얻었으면 -1. */
    fun tier(c: Collection, asset: String): Int {
        val n = count(c, asset)
        return TIER_COUNTS.indexOfLast { n >= it }
    }

    /** 다음 단계까지 필요한 총 횟수. 마지막 단계면 null. */
    fun nextGoal(c: Collection, asset: String): Int? {
        val t = tier(c, asset)
        return TIER_COUNTS.getOrNull(t + 1)
    }

    /** 아기 한 마리가 태어났을 때. 종족·형질·지역 아이템을 1회씩 셉니다. */
    fun recordBirth(c: Collection, genome: Genome) {
        add(c, genome.species.asset)
        genome.traits.values.forEach { add(c, it.asset) }
        genome.accessories.forEach { add(c, it.asset) }
    }

    /** 이미 태어난 캐릭터가 주행으로 지역 아이템을 새로 얻었을 때. */
    fun recordAccessory(c: Collection, region: Region) = add(c, region.asset)

    private fun add(c: Collection, asset: String) {
        c.counts[asset] = count(c, asset) + 1
    }

    /** 이 캐릭터의 이 슬롯에 입힌 단계. 열린 단계보다 높으면 낮춰서 돌려줍니다. */
    fun equippedTier(c: Collection, creature: Creature, slot: Slot): Int {
        val asset = creature.genome.traits[slot]?.asset ?: return 0
        val want = c.equipped[creature.id]?.get(slot) ?: 0
        return want.coerceIn(0, tier(c, asset).coerceAtLeast(0))
    }

    /**
     * 단계를 입힙니다. 열리지 않은 단계면 false.
     * 기어 P 에서만 부르도록 화면에서 막습니다.
     */
    fun equip(c: Collection, creature: Creature, slot: Slot, wantTier: Int): Boolean {
        val asset = creature.genome.traits[slot]?.asset ?: return false
        if (wantTier !in 0..MAX_TIER || wantTier > tier(c, asset)) return false
        val m = c.equipped.getOrPut(creature.id) { mutableMapOf() }
        if (wantTier == 0) m.remove(slot) else m[slot] = wantTier
        if (m.isEmpty()) c.equipped.remove(creature.id)
        return true
    }

    /** 놓아준 캐릭터의 입힘 기록만 지웁니다. 도감 횟수는 그대로입니다. */
    fun forget(c: Collection, creatureId: String) {
        c.equipped.remove(creatureId)
    }

    /** 이 슬롯 형질 5종이 모두 해당 단계 이상인지 (방 꾸미기 보상 조건). */
    fun slotComplete(c: Collection, slot: Slot, atTier: Int): Boolean =
        TraitCatalog.of(slot).all { tier(c, it.asset) >= atTier }

    /** 형질 슬롯 중 하나라도 해당 단계로 완성됐는지. */
    fun anySlotComplete(c: Collection, atTier: Int): Boolean =
        Slot.values().any { slotComplete(c, it, atTier) }

    /** 도감 전체 완성: 형질 칸은 모두 해당 단계 이상, 종족·지역 칸은 모두 등록 (은색 트로피 조건). */
    fun allComplete(c: Collection, atTier: Int): Boolean =
        ENTRIES.all { tier(c, it.asset) >= (if (it.tiered) atTier else 0) }

    /** 도감에 등록된 칸 수 / 전체 칸 수 */
    fun registered(c: Collection): Int = ENTRIES.count { count(c, it.asset) > 0 }

    /** 이번 부화로 새로 열린 단계를 화면에 알리기 위한 비교용 스냅샷. */
    fun tiers(c: Collection): Map<String, Int> = ENTRIES.associate {
        // 종족·지역은 단계 없이 등록만 합니다.
        it.asset to if (it.tiered) tier(c, it.asset) else tier(c, it.asset).coerceAtMost(0)
    }

    /** 두 스냅샷 사이에 단계가 오른 칸들. */
    fun newlyUnlocked(before: Map<String, Int>, after: Map<String, Int>): List<Pair<Entry, Int>> =
        ENTRIES.mapNotNull { e ->
            val a = after[e.asset] ?: -1
            val b = before[e.asset] ?: -1
            if (a > b && a >= 0) e to a else null
        }

    /** 처음 업데이트할 때: 이미 있는 캐릭터들을 한 번씩 등록합니다. */
    fun seedFrom(c: Collection, creatures: List<Creature>) {
        if (c.counts.isNotEmpty()) return
        creatures.forEach { recordBirth(c, it.genome) }
    }
}
