package com.mcc.app.trait

import com.mcc.app.region.Region

/**
 * 유전 코드 — 캐릭터의 유전 정보를 짧은 글자로 주고받습니다.
 *
 * 다른 운전자의 캐릭터와 유전하려면 그 사람의 코드를 입력하면 됩니다.
 * 서버 없이도 동작하고, 같은 코드를 앱 내부 저장 형식으로도 씁니다.
 *
 *   C 3152 - A4 07 X
 *   │ │        │  │  └ 검증 글자 (오타 감지)
 *   │ │        │  └── 세대 (01~99)
 *   │ │        └───── 악세사리 (지역별 1비트, 16진수 2자리)
 *   │ └────────────── 머리·눈·입·의상 번호 (각 1~5)
 *   └──────────────── 종족 (C 고양이 · S 시바견 · B 곰)
 *
 * 검증 글자 덕분에 '한 글자 오타'는 전부, '이웃한 두 글자가 뒤바뀐 입력'도
 * 거의 전부 잡아냅니다. 잘못된 코드가 엉뚱한 캐릭터로 읽히지 않습니다.
 */
object GeneCode {

    /** 헷갈리는 I·L·O·U 를 뺀 32글자 (Crockford Base32). */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** 검증용 소수. 31 미만의 차이는 모두 감지됩니다. */
    private const val PRIME = 31

    const val LENGTH = 10   // 대시 제외

    sealed class Result {
        data class Ok(val genome: Genome) : Result()
        data class Error(val message: String) : Result()
    }

    fun encode(g: Genome): String {
        val body = buildString {
            append(g.species.code)
            Slot.values().forEach { slot ->
                val trait = g.traits[slot] ?: error("형질이 비어 있습니다: ${slot.label}")
                append(TraitCatalog.indexOf(trait))
            }
            var mask = 0
            g.accessories.forEach { mask = mask or (1 shl it.ordinal) }
            append("%02X".format(mask))
            append("%02d".format(g.generation.coerceIn(1, Genetics.MAX_GENERATION)))
        }
        return body.substring(0, 5) + "-" + body.substring(5) + checksum(body)
    }

    fun decode(input: String): Result {
        val s = normalize(input)
        if (s.length != LENGTH) {
            return Result.Error("코드는 XXXXX-XXXXX 모양의 10자리예요. 지금 ${s.length}자리.")
        }
        val body = s.substring(0, LENGTH - 1)
        if (checksum(body) != s[LENGTH - 1]) {
            return Result.Error("코드가 맞지 않습니다. 한 글자씩 다시 확인해 주세요.")
        }

        val species = Species.byCode(body[0])
            ?: return Result.Error("첫 글자는 C(고양이) · S(시바견) · B(곰) 중 하나입니다.")

        val traits = mutableMapOf<Slot, Trait>()
        Slot.values().forEachIndexed { i, slot ->
            val number = body[1 + i].digitToIntOrNull()
            val trait = number?.let { TraitCatalog.byIndex(slot, it) }
                ?: return Result.Error("${slot.label} 번호가 잘못됐습니다 (1~5).")
            traits[slot] = trait
        }

        val mask = body.substring(5, 7).toIntOrNull(16)
            ?: return Result.Error("악세사리 부분이 잘못됐습니다.")
        if ((mask shr Region.values().size) != 0) {
            return Result.Error("이 앱에 없는 지역 악세사리가 들어 있습니다. 앱을 업데이트해 주세요.")
        }
        val accessories = Region.values().filter { (mask and (1 shl it.ordinal)) != 0 }.toSet()

        val generation = body.substring(7, 9).toIntOrNull()
        if (generation == null || generation !in 1..Genetics.MAX_GENERATION) {
            return Result.Error("세대 부분이 잘못됐습니다.")
        }

        return Result.Ok(Genome(species, traits, accessories, generation))
    }

    /** 소문자·공백·대시를 허용하고, 헷갈리는 글자(O→0, I·L→1)를 바로잡습니다. */
    private fun normalize(input: String): String =
        input.uppercase()
            .filter { it.isLetterOrDigit() }
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
            .joinToString("")

    private fun checksum(body: String): Char {
        var sum = 0
        body.forEachIndexed { i, c -> sum += (ALPHABET.indexOf(c) + 1) * (i + 1) }
        return ALPHABET[((sum % PRIME) + PRIME) % PRIME]
    }
}
