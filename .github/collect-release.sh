#!/usr/bin/env bash
# Что едет в выпуск: серверный и клиентский jar для сервера и игрока, api и dev для тех, кто пишет свой
# провайдер. Универсальный jar остаётся для dev-запусков, исходники и промежуточный preshadow наружу не
# нужны никому.
set -eu

mod="$1"
out="$2"
mkdir -p "$out"

id=$(grep -E '^modId *=' "$mod/gradle.properties" | sed -E 's/.*= *//' | tr -d '\r')
version=$(grep -E '^modVersion *=' "$mod/gradle.properties" | sed -E 's/.*= *//' | tr -d '\r')

for file in "$mod"/build/libs/*.jar; do
  name=$(basename "$file")
  case "$name" in
    *-sources.jar|*-javadoc.jar|*-dev-preshadow.jar) continue ;;
    "$id-$version.jar") continue ;;
  esac
  cp "$file" "$out/"
  echo "в выпуск: $name"
done

ls "$out" >/dev/null
