package com.mcc.app.trait

/**
 * 형질 정의.
 *
 * 그림(에셋)의 파일명과 일치시켜 두었으므로, 형질을 바꾸면 세 곳을 함께 고쳐야 합니다.
 *   ① 이 파일  ② 에셋 파일명  ③ 기획서 매핑표
 *
 * ★ 각 목록의 '순서'는 유전 코드(GeneCode)에 그대로 쓰입니다.
 *   순서를 바꾸면 이미 나눠준 유전 코드가 다른 캐릭터로 읽힙니다.
 *   새 형질은 목록 맨 뒤에만 추가하세요.
 */

/** 캐릭터를 구성하는 형질 슬롯. 악세사리는 지역(Region)으로 따로 관리합니다. */
enum class Slot(val label: String) {
    HAIR("모자"),
    EYE("눈"),
    MOUTH("입"),
    OUTFIT("의상"),
}

/**
 * 형질 하나.
 * @param asset 에셋 파일명 (확장자 제외). drawable 리소스명과 같게 둡니다.
 * @param basis 이 형질의 등장 확률을 올리는 운전 성향. 화면과 기획서에 그대로 씁니다.
 */
data class Trait(
    val slot: Slot,
    val id: String,
    val label: String,
    val asset: String,
    val basis: String,
)

object TraitCatalog {

    // basis 의 숫자는 Genetics.STYLE_RULES 의 조건과 일치합니다.
    // '(기본)' 표시가 있는 형질은 운전 조건 없이 기본 확률만 가집니다.

    val HAIR = listOf(
        Trait(Slot.HAIR, "wave",  "선장 모자",      "hair_01_wave",  "기본"),
        Trait(Slot.HAIR, "spike", "번개 모자",      "hair_02_spike", "급가속 km당 0.3회 이상"),
        Trait(Slot.HAIR, "wet",   "물방울 모자",    "hair_03_wet",   "와이퍼 켠 시간 20% 이상"),
        Trait(Slot.HAIR, "wind",  "고글 모자",      "hair_04_wind",  "1회 평균 이동거리 40km 이상"),
        Trait(Slot.HAIR, "flat",  "택시기사 모자",  "hair_05_flat",  "1회 평균 이동거리 8km 미만"),
    )

    val EYE = listOf(
        Trait(Slot.EYE, "calm",   "차분한 눈",      "eye_01_calm",   "기본"),
        Trait(Slot.EYE, "sharp",  "슬픈 눈",        "eye_02_sharp",  "급가감속 km당 0.6회 이상"),
        Trait(Slot.EYE, "shiny",  "빛나는 눈",      "eye_03_shiny",  "야간 주행 비율 30% 이상"),
        Trait(Slot.EYE, "sleepy", "피곤한 눈",      "eye_04_sleepy", "1회 평균 45분 이상 연속 주행"),
        Trait(Slot.EYE, "happy",  "웃는 눈",        "eye_05_happy",  "무과속 주행 비율 60% 이상"),
    )

    val MOUTH = listOf(
        Trait(Slot.MOUTH, "flat",  "인내의 일자 입", "mouth_01_flat",  "정차 시간 비율 40% 이상"),
        Trait(Slot.MOUTH, "smile", "미소",           "mouth_02_smile", "무과속 주행 비율 55% 이상"),
        Trait(Slot.MOUTH, "yawn",  "하품",           "mouth_03_yawn",  "평균 속도 70km/h 이상"),
        Trait(Slot.MOUTH, "hum",   "콧노래",         "mouth_04_hum",   "기본"),
        Trait(Slot.MOUTH, "tight", "꾹 다문 입",     "mouth_05_tight", "급제동 km당 0.3회 이상"),
    )

    val OUTFIT = listOf(
        Trait(Slot.OUTFIT, "marathon", "장거리형", "outfit_01_marathon", "1회 평균 이동거리 45km 이상"),
        Trait(Slot.OUTFIT, "medal",    "모범형",   "outfit_02_medal",    "무과속 주행 비율 75% 이상"),
        Trait(Slot.OUTFIT, "commute",  "통근형",   "outfit_03_commute",  "기본"),
        Trait(Slot.OUTFIT, "rain",     "우천형",   "outfit_04_rain",     "와이퍼 켠 시간 25% 이상"),
        Trait(Slot.OUTFIT, "night",    "야간형",   "outfit_05_night",    "야간 주행 비율 40% 이상"),
    )

    fun of(slot: Slot): List<Trait> = when (slot) {
        Slot.HAIR -> HAIR
        Slot.EYE -> EYE
        Slot.MOUTH -> MOUTH
        Slot.OUTFIT -> OUTFIT
    }

    fun find(slot: Slot, id: String): Trait? = of(slot).firstOrNull { it.id == id }

    /** 유전 코드용 번호 (1부터). */
    fun indexOf(trait: Trait): Int = of(trait.slot).indexOfFirst { it.id == trait.id } + 1

    /** 유전 코드용 번호(1부터)로 찾기. 범위 밖이면 null. */
    fun byIndex(slot: Slot, index: Int): Trait? = of(slot).getOrNull(index - 1)
}

/**
 * 종족. 부화할 때 각 1/3 확률로 정해집니다. 서열이 없습니다.
 * @param code 유전 코드에 쓰이는 한 글자
 */
enum class Species(val label: String, val asset: String, val code: Char) {
    CAT  ("고양이", "set_cat",   'C'),
    SHIBA("시바견", "set_shiba", 'S'),
    BEAR ("곰",     "set_bear",  'B');

    companion object {
        fun byCode(c: Char): Species? = values().firstOrNull { it.code == c.uppercaseChar() }
    }
}

/** 파워트레인 속성 표식. 차량 정보로 정해지며 유전되지 않습니다. (추후 속성 레이어) */
enum class Powertrain(val label: String, val mark: String) {
    EV    ("전기",       "몸에 빛나는 라인"),
    HEV   ("하이브리드", "이중 톤 털색"),
    FCEV  ("수소",       "물방울 무늬"),
    ICE   ("내연기관",   "기본 색"),
}
