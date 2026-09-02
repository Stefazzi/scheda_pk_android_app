-- Dopo 09. Il DM può caricare immagini per tutti gli oggetti; cambiare lo sprite base solo per quelli custom.
-- Non cambia effetti, scorte o immagini esistenti. Nessun file viene eliminato.
begin;

-- Anche una modifica manuale delle immagini dal dashboard invalida editor obsoleti.
create or replace function public.track_catalog_image_revision()
returns trigger language plpgsql set search_path='' as $$
begin
    if new.sprite_path is distinct from old.sprite_path or new.custom_image_path is distinct from old.custom_image_path then
        if new.revision=old.revision then new.revision:=old.revision+1; end if;
        new.updated_at:=now();
    end if;
    return new;
end;
$$;
drop trigger if exists catalog_image_revision on public.catalog_items;
create trigger catalog_image_revision before update of sprite_path,custom_image_path on public.catalog_items
for each row execute function public.track_catalog_image_revision();
revoke all on function public.track_catalog_image_revision() from public,anon,authenticated;

create or replace function public.set_custom_item_image(
    p_id text,p_expected_revision integer,p_mode text,p_image_path text,p_source_item_id text
) returns setof public.catalog_items language plpgsql security definer set search_path='' as $$
declare v_item public.catalog_items;v_source public.catalog_items;v_sprite text;v_path text;
begin
    if auth.uid() is null or not public.is_dm() then raise exception 'Solo il DM può modificare le immagini' using errcode='42501'; end if;
    select * into v_item from public.catalog_items where id=p_id for update;
    if not found then raise exception 'Oggetto non disponibile'; end if;
    if p_expected_revision is null or v_item.revision<>p_expected_revision then
        raise exception 'Oggetto modificato altrove: aggiorna il catalogo e riprova' using errcode='40001';
    end if;
    if p_mode is null or p_mode not in ('upload','sprite','reset') then raise exception 'Operazione immagine non valida'; end if;
    v_sprite:=v_item.sprite_path;
    if p_mode='upload' then
        if p_image_path is null or length(p_image_path)>512 or p_image_path !~* '^[a-z0-9_-]+(/[a-z0-9_-]+)*[.](png|jpg|jpeg|webp)$'
            then raise exception 'Percorso immagine non valido'; end if;
        if not exists(select 1 from storage.objects where bucket_id='pokerole-items' and name=p_image_path)
            then raise exception 'Immagine non trovata nel bucket pokerole-items'; end if;
        v_path:=p_image_path;
    elsif p_mode='sprite' then
        if not v_item.is_custom then raise exception 'Il cambio sprite è disponibile solo sugli oggetti custom' using errcode='42501'; end if;
        select * into v_source from public.catalog_items where id=p_source_item_id;
        if not found or (v_source.sprite_path is null and v_source.custom_image_path is null)
            then raise exception 'Oggetto sorgente senza immagine disponibile'; end if;
        v_sprite:=v_source.sprite_path;v_path:=v_source.custom_image_path;
    else
        if v_item.is_custom then
            v_sprite:=v_item.original_data->>'Sprite';
            v_path:=v_item.original_data->>'CustomImagePath';
        else
            v_path:=null;
        end if;
    end if;
    update public.catalog_items set sprite_path=v_sprite,custom_image_path=v_path,
        revision=revision+1,updated_at=now() where id=p_id returning * into v_item;
    return next v_item;
end;
$$;
revoke all on function public.set_custom_item_image(text,integer,text,text,text) from public,anon;
grant execute on function public.set_custom_item_image(text,integer,text,text,text) to authenticated;

-- When creating a custom item, copy the selected image as well as its fallback sprite.
create or replace function public.create_catalog_item(p_id uuid,p_name text,p_category text,p_description text,p_effect text,p_price text,p_icon_item_id text
) returns setof public.catalog_items language plpgsql security definer set search_path='' as $$
declare v_item public.catalog_items; v_sprite text; v_custom_path text; v_original jsonb;
begin
    if auth.uid() is null or not public.is_dm() then raise exception 'Solo il DM può creare oggetti' using errcode='42501'; end if;
    if p_id is null or nullif(btrim(p_name),'') is null or length(p_name)>200 or length(coalesce(p_category,''))>200
        or length(coalesce(p_description,''))>20000 or length(coalesce(p_effect,''))>20000 or length(coalesce(p_price,''))>200 then
        raise exception 'Nome o testi non validi';
    end if;
    if p_icon_item_id is not null then
        select sprite_path,custom_image_path into v_sprite,v_custom_path from public.catalog_items where id=p_icon_item_id;
        if not found then raise exception 'Icona non disponibile: aggiorna il catalogo'; end if;
    end if;
    v_original := jsonb_build_object('Name',p_name,'Category',coalesce(p_category,''),'Description',coalesce(p_description,''),
        'Effect',coalesce(p_effect,''),'Price',coalesce(p_price,''),'Sprite',v_sprite,'CustomImagePath',v_custom_path,'Source','Custom DM');
    insert into public.catalog_items(id,name,category,description,effect,price,sprite_path,custom_image_path,original_data,is_custom,created_by)
    values('custom-'||p_id::text,p_name,coalesce(p_category,''),coalesce(p_description,''),coalesce(p_effect,''),coalesce(p_price,''),v_sprite,v_custom_path,v_original,true,auth.uid())
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
revoke all on function public.create_catalog_item(uuid,text,text,text,text,text,text) from public,anon;
grant execute on function public.create_catalog_item(uuid,text,text,text,text,text,text) to authenticated;
notify pgrst,'reload schema';
commit;
