-- Emergency rollback for Phase A1. This is not a forward migration.
-- Existing Android objects and data are not touched.
begin;

drop view if exists public.pokemon_moves_v1;
drop view if exists public.pokemon_battle_state_v1;
drop view if exists public.pokemon_summary_v1;
drop view if exists public.trainer_summary_v1;

drop index if exists public.catalog_items_normalized_name_idx;
drop index if exists public.catalog_moves_rules_version_normalized_name_key;
drop index if exists public.catalog_pokemon_rules_version_normalized_name_key;

alter table public.catalog_items drop column if exists name_key;
alter table public.catalog_moves drop column if exists name_key;
alter table public.catalog_pokemon drop column if exists name_key;

commit;
