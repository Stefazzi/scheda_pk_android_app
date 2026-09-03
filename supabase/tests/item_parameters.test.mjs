import {PGlite} from '@electric-sql/pglite';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const repo=process.env.POKEROLE_REPO || fileURLToPath(new URL('../../',import.meta.url));
const db=new PGlite();
await db.exec(`create role anon;create role authenticated;create schema auth;create schema storage;create schema extensions;
 create table auth.users(id uuid primary key,raw_user_meta_data jsonb default '{}',email text);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('test.uid',true),'')::uuid$$;
 create table storage.buckets(id text primary key,name text,public boolean default false,file_size_limit bigint,allowed_mime_types text[]);
 create table storage.objects(bucket_id text references storage.buckets(id),name text,primary key(bucket_id,name));
 alter table storage.objects enable row level security;
 grant usage on schema public,auth,storage to authenticated,anon;
 grant select,insert,update,delete on storage.objects to authenticated,anon;`);
for (const name of ['01_create_android_schema','06_item_catalog_inventory','07_seed_item_catalog','09_configure_item_image_storage','10_edit_custom_item_images']) {
    await db.exec((await readFile(`${repo}/supabase/migrations/${name}.sql`,'utf8')).replace('create extension if not exists pgcrypto with schema extensions;',''));
}
const before=(await db.query('select * from catalog_items order by id')).rows;
const migration=await readFile(`${repo}/supabase/migrations/11_item_affected_parameters.sql`,'utf8');
await db.exec(migration);await db.exec(migration);
const all=(await db.query('select * from catalog_items order by id')).rows;
assert.deepEqual(all.map(({affected_parameters,custom_affected_parameters,...row})=>row),before);
assert.ok(all.every(r=>r.affected_parameters.length===0 && r.custom_affected_parameters===null));
const uid=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0');
for(const n of [1,2])await db.query('insert into auth.users(id,email) values($1,$2)',[uid(n),`test${n}@example.invalid`]);
await db.query("update user_profiles set role='dm' where user_id=$1",[uid(2)]);
const login=async(n,role='authenticated')=>{await db.exec('reset role');await db.query("select set_config('test.uid',$1,false)",[n?uid(n):'']);await db.exec(`set role ${role}`);};
const params=['attributes.strength','social_attributes.cool','skills.fight.clash','quick.hp'];
const create=async(n,p=params)=>(await db.query('select * from create_catalog_item_with_parameters($1,$2,$3,$4,$5,$6,$7,$8)',[uid(n),'Charm','Custom','Description','Effect','10','potion',p])).rows[0];
const update=async(item,p=params,restore=false)=>(await db.query('select * from update_catalog_item_with_parameters($1,$2,$3,$4,$5,$6,$7,$8)',[item.id,item.revision,'Edited','New description','New effect','20',restore,p])).rows[0];
await login(1);await assert.rejects(()=>create(50));await assert.rejects(()=>update(all[0]));
await assert.rejects(()=>db.query("update catalog_items set affected_parameters=array['quick.hp'] where id='potion'"));
await login(0,'anon');await assert.rejects(()=>create(50));await assert.rejects(()=>update(all[0]));
await login(2);
let custom=await create(50);
assert.deepEqual(custom.affected_parameters,[...params].sort());
assert.equal(custom.sprite_path,'potion.png');assert.equal(custom.custom_affected_parameters,null);
assert.deepEqual(await create(50,[...params].reverse()),custom);
await assert.rejects(()=>create(50,['quick.will']));
const original=custom;
custom=await update(custom,['quick.will']);
assert.equal(custom.revision,original.revision+1);
assert.deepEqual(custom.custom_affected_parameters,['quick.will']);
assert.deepEqual(custom.affected_parameters,original.affected_parameters);
assert.deepEqual(custom.original_data,original.original_data);
await assert.rejects(()=>update(original,[]));
for (const p of [null,['unknown'],['attributes.strength',null],['quick.hp',...Array(35).fill('quick.hp')]]) {
    await assert.rejects(()=>create(51,p));await assert.rejects(()=>update(custom,p));
}
assert.deepEqual((await db.query('select * from catalog_items where id=$1',[custom.id])).rows[0],custom);
custom=await update(custom,[]);
assert.deepEqual(custom.custom_affected_parameters,[]); // explicitly disabled, not inherited
custom=await update(custom,[],true);
assert.equal(custom.custom_affected_parameters,null);assert.equal(custom.custom_name,null);
assert.deepEqual(custom.affected_parameters,original.affected_parameters);
custom=await update(custom,['skills.fight.clash']);
const oldClient=(await db.query('select * from update_catalog_item($1,$2,$3,$4,$5,$6,$7)',[custom.id,custom.revision,'Old APK','desc','effect','10',false])).rows[0];
assert.deepEqual(oldClient.custom_affected_parameters,custom.custom_affected_parameters);
assert.deepEqual(oldClient.affected_parameters,custom.affected_parameters);
await db.exec('reset role');await db.exec(migration);
assert.deepEqual((await db.query('select * from catalog_items where id=$1',[custom.id])).rows[0],oldClient);
await assert.rejects(()=>db.query("update catalog_items set affected_parameters=array['role.dm'] where id='potion'"));
// Keep SQL whitelist synchronized with every field displayed by the Android selector.
const source=await readFile(`${repo}/android-app/app/src/main/java/it/stefazzi/pokerolesheets/data/LegacySheet.kt`,'utf8');
const keys=[...source.substring(source.indexOf('object SheetStats'),source.indexOf('data class EditableSheet')).matchAll(/"((?:attributes|social_attributes|skills)\.[a-z.]+)" to/g)].map(m=>m[1]);
keys.push('quick.hp','quick.will','quick.physical_defense','quick.special_defense','quick.initiative','quick.evasion');
assert.equal(keys.length,35);
assert.equal((await db.query('select valid_item_parameters($1) as valid',[keys])).rows[0].valid,true);
assert.equal((await db.query('select count(*)::int as n from trainers')).rows[0].n,0);
assert.equal((await db.query('select count(*)::int as n from pokemon')).rows[0].n,0);
console.log('OK item parameters: DM-only, all 35 keys, atomic revision guard, retry safety, original reset, old APK compatibility, preservation on repeat migration.');

// Standard defaults are a separate, repeatable data migration, not an app formula change.
const standardSeed=await readFile(`${repo}/supabase/migrations/12_seed_standard_item_parameters.sql`,'utf8');
const expected={
    'choice-scarf':['quick.initiative'],
    'eviolite':['quick.physical_defense','quick.special_defense'],
    'iron-ball':['attributes.dexterity'],
    'light-ball':['attributes.special','attributes.strength'],
    'lucky-punch':['attributes.strength'],
    'power-increasers':['attributes.dexterity'],
    'quick-claw':['quick.initiative'],
    'thick-club':['attributes.strength'],
    'throat-spray':['attributes.special'],
    'weakness-policy':['attributes.special','attributes.strength'],
};
const snapshot=async()=> (await db.query('select * from catalog_items order by id')).rows;
const beforeSeed=await snapshot();
await db.exec(standardSeed);
const afterSeed=await snapshot();
for(const row of afterSeed) {
    const previous=beforeSeed.find(r=>r.id===row.id);
    if(expected[row.id]) {
        assert.deepEqual(row.affected_parameters,expected[row.id],row.id);
        assert.equal(row.revision,previous.revision+1,row.id);
        const {affected_parameters,revision,updated_at,...untouched}=row;
        const {affected_parameters:a,revision:r,updated_at:u,...prior}=previous;
        assert.deepEqual(untouched,prior,row.id);
    } else assert.deepEqual(row,previous,row.id);
}
await db.exec(standardSeed);assert.deepEqual(await snapshot(),afterSeed);
// The player reads the standard metadata through the same table/API as custom items.
await login(1);
assert.deepEqual((await db.query("select affected_parameters from catalog_items where id='eviolite'")).rows[0].affected_parameters,expected.eviolite);
await assert.rejects(()=>update(afterSeed.find(r=>r.id==='eviolite'),[]));
await login(2);
let standard=await update(afterSeed.find(r=>r.id==='eviolite'),['quick.hp']);
assert.deepEqual(standard.custom_affected_parameters,['quick.hp']);
standard=await update(standard,[],true);
assert.equal(standard.custom_affected_parameters,null);
assert.deepEqual(standard.affected_parameters,expected.eviolite);
await db.exec('reset role');
// Simulate pre-existing campaign edits BEFORE first import. All must survive the seed.
await db.query("update catalog_items set affected_parameters='{}' where id=any($1)",[Object.keys(expected)]);
await db.exec(`
    update catalog_items set custom_affected_parameters='{}' where id='quick-claw';
    update catalog_items set custom_affected_parameters=array['quick.hp'] where id='eviolite';
    update catalog_items set affected_parameters=array['social_attributes.cool'] where id='thick-club';
    update catalog_items set custom_description='Campaign-specific description' where id='light-ball';
    update catalog_items set custom_effect='Campaign-specific effect' where id='lucky-punch';
    update catalog_items set effect='Changed directly in SQL' where id='choice-scarf';
    update catalog_items set is_custom=true where id='iron-ball';
    update catalog_items set description='Changed directly in SQL' where id='power-increasers';
    delete from catalog_items where id='weakness-policy';
`);
const guardedBefore=await snapshot();await db.exec(standardSeed);
const guardedAfter=await snapshot();
for(const row of guardedAfter.filter(r=>r.id!=='throat-spray')) assert.deepEqual(row,guardedBefore.find(r=>r.id===row.id),row.id);
assert.equal(guardedAfter.length,guardedBefore.length);
assert.deepEqual(guardedAfter.find(r=>r.id==='throat-spray').affected_parameters,['attributes.special']);
await db.exec(standardSeed);assert.deepEqual(await snapshot(),guardedAfter);
console.log('OK standard parameters: 10 mapped items, no false move-damage/stat association, one revision increment, player read, DM edit/reset, repeatability, preserved custom/empty overrides and modified descriptions/effects.');
await db.close();
