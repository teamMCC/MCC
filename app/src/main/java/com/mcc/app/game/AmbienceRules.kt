package com.mcc.app.game

/**
 * 밤·비 연출을 켤지 정하는 규칙 (순수 함수, 테스트 대상).
 *
 * 사용자가 버튼으로 고른 값이 있으면 그것, 없으면 차량 신호를 따릅니다.
 * 차량 신호가 바뀌는 순간 고른 값은 지워집니다 (MainActivity.updateAmbience):
 * 와이퍼를 켜면 비가 켜지고, 끄면 꺼집니다.
 */
object AmbienceRules {
    fun effective(choice: Boolean?, vehicle: Boolean?): Boolean = choice ?: (vehicle == true)
}
