-- Pokerole Sheets Android
-- Area di importazione isolata per il CSV Characters_rows.csv.
--
-- Eseguire questo script nel NUOVO progetto dopo 01_create_android_schema.sql.
-- Successivamente importare il CSV nella tabella legacy_characters_import
-- tramite Table Editor. I dati finali non vengono ancora modificati.

begin;

create table if not exists public.legacy_characters_import (
    name text primary key,
    value text not null,
    imported_at timestamptz not null default now(),
    constraint legacy_name_not_blank check (btrim(name) <> ''),
    constraint legacy_value_not_blank check (btrim(value) <> '')
);

comment on table public.legacy_characters_import is
    'Copia isolata dell''export Characters del vecchio Supabase; non usata direttamente dall''app.';

alter table public.legacy_characters_import enable row level security;

-- Nessuna policy: la tabella è accessibile dal Dashboard, ma non dall'app.
revoke all on table public.legacy_characters_import from anon, authenticated;

commit;

-- Dopo l'importazione del CSV, questa query deve restituire:
-- total_rows = 20, valid_json_rows = 20, invalid_json_rows = 0.
--
-- select
--     count(*) as total_rows,
--     count(*) filter (where pg_input_is_valid(value, 'jsonb')) as valid_json_rows,
--     count(*) filter (where not pg_input_is_valid(value, 'jsonb')) as invalid_json_rows
-- from public.legacy_characters_import;
