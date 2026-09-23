-- Read-only baseline verifier. Run with a catalog-reading administrative role.
-- It changes no data and intentionally returns metadata only.
with column_specs as (
    select
        c.relname,
        string_agg(
            format('%s|%s|%s|%s|%s', a.attnum, a.attname,
                format_type(a.atttypid, a.atttypmod), a.attnotnull,
                coalesce(pg_get_expr(d.adbin, d.adrelid), '')),
            ';' order by a.attnum
        ) as spec
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    join pg_attribute a on a.attrelid = c.oid
        and a.attnum > 0 and not a.attisdropped
    left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
    where n.nspname = 'public' and c.relkind = 'r'
    group by c.relname
)
select relname, md5(spec) as column_signature
from column_specs
order by relname;

select c.relname, c.relrowsecurity, c.relforcerowsecurity, c.relacl
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind in ('r', 'v', 'S')
order by c.relkind, c.relname;

select c.relname as table_name, con.conname,
       pg_get_constraintdef(con.oid, true) as definition
from pg_constraint con
join pg_class c on c.oid = con.conrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
order by c.relname, con.conname;

select tablename, indexname, indexdef
from pg_indexes where schemaname = 'public'
order by tablename, indexname;

select c.relname as table_name, p.polname, p.polcmd,
       pg_get_expr(p.polqual, p.polrelid) as using_expression,
       pg_get_expr(p.polwithcheck, p.polrelid) as check_expression
from pg_policy p
join pg_class c on c.oid = p.polrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
order by c.relname, p.polname;

select p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')' as signature,
       p.prosecdef as security_definer, p.proacl,
       md5(pg_get_functiondef(p.oid)) as definition_md5
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
order by signature;

select n.nspname as schema_name, c.relname as table_name, t.tgname,
       pg_get_triggerdef(t.oid, true) as definition
from pg_trigger t
join pg_class c on c.oid = t.tgrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname in ('public', 'auth') and not t.tgisinternal
order by n.nspname, c.relname, t.tgname;
