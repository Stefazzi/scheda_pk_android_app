package it.stefazzi.pokerolesheets.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class CatalogItem(
    val id: String, val name: String, val category: String = "", val description: String = "", val effect: String = "", val price: String = "",
    @SerialName("sprite_path") val spritePath: String? = null,
    @SerialName("custom_name") val customName: String? = null,
    @SerialName("custom_description") val customDescription: String? = null,
    @SerialName("custom_effect") val customEffect: String? = null,
    @SerialName("custom_price") val customPrice: String? = null,
    val revision: Int = 1,
    @SerialName("is_custom") val isCustom: Boolean = false,
    @SerialName("custom_image_path") val customImagePath: String? = null,
    @SerialName("affected_parameters") val affectedParameters: List<String> = emptyList(),
    @SerialName("custom_affected_parameters") val customAffectedParameters: List<String>? = null,
) {
    val displayName get() = customName ?: name
    val displayDescription get() = customDescription ?: description
    val displayEffect get() = customEffect ?: effect
    val displayPrice get() = customPrice ?: price
    val displayAffectedParameters get() = customAffectedParameters ?: affectedParameters
    val customized get() = customAffectedParameters != null || listOf(customName,customDescription,customEffect,customPrice).any { it != null }
    fun imageUrls(supabaseUrl: String): List<String> {
        val base=supabaseUrl.trimEnd('/')
        val uri=runCatching { java.net.URI(base) }.getOrNull()
        val validBase=uri?.scheme=="https" && uri.host!=null && uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null && uri.path.isNullOrEmpty()
        val custom=customImagePath?.takeIf { validBase && validItemImagePath(it) }
            ?.let { "$base/storage/v1/object/public/pokerole-items/$it?v=$revision" }
        return listOfNotNull(custom,spriteUrl).distinct()
    }
    val spriteUrl get() = spritePath?.takeIf { it.matches(Regex("[a-zA-Z0-9/_-]+\\.png")) && ".." !in it }
        ?.let {
            if(it.startsWith("community/")) "https://raw.githubusercontent.com/Pokerole-Software-Development/Pokerole-Data/abbe22a7e42853c95d6602b97bb0034833b7c7bc/images/ItemSprites/${it.removePrefix("community/")}"
            else "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/$it"
        }
}

fun validItemImagePath(path:String): Boolean = path.length<=512 && path.matches(Regex("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*\\.(png|jpg|jpeg|webp)",RegexOption.IGNORE_CASE))

@Serializable
data class InventorySlot(
    @SerialName("trainer_id") val trainerId: String,
    @SerialName("slot_key") val slotKey: String,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("free_text") val freeText: String = "",
    val quantity: Int = 0, val notes: String = "",
    @SerialName("custom_name") val customName: String? = null,
    @SerialName("custom_description") val customDescription: String? = null,
    @SerialName("custom_effect") val customEffect: String? = null,
    val revision: Int = 0,
) {
    val customized get() = listOf(customName,customDescription,customEffect).any { it != null }
    fun name(item: CatalogItem?) = customName ?: item?.displayName ?: freeText.ifBlank { if(itemId == null) "Slot vuoto" else "Oggetto non disponibile" }
    fun description(item: CatalogItem?) = customDescription ?: item?.displayDescription.orEmpty()
    fun effect(item: CatalogItem?) = customEffect ?: item?.displayEffect.orEmpty()
    fun choose(item: CatalogItem?) = copy(itemId = item?.id, freeText = "", quantity = if(item == null) 0 else quantity.coerceAtLeast(1),
        customName = null, customDescription = null, customEffect = null)
    fun useOne(): InventorySlot {
        require(quantity > 0) { "Nessun oggetto da usare" }
        return if(quantity == 1) choose(null).copy(notes = "") else copy(quantity = quantity - 1)
    }
    fun parameters(isDm: Boolean): JsonObject {
        require(slotKey.matches(Regex("(left|right)_([1-9]|1[0-5])"))) { "Slot non valido" }
        require(trainerId.isNotBlank() && quantity in 0..9999 && revision >= 0) { "Quantità o scheda non valida" }
        return buildJsonObject {
            put("p_trainer_id", trainerId); put("p_slot_key",slotKey); put("p_expected_revision",revision)
            put("p_item_id",itemId?.let(::JsonPrimitive) ?: JsonNull); put("p_free_text",freeText)
            put("p_quantity",quantity); put("p_notes",notes)
            put("p_custom_name",customName?.takeIf { isDm }?.let(::JsonPrimitive) ?: JsonNull)
            put("p_custom_description",customDescription?.takeIf { isDm }?.let(::JsonPrimitive) ?: JsonNull)
            put("p_custom_effect",customEffect?.takeIf { isDm }?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
}

fun legacyInventorySlot(sheet: EditableSheet, slotKey: String): InventorySlot {
    val match = Regex("(left|right)_([1-9]|1[0-5])").matchEntire(slotKey) ?: error("Slot non valido")
    val list = if (match.groupValues[1] == "left") sheet.bagItemsLeft else sheet.bagItemsRight
    val text = list.getOrElse(match.groupValues[2].toInt()-1) { "" }
    // Do not guess catalog association or parse quantities from legacy free text.
    return InventorySlot(sheet.recordId,slotKey,freeText=text,quantity=if(text.isBlank()) 0 else 1)
}

data class ItemEdits(val name: String, val description: String, val effect: String, val price: String, val affectedParameters: List<String> = emptyList())
data class NewItem(val id: String, val edits: ItemEdits, val category: String, val iconItemId: String?)

data class EquipmentAccessory(val id: String, val name: String, val catalogId: String = "", val notes: String = "") {
    fun toJson() = buildJsonObject { put("id",id);put("name",name);put("catalog_id",catalogId);put("notes",notes) }
    companion object {
        fun fromJson(value: JsonObject, index: Int): EquipmentAccessory {
            fun field(key:String)=(value[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return EquipmentAccessory(field("id").ifBlank{"legacy-$index"},field("name"),field("catalog_id"),field("notes"))
        }
    }
}

fun EditableSheet.equipHeldItem(item: CatalogItem?): EditableSheet = copy(
    heldItem = item?.displayName.orEmpty(), heldItemCatalogId = item?.id.orEmpty(),
)

/** Fixed counters remain in sheet JSON: opening the bag never migrates or duplicates them. */
enum class QuickBagItem(val catalogId: String, val fallbackName: String) {
    POTION("potion", "Potion"),
    SUPER_POTION("super-potion", "Super Potion"),
    HYPER_POTION("hyper-potion", "Hyper Potion");

    fun quantity(sheet: EditableSheet): String = when(this) {
        POTION -> sheet.potion
        SUPER_POTION -> sheet.superPotion
        HYPER_POTION -> sheet.hyperPotion
    }

    fun updateQuantity(sheet: EditableSheet, quantity: String): EditableSheet {
        require(quantity.toIntOrNull()?.let { it in 0..9999 } == true) { "Quantità non valida" }
        return when(this) {
            POTION -> sheet.copy(potion = quantity)
            SUPER_POTION -> sheet.copy(superPotion = quantity)
            HYPER_POTION -> sheet.copy(hyperPotion = quantity)
        }
    }

    fun catalogItem(catalog: List<CatalogItem>): CatalogItem = catalog.firstOrNull { it.id == catalogId }
        ?: CatalogItem(catalogId, fallbackName, spritePath = "$catalogId.png")
}

fun EditableSheet.clearLegacyBag(): EditableSheet = copy(
    bagItemsLeft = List(15) { "" }, bagItemsRight = List(15) { "" },
)

class ItemRepository(private val client: SupabaseClient) {
    suspend fun setImage(item:CatalogItem,mode:String,path:String?=null,sourceId:String?=null):CatalogItem {
        require(item.isCustom || mode!="sprite") { "Il cambio sprite base è disponibile solo per gli oggetti custom" }
        require(mode in listOf("upload","sprite","reset"))
        return client.postgrest.rpc("set_custom_item_image",buildJsonObject {
            put("p_id",item.id);put("p_expected_revision",item.revision);put("p_mode",mode)
            put("p_image_path",path?.let(::JsonPrimitive) ?: JsonNull)
            put("p_source_item_id",sourceId?.let(::JsonPrimitive) ?: JsonNull)
        }).decodeSingle<CatalogItem>()
    }
    suspend fun uploadImage(item:CatalogItem,data:ByteArray):CatalogItem {
        require(data.isNotEmpty() && data.size<=2*1024*1024) { "Immagine vuota o superiore a 2 MB" }
        val path="oggetti/${java.util.UUID.randomUUID()}.webp"
        client.storage.from("pokerole-items").upload(path,data) {
            upsert=false;contentType=ContentType("image","webp")
        }
        try { return setImage(item,"upload",path=path) }
        catch(error:CancellationException) { throw error }
        catch(error:Exception) {
            // Never delete after an ambiguous RPC failure: the server may have committed.
            throw IllegalStateException("File caricato ($path), ma associazione non confermata. Aggiorna il catalogo prima di riprovare. ${error.message.orEmpty()}",error)
        }
    }
    suspend fun createItem(item: NewItem): CatalogItem = client.postgrest.rpc("create_catalog_item_with_parameters", buildJsonObject {
        put("p_id",item.id); put("p_name",item.edits.name); put("p_category",item.category)
        put("p_description",item.edits.description); put("p_effect",item.edits.effect); put("p_price",item.edits.price)
        put("p_icon_item_id",item.iconItemId?.let(::JsonPrimitive) ?: JsonNull)
        put("p_affected_parameters", JsonArray(item.edits.affectedParameters.distinct().sorted().map(::JsonPrimitive)))
    }).decodeSingle<CatalogItem>()
    suspend fun loadCatalog(): List<CatalogItem> {
        val result=mutableListOf<CatalogItem>()
        do {
            val offset=result.size.toLong()
            val page=client.from("catalog_items").select { order("id",Order.ASCENDING); range(offset..offset+999) }.decodeList<CatalogItem>()
            result += page
        } while(page.size==1000)
        return result
    }
    suspend fun loadInventory(): List<InventorySlot> {
        val result=mutableListOf<InventorySlot>()
        do {
            val offset=result.size.toLong()
            val page=client.from("trainer_inventory").select { order("trainer_id",Order.ASCENDING); order("slot_key",Order.ASCENDING); range(offset..offset+999) }.decodeList<InventorySlot>()
            result += page
        } while(page.size==1000)
        return result
    }
    suspend fun saveSlot(slot: InventorySlot,isDm: Boolean): InventorySlot = client.postgrest
        .rpc("save_inventory_slot",slot.parameters(isDm)).decodeSingle<InventorySlot>()
    suspend fun saveItem(item: CatalogItem,edits: ItemEdits,restore: Boolean): CatalogItem = client.postgrest.rpc("update_catalog_item_with_parameters",buildJsonObject {
        put("p_id",item.id); put("p_expected_revision",item.revision); put("p_name",edits.name)
        put("p_description",edits.description); put("p_effect",edits.effect); put("p_price",edits.price); put("p_restore",restore)
        put("p_affected_parameters", JsonArray(edits.affectedParameters.distinct().sorted().map(::JsonPrimitive)))
    }).decodeSingle<CatalogItem>()
}
