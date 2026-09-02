-- Immagini personalizzate degli oggetti: eseguire dopo 06_item_catalog_inventory.sql.
-- Crea un bucket PUBBLICO dedicato: non caricare foto/documenti riservati.
-- Non modifica pokerole-media, schede, inventario, sprite originali o effetti.
-- Questa migrazione prepara solo Supabase. L'APK 0.10.2 non legge ancora il nuovo campo.
begin;

-- Non rendere accidentalmente pubblici file di un bucket privato preesistente.
do $$
begin
    if exists(select 1 from storage.buckets where id='pokerole-items' and not public)
       and exists(select 1 from storage.objects where bucket_id='pokerole-items') then
        raise exception 'pokerole-items contiene già file privati: controllare il bucket prima di renderlo pubblico';
    end if;
end;
$$;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('pokerole-items','pokerole-items',true,2097152,array['image/png','image/jpeg','image/webp'])
on conflict(id) do update set public=true,file_size_limit=excluded.file_size_limit,
    allowed_mime_types=excluded.allowed_mime_types;

alter table public.catalog_items add column if not exists custom_image_path text;
do $$
begin
    if not exists(select 1 from pg_constraint where conname='catalog_items_custom_image_path_check'
        and conrelid='public.catalog_items'::regclass) then
        alter table public.catalog_items add constraint catalog_items_custom_image_path_check check (
            custom_image_path is null or (
                length(custom_image_path)<=512 and
                custom_image_path ~* '^[a-z0-9_-]+(/[a-z0-9_-]+)*[.](png|jpg|jpeg|webp)$'
            )
        );
    end if;
end;
$$;
comment on column public.catalog_items.custom_image_path is
    'Percorso relativo nel bucket pubblico pokerole-items, es. oggetti/amuleto-v1.png. NULL = sprite originale. Non inserire URL o Base64.';

-- SELECT serve al DM anche per listare/sostituire file tramite Storage.
-- I download pubblici dei file non richiedono una policy SELECT per i player.
drop policy if exists pokerole_items_dm_manage on storage.objects;
create policy pokerole_items_dm_manage on storage.objects for all to authenticated
using(bucket_id='pokerole-items' and public.is_dm())
with check(bucket_id='pokerole-items' and public.is_dm() and length(name)<=512
    and name ~* '^[a-z0-9_-]+(/[a-z0-9_-]+)*[.](png|jpg|jpeg|webp)$');

-- Limitano eventuali policy permissive generiche senza cambiare gli altri bucket.
drop policy if exists pokerole_items_authenticated_guard on storage.objects;
create policy pokerole_items_authenticated_guard on storage.objects as restrictive for all to authenticated
using(bucket_id<>'pokerole-items' or public.is_dm())
with check(bucket_id<>'pokerole-items' or (public.is_dm() and length(name)<=512
    and name ~* '^[a-z0-9_-]+(/[a-z0-9_-]+)*[.](png|jpg|jpeg|webp)$'));

drop policy if exists pokerole_items_anon_guard on storage.objects;
create policy pokerole_items_anon_guard on storage.objects as restrictive for all to anon
using(bucket_id<>'pokerole-items') with check(bucket_id<>'pokerole-items');

-- Non concediamo scrittura diretta su catalog_items ai client: per ora il percorso
-- viene impostato dal proprietario del progetto tramite Table Editor/SQL Editor.
notify pgrst, 'reload schema';
commit;

select id,public,file_size_limit,allowed_mime_types from storage.buckets where id='pokerole-items';
select column_name,data_type,is_nullable from information_schema.columns
where table_schema='public' and table_name='catalog_items' and column_name='custom_image_path';
