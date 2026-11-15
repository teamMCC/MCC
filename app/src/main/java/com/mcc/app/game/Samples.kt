package com.mcc.app.game

import com.mcc.app.region.Region
import com.mcc.app.trait.GeneCode
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.Trait
import com.mcc.app.trait.TraitCatalog

/**
 * 샘플 운전자 — 심사·시연용.
 *
 * 다른 운전자의 유전 코드가 없어도 '타인과의 유전'을 바로 시연할 수 있게,
 * 지역 악세사리를 가진 가상의 운전자 캐릭터를 준비해 둡니다.
 */
object Samples {

    data class Partner(val owner: String, val genome: Genome) {
        val code: String get() = GeneCode.encode(genome)
    }

    private fun t(slot: Slot, id: String): Trait =
        TraitCatalog.find(slot, id) ?: error("샘플 형질이 카탈로그에 없습니다: ${slot.label}/$id")

    private fun genome(
        species: Species,
        hair: String, eye: String, mouth: String, outfit: String,
        accessories: Set<Region>,
        generation: Int,
    ) = Genome(
        species = species,
        traits = mapOf(
            Slot.HAIR to t(Slot.HAIR, hair),
            Slot.EYE to t(Slot.EYE, eye),
            Slot.MOUTH to t(Slot.MOUTH, mouth),
            Slot.OUTFIT to t(Slot.OUTFIT, outfit),
        ),
        accessories = accessories,
        generation = generation,
    )

    val PARTNERS: List<Partner> = listOf(
        Partner(
            "부산 택시 기사",
            genome(Species.SHIBA, "wind", "sharp", "yawn", "marathon",
                setOf(Region.BUSAN, Region.GYEONGSANG), 3),
        ),
        Partner(
            "제주 렌터카 여행자",
            genome(Species.BEAR, "wet", "happy", "smile", "rain",
                setOf(Region.JEJU), 2),
        ),
        Partner(
            "강원 차박 캠퍼",
            genome(Species.CAT, "wind", "sleepy", "hum", "night",
                setOf(Region.GANGWON, Region.SEOUL), 4),
        ),
    )
}
