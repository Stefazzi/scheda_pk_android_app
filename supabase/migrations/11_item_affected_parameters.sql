-- Run after 06–10. Adds visual equipment hints; does not change sheet values or legacy tables.
begin;

create or replace function public.valid_item_parameters(p_values text[])
returns boolean language sql immutable set search_path='' as $$
    select p_values is not null and cardinality(p_values)<=35
        and coalesce(array_ndims(p_values),1)=1 and array_position(p_values,null) is null
        and p_values <@ array[
            'attributes.strength','attributes.dexterity','attributes.vitality','attributes.special','attributes.insight',
            'social_attributes.tough','social_attributes.cool','social_attributes.beauty','social_attributes.cute','social_attributes.clever',
            'skills.fight.brawl','skills.fight.channel','skills.fight.clash','skills.fight.evasion','skills.fight.throw','skills.fight.weapons',
            'skills.survival.alert','skills.survival.athletic','skills.survival.nature','skills.survival.stealth',
            'skills.social.charm','skills.social.empathy','skills.social.etiquette','skills.social.intimidate','skills.social.perform',
            'skills.knowledge.crafts','skills.knowledge.lore','skills.knowledge.medicine','skills.knowledge.science',
            'quick.hp','quick.will','quick.physical_defense','quick.special_defense','quick.initiative','quick.evasion'
        ]::text[];
$$;
revoke all on function public.valid_item_parameters(text[]) from public,anon,authenticated;

alter table public.catalog_items add column if not exists affected_parameters text[] not null default '{}';
alter table public.catalog_items add column if not exists custom_affected_parameters text[];
alter table public.catalog_items drop constraint if exists catalog_items_affected_parameters_check;
alter table public.catalog_items add constraint catalog_items_affected_parameters_check
    check (public.valid_item_parameters(affected_parameters) and
        (custom_affected_parameters is null or public.valid_item_parameters(custom_affected_parameters)));

-- New RPC names keep older APKs compatible. Old editors preserve the metadata.
-- Text changes and metadata changes commit/rollback together, using the existing revision guard.
create or replace function public.update_catalog_item_with_parameters(
    p_id text,p_expected_revision integer,p_name text,p_description text,p_effect text,p_price text,
    p_restore boolean,p_affected_parameters text[]
) returns setof public.catalog_items language plpgsql security definer set search_path='' as $$
declare v_item public.catalog_items; v_parameters text[];
begin
    if auth.uid() is null or not public.is_dm() then raise exception 'Solo il DM può modificare il catalogo' using errcode='42501'; end if;
    if not public.valid_item_parameters(p_affected_parameters) then raise exception 'Parametri influenzati non validi'; end if;
    select coalesce(array_agg(distinct value order by value),'{}') into v_parameters from unnest(p_affected_parameters) as value;
    select * into v_item from public.update_catalog_item(p_id,p_expected_revision,p_name,p_description,p_effect,p_price,p_restore);
    update public.catalog_items set custom_affected_parameters=case when p_restore then null else v_parameters end
        where id=v_item.id returning * into v_item;
    return next v_item;
end;
$$;

create or replace function public.create_catalog_item_with_parameters(
    p_id uuid,p_name text,p_category text,p_description text,p_effect text,p_price text,p_icon_item_id text,
    p_affected_parameters text[]
) returns setof public.catalog_items language plpgsql security definer set search_path='' as $$
declare v_item public.catalog_items; v_existing public.catalog_items; v_parameters text[]; v_exists boolean;
begin
    if auth.uid() is null or not public.is_dm() then raise exception 'Solo il DM può creare oggetti' using errcode='42501'; end if;
    if p_id is null or not public.valid_item_parameters(p_affected_parameters) then raise exception 'Parametri influenzati non validi'; end if;
    select coalesce(array_agg(distinct value order by value),'{}') into v_parameters from unnest(p_affected_parameters) as value;
    -- Serialize retries sharing a request ID before testing immutable creation metadata.
    perform pg_advisory_xact_lock(hashtextextended('item-parameters-'||p_id::text,0));
    select * into v_existing from public.catalog_items where id='custom-'||p_id::text for update;
    v_exists:=found;
    if v_exists and v_existing.affected_parameters is distinct from v_parameters then
        raise exception 'Richiesta già usata con parametri diversi: aggiorna il catalogo' using errcode='40001';
    end if;
    select * into v_item from public.create_catalog_item(p_id,p_name,p_category,p_description,p_effect,p_price,p_icon_item_id);
    if not v_exists then
        update public.catalog_items set affected_parameters=v_parameters where id=v_item.id returning * into v_item;
    end if;
    return next v_item;
end;
$$;

revoke all on function public.update_catalog_item_with_parameters(text,integer,text,text,text,text,boolean,text[]) from public,anon;
revoke all on function public.create_catalog_item_with_parameters(uuid,text,text,text,text,text,text,text[]) from public,anon;
grant execute on function public.update_catalog_item_with_parameters(text,integer,text,text,text,text,boolean,text[]) to authenticated;
grant execute on function public.create_catalog_item_with_parameters(uuid,text,text,text,text,text,text,text[]) to authenticated;
notify pgrst,'reload schema';
commit;
