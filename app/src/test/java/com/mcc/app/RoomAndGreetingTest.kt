package com.mcc.app

import com.mcc.app.game.Creature
import com.mcc.app.game.Egg
import com.mcc.app.game.GameState
import com.mcc.app.game.Greetings
import com.mcc.app.game.RoomCatalog
import com.mcc.app.game.RoomRules
import com.mcc.app.game.RoomSlot
import com.mcc.app.region.Region
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.Species
import com.mcc.app.trait.TraitCatalog
import com.mcc.app.trait.TraitEngine
import com.mcc.app.ui.HitTest
import com.mcc.app.ui.NightLight
import com.mcc.app.game.AmbienceRules
import com.mcc.app.game.CollectionRules
import com.mcc.app.game.DemoUnlock
import com.mcc.app.ui.Recolor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 방 꾸미기 해금·선택 · 말풍선 길이 · 단계 색 변환 */
class RoomAndGreetingTest {

    @Test
    fun catalog_ids_unique_and_defaults_free() {
        assertEquals(RoomCatalog.ITEMS.size, RoomCatalog.ITEMS.map { it.id }.toSet().size)
        val s = GameState()
        assertEquals("theme_default", RoomRules.theme(s).id)
        assertEquals("처음엔 화분 하나", listOf("item_plant"), RoomRules.placed(s).map { it.first.id })
        assertTrue(RoomRules.isUnlocked(RoomCatalog.find("item_plant")!!, s))
    }

    @Test
    fun km_and_region_unlocks() {
        val s = GameState()
        val sea = RoomCatalog.find("theme_busan")!!
        assertFalse(RoomRules.isUnlocked(sea, s))
        assertFalse("잠긴 방은 못 고름", RoomRules.selectTheme(s, "theme_busan"))
        s.creatures += Creature("a", Genome(Species.CAT,
            Slot.values().associateWith { TraitCatalog.of(it)[0] }, setOf(Region.BUSAN), 1))
        assertTrue("부산 아이템을 가진 캐릭터가 있으면 열림", RoomRules.isUnlocked(sea, s))
        assertTrue(RoomRules.selectTheme(s, "theme_busan"))
        assertEquals("theme_busan", RoomRules.theme(s).id)
        assertFalse("소품은 테마로 못 고름", RoomRules.selectTheme(s, "item_plant"))

        assertEquals(RoomRules.PlaceResult.LOCKED, RoomRules.place(s, "item_globe"))
        s.lifetime.totalKm = 1000f
        assertEquals("누적 1000km 면 지구본", RoomRules.PlaceResult.OK, RoomRules.place(s, "item_globe"))
    }

    @Test
    fun place_up_to_four_items_and_remove() {
        val s = GameState()
        assertEquals("잠긴 소품은 못 꺼냄", RoomRules.PlaceResult.LOCKED, RoomRules.place(s, "item_toycar"))
        // 소품 8개 모두 열기
        Slot.values().forEach { slot -> TraitCatalog.of(slot).forEach { s.collection.counts[it.asset] = 6 } }
        Region.values().forEach { s.collection.counts[it.asset] = 1 }
        s.lifetime.totalKm = 1000f
        assertEquals(RoomRules.PlaceResult.ALREADY, RoomRules.place(s, "item_plant"))
        listOf("item_toycar", "item_books", "item_lamp").forEach {
            assertEquals(RoomRules.PlaceResult.OK, RoomRules.place(s, it))
        }
        assertEquals(4, RoomRules.placed(s).size)
        assertEquals("5개째는 안 됨", RoomRules.PlaceResult.FULL, RoomRules.place(s, "item_globe"))
        assertTrue(RoomRules.remove(s, "item_books"))
        assertEquals(RoomRules.PlaceResult.OK, RoomRules.place(s, "item_globe"))
        assertEquals(RoomRules.MAX_PLACED, RoomRules.placed(s).size)
        assertEquals("테마는 기본 + 지역 8 + 황금", 10, RoomCatalog.of(RoomSlot.THEME).size)
        // 새로 꺼낸 소품은 서로 겹치지 않는 기본 자리에 놓임
        val spots = RoomRules.placed(s).map { it.second.x to it.second.y }
        assertEquals(spots.size, spots.toSet().size)
    }

    @Test
    fun move_keeps_items_inside_the_room() {
        val s = GameState()
        assertTrue(RoomRules.move(s, "item_plant", 0.42f, 0.7f))
        val p = RoomRules.placed(s).single().second
        assertEquals(0.42f, p.x, 1e-6f)
        assertEquals(0.7f, p.y, 1e-6f)
        RoomRules.move(s, "item_plant", -3f, 5f)
        assertEquals(RoomRules.MIN_X, p.x, 1e-6f)
        assertEquals(RoomRules.MAX_Y, p.y, 1e-6f)
        assertFalse("꺼내지 않은 소품은 못 옮김", RoomRules.move(s, "item_globe", 0.5f, 0.5f))
    }

    @Test
    fun every_region_has_its_own_room() {
        val s = GameState()
        Region.values().forEach { region ->
            val room = RoomCatalog.of(RoomSlot.THEME).single { (it.unlock as? com.mcc.app.game.Unlock.RegionGot)?.region == region }
            assertFalse("${region.label} 방은 처음엔 잠김", RoomRules.isUnlocked(room, s))
            s.collection.counts[region.asset] = 1
            assertTrue("${region.label} 아이템을 얻으면 열림", RoomRules.isUnlocked(room, s))
        }
    }

    @Test
    fun every_theme_has_a_window_inside_the_picture_and_lamp_lights_up() {
        RoomCatalog.of(RoomSlot.THEME).forEach { theme ->
            val w = theme.window
            assertTrue("${theme.label} 창문 자리 없음", w != null)
            w!!
            assertTrue("${theme.label} 창문 범위", w.left in 0f..1f && w.right in 0f..1f && w.top in 0f..1f && w.bottom in 0f..1f)
            assertTrue("${theme.label} 창문 크기", w.right > w.left + 0.05f && w.bottom > w.top + 0.05f)
            assertTrue("${theme.label} 창문은 방 위쪽 절반쯤에", w.top < 0.4f && w.bottom < 0.7f)
        }
        assertEquals("불 켜지는 소품은 스탠드", listOf("item_lamp"), RoomCatalog.ITEMS.filter { it.light }.map { it.id })
    }

    @Test
    fun window_mask_bounds_cover_only_the_glass() {
        // 10×5 마스크: (2,1)~(6,3) 칸만 흰색 → 그 범위만
        val w = 10
        val h = 5
        val px = IntArray(w * h)
        for (y in 1..3) for (x in 2..6) px[y * w + x] = 0xFFFFFFFF.toInt()
        px[0] = 0x40FFFFFF   // 반투명 가장자리는 무시
        val b = com.mcc.app.ui.WindowMask.bounds(px, w, h)!!
        assertEquals(0.2f, b[0], 1e-6f)
        assertEquals(0.2f, b[1], 1e-6f)
        assertEquals(0.7f, b[2], 1e-6f)
        assertEquals(0.8f, b[3], 1e-6f)
        assertEquals("유리가 없으면 null", null, com.mcc.app.ui.WindowMask.bounds(IntArray(w * h), w, h))
    }

    @Test
    fun wiper_state_names_map_to_raining() {
        val w = com.mcc.app.vehicle.PleosVehicle
        listOf("ON", "LOW", "HIGH", "on").forEach { assertTrue("$it 은 움직이는 중", w.isWiping(it)) }
        listOf("OFF", "OTHER", "SERVICE", "off").forEach { assertFalse("$it 은 멈춤", w.isWiping(it)) }
    }

    @Test
    fun locked_saved_things_fall_back() {
        val s = GameState()
        s.room.theme = "theme_gold"                                   // 저장은 됐지만 조건이 안 맞는 경우
        assertEquals("theme_default", RoomRules.theme(s).id)
        s.room.placed += com.mcc.app.game.Placed("item_trophy", 0.5f, 0.5f)  // 잠긴 소품
        s.room.placed += com.mcc.app.game.Placed("no_such_item", 0.5f, 0.5f) // 없어진 소품
        assertEquals("잠긴·없는 소품은 안 보임", listOf("item_plant"), RoomRules.placed(s).map { it.first.id })
    }

    @Test
    fun every_line_fits_on_one_short_row() {
        Greetings.ALL.forEach { assertTrue("'$it' 은 15자 초과", it.length <= Greetings.MAX_LEN) }
        val rng = Random(3)
        val egg = Egg(emptyList(), 1, km = TraitEngine.HATCH_DISTANCE_KM)
        repeat(200) {
            val line = Greetings.greet(rng, it % 24, it % 3 == 0, if (it % 2 == 0) egg else null)
            assertTrue(line.length <= Greetings.MAX_LEN)
        }
        assertTrue(Greetings.afterDrive(rng, 123.4f).length <= Greetings.MAX_LEN)
    }

    @Test
    fun recolor_keeps_outline_white_and_transparent() {
        val outline = 0xFF5A2E1E.toInt()
        val white = 0xFFFFFFFF.toInt()
        val clear = 0x00000000
        val mint = 0xFF5CC8B8.toInt()
        val px = intArrayOf(outline, white, clear, mint)
        Recolor.apply(px, 1, includeDark = false)
        assertEquals("외곽선 그대로", outline, px[0])
        assertEquals("흰색 그대로", white, px[1])
        assertEquals("투명 그대로", clear, px[2])
        assertNotEquals("색 있는 부분은 바뀜", mint, px[3])
        assertEquals("알파 유지", 0xFF, px[3] ushr 24)

        val gold = intArrayOf(mint)
        Recolor.apply(gold, 2, includeDark = false)
        val hsv = FloatArray(3)
        Recolor.rgbToHsv((gold[0] shr 16) and 255, (gold[0] shr 8) and 255, gold[0] and 255, hsv)
        assertTrue("★★ 는 금빛 (색상 ${hsv[0]})", hsv[0] in 30f..60f)

        val eye = intArrayOf(0xFF4A2E24.toInt())
        Recolor.apply(eye, 1, includeDark = true)
        assertNotEquals("눈·입은 진한 선도 바뀜", 0xFF4A2E24.toInt(), eye[0])
    }

    @Test
    fun hsv_round_trip() {
        val hsv = FloatArray(3)
        listOf(0x5CC8B8, 0xFFD54A, 0x2E3A6B, 0xE85D5D).forEach { rgb ->
            Recolor.rgbToHsv(rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255, hsv)
            assertEquals(rgb, Recolor.hsvToRgb(hsv))
        }
    }

    @Test
    fun tap_maps_to_the_drawn_picture_only() {
        // 400×400 칸에 200×100 그림 → 2배로 커져 위아래 100px 씩 빈 띠가 생김
        assertEquals(listOf(100, 50), HitTest.bitmapPoint(400, 400, 200, 100, 200f, 200f)!!.toList())
        assertEquals(listOf(0, 0), HitTest.bitmapPoint(400, 400, 200, 100, 0f, 100f)!!.toList())
        assertEquals(listOf(199, 99), HitTest.bitmapPoint(400, 400, 200, 100, 399f, 299f)!!.toList())
        // 빈 띠·칸 밖은 그림이 아님
        assertEquals(null, HitTest.bitmapPoint(400, 400, 200, 100, 200f, 50f))
        assertEquals(null, HitTest.bitmapPoint(400, 400, 200, 100, 200f, 350f))
        assertEquals(null, HitTest.bitmapPoint(400, 400, 200, 100, -5f, 200f))
        assertEquals(null, HitTest.bitmapPoint(0, 400, 200, 100, 10f, 10f))
        // 손가락 여유: 가운데 + 둘레 8곳, 모두 반지름 안
        val probes = HitTest.probes(50f, 50f, 10f)
        assertEquals(9, probes.size)
        assertTrue(probes.all { (x, y) -> (x - 50f) * (x - 50f) + (y - 50f) * (y - 50f) <= 100.01f })
    }

    @Test
    fun ambience_button_wins_until_vehicle_changes() {
        // 고른 값이 없으면 차량(와이퍼·야간 모드)을 따름
        assertEquals(true, AmbienceRules.effective(null, true))
        assertEquals(false, AmbienceRules.effective(null, false))
        assertEquals(false, AmbienceRules.effective(null, null))   // 차량 신호 없음
        // 버튼으로 고른 값이 우선
        assertEquals(false, AmbienceRules.effective(false, true))
        assertEquals(true, AmbienceRules.effective(true, null))
    }

    @Test
    fun demo_unlock_opens_every_room_entry_and_tier() {
        val s = GameState()
        val c = Creature("a", Genome(Species.CAT, Slot.values().associateWith { TraitCatalog.of(it)[0] }, emptySet(), 1))
        s.creatures += c
        s.lifetime.totalKm = 12f
        DemoUnlock.unlockAll(s)
        assertTrue("방 테마·소품 전부 열림", RoomCatalog.ITEMS.all { RoomRules.isUnlocked(it, s) })
        assertTrue("도감 전부 ★★", CollectionRules.ENTRIES.all { CollectionRules.tier(s.collection, it.asset) == CollectionRules.MAX_TIER })
        assertTrue("캐릭터가 ★★ 파츠를 입음", Slot.values().all { CollectionRules.equippedTier(s.collection, c, it) == CollectionRules.MAX_TIER })
        assertEquals(DemoUnlock.SHOWCASE_KM, s.lifetime.totalKm, 0f)
        assertEquals("캐릭터·알은 그대로", 1, s.creatures.size)
        // 더 많이 달린 기록은 줄이지 않음
        s.lifetime.totalKm = 5000f
        DemoUnlock.unlockAll(s)
        assertEquals(5000f, s.lifetime.totalKm, 0f)
    }

    @Test
    fun night_light_fades_from_white_to_blue_dim() {
        assertEquals(0xFFFFFFFF.toInt(), NightLight.tint(0f))
        val night = NightLight.tint(1f)
        assertEquals(0xFF shl 24 or (NightLight.NIGHT_R shl 16) or (NightLight.NIGHT_G shl 8) or NightLight.NIGHT_B, night)
        // 밤에도 70% 정도 밝기는 남고, 파랑이 가장 많이 남음
        assertTrue(NightLight.NIGHT_R >= 0xA0 && NightLight.NIGHT_B > NightLight.NIGHT_R)
        // 중간은 둘 사이, 범위 밖 값은 잘라냄
        val half = NightLight.tint(0.5f) and 0xFF
        assertTrue(half in NightLight.NIGHT_B..0xFF)
        assertEquals(night, NightLight.tint(3f))
        assertEquals(0xFFFFFFFF.toInt(), NightLight.tint(-1f))
    }

    @Test
    fun lamp_light_lifts_the_night_near_the_lamp() {
        val far = NightLight.tint(1f, 0f)
        val near = NightLight.tint(1f, 1f)
        fun r(c: Int) = (c shr 16) and 0xFF
        fun b(c: Int) = c and 0xFF
        assertTrue("스탠드 옆은 더 밝음", r(near) > r(far))
        assertTrue("스탠드 옆은 따뜻함 (빨강 ≥ 파랑)", r(near) >= b(near))
        assertEquals("낮에는 불빛과 상관없이 원래 밝기", 0xFFFFFFFF.toInt(), NightLight.tint(0f, 1f))
        assertEquals(1f, NightLight.falloff(0f, 100f), 0f)
        assertEquals(0f, NightLight.falloff(100f, 100f), 0f)
        assertEquals(0f, NightLight.falloff(250f, 100f), 0f)
        assertEquals(0f, NightLight.falloff(10f, 0f), 0f)
        assertTrue(NightLight.falloff(30f, 100f) > NightLight.falloff(60f, 100f))
    }
}
