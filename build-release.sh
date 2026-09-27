#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
workspace_dir="$(cd "$project_dir/.." && pwd)"
sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$workspace_dir/.android-build/sdk}}"
build_tools_version="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
build_tools="$sdk_dir/build-tools/$build_tools_version"
android_jar="$sdk_dir/platforms/android-35/android.jar"

: "${BUDS_LOCK_KEYSTORE:?Set BUDS_LOCK_KEYSTORE to the release keystore path}"
: "${BUDS_LOCK_KEY_ALIAS:?Set BUDS_LOCK_KEY_ALIAS}"
: "${BUDS_LOCK_STORE_PASSWORD:?Set BUDS_LOCK_STORE_PASSWORD}"
: "${BUDS_LOCK_KEY_PASSWORD:?Set BUDS_LOCK_KEY_PASSWORD}"

out_dir="$project_dir/build/release"
dist_dir="$project_dir/dist"
generated_dir="$out_dir/generated"
classes_dir="$out_dir/classes"
dex_dir="$out_dir/dex"
compiled_res="$out_dir/compiled-res.zip"
unsigned_apk="$out_dir/resources-unsigned.apk"
unaligned_apk="$out_dir/BudsLock-v2.0-unaligned.apk"
aligned_apk="$out_dir/BudsLock-v2.0-aligned.apk"
final_apk="$dist_dir/BudsLock-v2.0.apk"

for required in aapt2 d8 zipalign apksigner; do
  if [[ ! -x "$build_tools/$required" ]]; then
    echo "Missing $build_tools/$required" >&2
    exit 1
  fi
done
if [[ ! -f "$android_jar" ]]; then
  echo "Missing Android 35 platform: $android_jar" >&2
  exit 1
fi

rm -rf "$out_dir"
mkdir -p "$generated_dir" "$classes_dir" "$dex_dir" "$dist_dir"

"$build_tools/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$compiled_res"
"$build_tools/aapt2" link -o "$unsigned_apk" -I "$android_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
  --java "$generated_dir" --min-sdk-version 24 --target-sdk-version 35 \
  --version-code 20 --version-name 2.0 --auto-add-overlay -R "$compiled_res"

mapfile -t java_sources < <(find "$project_dir/app/src/main/java" -name '*.java' -print | sort)
mapfile -t generated_sources < <(find "$generated_dir" -name '*.java' -print | sort)
compiler=(javac)
if ! command -v javac >/dev/null 2>&1; then
  compiler=(java -m jdk.compiler/com.sun.tools.javac.Main)
fi
"${compiler[@]}" -Xlint:all -source 17 -target 17 -cp "$android_jar" -d "$classes_dir" \
  "${generated_sources[@]}" "${java_sources[@]}"

mapfile -t class_files < <(find "$classes_dir" -name '*.class' -print | sort)
"$build_tools/d8" --release --min-api 24 --lib "$android_jar" \
  --output "$dex_dir" "${class_files[@]}"
cp "$unsigned_apk" "$unaligned_apk"
(cd "$dex_dir" && zip -q -u "$unaligned_apk" classes.dex)
"$build_tools/zipalign" -f 4 "$unaligned_apk" "$aligned_apk"
"$build_tools/apksigner" sign \
  --ks "$BUDS_LOCK_KEYSTORE" --ks-key-alias "$BUDS_LOCK_KEY_ALIAS" \
  --ks-pass "env:BUDS_LOCK_STORE_PASSWORD" --key-pass "env:BUDS_LOCK_KEY_PASSWORD" \
  --out "$final_apk" "$aligned_apk"
"$build_tools/apksigner" verify --verbose --print-certs "$final_apk"
echo "Built $final_apk"
