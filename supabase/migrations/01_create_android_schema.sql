-- Pokerole Sheets Android
-- Schema iniziale per un nuovo progetto Supabase.
--
-- Questo script non legge, modifica o cancella il vecchio progetto Supabase.
-- Eseguirlo nel SQL Editor del NUOVO progetto.

begin;

create extension if not exists pgcrypto with schema extensions;

-- -----------------------------------------------------------------------------
-- Tabelle applicative
-- -----------------------------------------------------------------------------

create table if not exists public.user_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    role text not null default 'player' check (role in ('player', 'dm')),
    display_name text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.trainers (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid unique references auth.users(id) on delete set null,
    legacy_name text unique,
    trainer_name text not null,
    team text,
    age text,
    money text,
    reputation text,
    sheet_data jsonb not null default '{}'::jsonb,
    claim_code_hash text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint trainers_name_not_blank check (btrim(trainer_name) <> ''),
    constraint trainers_sheet_is_object check (jsonb_typeof(sheet_data) = 'object')
);

create table if not exists public.pokemon (
    id uuid primary key default gen_random_uuid(),
    trainer_id uuid not null references public.trainers(id) on delete cascade,
    legacy_name text unique,
    nickname text,
    species text,
    pokedex_number text,
    primary_type text,
    secondary_type text,
    team_slot smallint,
    sheet_data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint pokemon_name_or_species check (
        nullif(btrim(coalesce(nickname, '')), '') is not null
        or nullif(btrim(coalesce(species, '')), '') is not null
    ),
    constraint pokemon_team_slot_range check (team_slot is null or team_slot between 1 and 6),
    constraint pokemon_sheet_is_object check (jsonb_typeof(sheet_data) = 'object')
);

-- Storico centralizzato. Lo snapshot contiene l'intera riga precedente,
-- incluso il JSON completo della scheda.
create table if not exists public.sheet_versions (
    id bigint generated always as identity primary key,
    entity_type text not null check (entity_type in ('trainer', 'pokemon')),
    entity_id uuid not null,
    storage_name text,
    operation text not null check (operation in ('update', 'delete')),
    snapshot jsonb not null,
    changed_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now()
);

create index if not exists trainers_owner_id_idx on public.trainers(owner_id);
create index if not exists pokemon_trainer_id_idx on public.pokemon(trainer_id);
create index if not exists pokemon_team_slot_idx on public.pokemon(trainer_id, team_slot);
create index if not exists sheet_versions_entity_idx
    on public.sheet_versions(entity_type, entity_id, created_at desc);

-- -----------------------------------------------------------------------------
-- Trigger di servizio
-- -----------------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function public.create_profile_for_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.user_profiles (user_id, display_name)
    values (
        new.id,
        coalesce(
            new.raw_user_meta_data ->> 'full_name',
            new.raw_user_meta_data ->> 'name',
            split_part(coalesce(new.email, ''), '@', 1)
        )
    )
    on conflict (user_id) do nothing;
    return new;
end;
$$;

create or replace function public.backup_sheet_row()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_entity_type text;
    v_storage_name text;
begin
    v_entity_type := case tg_table_name
        when 'trainers' then 'trainer'
        when 'pokemon' then 'pokemon'
        else null
    end;

    if v_entity_type is null then
        raise exception 'Tabella non supportata dal backup: %', tg_table_name;
    end if;

    v_storage_name := old.legacy_name;

    insert into public.sheet_versions (
        entity_type,
        entity_id,
        storage_name,
        operation,
        snapshot,
        changed_by
    ) values (
        v_entity_type,
        old.id,
        v_storage_name,
        lower(tg_op),
        to_jsonb(old),
        auth.uid()
    );

    if tg_op = 'DELETE' then
        return old;
    end if;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.create_profile_for_new_user();

drop trigger if exists trainers_10_backup on public.trainers;
create trigger trainers_10_backup
    before update or delete on public.trainers
    for each row execute function public.backup_sheet_row();

drop trigger if exists trainers_90_updated_at on public.trainers;
create trigger trainers_90_updated_at
    before update on public.trainers
    for each row execute function public.set_updated_at();

drop trigger if exists pokemon_10_backup on public.pokemon;
create trigger pokemon_10_backup
    before update or delete on public.pokemon
    for each row execute function public.backup_sheet_row();

drop trigger if exists pokemon_90_updated_at on public.pokemon;
create trigger pokemon_90_updated_at
    before update on public.pokemon
    for each row execute function public.set_updated_at();

drop trigger if exists user_profiles_90_updated_at on public.user_profiles;
create trigger user_profiles_90_updated_at
    before update on public.user_profiles
    for each row execute function public.set_updated_at();

-- Crea anche i profili di eventuali utenti registrati prima dello script.
insert into public.user_profiles (user_id, display_name)
select
    users.id,
    coalesce(
        users.raw_user_meta_data ->> 'full_name',
        users.raw_user_meta_data ->> 'name',
        split_part(coalesce(users.email, ''), '@', 1)
    )
from auth.users as users
on conflict (user_id) do nothing;

-- -----------------------------------------------------------------------------
-- Funzioni di autorizzazione e associazione della scheda
-- -----------------------------------------------------------------------------

create or replace function public.is_dm()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.user_profiles
        where user_id = auth.uid()
          and role = 'dm'
    );
$$;

create or replace function public.list_claimable_trainers()
returns table (id uuid, trainer_name text)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Autenticazione richiesta';
    end if;

    return query
    select trainers.id, trainers.trainer_name
    from public.trainers
    where trainers.owner_id is null
      and trainers.claim_code_hash is not null
    order by lower(trainers.trainer_name);
end;
$$;

create or replace function public.claim_trainer(
    p_trainer_id uuid,
    p_claim_code text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_claimed_id uuid;
begin
    if v_user_id is null then
        raise exception 'Autenticazione richiesta';
    end if;

    if exists (
        select 1 from public.trainers where owner_id = v_user_id
    ) then
        raise exception 'Questo account possiede già una scheda allenatore';
    end if;

    update public.trainers
       set owner_id = v_user_id,
           claim_code_hash = null
     where id = p_trainer_id
       and owner_id is null
       and claim_code_hash is not null
       and extensions.crypt(btrim(p_claim_code), claim_code_hash) = claim_code_hash
    returning id into v_claimed_id;

    if v_claimed_id is null then
        raise exception 'Codice non valido oppure scheda già assegnata';
    end if;

    return v_claimed_id;
end;
$$;

create or replace function public.set_trainer_claim_code(
    p_trainer_id uuid,
    p_claim_code text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_dm() then
        raise exception 'Operazione riservata al DM';
    end if;

    if length(btrim(p_claim_code)) < 6 then
        raise exception 'Il codice deve contenere almeno 6 caratteri';
    end if;

    update public.trainers
       set claim_code_hash = extensions.crypt(
               btrim(p_claim_code),
               extensions.gen_salt('bf')
           )
     where id = p_trainer_id
       and owner_id is null;

    if not found then
        raise exception 'Scheda inesistente oppure già assegnata';
    end if;
end;
$$;

create or replace function public.release_trainer(p_trainer_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.is_dm() then
        raise exception 'Operazione riservata al DM';
    end if;

    update public.trainers
       set owner_id = null,
           claim_code_hash = null
     where id = p_trainer_id;

    if not found then
        raise exception 'Scheda inesistente';
    end if;
end;
$$;

-- -----------------------------------------------------------------------------
-- Row Level Security: il DM vede tutto, il player solo la propria scheda.
-- -----------------------------------------------------------------------------

alter table public.user_profiles enable row level security;
alter table public.trainers enable row level security;
alter table public.pokemon enable row level security;
alter table public.sheet_versions enable row level security;

drop policy if exists profiles_select_own_or_dm on public.user_profiles;
create policy profiles_select_own_or_dm
    on public.user_profiles for select
    to authenticated
    using (user_id = auth.uid() or public.is_dm());

drop policy if exists profiles_update_own_name on public.user_profiles;
create policy profiles_update_own_name
    on public.user_profiles for update
    to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists trainers_select_owner_or_dm on public.trainers;
create policy trainers_select_owner_or_dm
    on public.trainers for select
    to authenticated
    using (owner_id = auth.uid() or public.is_dm());

drop policy if exists trainers_insert_dm on public.trainers;
create policy trainers_insert_dm
    on public.trainers for insert
    to authenticated
    with check (public.is_dm());

drop policy if exists trainers_update_owner_or_dm on public.trainers;
create policy trainers_update_owner_or_dm
    on public.trainers for update
    to authenticated
    using (owner_id = auth.uid() or public.is_dm())
    with check (owner_id = auth.uid() or public.is_dm());

drop policy if exists trainers_delete_dm on public.trainers;
create policy trainers_delete_dm
    on public.trainers for delete
    to authenticated
    using (public.is_dm());

drop policy if exists pokemon_select_owner_or_dm on public.pokemon;
create policy pokemon_select_owner_or_dm
    on public.pokemon for select
    to authenticated
    using (
        public.is_dm()
        or exists (
            select 1 from public.trainers
            where trainers.id = pokemon.trainer_id
              and trainers.owner_id = auth.uid()
        )
    );

drop policy if exists pokemon_insert_owner_or_dm on public.pokemon;
create policy pokemon_insert_owner_or_dm
    on public.pokemon for insert
    to authenticated
    with check (
        public.is_dm()
        or exists (
            select 1 from public.trainers
            where trainers.id = pokemon.trainer_id
              and trainers.owner_id = auth.uid()
        )
    );

drop policy if exists pokemon_update_owner_or_dm on public.pokemon;
create policy pokemon_update_owner_or_dm
    on public.pokemon for update
    to authenticated
    using (
        public.is_dm()
        or exists (
            select 1 from public.trainers
            where trainers.id = pokemon.trainer_id
              and trainers.owner_id = auth.uid()
        )
    )
    with check (
        public.is_dm()
        or exists (
            select 1 from public.trainers
            where trainers.id = pokemon.trainer_id
              and trainers.owner_id = auth.uid()
        )
    );

drop policy if exists pokemon_delete_owner_or_dm on public.pokemon;
create policy pokemon_delete_owner_or_dm
    on public.pokemon for delete
    to authenticated
    using (
        public.is_dm()
        or exists (
            select 1 from public.trainers
            where trainers.id = pokemon.trainer_id
              and trainers.owner_id = auth.uid()
        )
    );

drop policy if exists versions_select_dm on public.sheet_versions;
create policy versions_select_dm
    on public.sheet_versions for select
    to authenticated
    using (public.is_dm());

-- Nessun accesso ai visitatori anonimi.
revoke all on table public.user_profiles from anon;
revoke all on table public.trainers from anon;
revoke all on table public.pokemon from anon;
revoke all on table public.sheet_versions from anon;

-- Accessi minimi per gli utenti autenticati; le policy RLS applicano i ruoli.
grant select on table public.user_profiles to authenticated;
grant update (display_name) on table public.user_profiles to authenticated;

grant select, insert, delete on table public.trainers to authenticated;
grant update (
    legacy_name,
    trainer_name,
    team,
    age,
    money,
    reputation,
    sheet_data
) on table public.trainers to authenticated;

grant select, insert, delete on table public.pokemon to authenticated;
grant update (
    trainer_id,
    legacy_name,
    nickname,
    species,
    pokedex_number,
    primary_type,
    secondary_type,
    team_slot,
    sheet_data
) on table public.pokemon to authenticated;

grant select on table public.sheet_versions to authenticated;
grant usage, select on sequence public.sheet_versions_id_seq to authenticated;

revoke all on function public.is_dm() from public, anon;
revoke all on function public.list_claimable_trainers() from public, anon;
revoke all on function public.claim_trainer(uuid, text) from public, anon;
revoke all on function public.set_trainer_claim_code(uuid, text) from public, anon;
revoke all on function public.release_trainer(uuid) from public, anon;
revoke all on function public.set_updated_at() from public, anon, authenticated;
revoke all on function public.create_profile_for_new_user() from public, anon, authenticated;
revoke all on function public.backup_sheet_row() from public, anon, authenticated;

grant execute on function public.is_dm() to authenticated;
grant execute on function public.list_claimable_trainers() to authenticated;
grant execute on function public.claim_trainer(uuid, text) to authenticated;
grant execute on function public.set_trainer_claim_code(uuid, text) to authenticated;
grant execute on function public.release_trainer(uuid) to authenticated;

commit;

-- Dopo che il master avrà effettuato almeno un accesso con Google,
-- promuoverlo eseguendo SEPARATAMENTE questa query con la sua email:
--
-- update public.user_profiles
-- set role = 'dm'
-- where user_id = (
--     select id
--     from auth.users
--     where lower(email) = lower('EMAIL_GOOGLE_DEL_MASTER')
-- );
