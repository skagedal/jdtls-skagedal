#!/usr/bin/env bash
# Build this fork of eclipse.jdt.ls and symlink its launcher into ~/local/bin
# so it shadows any system/homebrew-installed jdtls on $PATH.
#
# Usage: ./install.sh [--rebuild]
#
#   --rebuild   Wipe the existing target/ directory before building.
#
# Requirements:
#   * JAVA_HOME pointing at a JDK 21+ (or `java` on PATH with version >= 21)
#   * Network access for Maven to fetch dependencies on first build

set -euo pipefail

repo_dir="$(cd "$(dirname "$0")" && pwd)"
dist_dir="$repo_dir/org.eclipse.jdt.ls.product/target/repository"
launcher="$dist_dir/bin/jdtls"
link_dir="$HOME/local/bin"
link_path="$link_dir/jdtls"

rebuild=0
for arg in "$@"; do
	case "$arg" in
		--rebuild) rebuild=1 ;;
		-h|--help)
			sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
			exit 0
			;;
		*)
			echo "install.sh: unknown argument: $arg" >&2
			exit 2
			;;
	esac
done

if [[ $rebuild -eq 1 ]]; then
	echo "==> Removing existing build at $repo_dir/org.eclipse.jdt.ls.product/target"
	rm -rf "$repo_dir/org.eclipse.jdt.ls.product/target"
fi

if [[ ! -x "$launcher" ]]; then
	echo "==> No existing build found. Running ./mvnw clean verify -DskipTests=true (this takes several minutes)..."
	cd "$repo_dir"
	./mvnw clean verify -DskipTests=true
fi

if [[ ! -x "$launcher" ]]; then
	echo "install.sh: build did not produce $launcher" >&2
	exit 1
fi

mkdir -p "$link_dir"
ln -sfn "$launcher" "$link_path"

echo
echo "Installed: $link_path -> $launcher"
echo
echo "Verify with:  $link_path --help  (or: jdtls --help, if ~/local/bin is on PATH)"
