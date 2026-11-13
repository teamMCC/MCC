package com.mcc.app.game

import com.mcc.app.region.Region
import com.mcc.app.trait.Slot

/**
 * 방 꾸미기.
 *
 * 메인 화면 배경(방)의 테마를 고르고, 소품을 최대 4개까지 꺼내 방 안 원하는 곳에 둡니다.
 * 소품은 홈 화면에서 '길게 눌러 끌기'로 옮기고, 손을 떼면 그 자리에 고정됩니다 (기어 P 에서만).
 *
 * 그림은 res/drawable-nodpi 에 asset 이름 그대로 넣습니다.
 *   · 방 테마: 방 전체 그림. 화면을 꽉 채우도록 가운데 기준으로 잘라 보여 줍니다 (16:9 권장, 지역 방은 1672×941)
 *   · 소품: 물건만 잘라낸 투명 PNG. 저장된 위치에 '아래 가운데'를 맞춰 세웁니다.
 */
enum class RoomSlot(val label: String) {
    THEME("방 테마"),
    ITEM("소품"),
}

/** 소품이 열리는 조건. */
sealed class Unlock(val hint: String) {
    object Free : Unlock("기본")
    class Km(val km: Int) : Unlock("누적 ${km}km 주행")
    class SlotRegistered(val slot: Slot) : Unlock("도감 ${slot.label} 5종 모두 등록")
    class AnySlotTier(val tier: Int) : Unlock("도감 한 줄 모두 " + "★".repeat(tier))
    /** 도감 31칸 전부: 형질 20종은 이 단계 이상, 종족·지역은 등록 */
    class AllTier(val tier: Int) : Unlock("도감 전체 " + "★".repeat(tier) + " 달성")
    class RegionGot(val region: Region) : Unlock("${region.label} 지역 아이템 획득")
    object AllRegions : Unlock("지역 아이템 8종 모두 획득")
}

/**
 * 방 그림 속 창문 자리 (그림 크기에 대한 비율 0~1). 비 오는 날 이 안에만 빗줄기를 그립니다.
 * shape: RECT 네모 · ROUND 위가 둥근 창 · OVAL 동그란 창
 */
data class WindowArea(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val shape: Shape = Shape.RECT,
) {
    enum class Shape { RECT, ROUND, OVAL }
}

/**
 * @param window 방 테마의 창문 자리 (비 연출용, 없으면 비를 그리지 않음)
 * @param light  밤이 되면 불빛이 켜지는 소품인지 (스탠드 등)
 * @param height 소품 높이. 캐릭터 그림 크기를 1로 봤을 때의 비율 (화면 크기가 달라도 캐릭터와 같은 비율로 보임)
 */
data class RoomItem(
    val id: String,
    val slot: RoomSlot,
    val label: String,
    val asset: String,
    val unlock: Unlock,
    val height: Float = 0.4f,
    val window: WindowArea? = null,
    val light: Boolean = false,
)

/**
 * 방에 꺼내 둔 소품 하나.
 * x = 소품 가운데의 가로 위치, y = 소품 바닥의 세로 위치. 둘 다 방 화면 크기에 대한 비율 (0~1)이라
 * 화면 크기가 달라도 같은 자리에 놓입니다.
 */
data class Placed(val id: String, var x: Float, var y: Float)

/**
 * 내 방.
 * @param theme  고른 방 테마 id (null 이면 기본 방)
 * @param placed 꺼내 둔 소품들 (최대 RoomRules.MAX_PLACED 개). 처음엔 화분 하나.
 */
class RoomState(
    var theme: String? = null,
    val placed: MutableList<Placed> = mutableListOf(
        Placed("item_plant", RoomCatalog.SPOTS[0].first, RoomCatalog.SPOTS[0].second),
    ),
)

object RoomCatalog {

    /** 기본 방(room_default, 16:9)의 창문 자리. 마스크(room_default_window.png)가 있으면 마스크가 우선 */
    private val W_DEFAULT = WindowArea(0.205f, 0.084f, 0.374f, 0.337f)
    /** 황금 방(별이 뜬 아치 창)의 창문 자리. 마스크(room_gold_window.png)가 있으면 마스크가 우선 */
    private val W_GOLD = WindowArea(0.268f, 0.159f, 0.731f, 0.552f, WindowArea.Shape.ROUND)

    // 순서는 목록에 보이는 순서입니다. id 는 저장에 쓰이므로 바꾸지 마세요.
    val ITEMS: List<RoomItem> = listOf(
        RoomItem("theme_default", RoomSlot.THEME, "포근한 방",  "room_default", Unlock.Free, window = W_DEFAULT),

        // 지역 방: 그 지역 아이템을 한 번이라도 얻으면 열립니다 (도감 기록 또는 지금 캐릭터가 착용 중).
        RoomItem("theme_seoul",       RoomSlot.THEME, "서울 남산 한옥",   "room_seoul",       Unlock.RegionGot(Region.SEOUL),
            window = WindowArea(0.393f, 0.118f, 0.618f, 0.455f)),
        RoomItem("theme_gyeonggi",    RoomSlot.THEME, "인천 월미도 방",   "room_gyeonggi",    Unlock.RegionGot(Region.GYEONGGI),
            window = WindowArea(0.282f, 0.130f, 0.712f, 0.560f, WindowArea.Shape.ROUND)),
        RoomItem("theme_gangwon",     RoomSlot.THEME, "강원 산장",        "room_gangwon",     Unlock.RegionGot(Region.GANGWON),
            window = WindowArea(0.245f, 0.085f, 0.770f, 0.435f)),
        RoomItem("theme_chungcheong", RoomSlot.THEME, "충청 도자기 공방", "room_chungcheong", Unlock.RegionGot(Region.CHUNGCHEONG),
            window = WindowArea(0.345f, 0.112f, 0.665f, 0.600f, WindowArea.Shape.OVAL)),
        RoomItem("theme_jeolla",      RoomSlot.THEME, "전주 한옥 방",     "room_jeolla",      Unlock.RegionGot(Region.JEOLLA),
            window = WindowArea(0.318f, 0.128f, 0.684f, 0.540f)),
        RoomItem("theme_gyeongsang",  RoomSlot.THEME, "경주 신라 방",     "room_gyeongsang",  Unlock.RegionGot(Region.GYEONGSANG),
            window = WindowArea(0.308f, 0.215f, 0.690f, 0.562f, WindowArea.Shape.ROUND)),
        RoomItem("theme_busan",       RoomSlot.THEME, "부산 광안리 방",   "room_busan",       Unlock.RegionGot(Region.BUSAN),
            window = WindowArea(0.298f, 0.190f, 0.705f, 0.565f, WindowArea.Shape.ROUND)),
        RoomItem("theme_jeju",        RoomSlot.THEME, "제주 돌담 방",     "room_jeju",        Unlock.RegionGot(Region.JEJU),
            window = WindowArea(0.322f, 0.140f, 0.680f, 0.555f, WindowArea.Shape.ROUND)),

        RoomItem("theme_gold",    RoomSlot.THEME, "황금 방",    "room_gold",    Unlock.AnySlotTier(2), window = W_GOLD),

        RoomItem("item_plant",    RoomSlot.ITEM, "화분",      "room_item_plant",    Unlock.Free, 0.42f),
        RoomItem("item_hatstand", RoomSlot.ITEM, "모자 진열대", "room_item_hatstand", Unlock.SlotRegistered(Slot.HAIR), 0.40f),
        RoomItem("item_lamp",     RoomSlot.ITEM, "스탠드",    "room_item_lamp",     Unlock.SlotRegistered(Slot.EYE), 0.55f, light = true),
        RoomItem("item_books",    RoomSlot.ITEM, "책 더미",   "room_item_books",    Unlock.SlotRegistered(Slot.MOUTH), 0.32f),
        RoomItem("item_toycar",   RoomSlot.ITEM, "장난감 차", "room_item_toycar",   Unlock.SlotRegistered(Slot.OUTFIT), 0.28f),
        RoomItem("item_suitcase", RoomSlot.ITEM, "여행 가방", "room_item_suitcase", Unlock.AllRegions, 0.38f),
        RoomItem("item_globe",    RoomSlot.ITEM, "지구본",    "room_item_globe",    Unlock.Km(1000), 0.38f),
        RoomItem("item_trophy_silver", RoomSlot.ITEM, "은색 트로피", "room_item_trophy_silver", Unlock.AllTier(1), 0.38f),
        RoomItem("item_trophy",   RoomSlot.ITEM, "트로피",    "room_item_trophy",   Unlock.AnySlotTier(2), 0.38f),
    )

    /** 처음 방 테마 */
    const val DEFAULT_THEME = "theme_default"

    fun find(id: String): RoomItem? = ITEMS.firstOrNull { it.id == id }
    fun of(slot: RoomSlot): List<RoomItem> = ITEMS.filter { it.slot == slot }

    /**
     * 새로 꺼낸 소품이 처음 놓이는 자리 (x 가운데, y 바닥 · 화면 비율).
     * 캐릭터(가운데) 양옆 바닥부터 채웁니다. 꺼낸 뒤에는 길게 눌러 원하는 곳으로 옮깁니다.
     */
    val SPOTS: List<Pair<Float, Float>> = listOf(
        0.30f to 0.90f,
        0.70f to 0.90f,
        0.16f to 0.86f,
        0.84f to 0.86f,
    )
}

object RoomRules {

    /** 방에 한 번에 꺼내 둘 수 있는 소품 수 */
    const val MAX_PLACED = 4

    /** 소품이 화면 밖으로 나가지 않게 하는 범위 (x 가운데, y 바닥) */
    const val MIN_X = 0.04f
    const val MAX_X = 0.96f
    const val MIN_Y = 0.25f
    const val MAX_Y = 0.99f

    fun isUnlocked(item: RoomItem, state: GameState): Boolean = when (val u = item.unlock) {
        Unlock.Free -> true
        is Unlock.Km -> state.lifetime.totalKm >= u.km
        is Unlock.SlotRegistered -> CollectionRules.slotComplete(state.collection, u.slot, 0)
        is Unlock.AnySlotTier -> CollectionRules.anySlotComplete(state.collection, u.tier)
        is Unlock.AllTier -> CollectionRules.allComplete(state.collection, u.tier)
        is Unlock.RegionGot -> hasRegion(state, u.region)
        Unlock.AllRegions -> Region.values().all { hasRegion(state, it) }
    }

    /** 도감에 기록됐거나, 지금 캐릭터가 가지고 있으면 획득으로 봅니다. */
    private fun hasRegion(state: GameState, region: Region): Boolean =
        CollectionRules.count(state.collection, region.asset) > 0 ||
            state.creatures.any { region in it.genome.accessories }

    fun unlocked(state: GameState): List<RoomItem> = RoomCatalog.ITEMS.filter { isUnlocked(it, state) }

    /** 지금 방 테마. 저장된 게 잠겼거나 없으면 기본 방. */
    fun theme(state: GameState): RoomItem {
        val chosen = state.room.theme?.let { RoomCatalog.find(it) }
            ?.takeIf { it.slot == RoomSlot.THEME && isUnlocked(it, state) }
        return chosen ?: RoomCatalog.find(RoomCatalog.DEFAULT_THEME)!!
    }

    /** 방 테마 고르기. 잠긴 테마면 false. */
    fun selectTheme(state: GameState, id: String): Boolean {
        val item = RoomCatalog.find(id) ?: return false
        if (item.slot != RoomSlot.THEME || !isUnlocked(item, state)) return false
        state.room.theme = item.id
        return true
    }

    /** 지금 방에 보일 소품들 (잠겼거나 없어진 소품·중복은 빼고, 최대 MAX_PLACED 개). */
    fun placed(state: GameState): List<Pair<RoomItem, Placed>> =
        state.room.placed
            .distinctBy { it.id }
            .mapNotNull { p -> RoomCatalog.find(p.id)?.takeIf { it.slot == RoomSlot.ITEM && isUnlocked(it, state) }?.let { it to p } }
            .take(MAX_PLACED)

    fun isPlaced(state: GameState, id: String): Boolean = placed(state).any { it.first.id == id }

    /** 꺼낼 수 있는 상태인지 (잠김·이미 꺼냄·4개 가득). */
    enum class PlaceResult { OK, LOCKED, ALREADY, FULL, UNKNOWN }

    /** 소품 꺼내기. 비어 있는 기본 자리에 놓고, 그다음 길게 눌러 옮깁니다. */
    fun place(state: GameState, id: String): PlaceResult {
        val item = RoomCatalog.find(id)?.takeIf { it.slot == RoomSlot.ITEM } ?: return PlaceResult.UNKNOWN
        if (!isUnlocked(item, state)) return PlaceResult.LOCKED
        val now = placed(state)
        if (now.any { it.first.id == id }) return PlaceResult.ALREADY
        if (now.size >= MAX_PLACED) return PlaceResult.FULL
        // 보이지 않는(잠긴) 기록은 정리하고 새로 넣습니다.
        state.room.placed.retainAll { p -> now.any { it.second === p } }
        val spot = RoomCatalog.SPOTS.firstOrNull { (x, y) ->
            now.none { (_, p) -> kotlin.math.abs(p.x - x) < 0.08f && kotlin.math.abs(p.y - y) < 0.08f }
        } ?: RoomCatalog.SPOTS[now.size % RoomCatalog.SPOTS.size]
        state.room.placed += Placed(id, spot.first, spot.second)
        return PlaceResult.OK
    }

    /** 소품 넣기 (방에서 치우기). */
    fun remove(state: GameState, id: String): Boolean = state.room.placed.removeAll { it.id == id }

    /** 소품 옮기기. 화면 밖으로 나가지 않게 범위를 맞춥니다. 꺼내지 않은 소품이면 false. */
    fun move(state: GameState, id: String, x: Float, y: Float): Boolean {
        val p = state.room.placed.firstOrNull { it.id == id } ?: return false
        p.x = x.coerceIn(MIN_X, MAX_X)
        p.y = y.coerceIn(MIN_Y, MAX_Y)
        return true
    }

    /** 바로 전과 비교해 새로 열린 테마·소품 (부화·주행 직후 알림용). */
    fun newlyUnlocked(before: Set<String>, state: GameState): List<RoomItem> =
        unlocked(state).filter { it.id !in before }
}
