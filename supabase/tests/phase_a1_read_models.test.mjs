import {PGlite} from '@electric-sql/pglite';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';

const repo=fileURLToPath(new URL('../../',import.meta.url));
const migration=await readFile(`${repo}/supabase/migrations/20260923191834_phase_a1_read_models_exact_lookup.sql`,'utf8');
const rollbackSql=await readFile(`${repo}/supabase/rollbacks/20260923191834_phase_a1_read_models_exact_lookup.sql`,'utf8');
const uid=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0');
const db=new PGlite();

await db.exec(`
create role anon; create role authenticated; create schema auth; create schema extensions;
create table auth.users(id uuid primary key,raw_user_meta_data jsonb default '{}',email text);
create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('test.uid',true),'')::uuid$$;
grant usage on schema public,auth to authenticated,anon;
`);
await db.exec((await readFile(`${repo}/supabase/migrations/01_create_android_schema.sql`,'utf8'))
  .replace('create extension if not exists pgcrypto with schema extensions;',''));
await db.exec(await readFile(`${repo}/supabase/migrations/06_item_catalog_inventory.sql`,'utf8'));

await db.exec(`
create table catalog_pokemon(rules_version text not null,id text not null,name text not null,primary key(rules_version,id));
create table catalog_moves(rules_version text not null,id text not null,name text not null,primary key(rules_version,id));
alter table catalog_pokemon enable row level security;
alter table catalog_moves enable row level security;
create policy catalog_pokemon_read on catalog_pokemon for select to authenticated using(true);
create policy catalog_moves_read on catalog_moves for select to authenticated using(true);
grant select on catalog_pokemon,catalog_moves to authenticated;
insert into catalog_pokemon values
 ('3.0','zygarde-10','Zygarde 10%'),('3.0','zygarde-50','Zygarde 50%'),
 ('3.0','zygarde-100','Zygarde 100%'),('3.0','under','Under_score');
insert into catalog_moves values
 ('3.0','percent','Power % Move'),('3.0','under','Under_score Move');
insert into catalog_items(id,name,custom_name) values
 ('potion','Potion',null),('custom-potion','Other',' Potion ');
`);

await db.exec(migration);

assert.equal((await db.query("select id from catalog_pokemon where rules_version='3.0' and name_key=lower(btrim($1))",['  zYgArDe 10%  '])).rows[0].id,'zygarde-10');
assert.equal((await db.query("select count(*)::int n from catalog_pokemon where name_key=lower(btrim($1))",['Zygarde 10%'])).rows[0].n,1);
assert.equal((await db.query("select id from catalog_pokemon where name_key=lower(btrim($1))",['under_SCORE'])).rows[0].id,'under');
assert.equal((await db.query("select id from catalog_moves where name_key=lower(btrim($1))",[' POWER % MOVE '])).rows[0].id,'percent');
assert.equal((await db.query("select id from catalog_moves where name_key=lower(btrim($1))",['under_SCORE move'])).rows[0].id,'under');
assert.equal((await db.query("select count(*)::int n from catalog_items where name_key='potion'")).rows[0].n,2);

for(const n of [1,2,3]) await db.query('insert into auth.users(id,email) values($1,$2)',[uid(n),`user${n}@example.invalid`]);
await db.query("update user_profiles set role='dm' where user_id=$1",[uid(3)]);
for(const n of [1,2]) await db.query(
  "insert into trainers(id,owner_id,trainer_name) values($1,$2,$3)",
  [uid(10+n),uid(n),`Trainer ${n}`]);

const good={
  quick_references:{hp:{actual:' 4 ',total:'8'},will:{actual:'2',total:'3'},def_spdef:{actual:'5',total:'6'},status_effect:' Burn ',initiative:'7',evasion:'8'},
  pokerole:{rank:'Rookie'},moves:[{id:'move1',value:'Power % Move'},{id:'move2',value:'Manual move'}]
};
const malformed={quick_references:{hp:{actual:'many',total:'9999999999'},initiative:'-1'},moves:{value:'not an array'}};
await db.query("insert into pokemon(id,trainer_id,nickname,species,team_slot,sheet_data) values($1,$2,'One','Species',1,$3),($4,$5,'Two','Species',1,$6)",
  [uid(21),uid(11),JSON.stringify(good),uid(22),uid(12),JSON.stringify(malformed)]);
await db.query("insert into trainer_inventory(trainer_id,slot_key,item_id,quantity) values($1,'left_1','potion',1),($2,'left_1','potion',1)",[uid(11),uid(12)]);

const login=async(n,role='authenticated')=>{
  await db.exec('reset role');
  await db.query("select set_config('test.uid',$1,false)",[n?uid(n):'']);
  await db.exec(`set role ${role}`);
};
const count=async table=>(await db.query(`select count(*)::int n from ${table}`)).rows[0].n;
const denied=async query=>assert.rejects(()=>db.query(query),e=>e.code==='42501');

await login(1);
assert.equal(await count('trainer_summary_v1'),1);
assert.equal(await count('pokemon_summary_v1'),1);
assert.equal(await count('trainer_inventory'),1);
assert.equal(await count('catalog_pokemon'),4);
const battle=(await db.query('select * from pokemon_battle_state_v1')).rows[0];
assert.equal(battle.current_hp,4); assert.equal(battle.max_hp,8); assert.equal(battle.status,'Burn');
const moves=(await db.query('select move_position,move_name from pokemon_moves_v1 order by move_position')).rows;
assert.deepEqual(moves,[{move_position:1,move_name:'Power % Move'},{move_position:2,move_name:'Manual move'}]);

await login(2);
assert.equal(await count('trainer_summary_v1'),1);
assert.equal((await db.query('select current_hp,initiative from pokemon_battle_state_v1')).rows[0].current_hp,null);
assert.equal(await count('pokemon_moves_v1'),0);

await login(3);
assert.equal(await count('trainer_summary_v1'),2);
assert.equal(await count('pokemon_summary_v1'),2);
assert.equal(await count('trainer_inventory'),2);

await login(0,'anon');
for(const relation of ['trainer_summary_v1','pokemon_summary_v1','pokemon_battle_state_v1','pokemon_moves_v1','catalog_pokemon','catalog_items'])
  await denied(`select * from ${relation}`);

await db.exec('reset role');
await db.exec(rollbackSql);
assert.equal((await db.query("select count(*)::int n from information_schema.views where table_schema='public' and table_name like '%_v1'")).rows[0].n,0);
assert.equal((await db.query("select count(*)::int n from information_schema.columns where table_schema='public' and table_name in ('catalog_pokemon','catalog_moves','catalog_items') and column_name='name_key'")).rows[0].n,0);
assert.equal((await db.query('select count(*)::int n from pokemon')).rows[0].n,2);
assert.equal((await db.query('select count(*)::int n from trainers')).rows[0].n,2);

const collisionDb=new PGlite();
await collisionDb.exec(`
create table catalog_pokemon(rules_version text,id text,name text);
create table catalog_moves(rules_version text,id text,name text);
create table catalog_items(id text,name text,custom_name text);
create table trainers(id uuid,owner_id uuid,trainer_name text,team text,age text,reputation text);
create table pokemon(id uuid,trainer_id uuid,nickname text,species text,pokedex_number text,primary_type text,secondary_type text,team_slot smallint,sheet_data jsonb);
insert into catalog_pokemon values('3.0','a','Same'),('3.0','b',' same ');
`);
await assert.rejects(()=>collisionDb.exec(migration),/normalized-name collisions/);
await collisionDb.exec('rollback');
assert.equal((await collisionDb.query("select count(*)::int n from information_schema.columns where table_name='catalog_pokemon' and column_name='name_key'")).rows[0].n,0);

console.log('OK Phase A1 SQL: exact special-character lookup, item ambiguity, safe projections, player/DM RLS, anon denial, atomic collision stop, and rollback.');
await collisionDb.close(); await db.close();
