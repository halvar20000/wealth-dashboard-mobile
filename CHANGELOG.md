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

## 1.2.9
- Beträge sehen überall gleich aus: immer mit Cent, z. B. 3.300,00 € statt 3.300 € neben 200,30 €.

## 1.2.8
- Jede Buchung lässt sich antippen: Die Detailseite zeigt alles zum Eintrag, man kann sie direkt kategorisieren und Beschreibung, Gegenpartei, Datum und Betrag korrigieren — unter Portfolio → Konto, in den Buchungen einer Cashflow-Kategorie und unter Cashflow → Alle Buchungen.
- Einstellungen → Kategorien: Kategorien anlegen, umbenennen, umfärben, als Ausgabe/Einnahme einordnen und löschen (braucht Dashboard 0.79.2).
- Portfolio → Konten: nach Kontoart gruppiert, jede Gruppe mit ihrer Summe — die Depots mit ihrer Gesamtsumme — und oben alle Konten zusammen.

## 1.2.7
- Die Einstellungen zeigen die Version der App und, unter „Was ist neu“, was sich in jeder Version geändert hat.
- Cashflow: Ein Tipp auf eine Kategorie zeigt die Buchungen dahinter — bei den Ausgaben wie bei den Einnahmen.
