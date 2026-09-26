# Was ist neu

Eine Datei für beide Apps: Android und iOS zeigen sie unter
Einstellungen → „Was ist neu“, und ein Tag schreibt den Abschnitt
seiner Version als „What to Test“ in TestFlight und in das GitHub-Release.

So wird sie gepflegt: Wer etwas ändert, das man in der App sieht,
schreibt eine Zeile unter die oberste Version, im selben Pull Request.
Die oberste Version ist immer die nächste, die getaggt wird; nach dem
Tag kommt darüber eine neue. Eine Überschrift ist `## 1.2.3`, jeder
Eintrag eine Zeile mit `- `. Text vor der ersten Überschrift zeigen die
Apps nicht.

## 1.2.7
- Die Einstellungen zeigen die Version der App und, unter „Was ist neu“, was sich in jeder Version geändert hat.

## 1.2.6
- Personenwahl oben rechts: alle Konten oder die einer Person, wie im Dashboard.
- Die Apps sprechen Deutsch, wenn das Telefon Deutsch spricht.
- Die Portfolio-Linie zeigt nur noch die Wertpapiere, nicht mehr das ganze Nettovermögen.
- Über die Linien wischen zeigt Datum und Wert jedes Punkts.
- iOS: Unter der Übersicht steht jetzt auch die Linie des Nettovermögens.

## 1.2.5
- iOS: Schulden zählen im Nettovermögen, abgelehnte Kurse werden genannt, heruntergeladene Dateien lassen sich mit Wealth öffnen, und ein Import kann rückgängig gemacht werden.

## 1.2.4
- Android: Ein Import lässt sich in der App rückgängig machen, wie im Browser.

## 1.2.3
- Die Hypothek fällt nicht mehr aus dem Nettovermögen, wenn Anlageklassen abgewählt sind.
- Ein abgelehnter Kurs gilt nicht mehr als gescheiterte Aktualisierung: die App sagt, welcher fehlt, und behält die übrigen.
- Android: Heruntergeladene Kontoauszüge lassen sich mit „Öffnen mit“ importieren, nicht nur über „Teilen“.

## 1.2.2
- iOS: Portfolio, Einordnen per Wischen, Widget, Teilen-Menü, Abgleich im Hintergrund und Sperre mit Face ID.

## 1.2.1
- Erste iOS-Version: Koppeln, Übersicht, Konten und Cashflow.

## 1.2.0
- Android: Die Übersicht kann Anlageklassen ausblenden.
- Kurse und Wechselkurse lassen sich aktualisieren, ohne die Banken abzugleichen.
- Neuer Cashflow-Tab: Monate, Kategorien und was übrig bleibt.
- Beim Einordnen lässt sich die Regel bearbeiten, die ein Wischen anlegt.
- Die Rendite zeigt alle acht Zeiträume ohne Scrollen.

## 1.1.0
- Portfolio-Tab: Bestände mit Wert und Gewinn, Aufteilung nach Anlageklasse und der Verlauf über einen wählbaren Zeitraum.

## 1.0.2
- Android: Ziel-API 36, wie Google Play es verlangt.

## 1.0.1
- Android: der Paketname, den Google Play festgelegt hat.
