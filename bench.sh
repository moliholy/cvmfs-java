#!/bin/bash
set -euo pipefail

JAVA_MOUNT="/tmp/bench_java_fuse"
CPP_MOUNT="/tmp/bench_cpp_java"
JAVA_CACHE="/tmp/bench_java_fuse_cache"
CPP_CACHE="/tmp/bench_cpp_java_cache"
REPO_URL="http://cvmfs-stratum-one.cern.ch/opt/boss"
REPO_FQRN="boss.cern.ch"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_JAR="$SCRIPT_DIR/target/cvmfs-0.3.0-jar-with-dependencies.jar"
JAVA_BIN="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home/bin/java"
JFFI_LIB_DIR="/tmp/jffi_native/jni/Darwin"

cleanup() {
    umount "$JAVA_MOUNT" 2>/dev/null || diskutil unmount force "$JAVA_MOUNT" 2>/dev/null || true
    sudo umount "$CPP_MOUNT" 2>/dev/null || diskutil unmount force "$CPP_MOUNT" 2>/dev/null || true
    kill "${JAVA_PID:-}" 2>/dev/null || true
    sudo kill "${CPP_PID:-}" 2>/dev/null || true
    sleep 1
    rmdir "$JAVA_MOUNT" "$CPP_MOUNT" 2>/dev/null || true
    sudo rm -rf "$CPP_CACHE"
    rm -rf "$JAVA_CACHE"
}
trap cleanup EXIT

if ! command -v hyperfine &>/dev/null; then
    echo "ERROR: hyperfine not found (brew install hyperfine)"
    exit 1
fi

if [ ! -f "$JAVA_JAR" ]; then
    echo "Building Java jar..."
    (cd "$SCRIPT_DIR" && mvn package -q -DskipTests)
fi

if ! command -v cvmfs2 &>/dev/null; then
    echo "ERROR: cvmfs2 not found"
    exit 1
fi

if [ ! -f "$JFFI_LIB_DIR/libjffi-1.2.jnilib" ]; then
    echo "Extracting and signing jffi native library..."
    mkdir -p /tmp/jffi_native
    JFFI_JAR=$(find "$HOME/.m2/repository/com/github/jnr/jffi" -name "jffi-*-native.jar" 2>/dev/null | head -1)
    if [ -z "$JFFI_JAR" ]; then
        echo "ERROR: jffi native jar not found"
        exit 1
    fi
    unzip -o "$JFFI_JAR" "jni/Darwin/*" -d /tmp/jffi_native
    codesign -s - /tmp/jffi_native/jni/Darwin/libjffi-1.2.jnilib
fi

cleanup 2>/dev/null || true
mkdir -p "$JAVA_MOUNT" "$CPP_MOUNT" "$JAVA_CACHE" "$CPP_CACHE" /var/run/cvmfs

echo "Mounting Java cvmfs-java..."
"$JAVA_BIN" --enable-native-access=ALL-UNNAMED \
    -Djava.library.path="$JFFI_LIB_DIR" \
    -jar "$JAVA_JAR" "$REPO_URL" "$JAVA_MOUNT" "$JAVA_CACHE" 2>/dev/null &
JAVA_PID=$!
sleep 4

if ! stat "$JAVA_MOUNT/testfile" &>/dev/null; then
    echo "ERROR: Java mount failed"
    kill $JAVA_PID 2>/dev/null || true
    exit 1
fi
echo "  OK: $JAVA_MOUNT"

echo "Mounting C++..."
cat > /tmp/cvmfs_bench.local <<EOF
CVMFS_CACHE_BASE=$CPP_CACHE
CVMFS_SHARED_CACHE=no
CVMFS_HTTP_PROXY=DIRECT
CVMFS_SERVER_URL="http://cvmfs-stratum-one.cern.ch/cvmfs/@fqrn@"
CVMFS_KEYS_DIR=/etc/cvmfs/keys/cern.ch
EOF
sudo cvmfs2 -o config=/tmp/cvmfs_bench.local "$REPO_FQRN" "$CPP_MOUNT" &
CPP_PID=$!
sleep 4

if ! stat "$CPP_MOUNT/testfile" &>/dev/null; then
    echo "ERROR: C++ mount failed"
    kill $JAVA_PID 2>/dev/null || true
    sudo kill $CPP_PID 2>/dev/null || true
    exit 1
fi
echo "  OK: $CPP_MOUNT"

echo "Warming up..."
for p in / /testfile /database /pacman-3.29 /pacman-3.29/setup.csh /slc4_ia32_gcc34 /pacman-latest.tar.gz; do
    stat "${JAVA_MOUNT}${p}" &>/dev/null || true
    stat "${CPP_MOUNT}${p}" &>/dev/null || true
done
ls "$JAVA_MOUNT/" >/dev/null 2>&1 || true
ls "$CPP_MOUNT/" >/dev/null 2>&1 || true
cat "$JAVA_MOUNT/testfile" >/dev/null 2>&1 || true
cat "$CPP_MOUNT/testfile" >/dev/null 2>&1 || true
find "$JAVA_MOUNT" -maxdepth 3 >/dev/null 2>&1 || true
find "$CPP_MOUNT" -maxdepth 3 >/dev/null 2>&1 || true

# ── Benchmarks ──

CVMFS2_VERSION=$(cvmfs2 --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")
JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1 || echo "?")

echo ""
echo "CVMFS Benchmark: Java ${JAVA_VERSION} (FUSE) vs C++ v${CVMFS2_VERSION} (FUSE)"
echo "Repository: $REPO_FQRN"
echo ""

HYPERFINE_OPTS="--warmup 3 --min-runs 50 --style full"

echo "== stat =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: stat /" "stat $JAVA_MOUNT/" \
    -n "C++:  stat /" "stat $CPP_MOUNT/"

hyperfine $HYPERFINE_OPTS \
    -n "Java: stat /testfile" "stat $JAVA_MOUNT/testfile" \
    -n "C++:  stat /testfile" "stat $CPP_MOUNT/testfile"

hyperfine $HYPERFINE_OPTS \
    -n "Java: stat symlink" "stat $JAVA_MOUNT/pacman-3.29/setup.csh" \
    -n "C++:  stat symlink" "stat $CPP_MOUNT/pacman-3.29/setup.csh"

echo ""
echo "== ls (readdir) =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: ls /" "ls $JAVA_MOUNT/" \
    -n "C++:  ls /" "ls $CPP_MOUNT/"

hyperfine $HYPERFINE_OPTS \
    -n "Java: ls /database" "ls $JAVA_MOUNT/database" \
    -n "C++:  ls /database" "ls $CPP_MOUNT/database"

hyperfine $HYPERFINE_OPTS \
    -n "Java: ls /slc4 (nested)" "ls $JAVA_MOUNT/slc4_ia32_gcc34" \
    -n "C++:  ls /slc4 (nested)" "ls $CPP_MOUNT/slc4_ia32_gcc34"

echo ""
echo "== readlink =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: readlink" "readlink $JAVA_MOUNT/pacman-3.29/setup.csh" \
    -n "C++:  readlink" "readlink $CPP_MOUNT/pacman-3.29/setup.csh"

echo ""
echo "== file read =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: cat testfile (50B)" "cat $JAVA_MOUNT/testfile" \
    -n "C++:  cat testfile (50B)" "cat $CPP_MOUNT/testfile"

hyperfine $HYPERFINE_OPTS \
    -n "Java: cat pacman-latest.tar.gz (836KB)" "cat $JAVA_MOUNT/pacman-latest.tar.gz" \
    -n "C++:  cat pacman-latest.tar.gz (836KB)" "cat $CPP_MOUNT/pacman-latest.tar.gz"

echo ""
echo "== find (recursive traversal) =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: find /pacman-3.29" "find $JAVA_MOUNT/pacman-3.29 -maxdepth 1" \
    -n "C++:  find /pacman-3.29" "find $CPP_MOUNT/pacman-3.29 -maxdepth 1"

hyperfine $HYPERFINE_OPTS \
    -n "Java: find / -maxdepth 3" "find $JAVA_MOUNT -maxdepth 3" \
    -n "C++:  find / -maxdepth 3" "find $CPP_MOUNT -maxdepth 3"

echo ""
echo "== wc (byte count) =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: wc -c testfile" "wc -c $JAVA_MOUNT/testfile" \
    -n "C++:  wc -c testfile" "wc -c $CPP_MOUNT/testfile"

hyperfine $HYPERFINE_OPTS \
    -n "Java: wc -c pacman-latest.tar.gz" "wc -c $JAVA_MOUNT/pacman-latest.tar.gz" \
    -n "C++:  wc -c pacman-latest.tar.gz" "wc -c $CPP_MOUNT/pacman-latest.tar.gz"

echo ""
echo "== md5 (hash full file) =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: md5 testfile" "md5 -q $JAVA_MOUNT/testfile" \
    -n "C++:  md5 testfile" "md5 -q $CPP_MOUNT/testfile"

hyperfine $HYPERFINE_OPTS \
    -n "Java: md5 pacman-latest.tar.gz" "md5 -q $JAVA_MOUNT/pacman-latest.tar.gz" \
    -n "C++:  md5 pacman-latest.tar.gz" "md5 -q $CPP_MOUNT/pacman-latest.tar.gz"

echo ""
echo "== du (recursive stat) =="
hyperfine $HYPERFINE_OPTS \
    -n "Java: du -d 2" "du -d 2 -s $JAVA_MOUNT" \
    -n "C++:  du -d 2" "du -d 2 -s $CPP_MOUNT"

echo ""
echo "Done."
echo "Note: chunked file tests excluded (chunk data GC'd from server)."
