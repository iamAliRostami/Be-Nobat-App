@echo off
set ADB="D:\Android\sdk\platform-tools\adb.exe"
for /f "tokens=1,2" %%A in ('%ADB% devices') do (
    if "%%B"=="device" (
        %ADB% -s %%A reverse tcp:8080 tcp:8080
        echo Port 8080 forwarded for: %%A
    )
)
