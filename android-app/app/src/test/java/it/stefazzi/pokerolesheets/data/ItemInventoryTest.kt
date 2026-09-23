package it.stefazzi.pokerolesheets.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ItemInventoryTest {
    @Test fun customImageComesBeforeOriginalSprite() {
        val item=CatalogItem("potion","Potion",spritePath="potion.png",customImagePath="oggetti/test.webp",revision=4)
        assertEquals(listOf("https://project.supabase.co/storage/v1/object/public/pokerole-items/oggetti/test.webp?v=4",item.spriteUrl),item.imageUrls("https://project.supabase.co/"))
        assertEquals(listOf(item.spriteUrl),item.copy(customImagePath=null).imageUrls("https://project.supabase.co"))
        assertEquals(emptyList<String>(),item.copy(customImagePath=null,spritePath=null).imageUrls("https://project.supabase.co"))
    }
    @Test fun customPathsCannotEscapeBucketOrLoadAnExternalHost() {
        val item=CatalogItem("potion","Potion",spritePath="potion.png")
        for(path in listOf("", "../x.png", "a/../x.png", "https://example.com/x.png", "/x.png", "x.png?token=a", "x.svg", "a%2fb.png", "a\\b.png")) {
            assertFalse(validItemImagePath(path))
            assertEquals(listOf(item.spriteUrl),item.copy(customImagePath=path).imageUrls("https://project.supabase.co"))
        }
        assertTrue(validItemImagePath("oggetti/My-item_1.JPG"))
        assertFalse(validItemImagePath("a".repeat(513)+".png"))
        assertEquals(listOf(item.spriteUrl),item.copy(customImagePath="x.png").imageUrls("http://project.supabase.co"))
        assertEquals(listOf(item.spriteUrl),item.copy(customImagePath="x.png").imageUrls("https://user:pass@project.supabase.co"))
    }
    @Test fun catalogCustomImageRoundTripKeepsFallback() {
        val item=CatalogItem("custom-a","Amulet",isCustom=true,spritePath="community/leek.png",customImagePath="oggetti/a.webp")
        assertEquals(item,Json.decodeFromString<CatalogItem>(Json.encodeToString(item)))
        assertEquals(2,item.imageUrls("https://project.supabase.co").size)
    }
    @Test fun cleanupOnlyClaimsFilesInsideTheItemsOwnFolder() {
        assertEquals("oggetti/custom-abc/file.webp",ownedItemImagePath("custom-abc","oggetti/custom-abc/file.webp"))
        assertNull(ownedItemImagePath("custom-abc","oggetti/other/file.webp"))
        assertNull(ownedItemImagePath("custom-abc","oggetti/legacy.webp"))
        assertNull(ownedItemImagePath("custom-abc","oggetti/custom-abc/../file.webp"))
        assertNull(ownedItemImagePath("bad/id","oggetti/bad/id/file.webp"))
    }
    @Test fun pokemonEquipmentSurvivesJsonRoundTripWithoutChangingLegacyItem() {
        val source=Json.parseToJsonElement("""{"header":{"pokemon_name":"Hatenna"},"quick_references":{"held_item":"Old Charm"},"equipment":{"keep":"future"}}""")
        val sheet=EditableSheet.from("Hatenna",source,Json)
        assertEquals("Old Charm",sheet.heldItem);assertEquals("",sheet.heldItemCatalogId)
        val accessory=EquipmentAccessory("a","Contest Ribbon",notes="Won at town fair")
        val edited=sheet.equipHeldItem(CatalogItem("leftovers","Leftovers")).copy(accessories=listOf(accessory))
        val json=edited.updatedJson()
        val loaded=EditableSheet.from("Hatenna",json,Json)
        assertEquals("leftovers",loaded.heldItemCatalogId);assertEquals("Leftovers",loaded.heldItem)
        assertEquals(listOf(accessory),loaded.accessories)
        assertEquals("future",json["equipment"]!!.jsonObject["keep"]!!.jsonPrimitive.content)
        assertEquals("Old Charm",sheet.heldItem)
    }
    @Test fun replacingOrRemovingHeldItemLeavesAccessoriesUntouched() {
        val sheet=EditableSheet.blank(true).copy(accessories=listOf(EquipmentAccessory("a","Ribbon")))
        val first=sheet.equipHeldItem(CatalogItem("first","First"))
        val next=first.equipHeldItem(CatalogItem("next","Next"))
        assertEquals("next",next.heldItemCatalogId);assertEquals(sheet.accessories,next.accessories)
        val removed=next.equipHeldItem(null)
        assertEquals("",removed.heldItem);assertEquals("",removed.heldItemCatalogId)
        assertEquals(sheet.accessories,removed.accessories)
    }
    @Test fun accessoryChangesDoNotAlterHeldItemOrInventory() {
        val sheet=EditableSheet.blank(true).copy(heldItem="Berry",heldItemCatalogId="berry",bagItemsLeft=listOf("Keep"))
        val accessory=EquipmentAccessory("a","Hat","custom-hat","Blue")
        val loaded=EditableSheet.from("Pokemon",sheet.copy(accessories=listOf(accessory)).updatedJson(),Json)
        assertEquals("Berry",loaded.heldItem);assertEquals("berry",loaded.heldItemCatalogId)
        assertEquals("Keep",loaded.bagItemsLeft.first());assertEquals(accessory,loaded.accessories.single())
    }
    @Test fun cleanupOnlyClearsLegacyArraysAndPreservesCountersAndOtherFields() {
        val sheet=EditableSheet.from("Trainer",Json.parseToJsonElement("""{
          "bag":{"potion":"4","super_potion":"2","hyper_potion":"1","items_left":["Old item"],"items_right":["Old key"],"keep":"note"},
          "unknown":{"keep":true}
        }"""),Json).copy(recordId="trainer")
        val cleaned=sheet.clearLegacyBag()
        assertTrue(cleaned.bagItemsLeft.all{it.isEmpty()});assertEquals(15,cleaned.bagItemsRight.size)
        assertEquals(sheet,cleaned.copy(bagItemsLeft=sheet.bagItemsLeft,bagItemsRight=sheet.bagItemsRight))
        val json=cleaned.updatedJson()
        assertEquals("4",json["bag"]!!.jsonObject["potion"]!!.jsonPrimitive.content)
        assertEquals("note",json["bag"]!!.jsonObject["keep"]!!.jsonPrimitive.content)
        assertEquals(sheet.raw["unknown"],json["unknown"])
        assertTrue(json["bag"]!!.jsonObject["items_left"]!!.jsonArray.all{it.jsonPrimitive.content.isEmpty()})
        assertEquals("Old item",sheet.bagItemsLeft.first())
        assertEquals(cleaned,cleaned.clearLegacyBag())
    }
    @Test fun quickPotionCountersUpdateOnlyTheirOwnField() {
        val sheet=EditableSheet.blank(false).copy(potion="4",superPotion="3",hyperPotion="2",bagItemsLeft=listOf("Keep"))
        assertEquals(listOf("4","3","2"),QuickBagItem.entries.map{it.quantity(sheet)})
        assertEquals(sheet.copy(potion="0"),QuickBagItem.POTION.updateQuantity(sheet,"0"))
        assertEquals(sheet.copy(superPotion="9"),QuickBagItem.SUPER_POTION.updateQuantity(sheet,"9"))
        assertEquals(sheet.copy(hyperPotion="9999"),QuickBagItem.HYPER_POTION.updateQuantity(sheet,"9999"))
        listOf("", "-1", "10000", "2.5", "unknown").forEach { q ->
            assertThrows(IllegalArgumentException::class.java){QuickBagItem.POTION.updateQuantity(sheet,q)}
        }
    }
    @Test fun quickPotionDetailsUseStableCatalogIdAndDmOverrides() {
        val item=CatalogItem("super-potion","Super Potion",customName="DM Potion",customDescription="New description",customEffect="New effect",spritePath="super-potion.png")
        val resolved=QuickBagItem.SUPER_POTION.catalogItem(listOf(item))
        assertEquals(item,resolved);assertEquals("DM Potion",resolved.displayName);assertEquals("New effect",resolved.displayEffect)
        QuickBagItem.entries.forEach { quick ->
            val fallback=quick.catalogItem(emptyList())
            assertEquals(quick.catalogId,fallback.id);assertNotNull(fallback.spriteUrl)
            assertEquals("",fallback.effect)
        }
    }
    @Test fun usingOnePreservesRemainingItemAndDmCustomization() {
        val slot=InventorySlot("trainer","left_1",itemId="potion",quantity=3,notes="Keep",customName="DM potion",customEffect="Heal 4",revision=5)
        assertEquals(slot.copy(quantity=2),slot.useOne())
        assertEquals(JsonNull,slot.useOne().parameters(false)["p_custom_effect"])
    }
    @Test fun usingLastUnitCreatesEmptySlotAtSameRevision() {
        val slot=InventorySlot("trainer","right_15",itemId="potion",freeText="Legacy",quantity=1,notes="Note",customName="DM potion",customEffect="Heal 4",revision=8)
        val consumed=slot.useOne()
        assertEquals(InventorySlot("trainer","right_15",revision=8),consumed)
        assertEquals(JsonNull,consumed.parameters(true)["p_item_id"])
        assertEquals(JsonPrimitive(8),consumed.parameters(true)["p_expected_revision"])
    }
    @Test fun emptyInventoryCannotBeUsed() {
        assertThrows(IllegalArgumentException::class.java){InventorySlot("trainer","left_1").useOne()}
    }
    private val potion = CatalogItem("potion", "Potion", description="Original description", effect="Original effect", spritePath="potion.png")
    @Test fun catalogAndInstanceOverridesHaveExplicitPrecedence() {
        val shared=potion.copy(customName="Campaign Potion",customEffect="Heal 3")
        val slot=InventorySlot("trainer","left_1",itemId=potion.id,quantity=1)
        assertEquals("Heal 3",slot.effect(shared))
        assertEquals("Original description",slot.description(shared))
        assertEquals("Special potion",slot.copy(customName="Special potion").name(shared))
        assertEquals("",slot.copy(customEffect="").effect(shared))
        assertEquals("Heal 3",slot.copy(customEffect=null).effect(shared))
        assertEquals("Original effect",potion.effect)
    }
    @Test fun selectingAnotherItemClearsInstanceOverrides() {
        val slot=InventorySlot("trainer","left_1",quantity=0,customName="Custom",customEffect="Heal 9")
        val chosen=slot.choose(potion)
        assertEquals(1,chosen.quantity);assertEquals("potion",chosen.itemId);assertFalse(chosen.customized)
        val cleared=chosen.choose(null)
        assertNull(cleared.itemId);assertEquals(0,cleared.quantity)
    }
    @Test fun legacyTextIsPreservedWithoutGuessingItemOrQuantity() {
        val sheet=EditableSheet.blank(false).copy(recordId="trainer",bagItemsLeft=listOf("Potion x12, used 2"))
        val slot=legacyInventorySlot(sheet,"left_1")
        assertEquals("Potion x12, used 2",slot.freeText);assertEquals(1,slot.quantity)
        assertNull(slot.itemId);assertEquals(0,slot.revision)
        assertEquals("Potion x12, used 2",sheet.bagItemsLeft.first())
        assertEquals(0,legacyInventorySlot(sheet,"right_15").quantity)
    }
    @Test fun slotsAndQuantitiesAreValidated() {
        listOf("left_0","left_16","right_99","left_1_extra").forEach { key ->
            assertThrows(IllegalArgumentException::class.java){InventorySlot("trainer",key).parameters(true)}
        }
        listOf(-1,10000).forEach { q ->assertThrows(IllegalArgumentException::class.java){InventorySlot("trainer","left_1",quantity=q).parameters(true)} }
        assertEquals(JsonPrimitive(9999),InventorySlot("trainer","right_15",quantity=9999).parameters(true)["p_quantity"])
    }
    @Test fun playerRpcNeverSubmitsPrivilegedOverrides() {
        val slot=InventorySlot("trainer","left_1",customName="DM name",customEffect="",customDescription="DM description")
        val player=slot.parameters(false)
        assertEquals(JsonNull,player["p_custom_name"]);assertEquals(JsonNull,player["p_custom_effect"])
        assertEquals(JsonNull,player["p_custom_description"]);assertEquals(JsonNull,player["p_item_id"])
        assertEquals(JsonPrimitive(""),slot.parameters(true)["p_custom_effect"])
    }
    @Test fun slotsRoundTripIncludingBlankOverridesAndRevision() {
        val slot=InventorySlot("trainer","right_2",itemId="custom-uuid",quantity=4,notes="Keep",customEffect="",revision=7)
        assertEquals(slot,Json.decodeFromString<InventorySlot>(Json.encodeToString(slot)))
    }
    @Test fun spritePathsAreRestrictedToPublicItemAssets() {
        assertTrue(potion.spriteUrl!!.endsWith("/items/potion.png"))
        assertNotNull(potion.copy(spritePath="gen9/example--bag.png").spriteUrl)
        listOf("../secret.png","https://example.com/potion.png","potion.svg","potion.png?x=1").forEach {
            assertNull(potion.copy(spritePath=it).spriteUrl)
        }
        assertNull(potion.copy(spritePath=null).spriteUrl)
    }
    @Test fun communitySpriteUrlsUsePinnedRepositoryOnly() {
        val item=CatalogItem("pokedex","Pokedex",spritePath="community/pokedex.png")
        assertEquals("https://raw.githubusercontent.com/Pokerole-Software-Development/Pokerole-Data/abbe22a7e42853c95d6602b97bb0034833b7c7bc/images/ItemSprites/pokedex.png",item.spriteUrl)
        assertNull(item.copy(spritePath="community/../private.png").spriteUrl)
        assertNull(item.copy(spritePath="community/https://example.com/x.png").spriteUrl)
    }
    @Test fun customCatalogItemsRetainTheirOwnCreationBaseline() {
        val item=CatalogItem("custom-uuid","Amulet",isCustom=true,effect="Base DM effect",customEffect="Updated effect")
        val decoded=Json.decodeFromString<CatalogItem>(Json.encodeToString(item))
        assertTrue(decoded.isCustom);assertEquals("Updated effect",decoded.displayEffect)
        assertEquals("Base DM effect",decoded.copy(customEffect=null).displayEffect)
    }
}
