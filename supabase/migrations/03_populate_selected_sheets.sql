-- Pokerole Sheets Android
-- Popola le tabelle definitive usando esclusivamente le schede confermate.
--
-- Prerequisiti:
--   1. 01_create_android_schema.sql eseguito
--   2. 02_prepare_legacy_import.sql eseguito
--   3. Characters_rows.csv importato in legacy_characters_import (20 righe)
--
-- Lo script è ripetibile: aggiorna le righe già importate senza crearne copie.

begin;

-- -----------------------------------------------------------------------------
-- Allenatori confermati
-- La squadra nel JSON viene normalizzata sul nome visualizzato del Pokémon.
-- -----------------------------------------------------------------------------

with trainer_mapping(storage_name, canonical_name, pokemon_display_name) as (
    values
        ('Elia Aster'::text, 'Elia Aster'::text, 'Hatenna'::text),
        (
            'Macharius Von Valancius Massimo Septimo Af Scarius II'::text,
            'Macharius Von Valancius Massimo Septimo Af Scarius II'::text,
            'El Putress'::text
        ),
        ('Wilton'::text, 'Wilton'::text, 'Gumpod'::text),
        ('Zeno'::text, 'Zeno'::text, 'POLIWURL'::text)
),
source_rows as (
    select
        mapping.storage_name,
        mapping.canonical_name,
        imported.value::jsonb as original_sheet,
        jsonb_set(
            imported.value::jsonb,
            '{pokemon_team}',
            jsonb_build_array(
                jsonb_build_object('slot', 1, 'id', 'pk1', 'value', mapping.pokemon_display_name),
                jsonb_build_object('slot', 2, 'id', 'pk2', 'value', ''),
                jsonb_build_object('slot', 3, 'id', 'pk3', 'value', '')
            ),
            true
        ) as normalized_sheet
    from trainer_mapping as mapping
    join public.legacy_characters_import as imported
      on imported.name = mapping.storage_name
)
insert into public.trainers (
    legacy_name,
    trainer_name,
    team,
    age,
    money,
    reputation,
    sheet_data
)
select
    storage_name,
    canonical_name,
    normalized_sheet #>> '{header,team}',
    normalized_sheet #>> '{header,age}',
    normalized_sheet #>> '{header,money}',
    normalized_sheet #>> '{header,reputation}',
    normalized_sheet
from source_rows
on conflict (legacy_name) do update set
    trainer_name = excluded.trainer_name,
    team = excluded.team,
    age = excluded.age,
    money = excluded.money,
    reputation = excluded.reputation,
    sheet_data = excluded.sheet_data
where (
    trainers.trainer_name,
    trainers.team,
    trainers.age,
    trainers.money,
    trainers.reputation,
    trainers.sheet_data
) is distinct from (
    excluded.trainer_name,
    excluded.team,
    excluded.age,
    excluded.money,
    excluded.reputation,
    excluded.sheet_data
);

-- -----------------------------------------------------------------------------
-- Pokémon confermati
-- Hatenna non ha soprannome: nickname è NULL e pokemon_name nel JSON è vuoto.
-- -----------------------------------------------------------------------------

with pokemon_mapping(
    storage_name,
    canonical_trainer,
    canonical_nickname,
    canonical_species,
    team_slot
) as (
    values
        ('Elia_Hatenna'::text, 'Elia Aster'::text, null::text, 'Hatenna'::text, 1::smallint),
        (
            'Trainer Macharius Von Valancius Massimo Septimo Af Scarius II_Nome EL PUTRESS'::text,
            'Macharius Von Valancius Massimo Septimo Af Scarius II'::text,
            'El Putress'::text,
            'Shinx'::text,
            1::smallint
        ),
        ('Wilton_Gumpod'::text, 'Wilton'::text, 'Gumpod'::text, 'Wimpod'::text, 1::smallint),
        ('Zeno_POLIWURL'::text, 'Zeno'::text, 'POLIWURL'::text, 'Poliwhirl'::text, 1::smallint)
),
source_rows as (
    select
        mapping.*,
        jsonb_set(
            jsonb_set(
                jsonb_set(
                    imported.value::jsonb,
                    '{header,trainer_name}',
                    to_jsonb(mapping.canonical_trainer),
                    true
                ),
                '{header,pokemon_name}',
                to_jsonb(coalesce(mapping.canonical_nickname, '')),
                true
            ),
            '{header,pokename}',
            to_jsonb(mapping.canonical_species),
            true
        ) as normalized_sheet
    from pokemon_mapping as mapping
    join public.legacy_characters_import as imported
      on imported.name = mapping.storage_name
)
insert into public.pokemon (
    trainer_id,
    legacy_name,
    nickname,
    species,
    pokedex_number,
    primary_type,
    secondary_type,
    team_slot,
    sheet_data
)
select
    trainers.id,
    source_rows.storage_name,
    source_rows.canonical_nickname,
    source_rows.canonical_species,
    source_rows.normalized_sheet #>> '{header,pokenr}',
    source_rows.normalized_sheet #>> '{header,type1,text}',
    nullif(source_rows.normalized_sheet #>> '{header,type2,text}', ''),
    source_rows.team_slot,
    source_rows.normalized_sheet
from source_rows
join public.trainers
  on trainers.trainer_name = source_rows.canonical_trainer
on conflict (legacy_name) do update set
    trainer_id = excluded.trainer_id,
    nickname = excluded.nickname,
    species = excluded.species,
    pokedex_number = excluded.pokedex_number,
    primary_type = excluded.primary_type,
    secondary_type = excluded.secondary_type,
    team_slot = excluded.team_slot,
    sheet_data = excluded.sheet_data
where (
    pokemon.trainer_id,
    pokemon.nickname,
    pokemon.species,
    pokemon.pokedex_number,
    pokemon.primary_type,
    pokemon.secondary_type,
    pokemon.team_slot,
    pokemon.sheet_data
) is distinct from (
    excluded.trainer_id,
    excluded.nickname,
    excluded.species,
    excluded.pokedex_number,
    excluded.primary_type,
    excluded.secondary_type,
    excluded.team_slot,
    excluded.sheet_data
);

-- Interrompe l'intera operazione se non sono state ottenute esattamente
-- le quattro coppie confermate.
do $$
declare
    v_trainers integer;
    v_pokemon integer;
begin
    select count(*) into v_trainers
    from public.trainers
    where legacy_name in (
        'Elia Aster',
        'Macharius Von Valancius Massimo Septimo Af Scarius II',
        'Wilton',
        'Zeno'
    );

    select count(*) into v_pokemon
    from public.pokemon
    where legacy_name in (
        'Elia_Hatenna',
        'Trainer Macharius Von Valancius Massimo Septimo Af Scarius II_Nome EL PUTRESS',
        'Wilton_Gumpod',
        'Zeno_POLIWURL'
    );

    if v_trainers <> 4 or v_pokemon <> 4 then
        raise exception
            'Importazione incompleta: allenatori %, Pokemon % (attesi 4 e 4)',
            v_trainers,
            v_pokemon;
    end if;
end;
$$;

commit;

-- Verifica finale facoltativa:
-- select
--     trainers.trainer_name,
--     pokemon.nickname,
--     pokemon.species,
--     pokemon.team_slot
-- from public.trainers
-- join public.pokemon on pokemon.trainer_id = trainers.id
-- order by trainers.trainer_name;
