# Права CodePerms

Полный перечень нод прав мода. Источник истины при расхождении: класс
`PermsPermissions` в коде. Сюда же вносятся новые ноды тем же коммитом, что и код.

Ноды прав и ключи перевода не пересекаются: подсказки лежат под `codeperms.command.usage.*`,
сообщения под `codeperms.message.*`, всё остальное под `codeperms.` это ноды из таблиц ниже.

## Личное

| Нода | Команда | Что даёт |
|---|---|---|
| `codeperms.me` | `/perms me` | посмотреть свои группы, мета и срок временных выдач |

## Группы

| Нода | Команда | Что даёт |
|---|---|---|
| `codeperms.group.list` | `/perms group list` | список групп |
| `codeperms.group.info` | `/perms group info <id>` | группа: вес, родители, ноды, мета, участники |
| `codeperms.group.create` | `/perms group create` | создать группу |
| `codeperms.group.delete` | `/perms group delete` | удалить группу |
| `codeperms.group.rename` | `/perms group rename` | переименовать группу |
| `codeperms.group.copy` | `/perms group copy` | копировать группу |
| `codeperms.group.setweight` | `/perms group setweight` | сменить вес группы |
| `codeperms.group.edit` | `/perms group parent\|node\|meta ...` | родители, ноды и мета группы |

## Игроки

| Нода | Команда | Что даёт |
|---|---|---|
| `codeperms.player.info` | `/perms player info <ник>` | игрок: группы, ноды, мета, срок выдач |
| `codeperms.player.setgroup` | `/perms player setgroup` | задать primary и набор групп |
| `codeperms.player.addgroup` | `/perms player addgroup` | выдать группу (можно со сроком) |
| `codeperms.player.rmgroup` | `/perms player rmgroup` | снять группу |
| `codeperms.player.node` | `/perms player node` | выдать или снять личную ноду (можно со сроком) |
| `codeperms.player.meta` | `/perms player meta` | сменить личную мета |
| `codeperms.player.cleanup` | `/perms player cleanup` | вычистить истёкшие выдачи |

## Треки

| Нода | Команда | Что даёт |
|---|---|---|
| `codeperms.track.list` | `/perms track list` | список треков |
| `codeperms.track.info` | `/perms track info` | состав трека |
| `codeperms.track.promote` | `/perms track promote` | поднять игрока по треку |
| `codeperms.track.demote` | `/perms track demote` | опустить игрока по треку |

## Обслуживание

| Нода | Команда | Что даёт |
|---|---|---|
| `codeperms.import` | `/perms import [--dry-run] [--force]` | перенос прав из файла ядра |
| `codeperms.export` | `/perms export` | выгрузка в формат ядра в свой каталог |
| `codeperms.reload` | `/perms reload` | перечитать файлы прав |
| `codeperms.debug` | `/perms debug <игрок> <нода>` | таблица кандидатов и победитель проверки |

## Дефолтные выдачи

`config/code/permissions/perms.toml`, поле `defaultNodes`: список нод, которые есть у каждого игрока
без записи. Заводское значение: `codeperms.me`. Эти ноды спрашиваются последними, после личных нод,
выданных групп и группы по умолчанию, поэтому любая явная правка их перебивает.

## Кто держит права

`config/code/config.toml`, секция `[owners]`, ключ `permissions`: `auto` отдаёт роль CodePerms, если
он стоит на сервере, `off` оставляет сервер без прав, имя владельца (`codeperms`, `luckperms`)
называет его прямо. Своего поля веса у мода больше нет: вес регистрации выставляет реестр адаптеров
ядра. Незнакомое имя уходит по правилу `auto` с предупреждением в лог. Владелец выбирается один раз
при загрузке модов: `/perms reload` его не меняет, нужен перезапуск сервера.
