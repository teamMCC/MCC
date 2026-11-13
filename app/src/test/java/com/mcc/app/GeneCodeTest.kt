package com.mcc.app

import com.mcc.app.game.Samples
import com.mcc.app.region.Region
import com.mcc.app.trait.GeneCode
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.TraitCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** 유전 코드: 왕복 변환 · 오타 감지 · 잘못된 입력 처리 */
class GeneCodeTest {

    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private fun randomGenome(rng: Random): Genome = Genome(
        species = Species.values()[rng.nextInt(Species.values().size)],
        traits = Slot.values().associateWith { slot ->
            val pool = TraitCatalog.of(slot)
            pool[rng.nextInt(pool.size)]
        },
        accessories = Region.values().filter { rng.nextBoolean() }.toSet(),
        generation = rng.nextInt(1, 100),
    )

    private fun decodeOk(code: String): Genome = when (val r = GeneCode.decode(code)) {
        is GeneCode.Result.Ok -> r.genome
        is GeneCode.Result.Error -> {
            fail("디코딩 실패: $code — ${r.message}")
            throw IllegalStateException()
        }
    }

    @Test
    fun encode_then_decode_returns_the_same_genome() {
        val rng = Random(7)
        repeat(3000) {
            val g = randomGenome(rng)
            val code = GeneCode.encode(g)
            assertEquals("코드 길이 (대시 포함)", 11, code.length)
            assertEquals("다섯 번째 뒤 대시", '-', code[5])
            assertEquals("왕복: $code", g, decodeOk(code))
        }
    }

    @Test
    fun all_species_trait_and_region_combinations_round_trip() {
        // 경계값: 모든 악세사리 · 악세사리 없음 · 1세대 · 99세대
        listOf(emptySet(), Region.values().toSet()).forEach { acc ->
            listOf(1, 99).forEach { gen ->
                Species.values().forEach { sp ->
                    for (i in 1..5) {
                        val g = Genome(sp, Slot.values().associateWith { TraitCatalog.byIndex(it, i)!! }, acc, gen)
                        assertEquals(g, decodeOk(GeneCode.encode(g)))
                    }
                }
            }
        }
    }

    @Test
    fun typing_variations_are_accepted() {
        val g = randomGenome(Random(3))
        val code = GeneCode.encode(g)
        listOf(
            code.lowercase(),
            code.replace("-", ""),
            code.replace("-", " - "),
            " $code ",
            code.replace('0', 'O'),     // 숫자 0 대신 영문 O
            code.replace('1', 'I'),     // 숫자 1 대신 영문 I
        ).forEach { variant ->
            assertEquals("입력 변형 허용: '$variant'", g, decodeOk(variant))
        }
    }

    @Test
    fun any_single_typo_is_detected() {
        val rng = Random(11)
        var checked = 0
        repeat(300) {
            val g = randomGenome(rng)
            val clean = GeneCode.encode(g).replace("-", "")
            for (pos in clean.indices) {
                for (c in alphabet) {
                    if (c == clean[pos]) continue
                    val typo = clean.substring(0, pos) + c + clean.substring(pos + 1)
                    val r = GeneCode.decode(typo)
                    checked++
                    if (r is GeneCode.Result.Ok) {
                        fail("오타가 다른 캐릭터로 읽힘: $clean → $typo")
                    }
                }
            }
        }
        assertTrue("검사 수", checked > 90000)
    }

    @Test
    fun swapped_neighbours_are_detected() {
        val rng = Random(12)
        var missed = 0
        var total = 0
        repeat(2000) {
            val g = randomGenome(rng)
            val clean = GeneCode.encode(g).replace("-", "")
            for (pos in 0 until clean.length - 1) {
                if (clean[pos] == clean[pos + 1]) continue
                val swapped = clean.substring(0, pos) + clean[pos + 1] + clean[pos] + clean.substring(pos + 2)
                total++
                if (GeneCode.decode(swapped) is GeneCode.Result.Ok) missed++
            }
        }
        // 본문 안의 자리바꿈은 전부 잡히고, 마지막 검증 글자와의 자리바꿈만 드물게 통과할 수 있음
        assertTrue("이웃 자리바꿈 놓친 비율 ${missed}/$total", missed.toDouble() / total < 0.01)
    }

    @Test
    fun bad_codes_explain_what_is_wrong() {
        val cases = listOf(
            "C3152-A40" to "10자리",
            "" to "10자리",
        )
        cases.forEach { (input, hint) ->
            val r = GeneCode.decode(input)
            assertTrue("'$input' 는 오류여야 함", r is GeneCode.Result.Error)
            assertTrue("오류 메시지에 '$hint'", (r as GeneCode.Result.Error).message.contains(hint))
        }
        // 검증 글자만 틀린 코드
        val good = GeneCode.encode(randomGenome(Random(5)))
        val last = good.last()
        val wrongCheck = good.dropLast(1) + alphabet.first { it != last }
        assertTrue(GeneCode.decode(wrongCheck) is GeneCode.Result.Error)
    }

    @Test
    fun sample_partner_codes_are_valid() {
        Samples.PARTNERS.forEach { p ->
            assertEquals("샘플 '${p.owner}' 코드 왕복", p.genome, decodeOk(p.code))
        }
    }
}
