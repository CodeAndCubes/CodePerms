# Тестовые векторы сопоставления нод

Общий файл для CodeCore и CodePerms. Обе реализации обязаны отвечать одинаково на каждую строку
таблицы, поэтому расхождение чинится по векторам, а не по памяти.

Семантика: сегменты разделяются точкой, звёздочка в конце покрывает всё, что глубже, звёздочка в
середине заменяет ровно один сегмент, правило без звёздочек подходит только ноде с тем же числом
сегментов. Точность равна числу сегментов без звёздочек. Сравнение регистрозависимое.

Правило со звёздочкой в конце подходит и ноде, которая совпадает с правилом без этой звёздочки:
`codechat.*` покрывает ноду `codechat`. Это поведение ядра, векторы его фиксируют.

Столбцы: правило, нода, подходит ли правило, точность правила.

| pattern | node | matches | specificity |
|---|---|---|---|
| codechat.channel.global.read | codechat.channel.global.read | true | 4 |
| codechat.channel.global.write | codechat.channel.global.read | false | 4 |
| codechat.create | codechat.create | true | 2 |
| codechat.channel.global | codechat.channel.edit | false | 3 |
| codechat.channel.global | codechat.channel.global.read | false | 3 |
| codechat.channel | codechat.channel.global.read | false | 2 |
| codechat.channel.global.read.extra | codechat.channel.global.read | false | 5 |
| * | codechat.channel.global.read | true | 0 |
| * | codechat | true | 0 |
| codechat.* | codechat.channel.global.read | true | 1 |
| codechat.* | codechat.channel.global.write | true | 1 |
| codechat.* | codechat | true | 1 |
| codeperms.* | codechat.channel.global.read | false | 1 |
| codechat.channel.* | codechat.channel | true | 2 |
| codechat.channel.* | codechat.channel.global.read | true | 2 |
| codechat.channel.global.* | codechat.channel.global | true | 3 |
| codechat.channel.*.read | codechat.channel.global.read | true | 3 |
| codechat.channel.*.read | codechat.channel.global.write | false | 3 |
| codechat.channel.*.read | codechat.read | false | 3 |
| codechat.*.read | codechat.channel.read | true | 2 |
| codechat.*.read | codechat.channel.global.read | false | 2 |
| codechat.*.channel | codechat.channel.channel | true | 2 |
| codechat.*.channel | codechat.channel.global | false | 2 |
| *.read | codechat.read | true | 1 |
| *.read | codechat.channel.read | false | 1 |
| codechat.*.* | codechat.channel.read | true | 1 |
| codechat.*.* | codechat | false | 1 |
| codechat.Channel | codechat.channel | false | 2 |
