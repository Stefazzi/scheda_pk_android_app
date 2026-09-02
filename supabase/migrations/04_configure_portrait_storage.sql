-- Pokerole Sheets Android
-- Policy per il bucket PRIVATO "pokerole-media".
-- Il percorso previsto è: trainers/<trainer_uuid>/portrait.webp

begin;

update storage.buckets
set public = false,
    file_size_limit = 2097152,
    allowed_mime_types = array['image/webp']::text[]
where id = 'pokerole-media';

drop policy if exists pokerole_media_select_trainer_portraits on storage.objects;
create policy pokerole_media_select_trainer_portraits
    on storage.objects for select
    to authenticated
    using (
        bucket_id = 'pokerole-media'
        and (storage.foldername(name))[1] = 'trainers'
        and storage.extension(name) = 'webp'
        and exists (
            select 1
            from public.trainers
            where trainers.id::text = (storage.foldername(name))[2]
              and (trainers.owner_id = auth.uid() or public.is_dm())
        )
    );

drop policy if exists pokerole_media_insert_trainer_portraits on storage.objects;
create policy pokerole_media_insert_trainer_portraits
    on storage.objects for insert
    to authenticated
    with check (
        bucket_id = 'pokerole-media'
        and (storage.foldername(name))[1] = 'trainers'
        and storage.extension(name) = 'webp'
        and storage.filename(name) = 'portrait.webp'
        and exists (
            select 1
            from public.trainers
            where trainers.id::text = (storage.foldername(name))[2]
              and (trainers.owner_id = auth.uid() or public.is_dm())
        )
    );

drop policy if exists pokerole_media_update_trainer_portraits on storage.objects;
create policy pokerole_media_update_trainer_portraits
    on storage.objects for update
    to authenticated
    using (
        bucket_id = 'pokerole-media'
        and (storage.foldername(name))[1] = 'trainers'
        and exists (
            select 1
            from public.trainers
            where trainers.id::text = (storage.foldername(name))[2]
              and (trainers.owner_id = auth.uid() or public.is_dm())
        )
    )
    with check (
        bucket_id = 'pokerole-media'
        and (storage.foldername(name))[1] = 'trainers'
        and storage.extension(name) = 'webp'
        and storage.filename(name) = 'portrait.webp'
        and exists (
            select 1
            from public.trainers
            where trainers.id::text = (storage.foldername(name))[2]
              and (trainers.owner_id = auth.uid() or public.is_dm())
        )
    );

drop policy if exists pokerole_media_delete_trainer_portraits on storage.objects;
create policy pokerole_media_delete_trainer_portraits
    on storage.objects for delete
    to authenticated
    using (
        bucket_id = 'pokerole-media'
        and (storage.foldername(name))[1] = 'trainers'
        and exists (
            select 1
            from public.trainers
            where trainers.id::text = (storage.foldername(name))[2]
              and (trainers.owner_id = auth.uid() or public.is_dm())
        )
    );

commit;
