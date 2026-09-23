import {PGlite} from '@electric-sql/pglite';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';

const repo=fileURLToPath(new URL('../../',import.meta.url));
const migration=await readFile(`${repo}/supabase/migrations/20260923212131_phase_a2_revision_atomic_team_integrity.sql`,'utf8');
const teamGuard=await readFile(`${repo}/supabase/migrations/20260923213815_phase_a2_trainer_team_rpc_guard.sql`,'utf8');
const rollback=await readFile(`${repo}/supabase/rollbacks/20260923212131_phase_a2_revision_atomic_team_integrity.sql`,'utf8');
const teamGuardRollback=await readFile(`${repo}/supabase/rollbacks/20260923213815_phase_a2_trainer_team_rpc_guard.sql`,'utf8');
const db=new PGlite();
const uid=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0');

await db.exec(`create role anon;create role authenticated;create schema auth;create schema extensions;
 create table auth.users(id uuid primary key,raw_user_meta_data jsonb default '{}',email text);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('test.uid',true),'')::uuid$$;
 grant usage on schema public,auth to authenticated,anon;`);
await db.exec((await readFile(`${repo}/supabase/migrations/01_create_android_schema.sql`,'utf8')).replace('create extension if not exists pgcrypto with schema extensions;',''));
await db.exec(migration);
await db.exec(teamGuard);

const login=async(n,role='authenticated')=>{await db.exec('reset role');await db.query("select set_config('test.uid',$1,false)",[n?uid(n):'']);await db.exec(`set role ${role}`);};
const denied=(promise,code)=>assert.rejects(promise,e=>!code||e.code===code);
for(const n of [1,2,3])await db.query('insert into auth.users(id,email) values($1,$2)',[uid(n),`a2-${n}@example.invalid`]);
await db.query("update user_profiles set role='dm' where user_id=$1",[uid(3)]);

const trainerParams=(id,rev,name,json={unknown:{kept:true},pokemon_team:[]})=>[id,rev,name,name,'Blue','20','100','1',JSON.stringify(json)];
await login(3);
let trainer=(await db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(null,0,'Trainer A'))).rows[0];
let other=(await db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(null,0,'Trainer B'))).rows[0];
await db.exec('reset role');
await db.query('update trainers set owner_id=$1 where id=$2',[uid(1),trainer.id]);
await db.query('update trainers set owner_id=$1 where id=$2',[uid(2),other.id]);
let trainerRevision=(await db.query('select revision from trainers where id=$1',[trainer.id])).rows[0].revision;
let otherRevision=(await db.query('select revision from trainers where id=$1',[other.id])).rows[0].revision;

await login(1);
trainer=(await db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(trainer.id,trainerRevision,'Trainer A edited',{unknown:{kept:true},pokemon_team:[]}))).rows[0];
assert.equal(trainer.revision,trainerRevision+1);assert.deepEqual(trainer.sheet_data.unknown,{kept:true});
await denied(db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(trainer.id,trainerRevision,'Stale')), '40001');
await denied(db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(other.id,otherRevision,'Forbidden')), '40001');
await db.exec('reset role');
const trainerHistoryBeforeTeam=(await db.query("select count(*)::int n from sheet_versions where entity_type='trainer' and entity_id=$1",[trainer.id])).rows[0].n;
await login(1);

const pokemonParams=(id,rev,trainerId,key,nick,slot=null,json={unknown:'survives',moves:[]})=>
  [id,rev,trainerId,key,nick,'Species','001','Normal',null,slot,JSON.stringify(json)];
let first=(await db.query('select * from save_pokemon_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)',pokemonParams(null,0,trainer.id,'one','One'))).rows[0];
let second=(await db.query('select * from save_pokemon_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)',pokemonParams(null,0,trainer.id,'two','Two'))).rows[0];
assert.equal(first.team_slot,1);assert.equal(second.team_slot,2);
assert.equal((await db.query('select revision from trainers where id=$1',[trainer.id])).rows[0].revision,trainerRevision+1);

first=(await db.query('select * from save_pokemon_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)',pokemonParams(first.id,1,trainer.id,'one','One edited',1))).rows[0];
assert.equal(first.revision,2);assert.equal(first.sheet_data.unknown,'survives');
await denied(db.query('select * from save_pokemon_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)',pokemonParams(first.id,1,trainer.id,'one','Stale',1)),'40001');
await denied(db.query('select * from save_pokemon_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)',pokemonParams(first.id,2,other.id,'one','Transfer',1)),'42501');

const beforeReorder=(await db.query('select id,revision from pokemon where trainer_id=$1 order by id',[trainer.id])).rows;
const ordered=(await db.query('select * from reorder_pokemon_team($1,$2)',[trainer.id,[second.id,first.id]])).rows;
assert.deepEqual(ordered.map(x=>x.id),[second.id,first.id]);
const afterReorder=(await db.query('select id,revision from pokemon where trainer_id=$1 order by id',[trainer.id])).rows;
for(const row of afterReorder)assert.equal(row.revision,beforeReorder.find(x=>x.id===row.id).revision+1);
const legacy=(await db.query("select sheet_data->'pokemon_team' team,revision from trainers where id=$1",[trainer.id])).rows[0];
assert.equal(legacy.revision,trainerRevision+1);assert.deepEqual(legacy.team.slice(0,2).map(x=>x.value),['Two','One edited']);
await db.exec('reset role');
assert.equal((await db.query("select count(*)::int n from sheet_versions where entity_type='trainer' and entity_id=$1",[trainer.id])).rows[0].n,trainerHistoryBeforeTeam);

await login(1);
const trainerAfterReorder=(await db.query('select revision from trainers where id=$1',[trainer.id])).rows[0].revision;
trainer=(await db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(trainer.id,trainerAfterReorder,'Trainer A',{unknown:{kept:true},pokemon_team:[]}))).rows[0];
assert.deepEqual(trainer.sheet_data.pokemon_team.slice(0,2).map(x=>x.value),['Two','One edited']);

await db.exec('reset role');
await assert.rejects(db.query("insert into pokemon(trainer_id,legacy_name,nickname,species,team_slot) values($1,'duplicate','Dup','Species',1)",[trainer.id]),e=>e.code==='23505');

await login(1);
await db.query('select release_pokemon($1,$2)',[second.id,trainer.id]);
assert.deepEqual((await db.query("select sheet_data->'pokemon_team' team from trainers where id=$1",[trainer.id])).rows[0].team.slice(0,2).map(x=>x.value),['','One edited']);
await denied(db.query('select * from reorder_pokemon_team($1,$2)',[other.id,[]]),'42501');

await login(3);
other=(await db.query('select * from save_trainer_sheet($1,$2,$3,$4,$5,$6,$7,$8,$9)',trainerParams(other.id,otherRevision,'Trainer B DM'))).rows[0];
assert.equal(other.revision,otherRevision+1);

await login(0,'anon');
await denied(db.query('select * from save_trainer_sheet(null,0,null,\'Anon\',null,null,null,null,\'{}\')'),'42501');

await db.exec('reset role');
await db.exec(teamGuardRollback);
await db.exec(rollback);
assert.equal((await db.query("select count(*)::int n from information_schema.columns where table_schema='public' and table_name in ('trainers','pokemon') and column_name='revision'")).rows[0].n,0);
assert.equal((await db.query("select count(*)::int n from pg_indexes where schemaname='public' and indexname='pokemon_trainer_active_team_slot_key'")).rows[0].n,0);

console.log('OK Phase A2 SQL: revisions, stale conflicts, ownership/DM, atomic slots/reorder, legacy team sync, history suppression, uniqueness, release, anon denial, rollback.');
await db.close();
