-- Phase A2: optimistic revisions, atomic sheet saves and relational team integrity.
-- Additive/backward-compatible: legacy direct UPDATE grants remain available.
begin;

do $$
begin
    if exists (
        select 1 from public.pokemon
        where team_slot is not null and team_slot not between 1 and 6
    ) then raise exception 'pokemon contains invalid active team slots'; end if;

    if exists (
        select 1 from public.pokemon where team_slot is not null
        group by trainer_id, team_slot having count(*) > 1
    ) then raise exception 'pokemon contains duplicate active team slots'; end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema='public' and table_name in ('trainers','pokemon')
          and column_name='revision'
    ) then raise exception 'Phase A2 revision columns already exist'; end if;
end $$;

alter table public.trainers add column revision integer not null default 1
    check (revision >= 1);
alter table public.pokemon add column revision integer not null default 1
    check (revision >= 1);

create unique index pokemon_trainer_active_team_slot_key
    on public.pokemon(trainer_id, team_slot)
    where team_slot is not null;

create or replace function public.initialize_sheet_revision()
returns trigger
language plpgsql
security invoker
set search_path=''
as $$
begin
    new.revision := 1;
    return new;
end $$;

revoke all on function public.initialize_sheet_revision() from public, anon, authenticated;

create trigger trainers_80_initialize_revision
    before insert on public.trainers
    for each row execute function public.initialize_sheet_revision();
create trigger pokemon_80_initialize_revision
    before insert on public.pokemon
    for each row execute function public.initialize_sheet_revision();

-- Technical team JSON synchronization and the temporary first half of an atomic
-- reorder are not independent sheet edits and must not create history entries.
create or replace function public.backup_sheet_row()
returns trigger
language plpgsql
security definer
set search_path=''
as $$
declare
    v_entity_type text;
begin
    if tg_op='UPDATE' and tg_table_name='pokemon'
       and current_setting('pokerole.team_reorder_transition',true)='on' then
        return new;
    end if;

    if tg_op='UPDATE' and tg_table_name='trainers'
       and (to_jsonb(new)-array['sheet_data','updated_at','revision'])
           = (to_jsonb(old)-array['sheet_data','updated_at','revision'])
       and ((to_jsonb(new)->'sheet_data')-'pokemon_team')=((to_jsonb(old)->'sheet_data')-'pokemon_team')
       and (to_jsonb(new)#>'{sheet_data,pokemon_team}') is distinct from (to_jsonb(old)#>'{sheet_data,pokemon_team}') then
        return new;
    end if;

    v_entity_type := case tg_table_name when 'trainers' then 'trainer' when 'pokemon' then 'pokemon' end;
    if v_entity_type is null then raise exception 'Tabella non supportata dal backup: %',tg_table_name; end if;

    insert into public.sheet_versions(entity_type,entity_id,storage_name,operation,snapshot,changed_by)
    values(v_entity_type,old.id,old.legacy_name,lower(tg_op),to_jsonb(old),auth.uid());
    return case when tg_op='DELETE' then old else new end;
end $$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path=''
as $$
begin
    if tg_table_name='pokemon'
       and current_setting('pokerole.team_reorder_transition',true)='on' then
        return new;
    end if;

    if tg_table_name='trainers'
       and (to_jsonb(new)-array['sheet_data','updated_at','revision'])
           = (to_jsonb(old)-array['sheet_data','updated_at','revision'])
       and ((to_jsonb(new)->'sheet_data')-'pokemon_team')=((to_jsonb(old)->'sheet_data')-'pokemon_team')
       and (to_jsonb(new)#>'{sheet_data,pokemon_team}') is distinct from (to_jsonb(old)#>'{sheet_data,pokemon_team}') then
        return new;
    end if;

    new.updated_at := now();
    if tg_table_name in ('trainers','pokemon') then new.revision := old.revision+1; end if;
    return new;
end $$;

create or replace function public.sync_trainer_pokemon_team()
returns trigger
language plpgsql
security invoker
set search_path=''
as $$
declare
    v_trainer_id uuid;
    v_team jsonb;
begin
    v_trainer_id := case when tg_op='DELETE' then old.trainer_id else new.trainer_id end;
    select jsonb_agg(jsonb_build_object(
        'slot',s.slot,'id','pk'||s.slot::text,
        'value',coalesce((select coalesce(nullif(btrim(p.nickname),''),nullif(btrim(p.species),''),'')
            from public.pokemon p where p.trainer_id=v_trainer_id and p.team_slot=s.slot),''))
        order by s.slot)
    into v_team from generate_series(1,6) s(slot);

    update public.trainers t
       set sheet_data=jsonb_set(t.sheet_data,'{pokemon_team}',v_team,true)
     where t.id=v_trainer_id
       and t.sheet_data->'pokemon_team' is distinct from v_team;
    return case when tg_op='DELETE' then old else new end;
end $$;

revoke all on function public.sync_trainer_pokemon_team() from public, anon, authenticated;

create trigger pokemon_85_sync_team_insert
    after insert on public.pokemon for each row
    execute function public.sync_trainer_pokemon_team();
create trigger pokemon_85_sync_team_update
    after update of trainer_id,team_slot,nickname,species on public.pokemon for each row
    when (old.* is distinct from new.*)
    execute function public.sync_trainer_pokemon_team();
create trigger pokemon_85_sync_team_delete
    after delete on public.pokemon for each row
    execute function public.sync_trainer_pokemon_team();

create or replace function public.save_trainer_sheet(
    p_id uuid,p_expected_revision integer,p_legacy_name text,p_trainer_name text,
    p_team text,p_age text,p_money text,p_reputation text,p_sheet_data jsonb
) returns setof public.trainers
language plpgsql
security invoker
set search_path=''
as $$
declare v_row public.trainers;
begin
    if auth.uid() is null then raise exception 'Accesso richiesto' using errcode='42501'; end if;
    if nullif(btrim(p_trainer_name),'') is null or jsonb_typeof(p_sheet_data) is distinct from 'object'
        then raise exception 'Dati allenatore non validi'; end if;

    if p_id is null then
        if p_expected_revision<>0 then raise exception 'Revisione iniziale non valida' using errcode='40001'; end if;
        insert into public.trainers(legacy_name,trainer_name,team,age,money,reputation,sheet_data)
        values(p_legacy_name,btrim(p_trainer_name),p_team,p_age,p_money,p_reputation,p_sheet_data)
        returning * into v_row;
    else
        update public.trainers set legacy_name=p_legacy_name,trainer_name=btrim(p_trainer_name),
            team=p_team,age=p_age,money=p_money,reputation=p_reputation,sheet_data=p_sheet_data
        where id=p_id and revision=p_expected_revision returning * into v_row;
        if not found then
            raise exception 'La scheda è stata modificata altrove. Ricarica la versione più recente prima di salvare.' using errcode='40001';
        end if;
    end if;
    return next v_row;
end $$;

create or replace function public.save_pokemon_sheet(
    p_id uuid,p_expected_revision integer,p_trainer_id uuid,p_legacy_name text,
    p_nickname text,p_species text,p_pokedex_number text,p_primary_type text,
    p_secondary_type text,p_team_slot smallint,p_sheet_data jsonb
) returns setof public.pokemon
language plpgsql
security invoker
set search_path=''
as $$
declare v_row public.pokemon; v_existing public.pokemon; v_slot smallint;
begin
    if auth.uid() is null then raise exception 'Accesso richiesto' using errcode='42501'; end if;
    if p_trainer_id is null or (nullif(btrim(coalesce(p_nickname,'')),'') is null and nullif(btrim(coalesce(p_species,'')),'') is null)
       or jsonb_typeof(p_sheet_data) is distinct from 'object' then raise exception 'Dati Pokemon non validi'; end if;

    perform 1 from public.trainers t where t.id=p_trainer_id for update;
    if not found then raise exception 'Allenatore non trovato o non accessibile' using errcode='42501'; end if;

    if p_id is null then
        if p_expected_revision<>0 then raise exception 'Revisione iniziale non valida' using errcode='40001'; end if;
        v_slot:=p_team_slot;
        if v_slot is null then
            select s::smallint into v_slot from generate_series(1,6) s
            where not exists(select 1 from public.pokemon p where p.trainer_id=p_trainer_id and p.team_slot=s)
            order by s limit 1;
        end if;
        insert into public.pokemon(trainer_id,legacy_name,nickname,species,pokedex_number,primary_type,secondary_type,team_slot,sheet_data)
        values(p_trainer_id,p_legacy_name,nullif(btrim(coalesce(p_nickname,'')),''),nullif(btrim(coalesce(p_species,'')),''),
            p_pokedex_number,p_primary_type,p_secondary_type,v_slot,p_sheet_data)
        returning * into v_row;
    else
        select * into v_existing from public.pokemon where id=p_id;
        if not found or v_existing.trainer_id<>p_trainer_id then
            raise exception 'Pokemon non trovato o associazione cambiata' using errcode='42501';
        end if;
        update public.pokemon set legacy_name=p_legacy_name,nickname=nullif(btrim(coalesce(p_nickname,'')),''),
            species=nullif(btrim(coalesce(p_species,'')),''),pokedex_number=p_pokedex_number,
            primary_type=p_primary_type,secondary_type=p_secondary_type,team_slot=p_team_slot,sheet_data=p_sheet_data
        where id=p_id and revision=p_expected_revision returning * into v_row;
        if not found then
            raise exception 'La scheda è stata modificata altrove. Ricarica la versione più recente prima di salvare.' using errcode='40001';
        end if;
    end if;
    return next v_row;
end $$;

create or replace function public.reorder_pokemon_team(p_trainer_id uuid,p_ordered_pokemon_ids uuid[])
returns setof public.pokemon
language plpgsql
security invoker
set search_path=''
as $$
declare v_changed uuid[];
begin
    if auth.uid() is null then raise exception 'Accesso richiesto' using errcode='42501'; end if;
    if p_ordered_pokemon_ids is null or cardinality(p_ordered_pokemon_ids)>6
       or array_position(p_ordered_pokemon_ids,null) is not null
       or (select count(distinct x) from unnest(p_ordered_pokemon_ids) x)<>cardinality(p_ordered_pokemon_ids)
       then raise exception 'Ordine squadra non valido'; end if;

    perform 1 from public.trainers t where t.id=p_trainer_id for update;
    if not found then raise exception 'Allenatore non trovato o non accessibile' using errcode='42501'; end if;

    if (select count(*) from public.pokemon where trainer_id=p_trainer_id and team_slot is not null)
       <>cardinality(p_ordered_pokemon_ids)
       or exists(select 1 from unnest(p_ordered_pokemon_ids) x where not exists(
           select 1 from public.pokemon p where p.id=x and p.trainer_id=p_trainer_id and p.team_slot is not null))
       then raise exception 'La squadra è cambiata: aggiorna prima di riordinare' using errcode='40001'; end if;

    select coalesce(array_agg(p.id),'{}') into v_changed
    from public.pokemon p
    join unnest(p_ordered_pokemon_ids) with ordinality wanted(id,slot) on wanted.id=p.id
    where p.team_slot is distinct from wanted.slot::smallint;

    if cardinality(v_changed)>0 then
        perform set_config('pokerole.team_reorder_transition','on',true);
        update public.pokemon set team_slot=null where id=any(v_changed);
        perform set_config('pokerole.team_reorder_transition','off',true);
        update public.pokemon p set team_slot=wanted.slot::smallint
        from unnest(p_ordered_pokemon_ids) with ordinality wanted(id,slot)
        where p.id=wanted.id and p.id=any(v_changed);
    end if;
    return query select * from public.pokemon where trainer_id=p_trainer_id and team_slot is not null order by team_slot;
end $$;

create or replace function public.release_pokemon(p_pokemon_id uuid,p_trainer_id uuid)
returns uuid
language plpgsql
security invoker
set search_path=''
as $$
declare v_deleted uuid;
begin
    if auth.uid() is null then raise exception 'Accesso richiesto' using errcode='42501'; end if;
    perform 1 from public.trainers t where t.id=p_trainer_id for update;
    if not found then raise exception 'Allenatore non trovato o non accessibile' using errcode='42501'; end if;
    delete from public.pokemon p where p.id=p_pokemon_id and p.trainer_id=p_trainer_id returning p.id into v_deleted;
    if v_deleted is null then raise exception 'Pokemon non trovato o associazione cambiata: aggiorna le schede'; end if;
    return v_deleted;
end $$;

revoke all on function public.save_trainer_sheet(uuid,integer,text,text,text,text,text,text,jsonb) from public,anon;
revoke all on function public.save_pokemon_sheet(uuid,integer,uuid,text,text,text,text,text,text,smallint,jsonb) from public,anon;
revoke all on function public.reorder_pokemon_team(uuid,uuid[]) from public,anon;
grant execute on function public.save_trainer_sheet(uuid,integer,text,text,text,text,text,text,jsonb) to authenticated;
grant execute on function public.save_pokemon_sheet(uuid,integer,uuid,text,text,text,text,text,text,smallint,jsonb) to authenticated;
grant execute on function public.reorder_pokemon_team(uuid,uuid[]) to authenticated;

commit;
