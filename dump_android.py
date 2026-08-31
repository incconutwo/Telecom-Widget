import os
import sys

def main():
    output_path = "android_app_complete_dump.txt"
    allowed_exts = {".kt", ".java", ".xml", ".gradle", ".kts", ".json", ".properties"}
    exclude_dirs = {"build", ".gradle", ".idea", "captures", ".cxx", ".externalNativeBuild", "gradle"}
    exclude_files = {"gradle-wrapper.jar"}

    total_files = 0
    with open(output_path, "w", encoding="utf-8") as out:
        out.write("=======================================================\n")
        out.write("  TELECOM WIDGET ANDROID APP - COMPLETE CODEBASE DUMP  \n")
        out.write("=======================================================\n\n")

        for root, dirs, files in os.walk("android_app"):
            dirs[:] = [d for d in dirs if d not in exclude_dirs and not d.startswith('.')]
            for f in sorted(files):
                if f in exclude_files:
                    continue
                ext = os.path.splitext(f)[1].lower()
                if ext in allowed_exts or f in {"gradle.properties", "local.properties", "settings.gradle.kts", "build.gradle.kts"}:
                    file_path = os.path.join(root, f)
                    total_files += 1
                    out.write("=" * 80 + "\n")
                    out.write(f"FILE: {file_path}\n")
                    out.write("=" * 80 + "\n\n")
                    try:
                        with open(file_path, "r", encoding="utf-8", errors="replace") as src:
                            out.write(src.read())
                    except Exception as e:
                        out.write(f"[Error reading file: {e}]\n")
                    out.write("\n\n")

    print(f"Successfully compiled {total_files} Android source files into {output_path}")

if __name__ == "__main__":
    main()
