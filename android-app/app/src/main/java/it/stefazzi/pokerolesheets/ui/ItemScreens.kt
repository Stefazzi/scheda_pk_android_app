package it.stefazzi.pokerolesheets.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.stefazzi.pokerolesheets.data.*
import it.stefazzi.pokerolesheets.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ItemUiContext(val state: AppUiState, val refresh: () -> Unit,
    val saveSlot: (InventorySlot, () -> Unit) -> Unit,
    val saveItem: (CatalogItem, ItemEdits, Boolean, () -> Unit) -> Unit,
    val createItem: (NewItem, () -> Unit) -> Unit,
    val changeImage: (CatalogItem, ByteArray?, String?, () -> Unit) -> Unit)
internal val LocalItems = staticCompositionLocalOf<ItemUiContext> { error("Item UI not provided") }

@Composable
private fun ItemSprite(item: CatalogItem?, modifier: Modifier = Modifier.size(48.dp)) {
    val urls=item?.imageUrls(BuildConfig.SUPABASE_URL).orEmpty()
    if(urls.isNotEmpty()) PortraitUrls(urls,item!!.displayName,modifier)
    else Box(modifier,contentAlignment=Alignment.Center) { Text("◇",style=MaterialTheme.typography.headlineSmall) }
}

@Composable
private fun ItemStatus() {
    val ui=LocalItems.current
    Text(ui.state.itemsStatus,style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick=ui.refresh,enabled=!ui.state.itemsLoading && !ui.state.itemSaving) {
        Text(if(ui.state.itemsLoading) "Caricamento…" else "Aggiorna oggetti e borsa")
    }
}

@Composable
internal fun ItemCatalogScreen() {
    val ui=LocalItems.current
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Tutte") }
    var selected by remember { mutableStateOf<CatalogItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    val rows=remember(ui.state.items,search,category) { ui.state.items.filter {
        (category=="Tutte" || category==it.category) &&
            listOf(it.displayName,it.name,it.category).any { text -> referenceKey(text).contains(referenceKey(search)) }
    }.sortedBy { it.displayName.lowercase() } }
    selected?.let { ItemDetail(it,onDismiss={selected=null}) }
    if(creating) NewItemDialog(onDismiss={creating=false})
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        ItemStatus()
        if(ui.state.isDm) OutlinedButton(onClick={creating=true},enabled=ui.state.itemsReady && !ui.state.itemSaving) { Text("Crea oggetto custom") }
        CompactSearch("Cerca oggetto o categoria",search,{search=it})
        ChoiceField("Categoria",category,listOf("Tutte")+ui.state.items.map{it.category}.distinct().sorted()) { category=it }
        Text("${rows.size} oggetti",style=MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=12.dp)) {
            if(rows.isEmpty()) item { Text("Nessun oggetto disponibile per questa ricerca.") }
            items(rows,key={it.id}) { item ->
                Card(onClick={selected=item},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        ItemSprite(item)
                        Column(Modifier.weight(1f)) {
                            Text(item.displayName,fontWeight=FontWeight.SemiBold)
                            Text(item.category,style=MaterialTheme.typography.bodySmall)
                            if(item.isCustom) Text("Oggetto custom del DM",style=MaterialTheme.typography.labelSmall)
                            if(item.customized) Text("Regole personalizzate dal DM",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemDetail(item: CatalogItem,onDismiss: () -> Unit) {
    val ui=LocalItems.current
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(item.displayName) }
    var description by remember { mutableStateOf(item.displayDescription) }
    var effect by remember { mutableStateOf(item.displayEffect) }
    var price by remember { mutableStateOf(item.displayPrice) }
    var parameters by remember { mutableStateOf(item.displayAffectedParameters) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    if(confirm != null) AlertDialog(onDismissRequest={if(!ui.state.itemSaving)confirm=null},
        title={Text(if(confirm==true) "Ripristinare l'originale?" else "Modificare il catalogo condiviso?")},
        text={Text("La modifica vale per tutte le borse collegate a questo oggetto. I campi personalizzati dei singoli esemplari avranno ancora precedenza. Gli altri dispositivi la riceveranno aggiornando il catalogo.")},
        confirmButton={TextButton(enabled=!ui.state.itemSaving,onClick={ui.saveItem(item,ItemEdits(name,description,effect,price,parameters),confirm==true,onDismiss)}) {Text("Conferma")}},
        dismissButton={TextButton(enabled=!ui.state.itemSaving,onClick={confirm=null}){Text("Annulla")}})
    AlertDialog(onDismissRequest={if(!ui.state.itemSaving)onDismiss()},title={Text(item.displayName)},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemSprite(item,Modifier.size(72.dp).align(Alignment.CenterHorizontally))
            Text(item.category,style=MaterialTheme.typography.bodySmall)
            if(editing) {
                ItemText("Nome",name,{name=it}); ItemText("Descrizione",description,{description=it},3)
                ItemText("Effetto",effect,{effect=it},3); ItemText("Prezzo",price,{price=it})
                ParameterPicker(parameters) { parameters = it }
            } else {
                Text(item.displayDescription); if(item.displayEffect!=item.displayDescription) Text(item.displayEffect)
                Text("Prezzo: ${item.displayPrice.ifBlank{"—"}}")
                if (item.displayAffectedParameters.isNotEmpty()) Text("Parametri influenzati: " + item.displayAffectedParameters.mapNotNull { EquipmentParameters.labels[it] }.joinToString())
            }
            CollapsibleCard(if(item.isCustom) "Dati alla creazione" else "Dati originali Pokérole") {
                Text(item.name); Text(item.description); if(item.effect!=item.description)Text(item.effect);Text("Prezzo: ${item.price}")
            }
            if(ui.state.isDm && ui.state.itemsReady) {
                if(!editing) ItemImageActions(item,onSuccess=onDismiss)
                if(!editing) OutlinedButton(onClick={editing=true},enabled=!ui.state.itemSaving){Text("Modifica nel catalogo")}
                if(item.customized) TextButton(onClick={confirm=true},enabled=!ui.state.itemSaving){Text("Ripristina originale")}
            }
        }},
        confirmButton={if(editing)TextButton(onClick={confirm=false},enabled=!ui.state.itemSaving && name.isNotBlank() && name.length<=200 && description.length<=20000 && effect.length<=20000 && price.length<=200){Text("Salva per tutti")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!ui.state.itemSaving){Text("Chiudi")}})
}

@Composable
internal fun InventoryBag(sheet: EditableSheet, onSheetChange: (EditableSheet) -> Unit) {
    val ui=LocalItems.current
    var showEmpty by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InventorySlot?>(null) }
    var customize by remember { mutableStateOf(false) }
    var detailKey by remember { mutableStateOf<String?>(null) }
    var quickItem by remember { mutableStateOf<QuickBagItem?>(null) }
    var confirmCleanup by remember { mutableStateOf(false) }
    val legacyCount=(sheet.bagItemsLeft+sheet.bagItemsRight).count { it.isNotBlank() }
    val sheetBusy=ui.state.saving || ui.state.uploadingPortrait || ui.state.itemSaving
    val saved=ui.state.inventory.filter{it.trainerId==sheet.recordId}.associateBy{it.slotKey}
    val rows=(listOf("left","right").flatMap { side -> (1..15).map { "${side}_$it" } }).map { key -> saved[key] ?: legacyInventorySlot(sheet,key) }
    selected?.let { InventoryEditor(it,customize,onDismiss={selected=null}) }
    quickItem?.let { quick ->
        QuickBagItemDialog(quick, sheet, onDismiss={quickItem=null}, onApply={
            onSheetChange(quick.updateQuantity(sheet,it));quickItem=null
        })
    }
    rows.firstOrNull { it.slotKey==detailKey }?.let { slot ->
        InventorySlotDetail(slot,ui.state.items.firstOrNull{it.id==slot.itemId},onDismiss={detailKey=null},onEdit={ personal ->
            detailKey=null;selected=slot;customize=personal
        })
    }
    if(confirmCleanup) AlertDialog(onDismissRequest={confirmCleanup=false},
        title={Text("Rimuovere i vecchi oggetti?")},
        text={Text("Verranno svuotati $legacyCount vecchi testi nelle colonne A/B di questa scheda, anche se coperti da uno slot nuovo. Le tre pozioni, gli oggetti del nuovo inventario e il catalogo non cambiano. Premi poi Salva scheda per confermare su Supabase; prima del salvataggio puoi annullare uscendo senza salvare.")},
        confirmButton={TextButton(enabled=!sheetBusy,onClick={onSheetChange(sheet.clearLegacyBag());confirmCleanup=false}) {Text("Rimuovi dalla scheda")}},
        dismissButton={TextButton(onClick={confirmCleanup=false}){Text("Annulla")}})
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ItemStatus()
        Text("Tocca un oggetto per descrizione e azioni.",style=MaterialTheme.typography.bodySmall)
        QuickBagItem.entries.forEach { quick ->
            val item=quick.catalogItem(ui.state.items)
            BagItemRow(item, item.displayName, quick.quantity(sheet).ifBlank{"0"}, "Pozioni · contatore della scheda") { quickItem=quick }
        }
        Text("Le quantità di queste tre pozioni e la pulizia dei vecchi oggetti si confermano con Salva scheda. Gli altri slot usano Salva slot e restano indipendenti.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={confirmCleanup=true},enabled=legacyCount>0 && !sheetBusy){Text("Rimuovi vecchi oggetti ($legacyCount)")}
        if(sheet.recordId.isBlank()) Text("Salva prima l'allenatore per gestire gli oggetti.")
        val empty=rows.firstOrNull{it.itemId==null && it.freeText.isBlank() && !it.customized}
        OutlinedButton(onClick={selected=empty;customize=false},enabled=empty!=null && sheet.recordId.isNotBlank() && ui.state.itemsReady && !ui.state.itemSaving){Text("Aggiungi oggetto")}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showEmpty,{showEmpty=it});Text("Mostra slot vuoti")}
        rows.filter { showEmpty || it.itemId!=null || it.freeText.isNotBlank() || it.customized }.forEach { slot ->
            val item=ui.state.items.firstOrNull{it.id==slot.itemId}
            BagItemRow(item,slot.name(item),slot.quantity.toString(),
                slotLabel(slot.slotKey) + when {
                    slot.customized -> " · Personalizzato dal DM"
                    slot.revision==0 && slot.freeText.isNotBlank() -> " · Vecchio oggetto: verifica quantità"
                    else -> ""
                }) { detailKey=slot.slotKey }
        }
    }
}

@Composable
private fun ItemImageActions(item:CatalogItem,onSuccess:()->Unit) {
    val ui=LocalItems.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var preparing by remember{mutableStateOf(false)}
    var pending by remember{mutableStateOf<ByteArray?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var chooseSprite by remember{mutableStateOf(false)}
    var sprite by remember{mutableStateOf<CatalogItem?>(null)}
    var reset by remember{mutableStateOf(false)}
    var attempted by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) {
            preparing=true;error=null;attempted=false
            scope.launch {
                try { pending=withContext(Dispatchers.IO){compressPortrait(context,uri)} }
                catch(e:CancellationException){throw e}
                catch(e:Exception){error=e.message ?: "Impossibile leggere l'immagine"}
                finally {preparing=false}
            }
        }
    }
    val busy=ui.state.itemSaving || preparing
    OutlinedButton(enabled=!busy,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("Carica immagine")}
    if(item.isCustom) TextButton(enabled=!busy,onClick={attempted=false;chooseSprite=true}){Text("Cambia sprite dal catalogo")}
    if(item.isCustom || item.customImagePath!=null) TextButton(enabled=!busy,onClick={attempted=false;reset=true}){Text(if(item.isCustom) "Ripristina immagine iniziale" else "Ripristina sprite originale")}
    if(preparing)Text("Preparazione immagine…",style=MaterialTheme.typography.bodySmall)
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    if(chooseSprite) ItemPicker(ui.state.items.filter{it.id!=item.id && it.imageUrls(BuildConfig.SUPABASE_URL).isNotEmpty()},onDismiss={chooseSprite=false}){sprite=it;chooseSprite=false}
    if(pending!=null || sprite!=null || reset) AlertDialog(onDismissRequest={if(!busy){pending=null;sprite=null;reset=false}},
        title={Text(if(reset) "Ripristinare l'immagine?" else "Confermare la nuova immagine?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            pending?.let { AsyncImage(model=it,contentDescription="Anteprima immagine oggetto",modifier=Modifier.size(140.dp).align(Alignment.CenterHorizontally)) }
            sprite?.let { ItemSprite(it,Modifier.size(96.dp).align(Alignment.CenterHorizontally)) }
            Text("La modifica verrà salvata subito e condivisa con tutte le borse e schede collegate. Gli effetti dell'oggetto non cambiano.")
            if(pending!=null)Text("Il file sarà pubblico: non caricare immagini riservate. Verrà ridimensionato e convertito in WebP (massimo 2 MB).")
            if(reset)Text("Il file precedente rimane nel bucket: non verrà eliminato automaticamente.")
            if(attempted && !busy) ui.state.message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=!busy,onClick={attempted=true;ui.changeImage(item,pending,sprite?.id,onSuccess)}){Text(if(ui.state.itemSaving)"Salvataggio…" else "Conferma")}},
        dismissButton={TextButton(enabled=!busy,onClick={pending=null;sprite=null;reset=false}){Text("Annulla")}})
}

@Composable
private fun BagItemRow(item: CatalogItem?, name: String, quantity: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ItemSprite(item)
            Column(Modifier.weight(1f)) {
                Text(name,fontWeight=FontWeight.SemiBold)
                Text(subtitle,style=MaterialTheme.typography.labelSmall)
            }
            Text("×$quantity",fontWeight=FontWeight.Bold,modifier=Modifier.widthIn(max=84.dp))
        }
    }
}

@Composable
private fun ItemDescription(description: String, effect: String) {
    Text(description.ifBlank { "Nessuna descrizione disponibile." })
    if(effect.isNotBlank() && effect!=description) {
        Text("Effetto",fontWeight=FontWeight.SemiBold)
        Text(effect)
    }
}

@Composable
private fun InventorySlotDetail(slot: InventorySlot,item: CatalogItem?,onDismiss:()->Unit,onEdit:(Boolean)->Unit) {
    val ui=LocalItems.current
    val editable=ui.state.itemsReady && slot.trainerId.isNotBlank() && !ui.state.itemSaving
    AlertDialog(onDismissRequest=onDismiss,title={Text(slot.name(item))},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemSprite(item,Modifier.size(72.dp).align(Alignment.CenterHorizontally))
            Text("${slotLabel(slot.slotKey)} · Quantità: ${slot.quantity}")
            if(slot.customized)Text("Esemplare personalizzato dal DM",fontWeight=FontWeight.Bold)
            if(slot.revision==0 && slot.freeText.isNotBlank())Text("Vecchio testo non collegato al catalogo: controlla la quantità e scegli un oggetto con Modifica slot.",style=MaterialTheme.typography.bodySmall)
            ItemDescription(slot.description(item),slot.effect(item))
            if(slot.notes.isNotBlank())Text("Note: ${slot.notes}")
            Button(onClick={ui.saveSlot(slot.useOne(),onDismiss)},enabled=editable && slot.quantity>0 && (slot.revision>0 || slot.itemId!=null)) {
                Text(if(ui.state.itemSaving) "Salvataggio…" else "Usa")
            }
            Text("Usa consuma una unità e salva subito lo slot. L'ultima unità libera lo slot. Non applica effetti alle statistiche.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={onEdit(false)},enabled=editable){Text("Modifica slot / quantità")}
            if(ui.state.isDm) TextButton(onClick={onEdit(true)},enabled=editable){Text("Personalizza questo esemplare")}
        }},
        confirmButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
}

@Composable
private fun QuickBagItemDialog(quick: QuickBagItem,sheet: EditableSheet,onDismiss:()->Unit,onApply:(String)->Unit) {
    val ui=LocalItems.current
    val item=quick.catalogItem(ui.state.items)
    var quantity by remember(quick) { mutableStateOf(quick.quantity(sheet).ifBlank{"0"}) }
    val valid=quantity.toIntOrNull()?.let{it in 0..9999}==true
    AlertDialog(onDismissRequest=onDismiss,title={Text(item.displayName)},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemSprite(item,Modifier.size(72.dp).align(Alignment.CenterHorizontally))
            if(ui.state.items.none{it.id==quick.catalogId}) Text("Descrizione non caricata: aggiorna il catalogo oggetti.")
            else ItemDescription(item.displayDescription,item.displayEffect)
            ItemText("Quantità (0–9999)",quantity,{quantity=it})
            Button(enabled=valid && quantity.toInt()>0 && !ui.state.saving && !ui.state.uploadingPortrait,
                onClick={onApply((quantity.toInt()-1).toString())}){Text("Usa")}
            Text("Usa scala una unità dalla quantità mostrata; a zero il contatore resta visibile. Conferma poi con Salva scheda.",style=MaterialTheme.typography.bodySmall)
            if(!valid)Text("Inserisci una quantità intera da 0 a 9999.",color=MaterialTheme.colorScheme.error)
            Text("Applica modifica la scheda in corso. Premi poi Salva scheda per salvare su Supabase. Questo contatore non viene sommato agli altri slot e non applica cure automatiche.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(enabled=valid && !ui.state.saving && !ui.state.uploadingPortrait,onClick={onApply(quantity)}){Text("Applica quantità")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
}

@Composable
private fun NewItemDialog(onDismiss:()->Unit) {
    val ui=LocalItems.current
    val requestId=rememberSaveable { java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Custom") }
    var description by rememberSaveable { mutableStateOf("") }
    var effect by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var parameters by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var icon by remember { mutableStateOf<CatalogItem?>(null) }
    var picking by remember { mutableStateOf(false) }
    if(picking) ItemPicker(ui.state.items.filter{it.imageUrls(BuildConfig.SUPABASE_URL).isNotEmpty()},onDismiss={picking=false}) { icon=it;picking=false }
    val valid=name.isNotBlank() && name.length<=200 && category.length<=200 && description.length<=20000 && effect.length<=20000 && price.length<=200
    AlertDialog(onDismissRequest={if(!ui.state.itemSaving)onDismiss()},title={Text("Crea oggetto custom")},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Visibile a tutti nel catalogo. Potrai modificarne gli effetti anche dopo la creazione.",style=MaterialTheme.typography.bodySmall)
            ItemText("Nome",name,{name=it});ItemText("Categoria",category,{category=it})
            ItemText("Descrizione",description,{description=it},2);ItemText("Effetto",effect,{effect=it},3);ItemText("Prezzo (facoltativo)",price,{price=it})
            ParameterPicker(parameters) { parameters = it }
            ItemSprite(icon,Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            OutlinedButton(onClick={picking=true},enabled=!ui.state.itemSaving){Text("Scegli icona dal catalogo")}
            if(icon!=null)TextButton(onClick={icon=null},enabled=!ui.state.itemSaving){Text("Nessuna icona")}
            Text("L'icona non copia gli effetti dell'oggetto originale. Senza icona viene mostrato un simbolo generico.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(enabled=valid && ui.state.itemsReady && !ui.state.itemSaving,onClick={
            ui.createItem(NewItem(requestId,ItemEdits(name,description,effect,price,parameters),category,icon?.id),onDismiss)
        }){Text(if(ui.state.itemSaving)"Creazione…" else "Crea oggetto")}},
        dismissButton={TextButton(enabled=!ui.state.itemSaving,onClick=onDismiss){Text("Annulla")}})
}

private fun slotLabel(key:String) = key.replace("left_","A").replace("right_","B")

@Composable
private fun InventoryEditor(initial: InventorySlot,startCustomized:Boolean,onDismiss:()->Unit) {
    val ui=LocalItems.current
    var draft by remember { mutableStateOf(initial) }
    var quantity by remember { mutableStateOf(initial.quantity.toString()) }
    var pick by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(initial.customized || startCustomized) }
    val item=ui.state.items.firstOrNull{it.id==draft.itemId}
    var name by remember { mutableStateOf(initial.name(item).takeUnless{it=="Slot vuoto"}.orEmpty()) }
    var description by remember { mutableStateOf(initial.description(item)) }
    var effect by remember { mutableStateOf(initial.effect(item)) }
    if(pick) ItemPicker(ui.state.items,onDismiss={pick=false}) { chosen ->
        draft=draft.choose(chosen);quantity=draft.quantity.toString();custom=false
        name=chosen.displayName;description=chosen.displayDescription;effect=chosen.displayEffect;pick=false
    }
    val q=quantity.toIntOrNull()
    val valid=q!=null && q in 0..9999 && draft.freeText.length<=200 && draft.notes.length<=10000 &&
        (!ui.state.isDm || !custom || (name.isNotBlank() && name.length<=200 && description.length<=20000 && effect.length<=20000))
    AlertDialog(onDismissRequest={if(!ui.state.itemSaving)onDismiss()},title={Text("Borsa · ${slotLabel(initial.slotKey)}")},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemSprite(item,Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            Text(item?.displayName ?: "Oggetto a testo libero",fontWeight=FontWeight.Bold)
            OutlinedButton(onClick={pick=true},enabled=!ui.state.itemSaving){Text("Cerca nel catalogo")}
            Button(onClick={
                draft=draft.copy(quantity=q!!).useOne();quantity=draft.quantity.toString()
                if(draft.quantity==0){custom=false;name="";description="";effect=""}
            },enabled=!ui.state.itemSaving && q!=null && q in 1..9999 && (draft.itemId!=null || draft.freeText.isNotBlank())) {Text("Usa")}
            Text("Usa consuma una unità dalla quantità mostrata; l'ultima libera lo slot. Premi Salva slot per confermare.",style=MaterialTheme.typography.bodySmall)
            if(draft.itemId==null)ItemText("Testo libero",draft.freeText,{draft=draft.copy(freeText=it)})
            ItemText("Quantità (0–9999)",quantity,{quantity=it})
            ItemText("Note",draft.notes,{draft=draft.copy(notes=it)},2)
            if(ui.state.isDm) {
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(custom,{custom=it});Text("Personalizza questo esemplare")}
                if(custom) {
                    Text("Vale solo per questo slot e tutte le sue unità. Per un esemplare singolo, usa quantità 1 in uno slot separato.",style=MaterialTheme.typography.bodySmall)
                    ItemText("Nome personalizzato",name,{name=it})
                    ItemText("Descrizione personalizzata",description,{description=it},3)
                    ItemText("Effetto personalizzato",effect,{effect=it},3)
                } else Text("Eredita nome, descrizione ed effetto dal catalogo aggiornato.",style=MaterialTheme.typography.bodySmall)
            } else if(initial.customized) Text("La personalizzazione del DM viene conservata cambiando quantità o note.",style=MaterialTheme.typography.bodySmall)
            if(!valid)Text("Controlla quantità, nome e lunghezza dei testi.",color=MaterialTheme.colorScheme.error)
            Text("Salva slot aggiorna subito la borsa su Supabase, senza salvare le altre modifiche della scheda. Nessun effetto viene applicato automaticamente.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(enabled=valid && ui.state.itemsReady && !ui.state.itemSaving,onClick={
            ui.saveSlot(draft.copy(quantity=q!!,customName=if(ui.state.isDm && custom)name else null,
                customDescription=if(ui.state.isDm && custom)description else null,customEffect=if(ui.state.isDm && custom)effect else null),onDismiss)
        }){Text(if(ui.state.itemSaving)"Salvataggio…" else "Salva slot")}},
        dismissButton={TextButton(enabled=!ui.state.itemSaving,onClick=onDismiss){Text("Annulla")}})
}

@Composable
private fun ItemPicker(items:List<CatalogItem>,onDismiss:()->Unit,onSelect:(CatalogItem)->Unit) {
    var query by remember { mutableStateOf("") }
    val rows=items.filter{referenceKey(it.displayName+" "+it.name+" "+it.category).contains(referenceKey(query))}.sortedBy{it.displayName.lowercase()}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Seleziona oggetto")},text={Column {
        CompactSearch("Cerca oggetto",query,{query=it})
        LazyColumn(Modifier.height(320.dp)){
            if(rows.isEmpty())item{Text("Nessun risultato")}
            items(rows,key={it.id}){item -> TextButton(onClick={onSelect(item)},modifier=Modifier.fillMaxWidth()){
                ItemSprite(item,Modifier.size(40.dp));Text(item.displayName,Modifier.weight(1f))
            }}
        }
    }},confirmButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}

@Composable
private fun ItemText(label:String,value:String,onChange:(String)->Unit,minLines:Int=1) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},minLines=minLines,modifier=Modifier.fillMaxWidth())
}

@Composable
internal fun PokemonEquipment(sheet: EditableSheet, onChange: (EditableSheet)->Unit) {
    val ui=LocalItems.current
    val busy=ui.state.saving || ui.state.uploadingPortrait
    var pickHeld by remember { mutableStateOf(false) }
    var heldDetails by remember { mutableStateOf(false) }
    var manualHeld by remember { mutableStateOf(false) }
    var accessoryEdit by remember { mutableStateOf<EquipmentAccessory?>(null) }
    val held=ui.state.items.firstOrNull{it.id==sheet.heldItemCatalogId}
    if(pickHeld) ItemPicker(ui.state.items,onDismiss={pickHeld=false}) { onChange(sheet.equipHeldItem(it));pickHeld=false }
    if(manualHeld) {
        var name by remember { mutableStateOf(sheet.heldItem) }
        AlertDialog(onDismissRequest={manualHeld=false},title={Text("Held Item · testo libero")},
            text={Column { ItemText("Nome",name,{name=it});Text("Il collegamento al catalogo verrà rimosso. Conferma poi con Salva scheda.") }},
            confirmButton={TextButton(enabled=!busy && name.isNotBlank() && name.length<=200,onClick={onChange(sheet.copy(heldItem=name,heldItemCatalogId=""));manualHeld=false}){Text("Applica")}},
            dismissButton={TextButton(onClick={manualHeld=false}){Text("Annulla")}})
    }
    if(heldDetails) AlertDialog(onDismissRequest={heldDetails=false},title={Text(held?.displayName ?: sheet.heldItem)},
        text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemSprite(held,Modifier.size(72.dp).align(Alignment.CenterHorizontally))
            if(held!=null)ItemDescription(held.displayDescription,held.displayEffect)
            else Text("Strumento a testo libero o catalogo non disponibile. Selezionalo dal catalogo per vederne sprite e descrizione.")
            Text("Rimuovi libera lo spazio sulla scheda: non consuma né modifica oggetti nella borsa. Conferma con Salva scheda.",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!busy,onClick={onChange(sheet.equipHeldItem(null));heldDetails=false}){Text("Rimuovi strumento")}
        }},confirmButton={TextButton(onClick={heldDetails=false}){Text("Chiudi")}})
    accessoryEdit?.let { accessory ->
        EquipmentAccessoryDialog(accessory,onDismiss={accessoryEdit=null},onSave={ updated ->
            onChange(sheet.copy(accessories=if(sheet.accessories.any{it.id==updated.id}) sheet.accessories.map{if(it.id==updated.id)updated else it} else sheet.accessories+updated))
            accessoryEdit=null
        },onRemove=if(sheet.accessories.any{it.id==accessory.id}) ({onChange(sheet.copy(accessories=sheet.accessories.filterNot{it.id==accessory.id}));accessoryEdit=null}) else null)
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Held Item",style=MaterialTheme.typography.titleMedium)
            Text("Un solo strumento equipaggiato.",style=MaterialTheme.typography.bodySmall)
            if(sheet.heldItem.isNotBlank() || sheet.heldItemCatalogId.isNotBlank())
                BagItemRow(held,held?.displayName ?: sheet.heldItem.ifBlank{"Strumento non disponibile"},"1","Tocca per i dettagli") {heldDetails=true}
            else Text("Nessuno strumento equipaggiato")
            OutlinedButton(enabled=ui.state.itemsReady && !busy,onClick={pickHeld=true}){Text(if(sheet.heldItem.isBlank())"Equipaggia dal catalogo" else "Sostituisci dal catalogo")}
            TextButton(enabled=!busy,onClick={manualHeld=true}){Text("Inserisci nome manualmente")}
        } }
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Accessories & Ribbons",style=MaterialTheme.typography.titleMedium)
            Text("Accessori, costumi e fiocchi, separati dallo strumento.",style=MaterialTheme.typography.bodySmall)
            if(sheet.accessories.isEmpty())Text("Nessun accessorio o fiocco")
            sheet.accessories.forEach { accessory ->
                val item=ui.state.items.firstOrNull{it.id==accessory.catalogId}
                BagItemRow(item,accessory.name.ifBlank{item?.displayName.orEmpty()},"1","Tocca per dettagli e note"){accessoryEdit=accessory}
            }
            OutlinedButton(enabled=!busy,onClick={accessoryEdit=EquipmentAccessory(java.util.UUID.randomUUID().toString(),"")}){Text("Aggiungi accessorio / fiocco")}
        } }
        Text("Salva scheda conferma entrambi i riquadri. La borsa resta invariata; bonus e trasferimenti sono gestiti manualmente.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EquipmentAccessoryDialog(initial:EquipmentAccessory,onDismiss:()->Unit,onSave:(EquipmentAccessory)->Unit,onRemove:(()->Unit)?) {
    val ui=LocalItems.current
    var draft by remember(initial.id){mutableStateOf(initial)}
    var pick by remember{mutableStateOf(false)}
    val item=ui.state.items.firstOrNull{it.id==draft.catalogId}
    val busy=ui.state.saving || ui.state.uploadingPortrait
    if(pick)ItemPicker(ui.state.items,onDismiss={pick=false}){draft=draft.copy(catalogId=it.id,name=it.displayName);pick=false}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Accessories & Ribbons")},
        text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemSprite(item,Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            ItemText("Nome",draft.name,{draft=draft.copy(name=it)})
            OutlinedButton(enabled=ui.state.itemsReady && !busy,onClick={pick=true}){Text("Scegli dal catalogo")}
            if(draft.catalogId.isNotBlank())TextButton(enabled=!busy,onClick={draft=draft.copy(catalogId="")}){Text("Scollega dal catalogo")}
            if(item!=null)ItemDescription(item.displayDescription,item.displayEffect)
            ItemText("Note / descrizione del fiocco",draft.notes,{draft=draft.copy(notes=it)},3)
            Text("Conferma poi con Salva scheda. Non vengono applicati bonus o modifiche alla borsa.",style=MaterialTheme.typography.bodySmall)
            if(onRemove!=null)TextButton(enabled=!busy,onClick=onRemove){Text("Rimuovi accessorio / fiocco")}
        }},
        confirmButton={TextButton(enabled=!busy && draft.name.isNotBlank() && draft.name.length<=200 && draft.notes.length<=10000,onClick={onSave(draft)}){Text("Applica")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
}
