package com.mcc.app

import com.mcc.app.region.Region
import com.mcc.app.trait.Genetics
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.TraitCatalog
import com.mcc.app.trait.TraitEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 부화 확률 규칙 검증.
 *   종족 1/3 · 형질 기본 20% · 운전 성향 가산 · 부모 형질 가산 · 악세사리 부모마다 +10%
 */
class GeneticsTest {

    private val empty = TraitEngine.Profile()

    private fun profile(block: TraitEngine.Profile.() -> Unit) = TraitEngine.Profile().apply(block)

    private fun genome(
        species: Species = Species.CAT,
        hair: String = "wave", eye: String = "calm", mouth: String = "hum", outfit: String = "commute",
        accessories: Set<Region> = emptySet(),
        generation: Int = 1,
    ) = Genome(
        species,
        mapOf(
            Slot.HAIR to TraitCatalog.find(Slot.HAIR, hair)!!,
            Slot.EYE to TraitCatalog.find(Slot.EYE, eye)!!,
            Slot.MOUTH to TraitCatalog.find(Slot.MOUTH, mouth)!!,
            Slot.OUTFIT to TraitCatalog.find(Slot.OUTFIT, outfit)!!,
        ),
        accessories,
        generation,
    )

    // ── 종족 ─────────────────────────────────────

    @Test
    fun species_is_one_third_each() {
        val odds = Genetics.speciesOdds(emptyList())
        assertEquals("종족 수", 3, odds.size)
        odds.forEach { (s, p) -> assertEquals("${s.label} 확률", 1.0 / 3, p.toDouble(), 1e-6) }
    }

    @Test
    fun species_stays_one_third_even_with_parents() {
        val parents = listOf(genome(Species.BEAR), genome(Species.BEAR))
        Genetics.speciesOdds(parents).forEach { (s, p) ->
            assertEquals("부모가 곰·곰이어도 ${s.label} 1/3", 1.0 / 3, p.toDouble(), 1e-6)
        }
    }

    // ── 형질 기본 확률 ───────────────────────────

    @Test
    fun traits_start_equal_at_20_percent() {
        Slot.values().forEach { slot ->
            Genetics.traitOdds(slot, empty, emptyList()).forEach {
                assertEquals("${slot.label}/${it.trait.label} 기본 확률", 0.2, it.chance.toDouble(), 1e-6)
                assertTrue("기본 상태에서는 가산 표시가 없어야 함", it.boosts.isEmpty())
            }
        }
    }

    @Test
    fun odds_always_sum_to_one() {
        val rng = Random(1)
        repeat(300) {
            val p = profile {
                driveCount = rng.nextInt(0, 20); cleanDrives = rng.nextInt(0, driveCount + 1)
                totalKm = rng.nextFloat() * 200; totalSec = rng.nextLong(0, 20000)
                wiperSec = rng.nextLong(0, totalSec + 1); nightSec = rng.nextLong(0, totalSec + 1)
                idleSec = rng.nextLong(0, totalSec + 1)
                harshAccel = rng.nextInt(0, 50); harshBrake = rng.nextInt(0, 50)
            }
            val parents = if (rng.nextBoolean()) emptyList() else listOf(
                genome(hair = "wet", eye = "shiny"), genome(hair = "spike", mouth = "smile"),
            )
            Slot.values().forEach { slot ->
                val sum = Genetics.traitOdds(slot, p, parents).sumOf { it.chance.toDouble() }
                assertEquals("${slot.label} 확률 합", 1.0, sum, 1e-5)
            }
        }
    }

    // ── 운전 성향 가산 ───────────────────────────

    @Test
    fun rainy_driving_raises_wet_hair_and_rain_outfit() {
        // 알을 품는 동안 와이퍼를 켠 시간이 길면 젖은 머리결·우천형이 오릅니다 (20 → 50/130 ≈ 38%).
        val rainy = profile { driveCount = 2; totalKm = 30f; totalSec = 3600; wiperSec = 3600 }
        val wet = Genetics.traitOdds(Slot.HAIR, rainy, emptyList()).first { it.trait.id == "wet" }
        assertEquals("젖은 머리결 가산", 50.0 / 130, wet.chance.toDouble(), 1e-6)
        assertTrue(Genetics.Boost.STYLE in wet.boosts)
        val rain = Genetics.traitOdds(Slot.OUTFIT, rainy, emptyList()).first { it.trait.id == "rain" }
        assertEquals("우천형 가산", 50.0 / 130, rain.chance.toDouble(), 1e-6)

        // 경계: 와이퍼 19% → 젖은 머리결 가산 없음, 20% → 가산 / 24% → 우천형 없음, 25% → 가산
        assertFalse(Genetics.styleMatched(Slot.HAIR, "wet", profile { driveCount = 1; totalSec = 1000; wiperSec = 190 }))
        assertTrue(Genetics.styleMatched(Slot.HAIR, "wet", profile { driveCount = 1; totalSec = 1000; wiperSec = 200 }))
        assertFalse(Genetics.styleMatched(Slot.OUTFIT, "rain", profile { driveCount = 1; totalSec = 1000; wiperSec = 240 }))
        assertTrue(Genetics.styleMatched(Slot.OUTFIT, "rain", profile { driveCount = 1; totalSec = 1000; wiperSec = 250 }))
        // 맑은 날(와이퍼 0)은 기본 확률
        val dry = profile { driveCount = 2; totalKm = 30f; totalSec = 3600 }
        assertFalse(Genetics.styleMatched(Slot.HAIR, "wet", dry))
    }

    /** 가산 조건이 있는 형질 16종이 모두 실제로 켜질 수 있는지, 기본 형질 4종은 절대 안 켜지는지. */
    @Test
    fun every_styled_trait_is_reachable_and_defaults_never_boost() {
        val profiles = listOf(
            profile { driveCount = 2; totalKm = 30f; totalSec = 3600; wiperSec = 3600 },                // 비
            profile { driveCount = 3; totalKm = 30f; totalSec = 3600; harshAccel = 20; harshBrake = 20 }, // 급함
            profile { driveCount = 1; totalKm = 100f; totalSec = 3960 },                                // 장거리
            profile { driveCount = 10; totalKm = 30f; totalSec = 9000 },                                // 짧은 도심
            profile { driveCount = 4; totalKm = 30f; totalSec = 4000; idleSec = 2000 },                 // 정체
            profile { driveCount = 2; totalKm = 30f; totalSec = 3600; nightSec = 3600 },                // 야간
            profile { driveCount = 4; cleanDrives = 4; totalKm = 30f; totalSec = 3000 },                // 무과속
        )
        val defaults = setOf("wave", "calm", "hum", "commute")
        Slot.values().forEach { slot ->
            TraitCatalog.of(slot).forEach { trait ->
                val reachable = profiles.any { Genetics.styleMatched(slot, trait.id, it) }
                if (trait.id in defaults) {
                    assertFalse("기본 형질 ${trait.label} 은 운전으로 가산되면 안 됨", reachable)
                    assertEquals("기본 형질 설명", "기본", trait.basis)
                } else {
                    assertTrue("${slot.label}/${trait.label} 을 올릴 운전 조건이 있어야 함", reachable)
                }
            }
        }
    }

    @Test
    fun egg_without_driving_has_no_style_boost() {
        // 아직 한 번도 안 달린 알: '평균 0km < 8km' 같은 값으로 조건이 켜지면 안 됨
        Slot.values().forEach { slot ->
            Genetics.traitOdds(slot, empty, emptyList()).forEach {
                assertFalse("${it.trait.label} 이 빈 알에서 가산됨", Genetics.Boost.STYLE in it.boosts)
            }
        }
    }

    // ── 부모 형질 가산 ───────────────────────────

    @Test
    fun parent_traits_are_boosted() {
        val both = Genetics.traitOdds(Slot.HAIR, empty, listOf(genome(hair = "wet"), genome(hair = "wet")))
        assertEquals("양쪽 부모 젖은 머리", 60.0 / 140, both.first { it.trait.id == "wet" }.chance.toDouble(), 1e-4)

        val split = Genetics.traitOdds(Slot.HAIR, empty, listOf(genome(hair = "wet"), genome(hair = "spike")))
        assertEquals("한쪽 부모 젖은 머리", 40.0 / 140, split.first { it.trait.id == "wet" }.chance.toDouble(), 1e-4)
        assertEquals("한쪽 부모 스파이크", 40.0 / 140, split.first { it.trait.id == "spike" }.chance.toDouble(), 1e-4)
        assertTrue("가산 이유 = 유전", Genetics.Boost.PARENT in split.first { it.trait.id == "wet" }.boosts)
    }

    @Test
    fun style_and_parent_boosts_stack() {
        val nightly = profile { driveCount = 2; totalKm = 30f; totalSec = 3600; nightSec = 3600 }
        val odds = Genetics.traitOdds(Slot.HAIR, nightly, listOf(genome(hair = "wet"), genome(hair = "wave")))
        val outfit = Genetics.traitOdds(Slot.OUTFIT, nightly, listOf(genome(outfit = "night"), genome()))
        // 야간형: 20 + 운전 30 + 부모 20 = 70, 통근형: 20 + 부모 20 = 40, 나머지 20×3 → 합 170
        assertEquals(70.0 / 170, outfit.first { it.trait.id == "night" }.chance.toDouble(), 1e-4)
        // 야간은 머리에 영향 없음: wet = 20+20 = 40, wave = 20+20 = 40, 나머지 20×3 → 합 140
        assertEquals(40.0 / 140, odds.first { it.trait.id == "wet" }.chance.toDouble(), 1e-4)
        assertEquals(40.0 / 140, odds.first { it.trait.id == "wave" }.chance.toDouble(), 1e-4)
    }

    // ── 악세사리 ─────────────────────────────────

    @Test
    fun accessory_odds_are_10_percent_per_parent() {
        val a = genome(accessories = setOf(Region.BUSAN))
        val b = genome(accessories = setOf(Region.BUSAN, Region.JEJU))
        val odds = Genetics.accessoryOdds(listOf(a, b), emptyMap())
        assertEquals("양쪽 다 부산", 0.20, odds[Region.BUSAN]!!.toDouble(), 1e-6)
        assertEquals("한쪽만 제주", 0.10, odds[Region.JEJU]!!.toDouble(), 1e-6)
        assertEquals("부모에게 없는 악세사리는 0% (목록에서 빠짐)", 2, odds.size)
    }

    @Test
    fun driving_20km_in_region_while_incubating_guarantees_accessory() {
        val odds = Genetics.accessoryOdds(emptyList(), mapOf(Region.JEJU to 20f, Region.SEOUL to 19.9f))
        assertEquals("제주 20km → 100%", 1.0, odds[Region.JEJU]!!.toDouble(), 1e-9)
        assertFalse("서울 19.9km → 아직 없음", Region.SEOUL in odds)

        val rng = Random(9)
        repeat(200) {
            val baby = Genetics.hatch(emptyList(), 1, empty, mapOf(Region.JEJU to 25f), rng)
            assertTrue("20km 넘게 품은 지역 악세사리는 반드시 나와야 함", Region.JEJU in baby.accessories)
        }
    }

    // ── 실제 뽑기가 확률대로 나오는지 (3만 번) ──

    @Test
    fun hatch_frequencies_match_the_odds() {
        val rng = Random(2026)
        val n = 30000
        val rainy = profile { driveCount = 2; totalKm = 30f; totalSec = 3600; wiperSec = 3600 }
        val parents = listOf(genome(accessories = setOf(Region.BUSAN)), genome())

        val species = mutableMapOf<Species, Int>()
        var wet = 0
        var busan = 0
        repeat(n) {
            val g = Genetics.hatch(parents, 2, rainy, emptyMap(), rng)
            species[g.species] = (species[g.species] ?: 0) + 1
            if (g.traits[Slot.HAIR]?.id == "wet") wet++
            if (Region.BUSAN in g.accessories) busan++
        }
        Species.values().forEach {
            assertEquals("${it.label} 빈도", 1.0 / 3, (species[it] ?: 0).toDouble() / n, 0.015)
        }
        // 젖은 머리: 비 가산 +30, 부모 둘 다 웨이브 → wet 50 / (100+30+40) ≈ 0.294
        val expectedWet = Genetics.traitOdds(Slot.HAIR, rainy, parents).first { it.trait.id == "wet" }.chance
        assertEquals("젖은 머리 빈도", expectedWet.toDouble(), wet.toDouble() / n, 0.015)
        assertEquals("부산 악세사리 빈도 (부모 한쪽 → 10%)", 0.10, busan.toDouble() / n, 0.01)
    }

    // ── 세대·그림 순서 ───────────────────────────

    @Test
    fun child_generation_is_older_parent_plus_one() {
        assertEquals(4, Genetics.childGeneration(genome(generation = 3), genome(generation = 1)))
        assertEquals("99세대가 상한", 99, Genetics.childGeneration(genome(generation = 99), genome(generation = 99)))
    }

    @Test
    fun layer_order_puts_accessories_on_top() {
        val g = genome(Species.SHIBA, hair = "wet", accessories = setOf(Region.JEJU, Region.SEOUL))
        assertEquals(
            listOf("body_base", "set_shiba", "hair_03_wet", "eye_01_calm", "mouth_04_hum",
                "outfit_03_commute", "acc_seoul", "acc_jeju"),
            g.layerOrder(),
        )
    }
}
