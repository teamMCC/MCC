package com.mcc.app

import com.mcc.app.game.Creature
import com.mcc.app.game.Egg
import com.mcc.app.game.GameRules
import com.mcc.app.game.GameState
import com.mcc.app.game.Texts
import com.mcc.app.region.Region
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.TraitCatalog
import com.mcc.app.trait.TraitEngine
import com.mcc.app.vehicle.DemoDrive
import com.mcc.app.vehicle.DrivingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 슬롯 5개 · 시작 알 · 30km 부화 · 지역 20km 악세사리 · 유전 조건 · 놓아주기 */
class GameRulesTest {

    private var idSeq = 0
    private val newId: () -> String = { "id${++idSeq}" }
    private val rng = Random(42)

    /** 72km/h 로 정확히 km 만큼 달린 주행 */
    private fun drive(km: Float, region: Region?, speeding: Boolean = false): DrivingSession {
        val s = DrivingSession()
        var t = 0L
        s.start(t)
        while (s.distanceKm < km) {
            t += 500
            s.sample(t, 72f, 0f, false, false, speeding, region)
        }
        s.stop(t)
        return s
    }

    private fun genome(species: Species = Species.CAT, accessories: Set<Region> = emptySet(), gen: Int = 1) =
        Genome(species, Slot.values().associateWith { TraitCatalog.of(it).first() }, accessories, gen)

    private fun creature(id: String, g: Genome = genome()) = Creature(id, g)

    // ── 시작 알 · 부화 ───────────────────────────

    @Test
    fun first_launch_gets_a_starter_egg() {
        val s = GameState()
        assertTrue(GameRules.ensureStarterEgg(s))
        assertNotNull(s.egg)
        assertTrue("시작 알은 부모가 없음", s.egg!!.parents.isEmpty())
        assertEquals(1, s.egg!!.generation)
        assertFalse("두 번 주지 않음", GameRules.ensureStarterEgg(s))
    }

    @Test
    fun egg_hatches_after_30km_of_real_driving() {
        val s = GameState()
        GameRules.ensureStarterEgg(s)

        val e1 = GameRules.applyDrive(s, drive(29f, Region.SEOUL), rng, 1000L, newId)
        assertNotNull("29km 에서는 아직 알", s.egg)
        assertTrue(e1.none { it.contains("부화") })

        val e2 = GameRules.applyDrive(s, drive(1.5f, Region.SEOUL), rng, 2000L, newId)
        assertNotNull("30km 를 넘어도 저절로 깨지지 않고 기다림", s.egg)
        assertTrue(s.egg!!.readyToHatch)
        assertTrue("눌러서 깨우라는 안내", e2.contains(GameRules.READY_MESSAGE))
        assertTrue(s.creatures.isEmpty())

        val hatched = GameRules.tryHatch(s, rng, 3000L, newId)   // 알을 눌러 깨움
        assertNull("깨우면 부화", s.egg)
        assertEquals(1, s.creatures.size)
        assertEquals("아기가 대표가 됨", s.creatures[0].id, s.active?.id)
        assertTrue(hatched!!.contains("부화"))
        assertFalse("캐릭터가 있으면 시작 알을 또 주지 않음", GameRules.ensureStarterEgg(s))
    }

    @Test
    fun incubation_driving_changes_the_baby() {
        // 30km 를 전부 서울에서 품으면 → 서울 악세사리를 반드시 가지고 태어남
        repeat(30) {
            val s = GameState()
            GameRules.ensureStarterEgg(s)
            GameRules.applyDrive(s, drive(31f, Region.SEOUL), Random(it), 0L, newId)
            GameRules.tryHatch(s, Random(it), 0L, newId)
            val baby = s.creatures.single()
            assertTrue("서울에서 품은 알", Region.SEOUL in baby.genome.accessories)
            assertTrue("아기는 품는 동안의 지역 거리를 기억", (baby.regionKm[Region.SEOUL] ?: 0f) >= 30f)
        }
    }

    // ── 지역 악세사리 ─────────────────────────────

    @Test
    fun active_character_unlocks_region_accessory_at_20km_once() {
        val s = GameState(mutableListOf(creature("a")), activeId = "a")
        val e1 = GameRules.applyDrive(s, drive(19f, Region.BUSAN), rng, 0L, newId)
        assertFalse(Region.BUSAN in s.find("a")!!.genome.accessories)
        assertTrue(e1.isEmpty())

        val e2 = GameRules.applyDrive(s, drive(1.5f, Region.BUSAN), rng, 0L, newId)
        assertTrue("20km 넘으면 획득", Region.BUSAN in s.find("a")!!.genome.accessories)
        assertEquals(1, e2.count { it.contains("부산") })

        val e3 = GameRules.applyDrive(s, drive(5f, Region.BUSAN), rng, 0L, newId)
        assertTrue("이미 가진 악세사리는 다시 알리지 않음", e3.isEmpty())
    }

    @Test
    fun only_the_active_character_collects_region_distance() {
        val s = GameState(mutableListOf(creature("a"), creature("b")), activeId = "b")
        GameRules.applyDrive(s, drive(25f, Region.JEJU), rng, 0L, newId)
        assertTrue(Region.JEJU in s.find("b")!!.genome.accessories)
        assertFalse(Region.JEJU in s.find("a")!!.genome.accessories)
        assertTrue("대표가 아닌 캐릭터는 지역 거리 0", (s.find("a")!!.regionKm[Region.JEJU] ?: 0f) == 0f)
    }

    @Test
    fun unknown_region_counts_distance_but_no_accessory() {
        val s = GameState(mutableListOf(creature("a")), activeId = "a")
        GameRules.applyDrive(s, drive(40f, null), rng, 0L, newId)
        assertTrue(s.find("a")!!.genome.accessories.isEmpty())
        assertTrue(s.find("a")!!.totalKm >= 40f)
    }

    // ── 유전 ─────────────────────────────────────

    @Test
    fun breeding_rules() {
        val s = GameState(mutableListOf(creature("a"), creature("b")), activeId = "a")
        assertNotNull("부모 1명", GameRules.breedBlockReason(s, 1))
        assertNotNull("부모 3명", GameRules.breedBlockReason(s, 3))
        assertNull("2명이면 가능", GameRules.breedBlockReason(s, 2))

        val egg = GameRules.breed(s, genome(gen = 3), genome(gen = 1))
        assertEquals("세대 = 높은 쪽 + 1", 4, egg.generation)
        assertEquals(2, egg.parents.size)
        assertNotNull("알이 있으면 또 못 만듦", GameRules.breedBlockReason(s, 2))
    }

    @Test
    fun cannot_breed_when_all_slots_are_full() {
        assertEquals(7, GameRules.MAX_SLOTS)
        val s = GameState((0 until GameRules.MAX_SLOTS).map { creature("c$it") }.toMutableList(), activeId = "c0")
        val reason = GameRules.breedBlockReason(s, 2)
        assertNotNull(reason)
        assertTrue(reason!!.contains("가득"))
    }

    @Test
    fun bred_egg_carries_parent_accessories_about_10_percent_each() {
        var count = 0
        val n = 4000
        val r = Random(77)
        repeat(n) {
            val s = GameState(mutableListOf(creature("a")), activeId = "a")
            GameRules.breed(s, genome(accessories = setOf(Region.BUSAN)), genome())
            GameRules.applyDrive(s, drive(30.5f, null), r, 0L, newId)
            GameRules.tryHatch(s, r, 0L, newId)
            val baby = s.creatures.last()
            if (Region.BUSAN in baby.genome.accessories) count++
        }
        assertEquals("부모 한쪽 부산 → 약 10%", 0.10, count.toDouble() / n, 0.02)
    }

    // ── 기본 알 ───────────────────────────────────

    @Test
    fun basic_egg_can_be_taken_after_first_hatch_until_slots_are_full() {
        val s = GameState(mutableListOf(creature("a")), activeId = "a")
        assertNull("첫 캐릭터 부화 후 기본 알 받기 가능", GameRules.takeBasicEgg(s))
        assertTrue("기본 알은 부모 없음", s.egg!!.parents.isEmpty())
        assertNotNull("알이 있으면 또 못 받음", GameRules.takeBasicEgg(s))

        val full = GameState((0 until GameRules.MAX_SLOTS).map { creature("c$it") }.toMutableList(), activeId = "c0")
        assertNotNull("7마리가 다 차면 못 받음", GameRules.takeBasicEgg(full))
        assertNull(full.egg)
    }

    @Test
    fun two_basic_eggs_give_two_own_parents_for_breeding() {
        val s = GameState()
        GameRules.ensureStarterEgg(s)
        GameRules.applyDrive(s, drive(31f, null), rng, 0L, newId)
        GameRules.tryHatch(s, rng, 0L, newId)                           // 첫 부화
        GameRules.takeBasicEgg(s)
        GameRules.applyDrive(s, drive(31f, null), rng, 0L, newId)
        GameRules.tryHatch(s, rng, 0L, newId)                           // 기본 알 부화
        assertEquals("내 캐릭터 둘", 2, s.creatures.size)
        assertNull("둘로 유전 가능", GameRules.breedBlockReason(s, 2))
    }

    @Test
    fun existing_characters_do_not_change_while_an_egg_incubates() {
        val s = GameState(mutableListOf(creature("a")), activeId = "a")
        val before = s.find("a")!!.genome
        GameRules.takeBasicEgg(s)
        GameRules.applyDrive(s, drive(100f, Region.GANGWON), rng, 0L, newId)   // 부화하는 긴 주행
        GameRules.tryHatch(s, rng, 0L, newId)
        assertEquals("부화와 함께 기존 캐릭터 외형이 바뀌면 안 됨", before, s.find("a")!!.genome)
        assertEquals(2, s.creatures.size)
        assertTrue("강원 거리는 아기가 가져감", Region.GANGWON in s.creatures.last().genome.accessories)
    }

    // ── 놓아주기 · 자리 ───────────────────────────

    @Test
    fun releasing_active_moves_active_and_last_release_gives_starter_egg() {
        val s = GameState(mutableListOf(creature("a"), creature("b")), activeId = "a")
        GameRules.release(s, "a")
        assertEquals("b", s.active?.id)
        assertNull(s.egg)
        GameRules.release(s, "b")
        assertTrue(s.creatures.isEmpty())
        assertNotNull("모두 놓아주면 시작 알", s.egg)
    }

    @Test
    fun ready_egg_waits_for_a_free_slot() {
        val s = GameState((0 until GameRules.MAX_SLOTS).map { creature("c$it") }.toMutableList(), activeId = "c0")
        s.egg = Egg(parents = listOf(genome(), genome()), generation = 2, km = 31f)
        val waiting = GameRules.tryHatch(s, rng, 0L, newId)
        assertNotNull(waiting)
        assertTrue(waiting!!.contains("자리"))
        assertEquals(GameRules.MAX_SLOTS, s.creatures.size)

        GameRules.release(s, "c${GameRules.MAX_SLOTS - 1}")
        val hatched = GameRules.tryHatch(s, rng, 0L, newId)
        assertTrue(hatched!!.contains("부화"))
        assertEquals(GameRules.MAX_SLOTS, s.creatures.size)
        assertNull(s.egg)
    }

    // ── 데모 주행이 시연 흐름을 실제로 만족하는지 ─────

    @Test
    fun demo_styles_drive_in_their_region_and_long_drive_hatches_an_egg() {
        DemoDrive.STYLES.forEach { style ->
            val d = DemoDrive.generate(style, Random(1))
            val inRegion = d.regionKm[style.region] ?: 0f
            assertTrue("${style.label}: 지역 거리 > 0", inRegion > 0f)
            assertEquals("${style.label}: 지역 거리 = 전체 거리", d.distanceKm.toDouble(), inRegion.toDouble(), 0.01)
        }
        val long = DemoDrive.STYLES.first { it.label == "장거리 고속" }
        val s = GameState()
        GameRules.ensureStarterEgg(s)
        GameRules.applyDrive(s, DemoDrive.generate(long, Random(2)), rng, 0L, newId)
        GameRules.tryHatch(s, rng, 0L, newId)
        assertEquals("장거리 데모 1번으로 부화", 1, s.creatures.size)
        assertTrue("강원 악세사리를 가지고 태어남", Region.GANGWON in s.creatures[0].genome.accessories)
    }

    // ── 화면 문장 ─────────────────────────────────

    @Test
    fun odds_text_lists_every_slot() {
        val text = Texts.oddsBlock(TraitEngine.Profile(), emptyList(), emptyMap())
        listOf("종족", "머리", "눈", "입", "의상", "악세사리", "20%").forEach {
            assertTrue("확률표에 '$it'", text.contains(it))
        }
    }
}
