# Weekly General Training Plan migration rollback

Rollback is destructive once weekly content or weekly exercises have been saved. Do not run it automatically. First stop application writers and confirm the exact target identity and a current verified backup.

The migration is additive and leaves legacy `training_exercise.week_id` values NULL. If it must be reversed before weekly content exists, use one explicit transaction on the approved database: verify `@@SERVERNAME`, `DB_NAME()` and `DB_ID()`; verify `training_plan_week` contains zero rows and `training_exercise.week_id` contains only NULL; drop `fk_training_exercise_week_plan`, `fk_training_exercise_week`, `ix_training_exercise_week_order`; drop `week_id`; then drop the three week-table foreign keys and the two unique constraints, check constraint and primary key by their names; finally drop `training_plan_week`. Run the validation script before and after. If any weekly rows or non-NULL week IDs exist, stop and obtain owner approval for a data-preserving recovery plan instead of rolling back.

The checked-in migration's identity guard is intentionally specific to the authorized DB12 execution. A future deployment to another database requires a separately reviewed target-identity guard and explicit owner authorization; do not edit the guard and apply it to DB5 as part of this task.
