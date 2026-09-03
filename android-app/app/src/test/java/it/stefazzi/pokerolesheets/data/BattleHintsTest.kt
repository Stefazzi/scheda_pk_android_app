package it.stefazzi.pokerolesheets.data

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BattleHintsTest {
    private fun sheet() = EditableSheet.blank(true).copy(primaryType = "Psychic", secondaryType = "Fairy").let {
        it.copy(dotStats = it.dotStats + mapOf(
            "attributes.insight" to List(3) { true }, "attributes.strength" to List(2) { true },
            "attributes.dexterity" to List(4) { true }, "attributes.special" to List(5) { true },
            "skills.fight.channel" to List(2) { true }, "skills.fight.clash" to List(1) { true },
            "skills.fight.brawl" to List(3) { true }, "skills.survival.athletic" to List(1) { true },
        ))
    }
    private fun move() = CatalogMove("confusion", "Confusion", "Psychic", "Special", "2", "Insight + Channel", "Special", "Foe", "", "")

    @Test fun accuracyUsesCatalogNotAlwaysDexterity() {
        assertEquals(5, sheet().dicePools(move().accuracy).single().dice)
        assertEquals(7, sheet().dicePools("Dexterity + Brawl").single().dice)
        assertEquals(0, EditableSheet.blank(true).dicePools("Strength + Brawl").single().dice)
    }
    @Test fun slashAlternativesAreNotSummedOrChosenSilently() {
        val pools = sheet().dicePools("Strength/Dexterity + Brawl/Athletic")
        assertEquals(listOf(5,3,7,5), pools.map { it.dice })
        assertTrue(pools[0].formula.contains("Strength (2)"))
    }
    @Test fun rankBonusOnlyOnRollsContainingSkills() {
        for (rank in listOf("Master", "Champion")) {
            val s = sheet().withRuleValue("rank",rank)
            assertEquals(7, s.dicePools("Insight + Channel").single().dice)
            assertEquals(3, s.dicePools("Insight").single().dice)
            assertEquals(8, move().damagePools(s).single().dice)
            assertEquals(8, move().clashPools(s).single().dice)
        }
    }
    @Test fun damageIncludesPowerAndStabOnce() {
        assertEquals(8, move().damagePools(sheet()).single().dice)
        assertEquals(7, move().copy(type="Water").damagePools(sheet()).single().dice)
        assertEquals(8, move().damagePools(sheet().copy(secondaryType="Psychic")).single().dice)
        assertEquals(5, damageAfterDefense(8,3)); assertEquals(1, damageAfterDefense(8,99))
        assertEquals(6, move().clashPools(sheet()).single().dice)
        assertEquals(3, move().copy(category="Physical").clashPools(sheet()).single().dice)
        assertTrue(move().copy(category="Support").clashPools(sheet()).isEmpty())
    }
    @Test fun specialAndMissingFormulasNeverInventDice() {
        for (formula in listOf("Varies", "SetDamage", "Target'sStrength/Special", "SameAsCopiedMove", "", "Strength + Missing happiness"))
            assertTrue(sheet().dicePools(formula).isEmpty())
        assertTrue(move().copy(power="X").damagePools(sheet()).isEmpty())
        assertTrue(move().copy(category="Support").damagePools(sheet()).isEmpty())
        assertTrue(sheet().copy(dotStats=emptyMap()).dicePools("Insight + Channel").isEmpty())
    }
    @Test fun painThresholdsRoundDownAndDoNotStack() {
        fun warning(hp:String,max:String="9")=sheet().copy(hpActual=hp,hpTotal=max).painWarning()
        assertNull(warning("9"));assertNull(warning("5"));assertNull(warning("10"))
        assertTrue(warning("4")!!.contains("un successo"));assertTrue(warning("2")!!.contains("un successo"))
        assertTrue(warning("1")!!.contains("due successi"));assertTrue(warning("0")!!.contains("Fainted"))
        assertNull(warning("?"));assertNull(warning("-1"));assertNull(warning("1","0"));assertNull(warning("1","1"))
        assertTrue(warning("5","10")!!.contains("un successo"))
    }
    @Test fun healingReminderDoesNotTreatAllHealingAsWillCost() {
        assertTrue(move().copy(effect="Basic Heal.").healingWillReminder)
        assertTrue(move().copy(effect="Heal 2 HP instead of damage. Spend 1 Will Point to get this effect.").healingWillReminder)
        assertFalse(move().copy(effect="Damage cannot be healed for 24 hrs.").healingWillReminder)
        assertFalse(move().copy(effect="Heal all Status Ailments.").healingWillReminder)
    }
    @Test fun onlyEquippedCatalogIdsHighlightWithoutChangingStats() {
        val first=CatalogItem("a","Charm",affectedParameters=listOf("attributes.strength","social_attributes.cool"))
        val hat=CatalogItem("b","Hat",affectedParameters=listOf("social_attributes.cool","quick.hp"))
        val bag=CatalogItem("c","Bag only",affectedParameters=listOf("skills.fight.brawl"))
        val s=sheet().equipHeldItem(first).copy(accessories=listOf(EquipmentAccessory("hat","Hat","b")))
        val before=s.updatedJson()
        val hints=s.equipmentInfluences(listOf(first,hat,bag))
        assertEquals(listOf("Charm","Hat"),hints["social_attributes.cool"])
        assertFalse(hints.containsKey("skills.fight.brawl"));assertEquals(before,s.updatedJson())
        assertFalse(s.equipHeldItem(null).equipmentInfluences(listOf(first,hat)).containsKey("attributes.strength"))
        assertTrue(s.copy(isPokemon=false).equipmentInfluences(listOf(first,hat)).isEmpty())
        assertTrue(s.equipmentInfluences(listOf(first.copy(customAffectedParameters=emptyList()))).isEmpty())
    }
    @Test fun metadataDecodesOldRowsAndRoundTripsCustomOverrides() {
        assertTrue(Json.decodeFromString<CatalogItem>("""{"id":"old","name":"Old"}""").displayAffectedParameters.isEmpty())
        val item=CatalogItem("new","New",affectedParameters=listOf("attributes.strength"),customAffectedParameters=listOf("quick.hp"))
        assertEquals(item,Json.decodeFromString<CatalogItem>(Json.encodeToString(item)))
        assertEquals(listOf("quick.hp"),item.displayAffectedParameters)
    }
    @Test fun fullCatalogSmokeTestAndKnownHealingMoves() {
        val path=System.getProperty("catalog.fixture") ?: return
        val catalog=RemoteCatalog.parse(Json.parseToJsonElement(File(path).readText()).jsonObject)
        val s=sheet()
        catalog.moves.values.forEach { m ->
            (s.dicePools(m.accuracy)+m.damagePools(s)+m.clashPools(s)).forEach { assertTrue("${m.name}: ${it.formula}",it.dice>=0) }
        }
        for(name in listOf("Life Dew","Recover","Rest","Pollen Puff","Purify","Leech Seed")) assertTrue(name,catalog.moves.getValue(normalizeMoveName(name)).healingWillReminder)
        for(name in listOf("Confusion","Refresh","Shadow Force","Court Change","Recycle")) assertFalse(name,catalog.moves.getValue(normalizeMoveName(name)).healingWillReminder)
    }
}
