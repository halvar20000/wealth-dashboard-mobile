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
- Cashflow: Ein Tipp auf eine Kategorie zeigt die Buchungen dahinter — bei den Ausgaben wie bei den Einnahmen.
