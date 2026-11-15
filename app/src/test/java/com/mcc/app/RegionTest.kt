package com.mcc.app

import com.mcc.app.region.Region
import com.mcc.app.region.Regions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GPS → 지역 판정.
 * 대표 지점이 아닌, 각 지역 안쪽의 실제 장소들로 검사합니다.
 */
class RegionTest {

    private fun check(name: String, lat: Double, lng: Double, expected: Region) {
        assertEquals(name, expected, Regions.classify(lat, lng))
    }

    @Test
    fun seoul() {
        check("성수동", 37.5445, 127.0559, Region.SEOUL)
        check("목동", 37.5265, 126.8750, Region.SEOUL)
        check("광화문", 37.5759, 126.9768, Region.SEOUL)
    }

    @Test
    fun gyeonggi_incheon() {
        check("수원 영통", 37.2596, 127.0466, Region.GYEONGGI)
        check("일산", 37.6764, 126.7720, Region.GYEONGGI)
        check("분당", 37.3826, 127.1189, Region.GYEONGGI)
        check("인천 부평", 37.5070, 126.7219, Region.GYEONGGI)
    }

    @Test
    fun gangwon() {
        check("남춘천", 37.8640, 127.7230, Region.GANGWON)
        check("경포대", 37.7950, 128.9080, Region.GANGWON)
        check("평창 알펜시아", 37.6560, 128.6720, Region.GANGWON)
    }

    @Test
    fun chungcheong() {
        check("대전 유성", 36.3622, 127.3561, Region.CHUNGCHEONG)
        check("오송역", 36.6200, 127.3270, Region.CHUNGCHEONG)
        check("천안 불당", 36.8130, 127.1060, Region.CHUNGCHEONG)
    }

    @Test
    fun jeolla() {
        check("전주 한옥마을", 35.8150, 127.1530, Region.JEOLLA)
        check("광주 상무지구", 35.1530, 126.8510, Region.JEOLLA)
        check("여수 엑스포", 34.7520, 127.7470, Region.JEOLLA)
    }

    @Test
    fun gyeongsang() {
        check("대구 동성로", 35.8690, 128.5960, Region.GYEONGSANG)
        check("포항 영일대", 36.0560, 129.3780, Region.GYEONGSANG)
        check("창원 상남동", 35.2220, 128.6830, Region.GYEONGSANG)
        check("울산 삼산동", 35.5400, 129.3380, Region.GYEONGSANG)
    }

    @Test
    fun busan() {
        check("서면", 35.1577, 129.0596, Region.BUSAN)
        check("광안리", 35.1532, 129.1186, Region.BUSAN)
        check("해운대 해수욕장", 35.1587, 129.1604, Region.BUSAN)
    }

    @Test
    fun jeju() {
        check("제주공항", 33.5066, 126.4930, Region.JEJU)
        check("중문", 33.2500, 126.4120, Region.JEJU)
        check("성산일출봉", 33.4580, 126.9420, Region.JEJU)
    }

    @Test
    fun outside_korea_is_unknown() {
        assertNull("에뮬레이터 기본 위치 (Mountain View)", Regions.classify(37.3861, -122.0839))
        assertNull("도쿄", Regions.classify(35.6762, 139.6503))
        assertNull("평양", Regions.classify(39.0392, 125.7625))
        assertNull("대마도", Regions.classify(34.2000, 129.2900))
    }

    @Test
    fun every_region_has_anchors() {
        assertTrue("대표 지점 수", Regions.anchorCount > 150)
    }
}
