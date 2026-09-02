-- v0.10.0. Nuove tabelle Android; nessuna conversione/cancellazione degli slot JSON legacy.
-- Installare dopo 01_create_android_schema.sql, poi 07_seed_item_catalog.sql.
begin;
create table if not exists public.catalog_items (
    id text primary key,
    name text not null check (btrim(name) <> ''),
    category text not null default '',
    description text not null default '',
    effect text not null default '',
    price text not null default '',
    sprite_path text,
    is_custom boolean not null default false,
    created_by uuid references auth.users(id) on delete set null,
    original_data jsonb not null default '{}'::jsonb,
    custom_name text check (custom_name is null or btrim(custom_name) <> ''),
    custom_description text,
    custom_effect text,
    custom_price text,
    revision integer not null default 1,
    updated_at timestamptz not null default now()
);
create table if not exists public.trainer_inventory (
    trainer_id uuid not null references public.trainers(id) on delete cascade,
    slot_key text not null check (slot_key ~ '^(left|right)_([1-9]|1[0-5])$'),
    item_id text references public.catalog_items(id),
    free_text text not null default '',
    quantity integer not null default 0 check (quantity between 0 and 9999),
    notes text not null default '',
    custom_name text check (custom_name is null or btrim(custom_name) <> ''),
    custom_description text,
    custom_effect text,
    revision integer not null default 1,
    updated_at timestamptz not null default now(),
    primary key(trainer_id,slot_key)
);
alter table public.catalog_items enable row level security;
alter table public.trainer_inventory enable row level security;
drop policy if exists catalog_items_read on public.catalog_items;
create policy catalog_items_read on public.catalog_items for select to authenticated using (true);
drop policy if exists trainer_inventory_read on public.trainer_inventory;
create policy trainer_inventory_read on public.trainer_inventory for select to authenticated using (
    public.is_dm() or exists(select 1 from public.trainers t where t.id=trainer_id and t.owner_id=auth.uid())
);
revoke all on public.catalog_items,public.trainer_inventory from public,anon,authenticated;
grant select on public.catalog_items,public.trainer_inventory to authenticated;

-- Le scritture passano solo da funzioni che verificano ruolo e revisione.
-- NULL nelle personalizzazioni significa eredita, stringa vuota significa effetto/descrizione vuoti intenzionalmente.
create or replace function public.create_catalog_item(
    p_id uuid,p_name text,p_category text,p_description text,p_effect text,p_price text,p_icon_item_id text
) returns setof public.catalog_items language plpgsql security definer set search_path='' as $$
declare v_item public.catalog_items; v_sprite text; v_original jsonb;
begin
    if auth.uid() is null or not public.is_dm() then raise exception 'Solo il DM può creare oggetti' using errcode='42501'; end if;
    if p_id is null or nullif(btrim(p_name),'') is null or length(p_name)>200 or length(coalesce(p_category,''))>200
        or length(coalesce(p_description,''))>20000 or length(coalesce(p_effect,''))>20000 or length(coalesce(p_price,''))>200 then
        raise exception 'Nome o testi non validi';
    end if;
    if p_icon_item_id is not null then
        select sprite_path into v_sprite from public.catalog_items where id=p_icon_item_id;
        if not found then raise exception 'Icona non disponibile: aggiorna il catalogo'; end if;
    end if;
    v_original := jsonb_build_object('Name',p_name,'Category',coalesce(p_category,''),'Description',coalesce(p_description,''),
        'Effect',coalesce(p_effect,''),'Price',coalesce(p_price,''),'Sprite',v_sprite,'Source','Custom DM');
    insert into public.catalog_items(id,name,category,description,effect,price,sprite_path,original_data,is_custom,created_by)
    values('custom-'||p_id::text,p_name,coalesce(p_category,''),coalesce(p_description,''),coalesce(p_effect,''),coalesce(p_price,''),v_sprite,v_original,true,auth.uid())
    on conflict(id) do nothing returning * into v_item;
    if not found then
        -- A retry with the same request id must never create a duplicate or overwrite another item.
        select * into v_item from public.catalog_items where id='custom-'||p_id::text and created_by=auth.uid() and original_data=v_original;
        if not found then raise exception 'Richiesta già usata con dati diversi: aggiorna il catalogo' using errcode='40001'; end if;
    end if;
    return next v_item;
    return;
end;
$$;

create or replace function public.update_catalog_item(
    p_id text,p_expected_revision integer,p_name text,p_description text,p_effect text,p_price text,p_restore boolean
) returns setof public.catalog_items language plpgsql security definer set search_path='' as $$
declare v_item public.catalog_items;
begin
    if auth.uid() is null or not public.is_dm() then raise exception 'Solo il DM può modificare il catalogo' using errcode='42501'; end if;
    if p_restore is null then raise exception 'Operazione non valida'; end if;
    if not p_restore and (nullif(btrim(p_name),'') is null or length(p_name)>200 or length(coalesce(p_description,''))>20000 or length(coalesce(p_effect,''))>20000 or length(coalesce(p_price,''))>200) then
        raise exception 'Nome o testi non validi';
    end if;
    update public.catalog_items set
        custom_name=case when p_restore then null else p_name end,
        custom_description=case when p_restore then null else coalesce(p_description,'') end,
        custom_effect=case when p_restore then null else coalesce(p_effect,'') end,
        custom_price=case when p_restore then null else coalesce(p_price,'') end,
        revision=revision+1, updated_at=now()
    where id=p_id and revision=p_expected_revision returning * into v_item;
    if not found then raise exception 'Oggetto aggiornato da un altro utente o non disponibile: aggiorna il catalogo' using errcode='40001'; end if;
    return next v_item;
    return;
end;
$$;

create or replace function public.save_inventory_slot(
    p_trainer_id uuid,p_slot_key text,p_expected_revision integer,
    p_item_id text,p_free_text text,p_quantity integer,p_notes text,
    p_custom_name text,p_custom_description text,p_custom_effect text
) returns setof public.trainer_inventory language plpgsql security definer set search_path='' as $$
declare v_old public.trainer_inventory; v_result public.trainer_inventory; v_dm boolean; v_same boolean;
begin
    if auth.uid() is null then raise exception 'Accesso richiesto' using errcode='42501'; end if;
    v_dm := public.is_dm();
    perform 1 from public.trainers t where t.id=p_trainer_id and (v_dm or t.owner_id=auth.uid()) for update;
    if not found then raise exception 'Borsa non accessibile' using errcode='42501'; end if;
    if p_slot_key is null or p_slot_key !~ '^(left|right)_([1-9]|1[0-5])$' or p_quantity is null or p_quantity not between 0 and 9999
       or length(coalesce(p_free_text,''))>200 or length(coalesce(p_notes,''))>10000
       or length(coalesce(p_custom_name,''))>200 or length(coalesce(p_custom_description,''))>20000 or length(coalesce(p_custom_effect,''))>20000
       or (p_custom_name is not null and btrim(p_custom_name)='') then raise exception 'Slot, quantità o testi non validi'; end if;
    if not v_dm and (p_custom_name is not null or p_custom_description is not null or p_custom_effect is not null) then
        raise exception 'Solo il DM può personalizzare un esemplare' using errcode='42501';
    end if;
    if p_item_id is not null and not exists(select 1 from public.catalog_items where id=p_item_id) then raise exception 'Oggetto non trovato'; end if;
    select * into v_old from public.trainer_inventory where trainer_id=p_trainer_id and slot_key=p_slot_key;
    if p_expected_revision is null or coalesce(v_old.revision,0)<>p_expected_revision then
        raise exception 'Slot modificato altrove: aggiorna la borsa prima di salvare' using errcode='40001';
    end if;
    v_same := v_old.item_id is not distinct from p_item_id and v_old.free_text=coalesce(p_free_text,'');
    insert into public.trainer_inventory(trainer_id,slot_key,item_id,free_text,quantity,notes,custom_name,custom_description,custom_effect,revision)
    values(p_trainer_id,p_slot_key,p_item_id,coalesce(p_free_text,''),p_quantity,coalesce(p_notes,''),
        case when v_dm then p_custom_name when v_same then v_old.custom_name else null end,
        case when v_dm then p_custom_description when v_same then v_old.custom_description else null end,
        case when v_dm then p_custom_effect when v_same then v_old.custom_effect else null end,coalesce(v_old.revision,0)+1)
    on conflict(trainer_id,slot_key) do update set item_id=excluded.item_id,free_text=excluded.free_text,
        quantity=excluded.quantity,notes=excluded.notes,custom_name=excluded.custom_name,
        custom_description=excluded.custom_description,custom_effect=excluded.custom_effect,
        revision=excluded.revision,updated_at=now() returning * into v_result;
    return next v_result;
    return;
end;
$$;
revoke all on function public.create_catalog_item(uuid,text,text,text,text,text,text) from public,anon;
grant execute on function public.create_catalog_item(uuid,text,text,text,text,text,text) to authenticated;
revoke all on function public.update_catalog_item(text,integer,text,text,text,text,boolean) from public,anon;
revoke all on function public.save_inventory_slot(uuid,text,integer,text,text,integer,text,text,text,text) from public,anon;
grant execute on function public.update_catalog_item(text,integer,text,text,text,text,boolean) to authenticated;
grant execute on function public.save_inventory_slot(uuid,text,integer,text,text,integer,text,text,text,text) to authenticated;
commit;
