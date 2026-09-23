# Supabase live schema — pre-Phase-A1 snapshot

Captured from project `raisslpznoscphdxluwa` on 2026-09-23 before migration
`20260923191834_phase_a1_read_models_exact_lookup.sql`.

## Public relations

All eleven public tables had RLS enabled and not forced. `authenticated` had
SELECT on every table; `anon` had no table access. The relevant sequence was
`sheet_versions_id_seq` with authenticated usage/select.

| Table | Columns (ordered, compact) | Key/index summary | Column signature |
|---|---|---|---|
| user_profiles | user_id uuid, role text, display_name text, created_at/updated_at timestamptz | PK user_id; FK auth.users; role check | `7cc9c75ac4ceb23a33249dee53faee26` |
| trainers | id uuid, owner_id uuid, legacy_name/trainer fields text, sheet_data jsonb, claim_code_hash text, timestamps | PK id; unique owner_id/legacy_name; FK auth.users; owner index | `ce6fa008ce2d1ff8c3f4b89f649b2085` |
| pokemon | id/trainer_id uuid, identity/list text fields, team_slot smallint, sheet_data jsonb, timestamps | PK id; FK trainers; unique legacy_name; trainer/team indexes | `9d203050ef370b3b0bfd2d5e27042012` |
| sheet_versions | identity bigint plus entity/snapshot/audit fields | PK id; changed_by FK; entity and retention indexes | `2bf169dd0fd7fd749a3e32918f0f3eb3` |
| catalog_sources | rules_version and source/count fields | PK rules_version | `fe4f50c234b224190c3b59980e542020` |
| catalog_ranks | id smallint, name text | PK id; unique name | `ba80f84137c0e8ee6df3bfe3750bd92b` |
| catalog_pokemon | rules_version/id/name plus typed catalog fields and extra_data | PK (rules_version,id); unique case-sensitive (rules_version,name); FKs source/rank | `ec42583b2e96172301650b271afe4a01` |
| catalog_moves | rules_version/id/name plus move fields and JSON metadata | PK (rules_version,id); unique case-sensitive (rules_version,name); FK source | `d81104f320ce505ac43cfb2cb5dd2ee1` |
| catalog_learnsets | rules_version/pokemon/position/rank/move/rule fields | composite PK; FKs catalog tables; move/rank indexes | `87f11ac2547845949ed4126365f8b44a` |
| catalog_items | id/name/display overrides, item data, revision/image/parameter fields | PK id; item validation checks; FK auth.users | `e539a3f7726b03f134f3e525744ff8e0` |
| trainer_inventory | trainer/slot PK, item FK, quantity/content/override/revision fields | PK (trainer_id,slot_key); FKs trainers/items | `f919768ab05c4ad1379c770bcd82eb6a` |

Defaults, nullability, complete constraints, indexes, ACLs, policy expressions,
functions and triggers are reproducibly enumerated by
`verify_live_baseline.sql`; the signatures above detect column-level drift.

## RLS policy inventory

- `user_profiles`: `profiles_select_own_or_dm`, `profiles_update_own_name`.
- `trainers`: owner-or-DM SELECT/UPDATE, DM INSERT/DELETE.
- `pokemon`: owner-or-DM SELECT/INSERT/UPDATE/DELETE through its trainer.
- `sheet_versions`: DM SELECT.
- all five catalog tables plus sources/ranks: authenticated read.
- `catalog_items`: authenticated read.
- `trainer_inventory`: trainer owner or DM read.

## Functions and triggers

The live public schema contained 20 functions: profile/claim authorization,
sheet backup/retention, inventory and catalog-item mutation, image revision,
item-parameter validation, Pokémon release, and the health check. Function
definitions, security mode and ACLs are included in the verifier output.

Eight non-internal triggers existed: the auth profile trigger; backup and
updated-at triggers for trainers/Pokémon; the user-profile updated-at trigger;
sheet-version retention; and catalog-image revision.

Recorded function-definition fingerprints (MD5 of `pg_get_functiondef`) were:

| Function | MD5 |
|---|---|
| `backup_sheet_row()` | `e1f8202b263803c644cb75cd5820d1b4` |
| `claim_trainer(uuid,text)` | `b009a8ab49a2569724622e6b48686b08` |
| `create_catalog_item(uuid,text,text,text,text,text,text)` | `deaba1912d2bc1a5f206e0006bf26819` |
| `create_catalog_item_with_parameters(uuid,text,text,text,text,text,text,text[])` | `8963d49edb4ef195ca8b09dbe341b2f2` |
| `create_profile_for_new_user()` | `d65799a807735e328752a91957dc90d2` |
| `enforce_sheet_version_retention()` | `3d970000000fd69e97b725483f170ab9` |
| `is_dm()` | `14f3030f6b45a482cda268579e238ad9` |
| `list_claimable_trainers()` | `3af02364f828d79b779792031716849c` |
| `pokerole_healthcheck()` | `78f187b18142779507f92f2b367dbb82` |
| `release_pokemon(uuid,uuid)` | `98d53b611aa2c41fe0245df38f83cacd` |
| `release_trainer(uuid)` | `6dab31fead6a27961e5f42fb876eec32` |
| `rls_auto_enable()` | `6998ea6b4c2480f5d2e34b5dcf3f8d36` |
| `save_inventory_slot(uuid,text,integer,text,text,integer,text,text,text,text)` | `f597806b66698fb03a12c644c3ae6998` |
| `set_custom_item_image(text,integer,text,text,text)` | `40752c2875c80a338a7d106b36cd5c09` |
| `set_trainer_claim_code(uuid,text)` | `ddf492baba35cf4e8df423cdbf063e66` |
| `set_updated_at()` | `de36cf997a94dda966892702beb7fe46` |
| `track_catalog_image_revision()` | `1c8f9e55fc73b3f33809e4382e16211b` |
| `update_catalog_item(text,integer,text,text,text,text,boolean)` | `7c3d88e9327b5b8d28b7a26311bc003c` |
| `update_catalog_item_with_parameters(text,integer,text,text,text,text,boolean,text[])` | `679732844fbb35ac14d2e6b0e72a9560` |
| `valid_item_parameters(text[])` | `92675b5e76ae3dc0170db5b26802bd2d` |

Trigger-definition fingerprints were: auth profile
`36ee64ac469ff219410c34eebc763405`; trainer backup/updated-at
`7ebf78460c81b283b9843dd4c9555d0a` / `2130b4597334c22634c835b2122c0a4f`;
Pokémon backup/updated-at `03a1aae0cc6c8872a514abc5823df13c` /
`ee995c49817dad0067e93682a7388541`; profile updated-at
`065b5faf2ca951e1b0a303e493377707`; retention
`61b3a33dbf733e83a9dfbdafd7467a36`; catalog image revision
`d9fef6d9d04dcd3eefcee86844c3b77e`.

## Phase-A1 preconditions

- normalized Pokémon collision groups: 0;
- normalized move collision groups: 0;
- normalized effective item-name collision groups: 0 (observed only; not made unique);
- pre-existing Phase-A1 views/columns/indexes: 0;
- rows: trainers 35, Pokémon 36, inventory 18, catalog Pokémon 1200,
  moves 894, learnsets 25438, items 241.

This snapshot intentionally contains no row data, user identifiers, tokens, or
secrets.
