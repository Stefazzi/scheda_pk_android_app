-- Read-only live RLS regression test for Phase A1.
-- Run as an administrative database session. It performs SELECTs only and rolls back.
begin;
select set_config('request.jwt.claim.sub', (
    select t.owner_id::text from public.trainers t
    join public.user_profiles u on u.user_id=t.owner_id and u.role='player'
    where exists(select 1 from public.pokemon p where p.trainer_id=t.id)
      and exists(select 1 from public.trainer_inventory i where i.trainer_id=t.id)
    limit 1), true);
set local role authenticated;
do $$ begin
    if auth.uid() is null then raise exception 'PLAYER: no test identity'; end if;
    if (select count(*) from public.trainers where owner_id=auth.uid())<>1 then raise exception 'PLAYER: own trainer not visible'; end if;
    if exists(select 1 from public.trainers where owner_id is distinct from auth.uid()) then raise exception 'PLAYER: another trainer is visible'; end if;
    if not exists(select 1 from public.pokemon) then raise exception 'PLAYER: own Pokemon not visible'; end if;
    if exists(select 1 from public.pokemon p join public.trainers t on t.id=p.trainer_id where t.owner_id is distinct from auth.uid()) then raise exception 'PLAYER: another Pokemon is visible'; end if;
    if not exists(select 1 from public.trainer_inventory) then raise exception 'PLAYER: own inventory not visible'; end if;
    if not exists(select 1 from public.catalog_pokemon) then raise exception 'PLAYER: catalog not readable'; end if;
    if (select count(*) from public.trainer_summary_v1)<>(select count(*) from public.trainers) then raise exception 'PLAYER: trainer view changed RLS'; end if;
    if (select count(*) from public.pokemon_summary_v1)<>(select count(*) from public.pokemon) then raise exception 'PLAYER: Pokemon view changed RLS'; end if;
    if exists(select 1 from public.pokemon_battle_state_v1 b where not exists(select 1 from public.pokemon p where p.id=b.pokemon_id)) then raise exception 'PLAYER: battle view leaks Pokemon'; end if;
    if exists(select 1 from public.pokemon_moves_v1 m where not exists(select 1 from public.pokemon p where p.id=m.pokemon_id)) then raise exception 'PLAYER: move view leaks Pokemon'; end if;
end $$;
rollback;

begin;
select set_config('request.jwt.claim.sub',(select user_id::text from public.user_profiles where role='dm' limit 1),true);
select set_config('pokerole_test.expected_trainers',(select count(*)::text from public.trainers),true);
select set_config('pokerole_test.expected_pokemon',(select count(*)::text from public.pokemon),true);
select set_config('pokerole_test.expected_inventory',(select count(*)::text from public.trainer_inventory),true);
set local role authenticated;
do $$ begin
    if not public.is_dm() then raise exception 'DM: no test identity'; end if;
    if (select count(*) from public.trainer_summary_v1)<>current_setting('pokerole_test.expected_trainers')::int then raise exception 'DM: trainers not fully visible'; end if;
    if (select count(*) from public.pokemon_summary_v1)<>current_setting('pokerole_test.expected_pokemon')::int then raise exception 'DM: Pokemon not fully visible'; end if;
    if (select count(*) from public.trainer_inventory)<>current_setting('pokerole_test.expected_inventory')::int then raise exception 'DM: inventory not fully visible'; end if;
end $$;
rollback;

begin;
set local role anon;
do $$ begin
    if has_table_privilege(current_user,'public.trainer_summary_v1','select')
       or has_table_privilege(current_user,'public.pokemon_summary_v1','select')
       or has_table_privilege(current_user,'public.pokemon_battle_state_v1','select')
       or has_table_privilege(current_user,'public.pokemon_moves_v1','select')
       or has_table_privilege(current_user,'public.catalog_pokemon','select')
    then raise exception 'ANON: authenticated-only data is readable'; end if;
end $$;
rollback;
