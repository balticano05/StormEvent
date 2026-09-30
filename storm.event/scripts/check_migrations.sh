#!/usr/bin/env bash
# Проверка миграций StormEvent до коммита (шаг 299 плана).
# Ловит то, что не ловит компиляция: неверные имена файлов, дубли версий,
# разрушительный DDL в истории и «забытое» партиционирование.
# Глубокую проверку схемы делает SchemaConstraintTest на живой базе.
set -euo pipefail

# Пути считаем от расположения скрипта, иначе проверка зависит от того,
# из какого каталога её запустили.
module_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MIGRATIONS="storm.event/src/main/resources/db/migration"
migrations_dir="$module_root/src/main/resources/db/migration"
errors=0

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  errors=$((errors + 1))
}

if [[ ! -d "$migrations_dir" ]]; then
  fail "нет каталога $MIGRATIONS"
  exit 1
fi

files=("$migrations_dir"/V*__*.sql)
if [[ ${#files[@]} -eq 0 || ! -e "${files[0]}" ]]; then
  fail "миграций нет"
  exit 1
fi

# 1. Имя файла: V<version>__<описание>.sql, версия — целое число без ведущих нулей.
for file in "${files[@]}"; do
  name=$(basename "$file")
  if [[ ! "$name" =~ ^V[1-9][0-9]*__[a-z0-9_]+\.sql$ ]]; then
    fail "$name: ожидается V<version>__<snake_case>.sql"
  fi
  if [[ "${name,,}" == *"${name^^}" && "${name}" =~ [A-Z] ]]; then
    fail "$name: описание миграции должно быть в нижнем регистре"
  fi
done

# 2. Дубли версий Flyway не пропустит — ловим на месте.
versions=$(printf '%s\n' "${files[@]##*/}" | sed -E 's/^V([0-9]+)__.*/\1/' | sort | uniq -d)
if [[ -n "$versions" ]]; then
  fail "дублирующиеся версии миграций: $(echo "$versions" | tr '\n' ' ')"
fi

# 3. Разрушительный DDL в истории: применяется один раз и навсегда.
for file in "${files[@]}"; do
  name=$(basename "$file")
  if grep -nEi '^[[:space:]]*(drop[[:space:]]+table|truncate[[:space:]]|delete[[:space:]]+from)' "$file"; then
    fail "$name: DROP TABLE/TRUNCATE/DELETE FROM в миграции недопустимы"
  fi
done

# 4. Партиционированные таблицы обязаны иметь DEFAULT-партицию (ADR-033).
partitioned=$(grep -lE 'PARTITION BY RANGE' "$migrations_dir"/V*__*.sql | tr '\n' ' ')
if [[ -n "$partitioned" ]]; then
  for file in $partitioned; do
    name=$(basename "$file")
    if ! grep -qE 'PARTITION OF .* DEFAULT' "$file"; then
      fail "$name: партиционированные таблицы без DEFAULT-партиции"
    fi
  done
fi

# 5. Новые таблицы должны попасть в список очистки тестов (PostgresTestSupport).
support="storm.event/src/test/java/com/workspace/storm/event/db/support/PostgresTestSupport.java"
support_file="$module_root/src/test/java/com/workspace/storm/event/db/support/PostgresTestSupport.java"
for file in "${files[@]}"; do
  created=$(grep -oE 'CREATE TABLE (IF NOT EXISTS )?storm\.[a-z_]+( PARTITION OF)?' "$file" \
    | grep -v 'PARTITION OF' | sed -E 's/.*storm\.([a-z_]+).*/\1/' | sort -u || true)
  for table in $created; do
    if ! grep -q "storm\.${table}" "$support_file"; then
      fail "таблица storm.${table} не попала в очистку тестов ($support)"
    fi
  done
done

if [[ $errors -gt 0 ]]; then
  printf '\n%s проблем в миграциях\n' "$errors" >&2
  exit 1
fi

printf 'OK: %d миграций, нарушений нет\n' "${#files[@]}"
