import os
import sys
import subprocess
import time

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

PROJECT_ROOT = r"c:\Users\fekka\Documents\code\real project\apps\monespacemt reverse engineering"
ANDROID_DIR = os.path.join(PROJECT_ROOT, "android_app")
JDK_DIR = os.path.expanduser(r"~\.jdk\jdk-17.0.12+7")
SDK_DIR = os.path.expanduser(r"~\.android-sdk")
ADB_EXE = r"C:\platform-tools\adb.exe"

os.environ["JAVA_HOME"] = JDK_DIR
os.environ["ANDROID_HOME"] = SDK_DIR
os.environ["PATH"] = f"{os.path.join(JDK_DIR, 'bin')};{os.path.join(SDK_DIR, 'platform-tools')};C:\\platform-tools;{os.environ.get('PATH', '')}"

GRADLE_BIN = os.path.expanduser(r"~\.jdk\gradle-8.10.2\bin\gradle.bat")
apk_path = os.path.join(ANDROID_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")

start_time = time.time()
print(f"🚀 [1/3] Compiling Android APK with local Gradle & SDK 36...")
cmd = [GRADLE_BIN, "assembleDebug", "--build-cache", "--parallel", "-x", "test", "-x", "lint"]
ret = subprocess.run(cmd, cwd=ANDROID_DIR, capture_output=True, text=True)

if ret.returncode != 0:
    print(ret.stdout)
    print(ret.stderr)
    print("\n❌ [ERROR] Compilation failed.")
    sys.exit(ret.returncode)

build_time = time.time() - start_time
print(f"⚡ [2/3] Compiled APK in {build_time:.1f}s")

if os.path.exists(ADB_EXE):
    print(f"📲 [3/3] Deploying to Samsung device via ADB...")
    devices_out = subprocess.run([ADB_EXE, "devices"], capture_output=True, text=True).stdout
    devices = [line.split()[0] for line in devices_out.strip().split('\n')[1:] if '\tdevice' in line]
    target_args = ["-s", devices[0]] if devices else []

    install_res = subprocess.run([ADB_EXE] + target_args + ["install", "-r", apk_path], capture_output=True, text=True)
    
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in install_res.stderr or "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in install_res.stdout:
        print("Re-aligning signatures (uninstalling older package)...")
        subprocess.run([ADB_EXE] + target_args + ["uninstall", "com.telecom.widget"], capture_output=True)
        install_res = subprocess.run([ADB_EXE] + target_args + ["install", "-r", apk_path], capture_output=True, text=True)

    if "Success" in install_res.stdout:
        subprocess.run([ADB_EXE] + target_args + ["shell", "am", "start", "-n", "com.telecom.widget/.MainActivity"], capture_output=True)
        total_time = time.time() - start_time
        print(f"\n🎉 [COMPLETE] Built, deployed, and launched on phone in {total_time:.1f} seconds total!")
    else:
        print(install_res.stdout)
        print(install_res.stderr)
