package com.mcc.app.game

import kotlin.random.Random

/**
 * 캐릭터 말풍선 문구.
 *
 * 차량 화면에서 한눈에 읽히도록 모든 문구는 15자 이하 한 줄입니다 (테스트로 확인).
 * 시동 직후(기어 P)와 주차 직후에만 3초 동안 보여 주고, 주행 규제가 켜지면 바로 닫습니다.
 */
object Greetings {

    const val MAX_LEN = 15

    private val ANY = listOf(
        "오늘도 안전운전!",
        "같이 달려 볼까?",
        "보고 싶었어!",
        "준비 완료!",
        "천천히 가도 괜찮아",
        "안전벨트 했지?",
    )
    private val MORNING = listOf("좋은 아침이야!", "출근길 화이팅!", "아침 공기 좋다")
    private val AFTERNOON = listOf("점심은 먹었어?", "오후도 힘내자!")
    private val EVENING = listOf("오늘 하루 수고했어", "집에 가는 길이야?")
    private val NIGHT = listOf("밤길 조심해", "졸리면 쉬어 가자", "별이 예쁜 밤이야")
    private val EGG_EARLY = listOf("알이 꿈틀했어!", "알을 같이 품자")
    private val EGG_LATE = listOf("알이 따뜻해졌어", "곧 태어날 것 같아!")
    private val EGG_READY = listOf("알이 깨어나려 해!", "톡톡 눌러 줘!")

    private val TAP = listOf("헤헤 간지러워", "나 불렀어?", "오늘 멋지다!", "드라이브 가자!", "쓰담쓰담 좋아")

    private val AFTER_DRIVE = listOf("수고했어!", "오늘도 무사히 도착!", "같이 달려서 좋았어")

    /** 와이퍼가 움직이기 시작했을 때 (창밖에 비) */
    private val RAIN = listOf("비가 온다! 천천히 가자", "창밖에 비가 와", "빗길 조심해!")

    /** 차가 야간 모드로 바뀌었을 때 (방 불이 켜짐) */
    private val NIGHT_ON = listOf("어두워졌네, 불 켤게", "밤이 됐어!", "불 켜 줄게")

    /** 시동 인사. 시간대·야간 모드·알 상태에 맞는 문구를 섞어서 하나 고릅니다. */
    fun greet(rng: Random, hour: Int, night: Boolean?, egg: Egg?): String {
        val pool = mutableListOf<String>()
        pool += ANY
        pool += when {
            night == true || hour >= 21 || hour < 5 -> NIGHT
            hour < 11 -> MORNING
            hour < 17 -> AFTERNOON
            else -> EVENING
        }
        if (egg != null) {
            pool += when {
                egg.readyToHatch -> EGG_READY + EGG_READY      // 부화 직전이면 더 자주
                egg.progress >= 0.6f -> EGG_LATE
                else -> EGG_EARLY
            }
        }
        return pool[rng.nextInt(pool.size)]
    }

    /** 캐릭터를 눌렀을 때 (기어 P 에서만) */
    fun tap(rng: Random): String = TAP[rng.nextInt(TAP.size)]

    /** 주차 직후. 달린 거리를 짧게 붙입니다. */
    fun afterDrive(rng: Random, km: Float): String =
        if (km >= 1f) "${km.toInt()}km 함께 달렸어!" else AFTER_DRIVE[rng.nextInt(AFTER_DRIVE.size)]

    /** 차량 상태가 바뀐 순간의 한마디 (문·동승자·연료 등, Pleos 차량 SDK) */
    private val MOMENTS: Map<VehicleMoments.Moment, List<String>> = mapOf(
        VehicleMoments.Moment.DOOR_OPEN to listOf("문 조심해!", "문 열렸어!"),
        VehicleMoments.Moment.PASSENGER_IN to listOf("친구도 탔네!", "반가워, 친구!"),
        VehicleMoments.Moment.ENERGY_LOW to listOf("배고파… 밥 먹자", "배에서 꼬르륵…"),
        VehicleMoments.Moment.ENERGY_FULL to listOf("든든해!", "배부르다~"),
        VehicleMoments.Moment.CHARGING to listOf("충전 중… zZ", "밥 먹는 중!"),
        VehicleMoments.Moment.WINDOW_OPEN to listOf("바람 시원하다!", "바깥 공기 좋다"),
        VehicleMoments.Moment.ROUTE_START to listOf("어디 가? 두근두근!", "같이 가자, 출발!"),
        VehicleMoments.Moment.ARRIVED to listOf("도착! 수고했어", "다 왔다! 잘 달렸어"),
    )

    fun moment(rng: Random, m: VehicleMoments.Moment): String =
        MOMENTS.getValue(m).let { it[rng.nextInt(it.size)] }

    fun rain(rng: Random): String = RAIN[rng.nextInt(RAIN.size)]

    fun nightOn(rng: Random): String = NIGHT_ON[rng.nextInt(NIGHT_ON.size)]

    /** 테스트용: 모든 고정 문구 */
    val ALL: List<String>
        get() = ANY + MORNING + AFTERNOON + EVENING + NIGHT + EGG_EARLY + EGG_LATE + EGG_READY + TAP + AFTER_DRIVE + RAIN + NIGHT_ON + MOMENTS.values.flatten()
}
