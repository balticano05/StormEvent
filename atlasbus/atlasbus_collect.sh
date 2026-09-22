#!/usr/bin/env bash
set -euo pipefail

BASE='https://atlasbus.by'
LOCALE='ru'
DELAY="${DELAY:-1}"
MIN_RESULTS=20

LETTERS=(А Б В Г Д Е Ё Ж З И Й К Л М Н О П Р С Т У Ф Х Ц Ч Ш Щ Ъ Ы Ь Э Ю Я)
LOW_CASE=(а б в г д е ё ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ы ь э ю я)

OUT_BY_LETTER='stations_by_letter.json'
OUT_ALL='stations_all.json'
OUT_CSV='stations_by_letter.csv'

HEADERS=(
  -H 'Accept: application/json, text/plain, */*'
  -H "Accept-Language: ${LOCALE}"
  -H 'X-Application-Source: web'
  -H 'X-Application-Version: 2.63.2'
  -H 'X-SAAS-Partner-Id: atlas'
  -H "Cookie: next-i18next=${LOCALE}"
  -H "Referer: ${BASE}/"
)

TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"' EXIT

BODY="$TMPD/body.json"

# fetch <input> -> prints HTTP code on stdout, response body -> $BODY
fetch() {
  local input="$1" enc
  enc=$(printf '%s' "$input" | jq -sRr @uri)
  curl -s --max-time 30 -o "$BODY" -w '%{http_code}' \
    "${BASE}/api/search/suggest?user_input=${enc}&from_id=&to_id=&locale=${LOCALE}" \
    "${HEADERS[@]}"
}

# filter + return objects whose name starts with <prefix> (case-insensitive)
filter_by_prefix() {
  local prefix="$1"
  jq --arg P "$prefix" \
    '[.[]? | select((.name // "" | ascii_downcase) | startswith(($P | ascii_downcase)))]' \
    "$BODY"
}

# body is a valid JSON array?
body_is_array() {
  jq -e 'type == "array"' "$BODY" >/dev/null 2>&1
}

# resume: не перезаписываем уже собранные буквы
if [[ ! -s "$OUT_BY_LETTER" ]]; then
  echo '{}' > "$OUT_BY_LETTER"
fi

TOTAL_FOUND=0
TOTAL_UNIQ=0
LETTERS_OK=0

for L in "${LETTERS[@]}"; do
  if jq -e --arg L "$L" 'has($L)' "$OUT_BY_LETTER" >/dev/null 2>&1; then
    echo "=== [$L] ===  (уже собран, пропуск)"
    continue
  fi
  T0=$(date +%s%N)
  REQ=0
  FOUND=0
  ACC='[]'
  echo "=== [$L] ==="

  CODE=$(fetch "$L"); REQ=$((REQ + 1))
  if [[ "$CODE" != "200" ]]; then
    echo "  [!] HTTP $CODE, тело: $(head -c 400 "$BODY")" >&2
  elif ! body_is_array; then
    echo "  [!] не JSON / пустой ответ для буквы '$L'" >&2
  else
    NEW=$(filter_by_prefix "$L")
    FOUND=$((FOUND + $(jq 'length' <<<"$NEW")))
    ACC=$(jq -nc --argjson a "$ACC" --argjson b "$NEW" '$a + $b')
  fi
  sleep "$DELAY"

  UNIQ=$(jq -n --argjson a "$ACC" '$a | unique_by(.id) | length')

  if (( UNIQ < MIN_RESULTS )); then
    echo "  мало результатов ($UNIQ < $MIN_RESULTS) — уточняющие префиксы"
    for lo in "${LOW_CASE[@]}"; do
      P="${L}${lo}"
      CODE=$(fetch "$P"); REQ=$((REQ + 1))
      if [[ "$CODE" != "200" ]]; then
        echo "    [!] HTTP $CODE для '$P', тело: $(head -c 200 "$BODY")" >&2
        sleep "$DELAY"
        continue
      fi
      if ! body_is_array; then
        echo "    [!] не JSON для '$P'" >&2
        sleep "$DELAY"
        continue
      fi
      NEW=$(filter_by_prefix "$P")
      FOUND=$((FOUND + $(jq 'length' <<<"$NEW")))
      ACC=$(jq -nc --argjson a "$ACC" --argjson b "$NEW" '$a + $b')
      sleep "$DELAY"
    done
  fi

  BY_LETTER=$(jq 'unique_by(.id)' <<<"$ACC")
  U=$(jq 'length' <<<"$BY_LETTER")

  jq --arg L "$L" --argjson arr "$BY_LETTER" '.[$L] = $arr' "$OUT_BY_LETTER" > "$TMPD/tmp.json"
  mv "$TMPD/tmp.json" "$OUT_BY_LETTER"

  T1=$(date +%s%N)
  EL=$(awk -v a="$T0" -v b="$T1" 'BEGIN { printf "%.1f", (b - a) / 1e9 }')

  echo "  [$L] запросов=$REQ найдено=$FOUND уникальных=$U время=${EL}s"
  TOTAL_FOUND=$((TOTAL_FOUND + FOUND))
  TOTAL_UNIQ=$((TOTAL_UNIQ + U))
  LETTERS_OK=$((LETTERS_OK + 1))
done

# Сводный плоский файл: все объекты, дедупликация по id
jq '[.[][]?] | unique_by(.id)' "$OUT_BY_LETTER" > "$OUT_ALL"

# CSV
jq -r 'to_entries[] | .key as $L | .value[]? |
      [$L, .id, .name, .country, .latitude, .longitude] | @csv' \
  "$OUT_BY_LETTER" > "$OUT_CSV"

echo
echo "========== ИТОГ =========="
echo "Букв (всего в базе):   $(jq 'keys | length' "$OUT_BY_LETTER")"
echo "Букв (в этом запуске): $LETTERS_OK"
echo "Найдено записей:   $TOTAL_FOUND"
echo "Уникальных ($(basename "$OUT_ALL")): $(jq 'length' "$OUT_ALL")"
echo "Топ-5 букв по количеству объектов:"
jq -r 'to_entries |
       map({letter: .key, count: (.value | length)}) |
       sort_by(-.count) | .[0:5][] | "  \(.letter): \(.count)"' "$OUT_BY_LETTER"

echo
echo "Валидация JSON:"
jq . "$OUT_BY_LETTER" >/dev/null && echo "  OK: $(basename "$OUT_BY_LETTER")"
jq . "$OUT_ALL" >/dev/null && echo "  OK: $(basename "$OUT_ALL")"