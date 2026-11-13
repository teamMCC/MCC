package com.mcc.app.trait

import com.mcc.app.region.Region
import kotlin.random.Random

/**
 * 유전되는 정보 묶음. 유전 코드(GeneCode)로 주고받는 단위입니다.
 *
 * @param accessories 이 캐릭터가 가진 지역 시그니처 악세사리
 */
data class Genome(
    val species: Species,
    val traits: Map<Slot, Trait>,
    val accessories: Set<Region>,
    val generation: Int,
) {
    /** 아래에서 위로 겹치는 그림 순서. 악세사리는 맨 위에 모두 얹습니다. */
    fun layerOrder(): List<String> = buildList {
        add("body_base")
        add(species.asset)
        Slot.values().forEach { slot -> traits[slot]?.let { add(it.asset) } }
        Region.values().forEach { r -> if (r in accessories) add(r.asset) }
    }
}

/**
 * 부화 확률 계산과 유전.
 *
 * 규칙
 *   · 종족: 고양이·시바견·곰 각 1/3
 *   · 머리·눈·입·의상: 5종 모두 같은 기본값에서 출발
 *       + 알을 품는 동안의 운전 성향이 맞는 형질은 가산
 *       + 부모가 가진 형질은 가산 (부모 한쪽마다)
 *   · 악세사리: 부모가 가진 악세사리는 부모 한쪽마다 +10% 확률로 아기에게 나타남
 *              알을 품는 동안 그 지역에서 20km 이상 달렸으면 반드시 나타남
 *
 * 아래 상수만 바꾸면 전체 확률이 조정됩니다.
 */
object Genetics {

    /** 형질 5종의 기본 가중치. 5 × 20 = 100 이므로 기본은 각 20%. */
    const val BASE_WEIGHT = 20f

    /** 품는 동안의 운전 성향이 조건에 맞는 형질에 더하는 가중치. 20 → 50 (약 38%) */
    const val STYLE_BONUS = 30f

    /** 부모 한쪽이 가진 형질에 더하는 가중치. 양쪽 다 가졌으면 두 번 더합니다. */
    const val PARENT_BONUS = 20f

    /** 부모 종족에 더하는 가중치. 0 이면 부모와 무관하게 정확히 1/3 씩. */
    const val PARENT_SPECIES_BONUS = 0f

    /** 부모가 가진 악세사리가 아기에게 나타날 확률. 부모 한쪽마다 더합니다. */
    const val ACCESSORY_PER_PARENT = 0.10f

    /** 한 지역에서 이만큼 달리면 그 지역 악세사리를 얻습니다. */
    const val REGION_UNLOCK_KM = 20f

    /** 와이퍼를 켠 시간 비율이 이 이상이면 젖은 머리결 가산 (비 오는 날 운전) */
    const val WET_WIPER_RATIO = 0.20f

    /** 와이퍼를 켠 시간 비율이 이 이상이면 우천형 가산 */
    const val RAIN_WIPER_RATIO = 0.25f

    /** 유전 코드가 두 자리라 세대는 99까지. */
    const val MAX_GENERATION = 99

    /** 확률이 오른 이유. 화면에 표시합니다. */
    enum class Boost(val label: String) { STYLE("운전"), PARENT("유전") }

    data class TraitOdds(val trait: Trait, val chance: Float, val boosts: Set<Boost>)

    private class Rule(
        val slot: Slot,
        val traitId: String,
        val test: (TraitEngine.Profile) -> Boolean,
    )

    /**
     * 운전 성향 → 확률 가산 조건. TraitCatalog 의 basis 문장과 숫자가 같습니다.
     * '기본' 형질(웨이브·처진 눈·콧노래·통근형)은 조건 없이 기본 확률만 가집니다.
     * 젖은 머리결·우천형은 알을 품는 동안 와이퍼를 켠 시간 비율로 오릅니다 (Pleos 차량 SDK 와이퍼 신호).
     * driveCount > 0 은 아직 한 번도 안 달린 알에서 '평균 0km' 같은 값으로
     * 조건이 잘못 켜지는 것을 막습니다.
     */
    private val STYLE_RULES = listOf(
        Rule(Slot.HAIR, "spike")    { it.accelPerKm >= 0.30f },
        Rule(Slot.HAIR, "wind")     { it.avgKmPerDrive >= 40f },
        Rule(Slot.HAIR, "flat")     { it.driveCount > 0 && it.avgKmPerDrive < 8f },
        Rule(Slot.HAIR, "wet")      { it.wiperRatio >= WET_WIPER_RATIO },

        Rule(Slot.EYE, "sharp")     { it.harshPerKm >= 0.60f },
        Rule(Slot.EYE, "shiny")     { it.nightRatio >= 0.30f },
        Rule(Slot.EYE, "sleepy")    { it.avgSecPerDrive >= 2700f },
        Rule(Slot.EYE, "happy")     { it.driveCount > 0 && it.cleanRatio >= 0.60f },

        Rule(Slot.MOUTH, "flat")    { it.idleRatio >= 0.40f },
        Rule(Slot.MOUTH, "smile")   { it.driveCount > 0 && it.cleanRatio >= 0.55f },
        Rule(Slot.MOUTH, "yawn")    { it.avgSpeedKph >= 70f },
        Rule(Slot.MOUTH, "tight")   { it.brakePerKm >= 0.30f },

        Rule(Slot.OUTFIT, "marathon") { it.avgKmPerDrive >= 45f },
        Rule(Slot.OUTFIT, "medal")    { it.driveCount > 0 && it.cleanRatio >= 0.75f },
        Rule(Slot.OUTFIT, "night")    { it.nightRatio >= 0.40f },
        Rule(Slot.OUTFIT, "rain")     { it.wiperRatio >= RAIN_WIPER_RATIO },
    )

    /** 이 형질이 운전 성향 때문에 가산되는지. */
    fun styleMatched(slot: Slot, traitId: String, incubation: TraitEngine.Profile): Boolean =
        STYLE_RULES.any { it.slot == slot && it.traitId == traitId && it.test(incubation) }

    /** 한 슬롯의 형질별 등장 확률. 합은 1. 카탈로그 순서 그대로 돌려줍니다. */
    fun traitOdds(
        slot: Slot,
        incubation: TraitEngine.Profile,
        parents: List<Genome>,
    ): List<TraitOdds> {
        val weighted = TraitCatalog.of(slot).map { trait ->
            var weight = BASE_WEIGHT
            val boosts = mutableSetOf<Boost>()
            if (styleMatched(slot, trait.id, incubation)) {
                weight += STYLE_BONUS
                boosts += Boost.STYLE
            }
            parents.forEach { parent ->
                if (parent.traits[slot]?.id == trait.id) {
                    weight += PARENT_BONUS
                    boosts += Boost.PARENT
                }
            }
            Triple(trait, weight, boosts.toSet())
        }
        val total = weighted.sumOf { it.second.toDouble() }.toFloat()
        return weighted.map { (trait, weight, boosts) -> TraitOdds(trait, weight / total, boosts) }
    }

    /** 종족별 등장 확률. 기본 각 1/3. */
    fun speciesOdds(parents: List<Genome>): List<Pair<Species, Float>> {
        val weighted = Species.values().map { species ->
            var weight = 1f
            parents.forEach { if (it.species == species) weight += PARENT_SPECIES_BONUS }
            species to weight
        }
        val total = weighted.sumOf { it.second.toDouble() }.toFloat()
        return weighted.map { it.first to it.second / total }
    }

    /**
     * 악세사리별로 아기에게 나타날 확률. 0% 인 것은 빠집니다.
     * 악세사리마다 따로 굴리므로 여러 개가 함께 나타날 수 있습니다.
     */
    fun accessoryOdds(
        parents: List<Genome>,
        incubationRegionKm: Map<Region, Float>,
    ): Map<Region, Float> {
        val out = linkedMapOf<Region, Float>()
        Region.values().forEach { region ->
            val drivenThere = (incubationRegionKm[region] ?: 0f) >= REGION_UNLOCK_KM
            val chance = if (drivenThere) {
                1f
            } else {
                parents.count { region in it.accessories } * ACCESSORY_PER_PARENT
            }
            if (chance > 0f) out[region] = chance.coerceAtMost(1f)
        }
        return out
    }

    /** 알을 깨서 아기의 유전 정보를 정합니다. */
    fun hatch(
        parents: List<Genome>,
        generation: Int,
        incubation: TraitEngine.Profile,
        incubationRegionKm: Map<Region, Float>,
        rng: Random,
    ): Genome {
        val species = pickWeighted(speciesOdds(parents), rng)
        val traits = Slot.values().associateWith { slot ->
            pickWeighted(traitOdds(slot, incubation, parents).map { it.trait to it.chance }, rng)
        }
        val accessories = accessoryOdds(parents, incubationRegionKm)
            .filter { (_, chance) -> rng.nextFloat() < chance }
            .keys
        return Genome(species, traits, accessories, generation.coerceIn(1, MAX_GENERATION))
    }

    /** 아기의 세대 = 부모 중 높은 세대 + 1 */
    fun childGeneration(a: Genome, b: Genome): Int =
        (maxOf(a.generation, b.generation) + 1).coerceAtMost(MAX_GENERATION)

    /** 가중치 목록에서 하나를 뽑습니다. 가중치 합이 1이 아니어도 됩니다. */
    fun <T> pickWeighted(options: List<Pair<T, Float>>, rng: Random): T {
        require(options.isNotEmpty()) { "선택지가 비어 있습니다" }
        val total = options.sumOf { it.second.toDouble() }
        var x = rng.nextDouble() * total
        for ((value, weight) in options) {
            x -= weight
            if (x < 0.0) return value
        }
        return options.last().first
    }
}
