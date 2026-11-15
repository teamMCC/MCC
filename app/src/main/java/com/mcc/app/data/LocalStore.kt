package com.mcc.app.data

import android.content.Context
import android.util.Log
import com.mcc.app.game.Collection
import com.mcc.app.game.CollectionRules
import com.mcc.app.game.Creature
import com.mcc.app.game.Egg
import com.mcc.app.game.GameRules
import com.mcc.app.game.GameState
import com.mcc.app.game.Placed
import com.mcc.app.game.RoomCatalog
import com.mcc.app.game.RoomState
import com.mcc.app.region.Region
import com.mcc.app.trait.GeneCode
import com.mcc.app.trait.Genome
import com.mcc.app.trait.Slot
import com.mcc.app.trait.TraitEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * 차량 내부 저장소.
 *
 * 공모전 제출 버전은 서버가 없습니다. 캐릭터·알·주행 기록을 전부 차량에만 둡니다.
 *   · 원본 GPS 좌표는 저장하지 않습니다. 지역 이름과 거리만 저장합니다.
 *   · 심사위원이 네트워크 없이 실행해도 똑같이 동작합니다.
 *
 * 캐릭터의 유전 정보는 유전 코드(GeneCode) 그대로 저장합니다.
 * 저장 형식과 공유 형식이 같아서, 저장된 캐릭터는 언제든 코드로 꺼내 줄 수 있습니다.
 *
 * 키
 *   state_v1       캐릭터·알·누적 주행 (처음부터 쓰던 형식, 바꾸지 않음)
 *   collection_v1  도감 횟수·입힌 단계
 *   room_v1        방 꾸미기
 * 한 키가 깨져도 다른 키는 그대로 읽습니다.
 */
class LocalStore(context: Context) {

    private val sp = context.getSharedPreferences("mcc_game", Context.MODE_PRIVATE)

    fun load(): GameState {
        val collection = readOrNull(KEY_COLLECTION) { decodeCollection(it) }
        val room = readOrNull(KEY_ROOM) { decodeRoom(it) } ?: RoomState()
        val raw = sp.getString(KEY_STATE, null)
        val state = if (raw == null) {
            GameState(collection = collection ?: Collection(), room = room)
        } else try {
            decodeState(JSONObject(raw), collection ?: Collection(), room)
        } catch (e: Exception) {
            // 저장 형식이 깨졌으면 새로 시작합니다. 원본은 백업 키에 남겨 둡니다.
            Log.e(TAG, "저장 데이터 읽기 실패 — 새로 시작합니다", e)
            sp.edit().putString(KEY_BROKEN_BACKUP, raw).apply()
            GameState(collection = collection ?: Collection(), room = room)
        }
        // 도감이 생기기 전부터 있던 캐릭터는 한 번씩 등록해 둡니다.
        if (collection == null) CollectionRules.seedFrom(state.collection, state.creatures)
        return state
    }

    fun save(state: GameState) {
        sp.edit()
            .putString(KEY_STATE, encodeState(state).toString())
            .putString(KEY_COLLECTION, encodeCollection(state.collection).toString())
            .putString(KEY_ROOM, encodeRoom(state.room).toString())
            .apply()
    }

    private fun <T> readOrNull(key: String, decode: (JSONObject) -> T): T? {
        val raw = sp.getString(key, null) ?: return null
        return try {
            decode(JSONObject(raw))
        } catch (e: Exception) {
            Log.e(TAG, "$key 읽기 실패 — 비워서 시작합니다", e)
            null
        }
    }

    fun reset() {
        sp.edit().clear().apply()
        demo.edit().clear().apply()
    }

    // ── 시연용 '모두 해금' 백업 ────────────────────
    // 해금 전 진행 상황을 다른 파일(mcc_demo_backup)에 그대로 복사해 두었다가 되돌립니다.

    private val demo = context.getSharedPreferences("mcc_demo_backup", Context.MODE_PRIVATE)

    fun hasDemoBackup(): Boolean = demo.getBoolean(KEY_DEMO_SAVED, false)

    /** 지금 저장된 진행 상황을 백업합니다. 이미 백업이 있으면 덮어쓰지 않습니다 (처음 상태를 지킴). */
    fun backupForDemo(state: GameState) {
        if (hasDemoBackup()) return
        save(state)
        demo.edit()
            .putString(KEY_STATE, encodeState(state).toString())
            .putString(KEY_COLLECTION, encodeCollection(state.collection).toString())
            .putString(KEY_ROOM, encodeRoom(state.room).toString())
            .putBoolean(KEY_DEMO_SAVED, true)
            .commit()
    }

    /** 백업해 둔 진행 상황으로 되돌립니다. 백업이 없으면 false */
    fun restoreDemoBackup(): Boolean {
        if (!hasDemoBackup()) return false
        sp.edit()
            .putString(KEY_STATE, demo.getString(KEY_STATE, null))
            .putString(KEY_COLLECTION, demo.getString(KEY_COLLECTION, null))
            .putString(KEY_ROOM, demo.getString(KEY_ROOM, null))
            .commit()
        demo.edit().clear().commit()
        return true
    }

    // ── 직렬화 ────────────────────────────────────

    private fun encodeState(s: GameState): JSONObject = JSONObject().apply {
        put("v", 1)
        s.activeId?.let { put("activeId", it) }
        put("creatures", JSONArray().apply { s.creatures.forEach { put(encodeCreature(it)) } })
        s.egg?.let { put("egg", encodeEgg(it)) }
        put("lifetime", encodeProfile(s.lifetime))
    }

    private fun decodeState(o: JSONObject, collection: Collection, room: RoomState): GameState {
        val creatures = mutableListOf<Creature>()
        o.optJSONArray("creatures")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { decodeCreature(it) }?.let { creatures += it }
            }
        }
        val activeId = if (o.has("activeId")) o.optString("activeId", "") else ""
        return GameState(
            creatures = creatures.take(GameRules.MAX_SLOTS).toMutableList(),
            activeId = activeId.ifEmpty { null },
            egg = o.optJSONObject("egg")?.let { decodeEgg(it) },
            lifetime = o.optJSONObject("lifetime")?.let { decodeProfile(it) } ?: TraitEngine.Profile(),
            collection = collection,
            room = room,
        )
    }

    // ── 도감 · 방 ──

    private fun encodeCollection(c: Collection): JSONObject = JSONObject().apply {
        put("v", 1)
        put("counts", JSONObject().apply { c.counts.forEach { (k, n) -> put(k, n) } })
        put("equipped", JSONObject().apply {
            c.equipped.forEach { (id, slots) ->
                put(id, JSONObject().apply { slots.forEach { (slot, t) -> put(slot.name, t) } })
            }
        })
    }

    private fun decodeCollection(o: JSONObject): Collection {
        val c = Collection()
        o.optJSONObject("counts")?.let { m ->
            m.keys().forEach { k -> c.counts[k] = m.optInt(k, 0).coerceAtLeast(0) }
        }
        o.optJSONObject("equipped")?.let { m ->
            m.keys().forEach { id ->
                val slots = mutableMapOf<Slot, Int>()
                m.optJSONObject(id)?.let { so ->
                    Slot.values().forEach { s -> if (so.has(s.name)) slots[s] = so.optInt(s.name, 0) }
                }
                if (slots.isNotEmpty()) c.equipped[id] = slots
            }
        }
        return c
    }

    private fun encodeRoom(r: RoomState): JSONObject = JSONObject().apply {
        put("v", 2)
        r.theme?.let { put("theme", it) }
        put("placed", JSONArray().apply {
            r.placed.forEach { p ->
                put(JSONObject().put("id", p.id).put("x", p.x.toDouble()).put("y", p.y.toDouble()))
            }
        })
    }

    private fun decodeRoom(o: JSONObject): RoomState {
        if (o.optInt("v", 1) >= 2) {
            val placed = mutableListOf<Placed>()
            o.optJSONArray("placed")?.let { a ->
                for (i in 0 until a.length()) {
                    val p = a.optJSONObject(i) ?: continue
                    val id = RENAMED_ITEMS[p.optString("id", "")] ?: p.optString("id", "")
                    if (id.isEmpty()) continue
                    placed += Placed(id, p.optDouble("x", 0.5).toFloat(), p.optDouble("y", 0.9).toFloat())
                }
            }
            return RoomState(o.optString("theme", "").ifEmpty { null }, placed)
        }
        // v1 (자리 고정 방식) → 자유 배치로 옮기기: 왼쪽·오른쪽 소품을 기본 자리 1·2 에 둡니다.
        val placed = mutableListOf<Placed>()
        listOf("LEFT_FLOOR" to 0, "RIGHT_FLOOR" to 1).forEach { (key, spot) ->
            val id = o.optString(key, "").let { RENAMED_ITEMS[it] ?: it }
            if (id.isNotEmpty() && id != "none") {
                val (x, y) = RoomCatalog.SPOTS[spot]
                placed += Placed(id, x, y)
            }
        }
        if (!o.has("LEFT_FLOOR")) placed.add(0, Placed("item_plant", RoomCatalog.SPOTS[0].first, RoomCatalog.SPOTS[0].second))
        return RoomState(o.optString("THEME", "").ifEmpty { null }, placed)
    }

    private fun encodeCreature(c: Creature): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("code", GeneCode.encode(c.genome))
        put("regionKm", encodeRegionKm(c.regionKm))
        put("totalKm", c.totalKm.toDouble())
        put("born", c.bornAtMs)
    }

    private fun decodeCreature(o: JSONObject): Creature? {
        val genome = decodeGenome(o.optString("code", "")) ?: return null
        return Creature(
            id = o.optString("id", "").ifEmpty { return null },
            genome = genome,
            regionKm = decodeRegionKm(o.optJSONObject("regionKm")),
            totalKm = o.optDouble("totalKm", 0.0).toFloat(),
            bornAtMs = o.optLong("born", 0L),
        )
    }

    private fun encodeEgg(e: Egg): JSONObject = JSONObject().apply {
        put("generation", e.generation)
        put("km", e.km.toDouble())
        put("parents", JSONArray().apply { e.parents.forEach { put(GeneCode.encode(it)) } })
        put("incubation", encodeProfile(e.incubation))
        put("regionKm", encodeRegionKm(e.regionKm))
    }

    private fun decodeEgg(o: JSONObject): Egg {
        val parents = mutableListOf<Genome>()
        o.optJSONArray("parents")?.let { arr ->
            for (i in 0 until arr.length()) decodeGenome(arr.optString(i, ""))?.let { parents += it }
        }
        return Egg(
            parents = if (parents.size == 2) parents else emptyList(),
            generation = o.optInt("generation", 1),
            km = o.optDouble("km", 0.0).toFloat(),
            incubation = o.optJSONObject("incubation")?.let { decodeProfile(it) } ?: TraitEngine.Profile(),
            regionKm = decodeRegionKm(o.optJSONObject("regionKm")),
        )
    }

    private fun decodeGenome(code: String): Genome? =
        (GeneCode.decode(code) as? GeneCode.Result.Ok)?.genome

    private fun encodeRegionKm(m: Map<Region, Float>): JSONObject = JSONObject().apply {
        m.forEach { (region, km) -> put(region.name, km.toDouble()) }
    }

    private fun decodeRegionKm(o: JSONObject?): MutableMap<Region, Float> {
        val out = mutableMapOf<Region, Float>()
        if (o == null) return out
        Region.values().forEach { r ->
            if (o.has(r.name)) out[r] = o.optDouble(r.name, 0.0).toFloat()
        }
        return out
    }

    private fun encodeProfile(p: TraitEngine.Profile): JSONObject = JSONObject().apply {
        put("totalKm", p.totalKm.toDouble())
        put("driveCount", p.driveCount)
        put("cleanDrives", p.cleanDrives)
        put("harshAccel", p.harshAccel)
        put("harshBrake", p.harshBrake)
        put("sharpSteer", p.sharpSteer)
        put("idleSec", p.idleSec)
        put("wiperSec", p.wiperSec)
        put("nightSec", p.nightSec)
        put("totalSec", p.totalSec)
        put("cleanStreak", p.cleanDriveStreak)
        put("growth", p.growthPoints.toDouble())
    }

    private fun decodeProfile(o: JSONObject) = TraitEngine.Profile(
        totalKm = o.optDouble("totalKm", 0.0).toFloat(),
        driveCount = o.optInt("driveCount", 0),
        cleanDrives = o.optInt("cleanDrives", 0),
        harshAccel = o.optInt("harshAccel", 0),
        harshBrake = o.optInt("harshBrake", 0),
        sharpSteer = o.optInt("sharpSteer", 0),
        idleSec = o.optLong("idleSec", 0L),
        wiperSec = o.optLong("wiperSec", 0L),
        nightSec = o.optLong("nightSec", 0L),
        totalSec = o.optLong("totalSec", 0L),
        cleanDriveStreak = o.optInt("cleanStreak", 0),
        growthPoints = o.optDouble("growth", 0.0).toFloat(),
    )

    companion object {
        private const val TAG = "MCC_Store"
        private const val KEY_STATE = "state_v1"
        private const val KEY_COLLECTION = "collection_v1"
        private const val KEY_ROOM = "room_v1"
        private const val KEY_BROKEN_BACKUP = "state_broken_backup"
        private const val KEY_DEMO_SAVED = "saved"
        /** 그림을 새로 그리면서 바뀐 소품 (예전 저장의 방석·타이어 자리 → 모자 진열대·지구본) */
        private val RENAMED_ITEMS = mapOf("item_cushion" to "item_hatstand", "item_tire" to "item_globe")
    }
}
