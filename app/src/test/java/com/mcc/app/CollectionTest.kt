package com.mcc.app

import com.mcc.app.game.Collection
import com.mcc.app.game.CollectionRules
import com.mcc.app.game.Creature
import com.mcc.app.game.Egg
import com.mcc.app.game.GameRules
import com.mcc.app.game.GameState
import com.mcc.app.region.Region
import com.mcc.app.trait.Genetics
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.TraitCatalog
import com.mcc.app.trait.TraitEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 도감: 부화 때 횟수 +1 · 1/3/6회 단계 · 입히기 · 놓아줘도 기록 유지 · 확률 불변 */
class CollectionTest {

    private fun genome(
        species: Species = Species.CAT,
        index: Int = 0,
        accessories: Set<Region> = emptySet(),
    ) = Genome(species, Slot.values().associateWith { TraitCatalog.of(it)[index] }, accessories, 1)

    private val wave = TraitCatalog.HAIR[0].asset

    @Test
    fun entries_cover_species_traits_and_regions() {
        assertEquals(3 + 20 + 8, CollectionRules.ENTRIES.size)
        assertEquals(CollectionRules.ENTRIES.size, CollectionRules.ENTRIES.map { it.asset }.toSet().size)
    }

    @Test
    fun birth_counts_every_part_once() {
        val c = Collection()
        CollectionRules.recordBirth(c, genome(accessories = setOf(Region.BUSAN)))
        assertEquals(1, CollectionRules.count(c, "set_cat"))
        Slot.values().forEach { assertEquals(1, CollectionRules.count(c, TraitCatalog.of(it)[0].asset)) }
        assertEquals(1, CollectionRules.count(c, Region.BUSAN.asset))
        assertEquals(0, CollectionRules.count(c, Region.JEJU.asset))
        assertEquals(1 + 4 + 1, CollectionRules.registered(c))
    }

    @Test
    fun tiers_open_at_1_3_and_6() {
        val c = Collection()
        assertEquals(-1, CollectionRules.tier(c, wave))
        val expected = listOf(0, 0, 1, 1, 1, 2, 2)
        expected.forEach { want ->
            CollectionRules.recordBirth(c, genome())
            assertEquals(want, CollectionRules.tier(c, wave))
        }
        assertNull("마지막 단계 다음은 없음", CollectionRules.nextGoal(c, wave))
    }

    @Test
    fun equip_only_unlocked_tiers() {
        val c = Collection()
        val cr = Creature("a", genome())
        repeat(2) { CollectionRules.recordBirth(c, cr.genome) }
        assertFalse("2회로는 ★ 불가", CollectionRules.equip(c, cr, Slot.HAIR, 1))
        CollectionRules.recordBirth(c, cr.genome)
        assertTrue(CollectionRules.equip(c, cr, Slot.HAIR, 1))
        assertEquals(1, CollectionRules.equippedTier(c, cr, Slot.HAIR))
        assertFalse("★★ 아직", CollectionRules.equip(c, cr, Slot.HAIR, 2))
        assertTrue("기본으로 되돌리기", CollectionRules.equip(c, cr, Slot.HAIR, 0))
        assertEquals(0, CollectionRules.equippedTier(c, cr, Slot.HAIR))
        assertTrue("기본만 남으면 기록 정리", c.equipped.isEmpty())
    }

    @Test
    fun hatch_and_release_through_game_rules() {
        val s = GameState()
        s.egg = Egg(parents = emptyList(), generation = 1, km = TraitEngine.HATCH_DISTANCE_KM)
        var n = 0
        GameRules.tryHatch(s, Random(1), 0L) { "id${++n}" }
        val baby = s.creatures.single()
        assertEquals(1, CollectionRules.count(s.collection, baby.genome.species.asset))
        val hairAsset = baby.genome.traits.getValue(Slot.HAIR).asset
        assertEquals(1, CollectionRules.count(s.collection, hairAsset))

        s.collection.counts[hairAsset] = 3
        assertTrue(CollectionRules.equip(s.collection, baby, Slot.HAIR, 1))
        GameRules.release(s, baby.id)
        assertEquals("놓아줘도 횟수 유지", 3, CollectionRules.count(s.collection, hairAsset))
        assertTrue("입힘 기록은 지움", s.collection.equipped.isEmpty())
    }

    @Test
    fun newly_unlocked_reports_tier_changes() {
        val c = Collection()
        repeat(2) { CollectionRules.recordBirth(c, genome()) }
        val before = CollectionRules.tiers(c)
        CollectionRules.recordBirth(c, genome())
        val got = CollectionRules.newlyUnlocked(before, CollectionRules.tiers(c))
        assertEquals("형질 4개가 ★ 로", 4, got.size)
        assertTrue(got.all { it.second == 1 })
    }

    @Test
    fun seed_registers_existing_creatures_once() {
        val c = Collection()
        CollectionRules.seedFrom(c, listOf(Creature("a", genome()), Creature("b", genome(Species.BEAR, 1))))
        assertEquals(1, CollectionRules.count(c, "set_bear"))
        CollectionRules.seedFrom(c, listOf(Creature("a", genome())))
        assertEquals("이미 기록이 있으면 다시 세지 않음", 1, CollectionRules.count(c, "set_cat"))
    }

    @Test
    fun hatch_odds_do_not_depend_on_collection() {
        // 도감은 꾸밈 보상일 뿐 확률을 바꾸지 않습니다: 같은 시드면 도감이 달라도 같은 아기.
        val a = GameState().apply { egg = Egg(emptyList(), 1, TraitEngine.HATCH_DISTANCE_KM) }
        val b = GameState().apply {
            egg = Egg(emptyList(), 1, TraitEngine.HATCH_DISTANCE_KM)
            collection.counts[TraitCatalog.HAIR[2].asset] = 99
        }
        GameRules.tryHatch(a, Random(7), 0L) { "x" }
        GameRules.tryHatch(b, Random(7), 0L) { "x" }
        assertEquals(a.creatures.single().genome, b.creatures.single().genome)
        assertEquals(0.2f, Genetics.traitOdds(Slot.HAIR, TraitEngine.Profile(), emptyList())[2].chance, 1e-6f)
    }

    @Test
    fun slot_complete_needs_all_five() {
        val c = Collection()
        (0 until 4).forEach { CollectionRules.recordBirth(c, genome(index = it)) }
        assertFalse(CollectionRules.slotComplete(c, Slot.EYE, 0))
        CollectionRules.recordBirth(c, genome(index = 4))
        assertTrue(CollectionRules.slotComplete(c, Slot.EYE, 0))
        assertFalse(CollectionRules.anySlotComplete(c, 1))
    }
}
