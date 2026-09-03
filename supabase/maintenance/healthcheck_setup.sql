-- Installazione MANUALE nel SQL Editor del solo progetto Android.
-- Non rieseguire import o vecchie migrazioni per installare questo controllo.
-- Non legge tabelle, non modifica schede, storico, Storage o policy RLS.
begin;

create or replace function public.pokerole_healthcheck()
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$ select true; $$;

revoke all on function public.pokerole_healthcheck() from public, anon, authenticated;
grant execute on function public.pokerole_healthcheck() to anon;

comment on function public.pokerole_healthcheck() is
 'Read-only connectivity check: returns true, no table access, no privileged execution.';

notify pgrst, 'reload schema';
commit;

-- Verifica SQL: deve restituire true. Verificare anche via Actions con chiave pubblica.
select public.pokerole_healthcheck() as ok;
