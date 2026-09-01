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

`config/codeperms/config.json`, поле `defaultNodes`: список нод, которые есть у каждого игрока без
записи. Заводское значение: `codeperms.me`. Эти ноды спрашиваются последними, после личных нод,
выданных групп и группы по умолчанию, поэтому любая явная правка их перебивает.

## Вес в реестре сервисов ядра

`config/codeperms/config.json`, поле `servicePriority`: `BUILTIN`, `ADDON` или `OVERRIDE`,
по умолчанию `ADDON`. Мод с большим весом забирает `PermissionService` у мода с меньшим.
Непонятное значение заменяется на `ADDON` с записью в лог. Вес читается один раз при загрузке мода:
`/perms reload` его не меняет, нужен перезапуск сервера.
