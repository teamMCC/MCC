# 제출용 아이콘

| 파일 | 크기 | 용도 |
|---|---|---|
| `icon_512.png` | 512×512 | 앱 마켓·제출용 대표 아이콘 (1MB 이하) |
| `icon_foreground_432.png` | 432×432 | 적응형 아이콘 앞 레이어 (투명 배경, 알이 가운데 안전 영역 안) |
| `icon_background_432.png` | 432×432 | 적응형 아이콘 뒤 레이어 (살구 #F7C59F) |

앱 안에 들어가는 아이콘은 `app/src/main/res/mipmap-*/ic_launcher_foreground.png` 와
`res/mipmap-anydpi-v26/ic_launcher.xml` (배경색은 `res/values/ic_launcher_colors.xml`) 입니다.
