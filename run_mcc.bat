@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title MCC 실행 도우미

rem ============================================================
rem  MCC 실행 도우미
rem  "Waiting for all target devices to come online" /
rem  "is already running as process ..." 로 막힐 때 씁니다.
rem
rem  1) 끊긴 adb 연결을 다시 붙이고
rem  2) 에뮬레이터가 안 잡히면 남은 에뮬레이터를 끄고 잠금 파일을 지운 뒤 새로 켜고
rem  3) 부팅이 끝나면 app-debug.apk 를 설치해서 MCC 를 켭니다.
rem
rem  먼저 Android Studio 에서 Build > Build App Bundle(s) / APK(s) > Build APK(s) 를 한 번 하세요.
rem ============================================================

set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMU=%SDK%\emulator\emulator.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "PKG=com.mcc.app"

if not exist "%ADB%" (
  echo [오류] adb 를 찾을 수 없습니다: %ADB%
  echo        Android Studio ^> Settings ^> Languages ^& Frameworks ^> Android SDK 의 경로를 확인하세요.
  goto :end
)

echo.
echo [1/4] adb 연결을 다시 시작합니다...
"%ADB%" kill-server >nul 2>&1
"%ADB%" start-server >nul 2>&1

"%ADB%" get-state 2>nul | findstr /b "device" >nul
if not errorlevel 1 (
  echo       에뮬레이터가 연결되어 있습니다.
  goto :booted
)

echo.
echo [2/4] 에뮬레이터가 잡히지 않아 새로 켭니다...
taskkill /f /im qemu-system-x86_64.exe >nul 2>&1
taskkill /f /im qemu-system-x86_64-headless.exe >nul 2>&1
taskkill /f /im emulator.exe >nul 2>&1
timeout /t 2 >nul
for /d %%d in ("%USERPROFILE%\.android\avd\*.avd") do (
  del /f /q "%%d\*.lock" >nul 2>&1
  for /d %%l in ("%%d\*.lock") do rmdir /s /q "%%l" >nul 2>&1
)

set "AVD="
for /f "delims=" %%a in ('""%EMU%" -list-avds" 2^>nul') do (
  if not defined AVD set "AVD=%%a"
  echo %%a | findstr /i "Automotive 1408" >nul && set "AVD=%%a"
)
if not defined AVD (
  echo [오류] 에뮬레이터^(AVD^)가 없습니다. Device Manager 에서 Automotive 기기를 먼저 만드세요.
  goto :end
)
echo       에뮬레이터: !AVD!  ^(처음부터 켜는 중, 2~5분^)
start "MCC emulator" "%EMU%" -avd "!AVD!" -no-snapshot-load

"%ADB%" wait-for-device

:booted
echo.
echo [3/4] 부팅이 끝나기를 기다립니다...
set /a TRIES=0
:waitboot
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr /b "1" >nul
if not errorlevel 1 goto :install
set /a TRIES+=1
if !TRIES! geq 120 (
  echo [오류] 6분이 지나도 부팅이 끝나지 않았습니다.
  echo        Device Manager ^> Automotive ^> ⋮ ^> Wipe Data 후 다시 실행하세요.
  goto :end
)
timeout /t 3 >nul
goto :waitboot

:install
echo       부팅 완료.
echo.
if not exist "%APK%" (
  echo [안내] 설치할 앱 파일이 아직 없습니다:
  echo        %APK%
  echo        Android Studio 에서 Build ^> Build App Bundle^(s^) / APK^(s^) ^> Build APK^(s^) 를 누른 뒤
  echo        이 파일을 다시 실행하세요. 에뮬레이터는 켜진 채로 두면 됩니다.
  goto :end
)
echo [4/4] MCC 를 설치하고 켭니다...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo [오류] 설치에 실패했습니다. 위 메시지를 캡처해 주세요.
  goto :end
)
"%ADB%" shell am start -n %PKG%/.MainActivity >nul
echo.
echo 완료. 에뮬레이터 화면에 MCC 가 켜졌습니다.
echo 이제 Android Studio 위쪽 기기 목록에도 emulator-5554 가 보이니 ▶ Run 도 다시 쓸 수 있습니다.

:end
echo.
pause
