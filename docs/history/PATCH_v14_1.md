# v14.1 compile patch

ShotType gained DOUBLE and BOMB in v14. Kotlin therefore requires older exhaustive
`when` expressions over ShotType to handle those enum values too.

This patch audits the Kotlin source and adds DOUBLE/BOMB handling to the affected
ShotType `when` expressions. Gameplay behavior from v14 is otherwise unchanged.
