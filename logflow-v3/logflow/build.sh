#!/usr/bin/env bash
# Maven olmadan derle + test et + jar üret. Kullanım: ./build.sh [test|jar|clean|experiment [satır] [tekrar]]
set -euo pipefail
cd "$(dirname "$0")"
CP="lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar"
case "${1:-test}" in
  clean) rm -rf target; exit 0 ;;
esac
rm -rf target/classes target/test-classes && mkdir -p target/classes target/test-classes
javac -Xlint:all -d target/classes $(find src/main/java -name '*.java')
cp -r src/test/resources/. target/test-classes/ 2>/dev/null || true
javac -Xlint:all -cp "target/classes:$CP" -d target/test-classes $(find src/test/java -name '*.java')
TESTS=$(cd src/test/java && find . -name '*Test.java' | sed 's|^\./||; s|\.java$||; s|/|.|g' | sort | tr '\n' ' ')
java -cp "target/classes:target/test-classes:$CP" org.junit.runner.JUnitCore $TESTS
if [ "${1:-test}" = "experiment" ]; then
  java -Dstdout.encoding=UTF-8 -cp target/classes logflow.experiment.OrderExperiment "${2:-1000000}" "${3:-7}"
fi
if [ "${1:-test}" = "jar" ]; then
  printf 'Main-Class: logflow.cli.Main\n' > target/manifest.txt
  jar cfm target/logflow.jar target/manifest.txt -C target/classes .
  echo "Üretildi: target/logflow.jar"
fi
