-- Associazioni visive per gli strumenti standard del catalogo community 3.0.
-- Eseguire DOPO 11_item_affected_parameters.sql nel progetto Android.
-- Nessun aggiornamento di schede, borse, immagini, statistiche o formule.
-- Gli oggetti che modificano Damage/Accuracy/Power NON sono associati a Strength/Special.
-- Le condizioni dell'effetto restano manuali. Nessun bonus numerico viene applicato.
begin;
create temporary table standard_item_parameter_seed (
    id text primary key, parameters text[] not null,
    expected_description text not null, expected_effect text not null, note text not null
) on commit drop;
insert into standard_item_parameter_seed values
('choice-scarf',array['quick.initiative'],'Hit fast! Increase initiative by 3. Choose a Move, it gets the effect Reaction 5. All other Moves get their Power reduced by 3','OneUse: false','Solo Initiative; Reaction e Power delle mosse non sono attributi.'),
('eviolite',array['quick.physical_defense','quick.special_defense'],'A mineral that reacts to raw potential. Increase by 1 the Defense and Sp. Defense of a 1st or 2nd stage Pokémon.','OneUse: false
Boost: Defense SpecialDefense 
Value: 1','Verificare lo stadio evolutivo richiesto dalla descrizione.'),
('iron-ball',array['attributes.dexterity'],'A heavy ball chain that drags you down. Reduce the user’s Dexterity by 1, and remove immunity to Ground-Type','OneUse: false','Riduzione di Dexterity, non potenziamento; immunita gestita manualmente.'),
('light-ball',array['attributes.special','attributes.strength'],'A bright ball that harnesses electricity. Pikachu loves it and they get their Strength and Special Attributes increased by 1.','OneUse: false
ForPokemon: pikachu
Boost: Strength Special
Value: 1','Solo Pikachu, come da descrizione.'),
('lucky-punch',array['attributes.strength'],'Boxing gloves. Chansey loves them and gets the High Critical Effect to all Physical Moves and Strength increased by 2.','OneUse: false
ForPokemon: chansey
Boost: Strength
Value: 2','Solo Chansey; High Critical non modifica una statistica della scheda.'),
('power-increasers',array['attributes.dexterity'],'Weighted gear, that Reduce Dexterity by 1 but grant an Increase of 2 on another Attribute/Trait of your choice.','OneUse: false','Associazione parziale: il DM deve aggiungere il parametro scelto per il +2.'),
('quick-claw',array['quick.initiative'],'These light and sharp claws make you excited to test them out in combat. Increase user''s Initiative roll by 2.','OneUse: false
Boost: Initiative
Value: 2','Modifica Initiative.'),
('thick-club',array['attributes.strength'],'A strong club made with dried bone. Cubone & Marowak love it and get their Strength Increased by 2.','OneUse: false
ForPokemon: cubone marowak marowak-alolan-form
Boost: Strength
Value: 2','Solo le specie indicate nella descrizione (Cubone/Marowak).'),
('throat-spray',array['attributes.special'],'This spray relaxes vocal cords. Increase Special by 1 after using a Sound-Based Move.','OneUse: false
Boost: Special
Value: 1','Effetto condizionato all uso di una mossa Sound-Based.'),
('weakness-policy',array['attributes.special','attributes.strength'],'A policy of compensation for damages. Increase user''s Strength and Special by 1 after being hit with a Super-Effective Move.','OneUse: false','Effetto condizionato a un colpo Super-Effective subito.');

-- Only fill untouched defaults. Explicit DM overrides (including an empty list)
-- and non-empty existing associations are never overwritten.
-- Exact text guards also skip standard items whose effects were changed directly in SQL.
with updated as (
    update public.catalog_items as item
    set affected_parameters=seed.parameters, revision=item.revision+1, updated_at=now()
    from standard_item_parameter_seed as seed
    where item.id=seed.id and not item.is_custom
        and item.affected_parameters='{}'::text[]
        and item.custom_affected_parameters is null
        and item.description=seed.expected_description and item.effect=seed.expected_effect
        and (item.custom_description is null or item.custom_description=item.description)
        and (item.custom_effect is null or item.custom_effect=item.effect)
    returning item.id
)
select count(*) as oggetti_aggiornati from updated;

-- A result for every candidate makes missing/customized rows visible instead of hiding skips.
select seed.id, item.name,
    coalesce(item.custom_affected_parameters,item.affected_parameters) as parametri_effettivi,
    case
        when item.id is null then 'Oggetto assente: nessuna riga creata'
        when item.is_custom then 'Oggetto custom: non modificato'
        when item.custom_affected_parameters is not null then 'Scelta DM preservata (anche lista vuota)'
        when item.description<>seed.expected_description or item.effect<>seed.expected_effect
          or (item.custom_description is not null and item.custom_description<>item.description)
          or (item.custom_effect is not null and item.custom_effect<>item.effect)
            then 'Effetto personalizzato: associazione da verificare con il DM'
        when item.affected_parameters=seed.parameters then 'Associazione standard disponibile'
        else 'Associazione preesistente preservata'
    end as esito,
    seed.note
from standard_item_parameter_seed seed left join public.catalog_items item on item.id=seed.id
order by seed.id;
commit;
