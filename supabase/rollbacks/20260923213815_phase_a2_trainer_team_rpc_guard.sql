-- Apply before the main Phase A2 rollback. Restores the original A2 RPC behavior.
begin;

create or replace function public.save_trainer_sheet(
    p_id uuid,p_expected_revision integer,p_legacy_name text,p_trainer_name text,
    p_team text,p_age text,p_money text,p_reputation text,p_sheet_data jsonb
) returns setof public.trainers
language plpgsql security invoker set search_path=''
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

commit;
