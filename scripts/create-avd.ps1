$ini = @"
avd.ini.encoding=UTF-8
path=C:\Users\ZeeqRyz\.android\avd\aosp_api34.avd
path.rel=avd\aosp_api34.avd
target=android-34
"@
Set-Content -Path "$HOME\.android\avd\aosp_api34.ini" -Value $ini

New-Item -ItemType Directory -Force -Path "$HOME\.android\avd\aosp_api34.avd" | Out-Null

$config = @"
AvdId=aosp_api34
PlayStore.enabled=false
abi.type=x86_64
avd.ini.displayname=AOSP API 34
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.chosenSnapshotFile=
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.arc=false
hw.audioInput=yes
hw.battery=yes
hw.camera.back=virtualscene
hw.camera.front=emulated
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.dPad=no
hw.device.hash2=MD5:2016577e1656e8e7c2adb0fac972beea
hw.device.manufacturer=Generic
hw.device.name=medium_phone
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gyroscope=yes
hw.initialOrientation=portrait
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2400
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=2048
hw.sdCard=yes
hw.sensors.light=yes
hw.sensors.magnetic_field=yes
hw.sensors.orientation=yes
hw.sensors.pressure=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=system-images\android-34\default\x86_64\
runtime.network.latency=none
runtime.network.speed=full
sdcard.size=512M
showDeviceFrame=yes
skin.dynamic=yes
tag.display=default
tag.displaynames=default
tag.id=default
tag.ids=default
target=android-34
vm.heapSize=228
"@
Set-Content -Path "$HOME\.android\avd\aosp_api34.avd\config.ini" -Value $config
Write-Host "AVD aosp_api34 configured successfully."
