# Phase A1 — baseline, read models, exact lookup

Phase A1 was applied to the live Supabase project on 2026-09-23. It is additive:
the Android app continues to use its existing tables and save paths unchanged.

## Baseline and migration workflow

The live database was adopted as authoritative because its migration history was
empty while scripts 01-12 already described earlier development. No historical
script was replayed or marked as applied. The pre-change inventory and read-only
verifier are in `baseline/`.

New work begins with incremental migration
`20260923191834_phase_a1_read_models_exact_lookup.sql`. Its precondition aborts
the transaction if normalized Pokémon or move names collide. Immediately before
application, collision groups and pre-existing Phase-A1 objects were both zero.

## Read models and null behavior

All four views use `security_invoker=true`, are granted only to `authenticated`,
and retain the underlying `trainers`/`pokemon` RLS visibility:

- `trainer_summary_v1` and `pokemon_summary_v1` omit `sheet_data`;
- `pokemon_battle_state_v1` exposes persisted HP, Will, defenses, status,
  initiative, evasion and rank;
- `pokemon_moves_v1` exposes ordered stored move names, including manual moves.

Battle integers accept trimmed non-negative text of at most nine digits. Missing,
blank, negative, decimal, oversized or malformed values become `NULL`; formulas
are never recalculated. A missing/non-array `moves` value produces no move rows.

## Exact lookup

Pokémon and moves have `name_key = lower(btrim(name))` plus unique indexes scoped
by `rules_version`. Items have a non-unique indexed key based on the effective
display name, `lower(btrim(coalesce(custom_name,name)))`. Equality replaces
wildcard `ILIKE`. Item ID remains authoritative and name ambiguity is explicit.

## Verification, rollback and limits

`tests/phase_a1_read_models.test.mjs` checks `%`/`_`, collision rollback, JSON
fallbacks and player/DM/anon RLS locally. `tests/phase_a1_live_rls.sql` is the
read-only live regression script. Both automated role checks passed after deploy.
A real Google OAuth login as DM remains **UNVERIFIED**.

Rollback SQL is in
`rollbacks/20260923191834_phase_a1_read_models_exact_lookup.sql`. Revert Python
consumers first, then apply its contents as a new compensating migration so the
live migration history remains truthful. It removes only the new views, indexes
and generated columns; it does not touch existing Android rows or objects.

Revision/locking, save RPC redesign, team/roster work, physical battle-state or
move normalization, JSON cleanup, inventory redesign, and AI/RAG/FastAPI work
remain deliberately postponed.
