-- v0.9.0: installa la funzione Libera e le policy per i ritratti Pokemon.
-- Eseguire nel progetto Android, dopo 01 e 04. Non elimina alcuna scheda
-- all'installazione; la cancellazione avviene solo quando l'utente conferma in app.
begin;

create or replace function public.release_pokemon(p_pokemon_id uuid, p_trainer_id uuid)
returns uuid language plpgsql security invoker set search_path = '' as $$
declare
    v_deleted uuid;
    v_team jsonb;
begin
    if auth.uid() is null then
        raise exception 'Accesso richiesto' using errcode = '42501';
    end if;
    -- RLS e controllo esplicito: DM o proprietario dell'allenatore.
    perform 1 from public.trainers t
    where t.id = p_trainer_id and (t.owner_id = auth.uid() or public.is_dm())
    for update;
    if not found then
        raise exception 'Allenatore non trovato o non accessibile' using errcode = '42501';
    end if;
    delete from public.pokemon p
    where p.id = p_pokemon_id and p.trainer_id = p_trainer_id
    returning p.id into v_deleted;
    if v_deleted is null then
        raise exception 'Pokemon non trovato o associazione cambiata: aggiorna le schede';
    end if;
    -- Il trigger di backup esistente conserva la riga eliminata.
    -- Ricostruisce solo pokemon_team, preservando il resto del JSON allenatore.
    select jsonb_agg(jsonb_build_object(
        'slot', slots.slot, 'id', 'pk' || slots.slot::text,
        'value', coalesce((select coalesce(nullif(p.nickname, ''), p.species, '')
            from public.pokemon p where p.trainer_id = p_trainer_id and p.team_slot = slots.slot
            order by p.id limit 1), '')
    ) order by slots.slot) into v_team
    from generate_series(1, 6) as slots(slot);
    update public.trainers set sheet_data = jsonb_set(sheet_data, '{pokemon_team}', v_team, true)
    where id = p_trainer_id;
    if not found then raise exception 'Impossibile aggiornare la squadra: operazione annullata'; end if;
    return v_deleted;
end;
$$;
revoke all on function public.release_pokemon(uuid, uuid) from public, anon;
grant execute on function public.release_pokemon(uuid, uuid) to authenticated;

-- Percorso privato pokemon/<pokemon_uuid>/portrait.webp, stesso bucket/limite 2 MB.
-- La SELECT su pokemon e trainers continua a essere protetta dalle RLS esistenti.
drop policy if exists pokerole_media_select_pokemon_portraits on storage.objects;
create policy pokerole_media_select_pokemon_portraits on storage.objects for select to authenticated
using (bucket_id = 'pokerole-media' and (storage.foldername(name))[1] = 'pokemon'
    and storage.filename(name) = 'portrait.webp'
    and exists (select 1 from public.pokemon p join public.trainers t on t.id = p.trainer_id
        where p.id::text = (storage.foldername(name))[2] and (t.owner_id = auth.uid() or public.is_dm())));

drop policy if exists pokerole_media_insert_pokemon_portraits on storage.objects;
create policy pokerole_media_insert_pokemon_portraits on storage.objects for insert to authenticated
with check (bucket_id = 'pokerole-media' and (storage.foldername(name))[1] = 'pokemon'
    and storage.filename(name) = 'portrait.webp'
    and exists (select 1 from public.pokemon p join public.trainers t on t.id = p.trainer_id
        where p.id::text = (storage.foldername(name))[2] and (t.owner_id = auth.uid() or public.is_dm())));

drop policy if exists pokerole_media_update_pokemon_portraits on storage.objects;
create policy pokerole_media_update_pokemon_portraits on storage.objects for update to authenticated
using (bucket_id = 'pokerole-media' and (storage.foldername(name))[1] = 'pokemon'
    and storage.filename(name) = 'portrait.webp'
    and exists (select 1 from public.pokemon p join public.trainers t on t.id = p.trainer_id
        where p.id::text = (storage.foldername(name))[2] and (t.owner_id = auth.uid() or public.is_dm())))
with check (bucket_id = 'pokerole-media' and (storage.foldername(name))[1] = 'pokemon'
    and storage.filename(name) = 'portrait.webp'
    and exists (select 1 from public.pokemon p join public.trainers t on t.id = p.trainer_id
        where p.id::text = (storage.foldername(name))[2] and (t.owner_id = auth.uid() or public.is_dm())));
commit;
