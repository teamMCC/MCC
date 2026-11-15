package com.mcc.app.game

import com.mcc.app.region.Region
import com.mcc.app.trait.Genetics
import com.mcc.app.trait.Genome
import com.mcc.app.trait.TraitEngine
import com.mcc.app.vehicle.DrivingSession
import kotlin.random.Random

/**
 * 내가 키우는 캐릭터 한 마리.
 *
 * @param genome   유전 정보. 지역 악세사리를 얻으면 새 genome 으로 바뀝니다.
 * @param regionKm 이 캐릭터가 대표일 때 지역별로 달린 거리. 20km 가 되면 그 지역 악세사리.
 */
class Creature(
    val id: String,
    var genome: Genome,
    val regionKm: MutableMap<Region, Float> = mutableMapOf(),
    var totalKm: Float = 0f,
    val bornAtMs: Long = 0L,
)

/**
 * 알. 30km 를 실제로 달려야 부화합니다.
 *
 * @param parents    부모 둘. 첫 알(시작 알)은 부모가 없습니다.
 * @param incubation 품는 동안의 운전 기록 — 이게 부화 확률을 바꿉니다.
 * @param regionKm   품는 동안 지역별로 달린 거리 — 20km 넘은 지역 악세사리는 반드시 가지고 태어납니다.
 */
class Egg(
    val parents: List<Genome>,
    val generation: Int,
    var km: Float = 0f,
    val incubation: TraitEngine.Profile = TraitEngine.Profile(),
    val regionKm: MutableMap<Region, Float> = mutableMapOf(),
) {
    val progress: Float get() = TraitEngine.hatchProgress(km)
    val readyToHatch: Boolean get() = km >= TraitEngine.HATCH_DISTANCE_KM
}

/**
 * 저장되는 전체 상태.
 * collection·room 은 기존 저장 형식(state_v1)을 건드리지 않도록 별도 키에 저장합니다.
 */
class GameState(
    val creatures: MutableList<Creature> = mutableListOf(),
    var activeId: String? = null,
    var egg: Egg? = null,
    val lifetime: TraitEngine.Profile = TraitEngine.Profile(),
    val collection: Collection = Collection(),
    val room: RoomState = RoomState(),
) {
    /** 대표 캐릭터 — 운전할 때 지역 거리를 쌓는 캐릭터. */
    val active: Creature?
        get() = creatures.firstOrNull { it.id == activeId } ?: creatures.firstOrNull()

    fun find(id: String): Creature? = creatures.firstOrNull { it.id == id }
}

/**
 * 게임 규칙. 화면(Android)과 분리해 두어 단위 테스트로 검증합니다.
 */
object GameRules {

    /** 캐릭터 보관 자리. */
    const val MAX_SLOTS = 7

    /**
     * 캐릭터도 알도 없으면 시작 알을 하나 줍니다. (첫 실행, 또는 모두 놓아준 뒤)
     * @return 새로 줬으면 true
     */
    fun ensureStarterEgg(state: GameState): Boolean {
        if (state.creatures.isEmpty() && state.egg == null) {
            state.egg = Egg(parents = emptyList(), generation = 1)
            return true
        }
        return false
    }

    /**
     * 주행 1회를 반영합니다.
     *   1) 알이 없을 때만: 대표 캐릭터에 지역 거리를 쌓고, 20km 넘은 지역의 악세사리를 줍니다
     *   2) 알에 운전 기록과 거리를 쌓고, 30km 가 되면 부화시킵니다
     *
     * @return 화면에 띄울 사건 메시지
     */
    fun applyDrive(
        state: GameState,
        session: DrivingSession,
        rng: Random,
        nowMs: Long,
        newId: () -> String,
    ): List<String> {
        val events = mutableListOf<String>()
        TraitEngine.accumulate(state.lifetime, session)

        // 알을 품는 동안에는 주행이 알에만 쌓입니다. 이미 있는 캐릭터의 외형(악세사리)은 바뀌지 않습니다.
        if (state.egg == null) state.active?.let { creature ->
            creature.totalKm += session.distanceKm
            session.regionKm.forEach { (region, km) ->
                val after = (creature.regionKm[region] ?: 0f) + km
                creature.regionKm[region] = after
                if (after >= Genetics.REGION_UNLOCK_KM && region !in creature.genome.accessories) {
                    creature.genome = creature.genome.copy(
                        accessories = creature.genome.accessories + region,
                    )
                    CollectionRules.recordAccessory(state.collection, region)
                    events += "★ ${region.label} 시그니처 획득 — ${region.accessory} (${Texts.name(creature, state)})"
                }
            }
        }

        state.egg?.let { egg ->
            TraitEngine.accumulate(egg.incubation, session)
            egg.km += session.distanceKm
            session.regionKm.forEach { (region, km) ->
                egg.regionKm[region] = (egg.regionKm[region] ?: 0f) + km
            }
        }

        // 30km 를 채워도 저절로 깨지지 않습니다. 운전자가 알을 톡톡 눌러 깨웁니다 (tryHatch).
        if (state.egg?.readyToHatch == true) events += READY_MESSAGE
        return events
    }

    /** 알이 30km 를 채웠을 때 주행 기록에 남기는 안내 */
    const val READY_MESSAGE = "알이 깨어나려 해요! 홈에서 알을 톡톡 눌러 깨워 주세요."

    /**
     * 30km 를 채운 알을 부화시킵니다 (홈에서 알을 눌러 깨웠을 때). 자리가 없으면 기다립니다.
     */
    fun tryHatch(state: GameState, rng: Random, nowMs: Long, newId: () -> String): String? {
        val egg = state.egg ?: return null
        if (!egg.readyToHatch) return null
        if (state.creatures.size >= MAX_SLOTS) {
            return "부화할 자리가 없어요. 하나를 놓아주면 바로 부화해요."
        }
        val genome = Genetics.hatch(egg.parents, egg.generation, egg.incubation, egg.regionKm, rng)
        val baby = Creature(
            id = newId(),
            genome = genome,
            regionKm = egg.regionKm.toMutableMap(),
            totalKm = 0f,
            bornAtMs = nowMs,
        )
        state.creatures += baby
        state.activeId = baby.id
        CollectionRules.recordBirth(state.collection, genome)
        state.egg = null
        return "★ 알이 부화했습니다! — ${Texts.traitsLine(genome)}"
    }

    /** 알을 만들 수 없는 이유. 만들 수 있으면 null. */
    fun breedBlockReason(state: GameState, parentCount: Int): String? = when {
        state.egg != null ->
            "이미 품고 있는 알이 있습니다. 부화한 뒤에 만들 수 있어요."
        state.creatures.size >= MAX_SLOTS ->
            "자리가 가득 찼어요 (${MAX_SLOTS}/${MAX_SLOTS}). 하나를 놓아주세요."
        parentCount < 2 ->
            "부모가 둘 필요해요 (지금 ${parentCount}명)."
        parentCount > 2 ->
            "부모는 둘만 고를 수 있습니다. (지금 ${parentCount}명)"
        else -> null
    }

    /** 두 부모로 알을 만듭니다. 호출 전에 breedBlockReason 으로 확인하세요. */
    fun breed(state: GameState, a: Genome, b: Genome): Egg {
        check(breedBlockReason(state, 2) == null) { breedBlockReason(state, 2) ?: "" }
        val egg = Egg(parents = listOf(a, b), generation = Genetics.childGeneration(a, b))
        state.egg = egg
        return egg
    }

    /** 알이 없고 자리가 남으면 부모 없는 기본 알을 받습니다. 못 받으면 이유를 돌려줍니다. */
    fun takeBasicEgg(state: GameState): String? = when {
        state.egg != null -> "이미 품고 있는 알이 있습니다. 부화한 뒤에 받을 수 있어요."
        state.creatures.size >= MAX_SLOTS -> "자리가 가득 찼어요 (${MAX_SLOTS}/${MAX_SLOTS}). 하나를 놓아주세요."
        else -> {
            state.egg = Egg(parents = emptyList(), generation = 1)
            null
        }
    }

    fun setActive(state: GameState, id: String) {
        if (state.find(id) != null) state.activeId = id
    }

    /** 캐릭터를 놓아줍니다. 되돌릴 수 없습니다. */
    fun release(state: GameState, id: String) {
        state.creatures.removeAll { it.id == id }
        CollectionRules.forget(state.collection, id)
        if (state.activeId == id) state.activeId = state.creatures.firstOrNull()?.id
        ensureStarterEgg(state)
    }
}
