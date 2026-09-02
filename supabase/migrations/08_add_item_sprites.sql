-- v0.10.2: eight additional verified community images. Install after 06 and 07.
-- Only fills missing sprite paths. Does not change sheets, stock, custom effects or existing icons.
-- Source: https://github.com/Pokerole-Software-Development/Pokerole-Data/tree/abbe22a7e42853c95d6602b97bb0034833b7c7bc/images/ItemSprites
-- The two tents share the community illustration; umbrella uses utility-umbrella art only, not its game rules.
begin;
update public.catalog_items as item
set sprite_path=images.path,revision=item.revision+1,updated_at=now()
from (values
    ('big-camping-tent','community/big-camping-tent.png'),
    ('small-camping-tent','community/small-camping-tent.png'),
    ('leek','community/leek.png'),
    ('mountain-bike','community/mountain-bike.png'),
    ('pokedex','community/pokedex.png'),
    ('regional-map','community/regional-map.png'),
    ('pokemon-repel','community/repel.png'),
    ('umbrella','community/utility-umbrella.png')
) as images(id,path)
where item.id=images.id and item.sprite_path is null and not item.is_custom;
commit;
select count(*) filter(where sprite_path is not null) as with_images,
       count(*) filter(where sprite_path is null) as without_images
from public.catalog_items where not is_custom;
