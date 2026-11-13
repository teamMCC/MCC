package com.mcc.app.game

import com.mcc.app.region.Region
import com.mcc.app.trait.Genetics
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.TraitEngine
import kotlin.math.roundToInt

/**
 * 화면에 쓰는 문장 만들기. Android 와 분리해 두어 테스트할 수 있습니다.
 */
object Texts {

    fun pct(x: Float): String = "${(x * 100).roundToInt()}%"

    fun km(x: Float): String = "%.1f".format(x)

    /**
     * 알의 정확한 부화 진행도. 예) "부화까지 57.6% · 17.3 / 30 km · 남은 12.7 km"
     * 30km 를 채웠으면 깨우라는 안내.
     */
    fun eggProgress(done: Float, goalKm: Float = TraitEngine.HATCH_DISTANCE_KM): String {
        if (done >= goalKm) return "부화 준비 완료 · 알을 톡톡 눌러 깨워 주세요"
        // 버림: 다 안 찼는데 100%·30.0 km 로 보이지 않게 (부동소수 오차만큼은 더해서 버림)
        val pct = (done / goalKm * 1000f + 0.001f).toInt() / 10f
        val shown = (done * 10f + 0.001f).toInt() / 10f
        // 남은 거리는 올림: 0.04km 남았는데 '0.0 km 남음'으로 보이지 않게
        val left = kotlin.math.ceil((goalKm - done) * 10f - 0.001f) / 10f
        return "부화까지 ${"%.1f".format(pct)}% · ${km(shown)} / ${goalKm.toInt()} km · 남은 ${km(left)} km"
    }

    /** "2번 시바견" */
    fun name(c: Creature, state: GameState): String {
        val i = state.creatures.indexOf(c)
        val prefix = if (i >= 0) "${i + 1}번 " else ""
        return prefix + c.genome.species.label
    }

    /** "시바견 · 젖은 머리결 · 반짝이는 눈 · 미소 · 우천형" */
    fun traitsLine(g: Genome): String =
        g.species.label + " · " + Slot.values().joinToString(" · ") { g.traits[it]?.label ?: "-" }

    /** "시바견 3세대" */
    fun genomeShort(g: Genome): String = "${g.species.label} ${g.generation}세대"

    fun accessoriesLine(accessories: Set<Region>): String =
        if (accessories.isEmpty()) {
            "악세사리 없음"
        } else {
            "악세사리 " + Region.values()
                .filter { it in accessories }
                .joinToString(", ") { "${it.label} ${it.accessory}" }
        }

    /** "지역 서울 ✓ · 부산 12.3/20km" — 달린 적 있는 지역만 */
    fun regionLine(regionKm: Map<Region, Float>, owned: Set<Region>): String {
        val parts = Region.values().mapNotNull { r ->
            val driven = regionKm[r] ?: 0f
            when {
                r in owned -> "${r.label} ✓"
                driven >= 0.05f -> "${r.label} ${km(driven)}/${Genetics.REGION_UNLOCK_KM.roundToInt()}km"
                else -> null
            }
        }
        return if (parts.isEmpty()) "지역 기록 없음" else "지역 " + parts.joinToString(" · ")
    }

    /** 캐릭터 카드 본문 */
    fun creatureCard(c: Creature, state: GameState): String = buildString {
        val isActive = state.active?.id == c.id
        append(name(c, state))
        append(" · ${c.genome.generation}세대")
        if (isActive) append("   [대표]")
        appendLine()
        appendLine(Slot.values().joinToString(" · ") { c.genome.traits[it]?.label ?: "-" })
        appendLine(accessoriesLine(c.genome.accessories))
        appendLine(regionLine(c.regionKm, c.genome.accessories))
        append("함께 달린 거리 ${km(c.totalKm)}km")
    }

    /**
     * 확률표. 알을 품는 동안 실시간으로 바뀝니다.
     *
     *   종족      고양이 33% · 시바견 33% · 곰 33%
     *   머리      젖은 머리결 38% ▲운전 · 부드러운 웨이브 15% · ...
     *   악세사리  부산 광안리 갈매기 20% ▲유전
     */
    fun oddsBlock(
        incubation: TraitEngine.Profile,
        parents: List<Genome>,
        regionKm: Map<Region, Float>,
    ): String = buildString {
        appendLine(
            "종족   " + Genetics.speciesOdds(parents)
                .joinToString(" · ") { (s, p) -> "${s.label} ${pct(p)}" }
        )
        Slot.values().forEach { slot ->
            val line = Genetics.traitOdds(slot, incubation, parents)
                .sortedByDescending { it.chance }
                .joinToString(" · ") { odds ->
                    val marks = if (odds.boosts.isEmpty()) "" else
                        " ▲" + odds.boosts.joinToString("·") { it.label }
                    "${odds.trait.label} ${pct(odds.chance)}$marks"
                }
            appendLine("${slot.label}   $line")
        }
        val acc = Genetics.accessoryOdds(parents, regionKm)
        if (acc.isEmpty()) {
            append("악세사리   없음 (한 지역 ${Genetics.REGION_UNLOCK_KM.roundToInt()}km)")
        } else {
            append("악세사리   " + acc.entries.joinToString(" · ") { (r, p) ->
                val why = if (p >= 1f) "▲지역" else "▲유전"
                "${r.label} ${r.accessory} ${pct(p)} $why"
            })
        }
    }
}
