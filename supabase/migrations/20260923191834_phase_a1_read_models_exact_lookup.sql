-- Phase A1: additive read models and deterministic catalog lookup.
-- The live database is authoritative; historical migrations 01-12 are not replayed.

begin;

-- Fail before changing the schema if normalized uniqueness is not true in live data.
do $$
begin
    if exists (
        select 1
        from public.catalog_pokemon
        group by rules_version, lower(btrim(name))
        having count(*) > 1
    ) then
        raise exception 'catalog_pokemon contains normalized-name collisions';
    end if;

    if exists (
        select 1
        from public.catalog_moves
        group by rules_version, lower(btrim(name))
        having count(*) > 1
    ) then
        raise exception 'catalog_moves contains normalized-name collisions';
    end if;
end;
$$;

alter table public.catalog_pokemon
    add column name_key text generated always as (lower(btrim(name))) stored;

alter table public.catalog_moves
    add column name_key text generated always as (lower(btrim(name))) stored;

-- Item identity remains id. The key follows the effective display name and is
-- deliberately non-unique because custom items may share a display name.
alter table public.catalog_items
    add column name_key text generated always as (
        lower(btrim(coalesce(custom_name, name)))
    ) stored;

create unique index catalog_pokemon_rules_version_normalized_name_key
    on public.catalog_pokemon (rules_version, name_key);

create unique index catalog_moves_rules_version_normalized_name_key
    on public.catalog_moves (rules_version, name_key);

create index catalog_items_normalized_name_idx
    on public.catalog_items (name_key);

create view public.trainer_summary_v1
with (security_invoker = true)
as
select
    id,
    owner_id,
    trainer_name,
    team,
    age,
    reputation
from public.trainers;

create view public.pokemon_summary_v1
with (security_invoker = true)
as
select
    id,
    trainer_id,
    nickname,
    species,
    pokedex_number,
    primary_type,
    secondary_type,
    team_slot
from public.pokemon;

-- Persisted effective values only. Numeric casts accept non-negative values of
-- up to nine digits; missing, blank, negative, decimal, or malformed values are
-- exposed as NULL instead of making the view fail.
create view public.pokemon_battle_state_v1
with (security_invoker = true)
as
select
    p.id as pokemon_id,
    p.trainer_id,
    case when btrim(p.sheet_data #>> '{quick_references,hp,actual}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,hp,actual}')::integer end as current_hp,
    case when btrim(p.sheet_data #>> '{quick_references,hp,total}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,hp,total}')::integer end as max_hp,
    case when btrim(p.sheet_data #>> '{quick_references,will,actual}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,will,actual}')::integer end as current_will,
    case when btrim(p.sheet_data #>> '{quick_references,will,total}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,will,total}')::integer end as max_will,
    case when btrim(p.sheet_data #>> '{quick_references,def_spdef,actual}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,def_spdef,actual}')::integer end as defense,
    case when btrim(p.sheet_data #>> '{quick_references,def_spdef,total}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,def_spdef,total}')::integer end as special_defense,
    nullif(btrim(p.sheet_data #>> '{quick_references,status_effect}'), '') as status,
    case when btrim(p.sheet_data #>> '{quick_references,initiative}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,initiative}')::integer end as initiative,
    case when btrim(p.sheet_data #>> '{quick_references,evasion}') ~ '^[0-9]{1,9}$'
        then btrim(p.sheet_data #>> '{quick_references,evasion}')::integer end as evasion,
    nullif(btrim(p.sheet_data #>> '{pokerole,rank}'), '') as rank
from public.pokemon as p;

create view public.pokemon_moves_v1
with (security_invoker = true)
as
select
    p.id as pokemon_id,
    p.trainer_id,
    move.ordinality::integer as move_position,
    case jsonb_typeof(move.value)
        when 'object' then move.value ->> 'value'
        when 'string' then move.value #>> '{}'
        else null
    end as move_name
from public.pokemon as p
cross join lateral jsonb_array_elements(
    case
        when jsonb_typeof(p.sheet_data -> 'moves') = 'array'
            then p.sheet_data -> 'moves'
        else '[]'::jsonb
    end
) with ordinality as move(value, ordinality);

revoke all on table public.trainer_summary_v1 from public, anon;
revoke all on table public.pokemon_summary_v1 from public, anon;
revoke all on table public.pokemon_battle_state_v1 from public, anon;
revoke all on table public.pokemon_moves_v1 from public, anon;

grant select on table public.trainer_summary_v1 to authenticated;
grant select on table public.pokemon_summary_v1 to authenticated;
grant select on table public.pokemon_battle_state_v1 to authenticated;
grant select on table public.pokemon_moves_v1 to authenticated;

commit;
