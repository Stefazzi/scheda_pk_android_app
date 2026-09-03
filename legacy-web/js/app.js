let TYPE_CHART = {};
let ranks = [];

// --- Helper Functions ---

function poke_list() {
  const popup = document.getElementById("popupPokeList");
  popup.classList.toggle('hidden');
}

function img_change() {
  // 1. Open a popup asking for the URL
  const newUrl = prompt("Enter the new image link (URL):", "");

  // 2. Check if the user actually entered something (didn't click cancel)
  if (newUrl !== null && newUrl !== "") {
    const img = document.getElementById('poke-img');

    // 3. Update the image source
    img.src = newUrl;
  }
}

function typeEff(t1, t2) {
  const listRes = document.getElementById('res');
  const listDeb = document.getElementById('deb');
  const listImm = document.getElementById('imm');

  // Reset current lists
  [listRes, listDeb, listImm].forEach(el => el.innerHTML = '');

  const multipliers = {};
  Object.keys(TYPE_CHART).forEach(type => multipliers[type] = 1.0);

  // Apply modifiers
  [t1, t2].forEach(t => {
    const typeKey = t ? t.toLowerCase() : null;
    if (!typeKey || !TYPE_CHART[typeKey]) return;

    TYPE_CHART[typeKey].weakness.forEach(type => multipliers[type] *= 2);
    TYPE_CHART[typeKey].resistance.forEach(type => multipliers[type] *= 0.5);
    TYPE_CHART[typeKey].immunity.forEach(type => multipliers[type] *= 0);
  });

  // Populate UI
  for (const [type, value] of Object.entries(multipliers)) {
    if (value === 1) continue; // Skip neutral effectiveness

    const li = document.createElement('li');
    const typeName = type.charAt(0).toUpperCase() + type.slice(1);

    if (value > 1) {
      li.textContent = `${typeName} (${value}x)`;
      listDeb.appendChild(li);
    } else if (value > 0 && value < 1) {
      li.textContent = `${typeName} (${value}x)`;
      listRes.appendChild(li);
    } else if (value === 0) {
      li.textContent = typeName;
      listImm.appendChild(li);
    }
  }
}

function addCircle(prefix) {
  // 1. Find the input with a name starting with the prefix to locate the correct container
  const firstInput = document.querySelector(`input[name^="${prefix}"]`);
  if (!firstInput) return;

  const container = firstInput.parentElement;
  const currentCount = container.querySelectorAll('input').length;

  // 2. Create the new checkbox
  const newCheckbox = document.createElement('input');

  // 3. Set the unique name (e.g., cb_str13)
  newCheckbox.name = `${prefix}${currentCount + 1}`;
  newCheckbox.type = 'checkbox';
  newCheckbox.className = 'checkbox-round';

  // 4. Append to the specific container
  container.appendChild(newCheckbox);
}

function removeCircle(prefix) {
  // Find the container holding the inputs for this specific stat
  const firstInput = document.querySelector(`input[name^="${prefix}"]`);
  if (!firstInput) return;

  const container = firstInput.parentElement;
  const checkboxes = container.querySelectorAll('input');

  // Only remove if there's more than 1 (keeps the UI from looking empty)
  if (checkboxes.length > 1) {
    container.removeChild(checkboxes[checkboxes.length - 1]);
  }
}

function toggleTypeEff(id) {
  const div = document.getElementById(id);
  const btn = event.currentTarget; // Prende il bottone che ha scatenato l'evento

  div.classList.toggle('hidden');

  if (div.classList.contains('hidden')) {
    btn.textContent = 'Show';
  } else {
    btn.textContent = 'Hide';
  }
}

function initRanks() {
  const listContainer = document.getElementById('popupRank');

  // Generate the list items
  const listHtml = ranks.map(rank => `
        <li onclick="selectRank('${rank.image}')">
            <img src="${rank.image}" alt="${rank.label}" class="img-rank">
        </li>
    `).join('');

  listContainer.innerHTML = listHtml;
}

// Function to handle clicking the main button
function rank_change() {
  const popup = document.getElementById('popupRank');
  popup.classList.toggle('hidden');
  const img = document.getElementById('rank-btn');
  img.classList.toggle('hidden');
}

// Function to change the main image when a rank is selected
function selectRank(imgSrc) {
  document.getElementById('rank-img').src = imgSrc;
  document.getElementById('popupRank').classList.add('hidden');
  document.getElementById('rank-btn').classList.remove('hidden');
}

/*function rank_change() {
  var popup = document.getElementById("popupRank");
  popup.classList.toggle('hidden');
}*/

// Function to add moves
function addMove(prefix) {
  // 1. Find the input with a name starting with the prefix to locate the correct container
  const currentCount = document.querySelectorAll(`input[name^="${prefix}"]`).length;

  num = currentCount + 1;

  const container = document.getElementById('div-moves'); // The parent element where you want to add this

  const moveBoxHTML = `
    <div class="margin10"></div>

    <div class="div-single-move" id="box_move${num}">
    <!--Move ${num}-->
      <input name="move${num}" id="move${num}"  class="inp-small">
      <button class="btn-search" onclick="search('move', ${num})">Search</button>
      <div id="div-move${num}" class="hidden">
      <div class="margin10"></div>
        <h4 id="move${num}_name"></h4>
        <h4 id="move${num}_type"></h4>
        <h4 id="move${num}_category"></h4>
        <h4 id="move${num}_power"></h4>
        <h4 id="move${num}_damage1"></h4>
        <h4 id="move${num}_damage2"></h4>
        <h4 id="move${num}_accuracy1"></h4>
        <h4 id="move${num}_accuracy2"></h4>
        <h4 id="move${num}_target"></h4>
        <h4 id="move${num}_effect"></h4>
        <h4 id="move${num}_description"></h4>
      </div>
    </div>
    `;

  // Add it to the page
  container.insertAdjacentHTML('beforeend', moveBoxHTML);

}

// Function to remove moves
function removeMove() {
  const container = document.getElementById('div-moves');
  const moves = container.querySelectorAll('.div-single-move');

  if (moves.length > 1) {
    // Remove the last element in the list
    moves[moves.length - 1].remove();

    // Also remove the margin div if you added one
    const margins = container.querySelectorAll('.margin10');
    if (margins.length > 0) {
      margins[margins.length - 1].remove();
    }
  } else {
    console.log("No moves left to remove!");
  }
}

function toTitleCase(val) {
  return String(val)
    .toLowerCase() // Optional: ensures "ICY WIND" becomes "Icy Wind"
    .split(' ')    // Split the string into an array of words
    .map(word => word.charAt(0).toUpperCase() + word.slice(1)) // Capitalize each word
    .join(' ');    // Join them back with spaces
}

// ability search
async function search(id, prog) {
  // 1. Get the raw value from input
  const idprog = id + prog;
  const raw = document.getElementById(idprog).value;

  // 2. Format it to Title Case (e.g., "icy wind" -> "Icy Wind")
  const name = toTitleCase(raw);

  //const filelocation = '';

  switch (id) {
    case "move":
      filelocation = '../json/Moves/';
      break;
    case "ability":
      filelocation = '../json/Abilities/';
      break;
    case "nature":
      filelocation = '../json/Natures/';
      break;
  }

  const file = `${filelocation}${name}.json`;

  try {
    // 2. Fetch the file (Wait for the response)
    const response = await fetch(file);

    // Check if the file actually exists
    if (!response.ok) throw new Error(id + ' not found');

    // 3. Parse the JSON (Wait for the parsing to finish)
    const json = await response.json();

    // 4. Update the UI
    switch (id) {
      case "move":
        document.getElementById("div-" + idprog).classList.remove('hidden');
        document.getElementById(idprog + '_name').textContent = 'Name: ' + json.Name;
        document.getElementById(idprog + '_type').textContent = 'Type: ' + json.Type;
        document.getElementById(idprog + '_category').textContent = 'Category: ' + json.Category;
        document.getElementById(idprog + '_power').textContent = 'Power: ' + json.Power;
        if (json.Damage2 === '') {
          document.getElementById(idprog + '_damage1').textContent = 'Damage: ' + json.Damage1;
        } else {
          document.getElementById(idprog + '_damage2').textContent = 'Damage: ' + json.Damage1 + ' + ' + json.Damage2;
        }
        if (json.Accuracy2 === '') {
          document.getElementById(idprog + '_damage1').textContent = 'Accuracy: ' + json.Accuracy1;
        } else {
          document.getElementById(idprog + '_damage2').textContent = 'Accuracy: ' + json.Accuracy1 + ' + ' + json.Accuracy2;
        }
        document.getElementById(idprog + '_target').textContent = 'Target: ' + json.Target;
        document.getElementById(idprog + '_effect').textContent = 'Effect: ' + json.Effect;
        document.getElementById(idprog + '_description').textContent = 'Description: ' + json.Description;
        break;
      case "ability":
        document.getElementById("h4-abil-name").classList.remove('hidden');
        document.getElementById("h4-abil-name").textContent = json.Name;
        document.getElementById("p-abil-eff").classList.remove('hidden');
        document.getElementById("p-abil-eff").textContent = json.Effect;
        document.getElementById("p-abil-text").classList.remove('hidden');
        document.getElementById("p-abil-text").textContent = json.Description;
        break;
      case "nature":
        document.getElementById("h4-nat-name").classList.remove('hidden');
        document.getElementById("h4-nat-name").textContent = json.Name;
        document.getElementById("h4-nat-conf").classList.remove('hidden');
        document.getElementById("h4-nat-conf").textContent = 'Confidence: ' + json.Confidence;
        document.getElementById("h4-nat-conf").classList.remove('hidden');
        document.getElementById("h4-nat-conf").textContent = 'Confidence: ' + json.Confidence;
        document.getElementById("h4-nat-key").classList.remove('hidden');
        document.getElementById("h4-nat-key").textContent = json.Keywords;
        document.getElementById("p-nat-text").classList.remove('hidden');
        document.getElementById("p-nat-text").textContent = json.Description;
        break;
    }

  } catch (err) {
    console.error("Error loading move:", err);
    // Optional: clear the display if the move isn't found
    //document.getElementById("div-" + move_input).classList.add('hidden');
  }
}

// Pokemon List
function setupPokeUI(allPokemon) {
  const listContainer = document.getElementById('pokeList');
  const searchInput = document.getElementById('pokeSearch');
  const popup = document.getElementById("popupPokeList");

  // DOM elements for display
  const nameDisplay = document.getElementById('pokename');
  const nrDisplay = document.getElementById('pokenr');
  const type1Display = document.getElementById('type1');
  const type1Text = document.getElementById('type1-text');
  const type2Display = document.getElementById('type2');
  const type2Text = document.getElementById('type2-text');

  const renderList = (pokemonArray) => {
    listContainer.innerHTML = '';

    pokemonArray.forEach(item => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.textContent = item.pokemon;
      a.href = '#';

      a.addEventListener('click', (e) => {
        e.preventDefault();

        // Update Display
        nameDisplay.textContent = item.pokemon;
        nrDisplay.textContent = '# ' + String(item.nr).padStart(3, '0');

        // Update Types
        type1Display.className = `spa-type type-${item.tipo1.toLowerCase()}`;
        type1Text.textContent = item.tipo1;

        if (item.tipo2) {
          type2Display.className = `spa-type type-${item.tipo2.toLowerCase()}`;
          type2Text.textContent = item.tipo2;
          type2Display.classList.remove('hidden');
        } else {
          type2Display.classList.add('hidden');
        }

        // Trigger Automation - Now uses the global TYPE_CHART loaded in init()
        typeEff(item.tipo1, item.tipo2);
        popup.classList.add('hidden');
      });

      li.appendChild(a);
      listContainer.appendChild(li);
    });
  };

  // Initial render
  renderList(allPokemon);

  // Search listener
  searchInput.addEventListener('input', (e) => {
    const query = e.target.value.toLowerCase();
    const filtered = allPokemon.filter(p => p.pokemon.toLowerCase().includes(query));
    renderList(filtered);
  });
}

// --- Main Logic ---

// Global variables to store the data
async function init() {
  try {
    // 1. Fetch all data concurrently
    const [typeRes, rankRes, pokeRes] = await Promise.all([
      fetch('../json/type_chart.json'),
      fetch('../json/ranks.json'),
      fetch('../json/poke_list.json')
    ]);

    // 2. Assign to variables
    TYPE_CHART = await typeRes.json();
    ranks = await rankRes.json();
    const allPokemon = await pokeRes.json();

    // 3. Initialize UI components
    initRanks();
    setupPokeUI(allPokemon);

  } catch (err) {
    console.error("Initialization failed:", err);
  }
}

// Save
function saveSheetData(id) {
  // 1. Sync all text input values to their 'value' attribute
  document.querySelectorAll('input[type="text"], textarea').forEach(input => {
    if (input.tagName.toLowerCase() === 'textarea') {
      // Textareas use internal text, not a value attribute
      input.textContent = input.value;
    } else {
      // Standard inputs use the value attribute
      input.setAttribute('value', input.value);
    }
  });

  // 2. Sync all checkbox states to their 'checked' attribute
  document.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
    if (checkbox.checked) {
      checkbox.setAttribute('checked', 'checked');
    } else {
      checkbox.removeAttribute('checked');
    }
  });

  let nameHeader = '';
  // 3. Sync contenteditable fields (like the Name header)
  if (id === 'poke') {
    nameHeader = document.getElementById('name');
  }
  /*if (nameHeader) {
      nameHeader.setAttribute('data-current-text', nameHeader.innerText);
  }*/

  const nameTrainer = document.getElementById('trainer');
  /*if (nameTrainer) {
      nameTrainer.setAttribute('data-current-text', nameTrainer.innerText);
  }*/

  // 4. Get the entire HTML content
  const htmlContent = "<!DOCTYPE html>\n" + document.documentElement.outerHTML;

  // 5. Create a "Blob" (the file data)
  const blob = new Blob([htmlContent], { type: 'text/html' });
  const url = URL.createObjectURL(blob);

  // 6. Create a hidden link and click it to trigger the download
  const link = document.createElement('a');
  link.href = url;
  if (id === 'poke') {
    link.download = nameTrainer.innerText + '_' + nameHeader.innerText + '.html';//`pokemon_sheet_${new Date().getTime()}.html`;
  } else {
    link.download = nameTrainer.innerText + '.html';
  }
  document.body.appendChild(link);
  link.click();

  // Cleanup
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

// Variable to store the file handle so we can reuse it
let fileHandle = null;

async function savesamefile(id) {
  // 1. & 2. (Keep your existing code for syncing attributes)
  document.querySelectorAll('input[type="text"], textarea').forEach(input => {
    if (input.tagName.toLowerCase() === 'textarea') {
      // Textareas use internal text, not a value attribute
      input.textContent = input.value;
    } else {
      // Standard inputs use the value attribute
      input.setAttribute('value', input.value);
      console.log(input.tagName.toLowerCase());
    }
  });
  document.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
    if (checkbox.checked) {
      checkbox.setAttribute('checked', 'checked');
    } else {
      checkbox.removeAttribute('checked');
    }
  });

  // 3. Get the HTML content
  const htmlContent = "<!DOCTYPE html>\n" + document.documentElement.outerHTML;

  try {
    // Check if we already have a file handle. If not, ask the user to "Save As" once.
    if (!fileHandle) {
      const nameHeader = document.getElementById('name');
      const nameTrainer = document.getElementById('trainer');
      const suggestedName = id === 'poke'
        ? `${nameTrainer.innerText}_${nameHeader.innerText}.html`
        : `${nameTrainer.innerText}.html`;

      fileHandle = await window.showSaveFilePicker({
        suggestedName: suggestedName,
        types: [{
          description: 'HTML Document',
          accept: { 'text/html': ['.html'] },
        }],
      });
    }

    // 4. Create a writable stream to the file
    const writable = await fileHandle.createWritable();

    // 5. Write the content and close the stream
    await writable.write(htmlContent);
    await writable.close();

    alert("File saved successfully!");
  } catch (err) {
    if (err.name !== 'AbortError') {
      console.error(err);
      alert("Failed to save file.");
    }
  }
}

// Function to Save to Browser Memory
function autoSave() {
  const data = {};
  // Save all text inputs
  document.querySelectorAll('input[type="text"], textarea').forEach(input => {
    data[input.id || input.name] = input.value;
  });
  // Save all checkboxes
  document.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
    data[checkbox.id || checkbox.name] = checkbox.checked;
  });

  localStorage.setItem('sheetData', JSON.stringify(data));
}

// Function to Load when the page opens
function loadData() {
  const saved = localStorage.getItem('sheetData');
  if (!saved) return;

  const data = JSON.parse(saved);
  Object.keys(data).forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      if (el.type === 'checkbox') el.checked = data[id];
      else el.value = data[id];
    }
  });
}

async function saveSheetData2(sheetType) {
  const getCheckboxes = (prefix) => {
    const result = [];
    for (let i = 1; i <= 5; i++) {
      const el = document.querySelector(`input[name="${prefix}${i}"]`);
      result.push(el ? el.checked : false);
    }
    return result;
  };

  // Mappatura di tutte le mosse presenti nella pagina
  const moves = [];
  document.querySelectorAll('.div-single-move input[name^="move"]').forEach((input) => {
    const moveId = input.id; // es. move1
    moves.push({
      id: moveId,
      value: input.value || ''
    });
  });

  // Estrazione e mappatura di tutti i campi dell'HTML
  const data = {
    header: {
      trainer_name: document.getElementById('trainer')?.innerText.trim() || '',
      pokemon_name: document.getElementById('name')?.innerText.trim() || '',
      pokename: document.getElementById('pokename')?.innerText.trim() || '',
      pokenr: document.getElementById('pokenr')?.innerText.trim() || '',
      type1: {
        text: document.getElementById('type1-text')?.innerText || '',
        class: document.getElementById('type1')?.className || ''
      },
      type2: {
        text: document.getElementById('type2-text')?.innerText || '',
        class: document.getElementById('type2')?.className || ''
      },
      rank_img: document.getElementById('rank-img')?.getAttribute('src') || '',
      happiness: getCheckboxes('cb_hap'),
      loyalty: getCheckboxes('cb_loy'),
      size: document.getElementById('size')?.value || '',
      weight: document.getElementById('weight')?.value || '',
      profile_picture: document.getElementById('poke-img')?.getAttribute('src') || '',

      // Mantenuti campi trainer se presenti in altri schemi
      id: document.getElementById('id')?.innerText.trim() || '',
      team: document.getElementById('team')?.value || '',
      age: document.getElementById('age')?.value || '',
      money: document.getElementById('money')?.value || '',
      reputation: document.getElementById('rep')?.value || ''
    },
    quick_references: {
      hp: {
        actual: document.getElementById('hpact')?.value || '',
        total: document.getElementById('hptot')?.value || ''
      },
      def_spdef: {
        actual: document.getElementById('defact')?.value || '',
        total: document.getElementById('deftot')?.value || ''
      },
      will: {
        actual: document.getElementById('willact')?.value || '',
        total: document.getElementById('willtot')?.value || ''
      },
      held_item: document.querySelector('input[name="held_item"]')?.value || '',
      status_effect: document.querySelector('input[name="status_effect"]')?.value || '',
      initiative: document.querySelector('input[name="init"]')?.value || '',
      evasion: document.querySelector('input[name="evasion_val"]')?.value || '',
      fatigue: getCheckboxes('cb_fat'),
      action_used: getCheckboxes('cb_act')
    },
    ability: {
      search_input: document.getElementById('ability')?.value || '',
      details: {
        name: document.getElementById('h4-abil-name')?.innerText || '',
        effect: document.getElementById('p-abil-eff')?.innerText || '',
        text: document.getElementById('p-abil-text')?.innerText || ''
      }
    },
    nature: {
      search_input: document.getElementById('nature')?.value || '',
      details: {
        name: document.getElementById('h4-nat-name')?.innerText || '',
        configuration: document.getElementById('h4-nat-conf')?.innerText || '',
        key: document.getElementById('h4-nat-key')?.innerText || '',
        text: document.getElementById('p-nat-text')?.innerText || ''
      }
    },
    pokemon_team: [
      { slot: 1, id: 'pk1', value: document.getElementById('pk1')?.value || '' },
      { slot: 2, id: 'pk2', value: document.getElementById('pk2')?.value || '' },
      { slot: 3, id: 'pk3', value: document.getElementById('pk3')?.value || '' }
    ],
    attributes: {
      strength: getCheckboxes('cb_str'),
      dexterity: getCheckboxes('cb_dex'),
      vitality: getCheckboxes('cb_vit'),
      special: getCheckboxes('cb_spe'),
      insight: getCheckboxes('cb_ins')
    },
    social_attributes: {
      tough: getCheckboxes('cb_tou'),
      cool: getCheckboxes('cb_coo'),
      beauty: getCheckboxes('cb_bea'),
      cute: getCheckboxes('cb_cut'),
      clever: getCheckboxes('cb_cle')
    },
    skills: {
      fight: {
        brawl: getCheckboxes('cb_bra'),
        channel: getCheckboxes('cb_chan'),
        clash: getCheckboxes('cb_cla'),
        evasion: getCheckboxes('cb_eva'),
        throw: getCheckboxes('cb_thr'),
        weapons: getCheckboxes('cb_wea')
      },
      survival: {
        alert: getCheckboxes('cb_ale'),
        athletic: getCheckboxes('cb_ath'),
        nature: getCheckboxes('cb_nat'),
        stealth: getCheckboxes('cb_ste')
      },
      social: {
        charm: getCheckboxes('cb_chm'),
        empathy: getCheckboxes('cb_emp'),
        etiquette: getCheckboxes('cb_eti'),
        intimidate: getCheckboxes('cb_int'),
        perform: getCheckboxes('cb_per')
      },
      knowledge: {
        crafts: getCheckboxes('cb_cra'),
        lore: getCheckboxes('cb_lor'),
        medicine: getCheckboxes('cb_med'),
        science: getCheckboxes('cb_sci')
      }
    },
    bag: {
      potion: document.getElementById('pot')?.value || '',
      super_potion: document.getElementById('spo')?.value || '',
      hyper_potion: document.getElementById('hpo')?.value || '',
      items_left: Array.from({ length: 15 }, (_, i) =>
        document.getElementById(`bag-l${i + 1}`)?.value || ''
      ),
      items_right: Array.from({ length: 15 }, (_, i) =>
        document.getElementById(`bag-r${i + 1}`)?.value || ''
      )
    },
    moves: moves,
    background: document.querySelector('#div-background textarea')?.value || '',
    conoscenze_personali: document.querySelector('#div-conoscenze textarea')?.value || ''
  };

  // Generazione del JSON e invio a Supabase
  const jsonString = JSON.stringify(data, null, 4);
  const trainer = document.getElementById('trainer')?.innerText.trim();
  const name = document.getElementById('name')?.innerText.trim();
  var filename;
  if (sheetType === 'trainer') {
    filename = trainer || 'trainer';
  }
  else {
    filename = [trainer, name].filter(Boolean).join('_') || 'poke';
  }

  const SUPABASE_URL = 'https://lzppgwqfahqrcgsjyybl.supabase.co';
  const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx6cHBnd3FmYWhxcmNnc2p5eWJsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU3NjgxMzMsImV4cCI6MjEwMTM0NDEzM30.eTTTCnQ0usjEHnILcc9yboMMI_uC8nS8WdpcA1N_-5k';

  const supabaseclient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

  const { db, error } = await supabaseclient
    .from('Characters')
    .upsert([{ name: filename, value: jsonString }]);
}

// Helper function to fill the form elements with JSON data
function populateSheet(data) {
  const setValue = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.value = val ?? '';
  };

  const setByName = (name, val) => {
    const el = document.querySelector(`input[name="${name}"]`);
    if (el) el.value = val ?? '';
  };

  const setText = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.innerText = val ?? '';
  };

  const setCheckboxes = (prefix, values = []) => {
    values.forEach((checked, i) => {
      const el = document.querySelector(`input[name="${prefix}${i + 1}"]`);
      if (el) el.checked = Boolean(checked);
    });
  };

  // --- 1. Header ---
  if (data.header) {
    setText('trainer', data.header.trainer_name);
    setText('name', data.header.pokemon_name);
    setText('pokename', data.header.pokename);
    setText('pokenr', data.header.pokenr);

    if (data.header.type1) {
      setText('type1-text', data.header.type1.text);
      if (data.header.type1.class) document.getElementById('type1').className = data.header.type1.class;
    }
    if (data.header.type2) {
      setText('type2-text', data.header.type2.text);
      if (data.header.type2.class) document.getElementById('type2').className = data.header.type2.class;
    }

    if (data.header.rank_img) {
      const rankImg = document.getElementById('rank-img');
      if (rankImg) rankImg.src = data.header.rank_img;
    }

    setCheckboxes('cb_hap', data.header.happiness);
    setCheckboxes('cb_loy', data.header.loyalty);

    setValue('size', data.header.size);
    setValue('weight', data.header.weight);

    setText('id', data.header.id);
    setValue('team', data.header.team);
    setValue('age', data.header.age);
    setValue('money', data.header.money);
    setValue('rep', data.header.reputation);

    if (data.header.profile_picture) {
      const profileImg = document.getElementById('poke-img');
      if (profileImg) profileImg.src = data.header.profile_picture;
    }
  }

  // --- 2. Quick References ---
  if (data.quick_references) {
    if (data.quick_references.hp) {
      setValue('hpact', data.quick_references.hp.actual);
      setValue('hptot', data.quick_references.hp.total);
    }
    if (data.quick_references.def_spdef) {
      setValue('defact', data.quick_references.def_spdef.actual);
      setValue('deftot', data.quick_references.def_spdef.total);
    }
    if (data.quick_references.will) {
      setValue('willact', data.quick_references.will.actual);
      setValue('willtot', data.quick_references.will.total);
    }
    setByName('held_item', data.quick_references.held_item);
    setByName('status_effect', data.quick_references.status_effect);
    setByName('init', data.quick_references.initiative);
    setByName('evasion_val', data.quick_references.evasion);

    setCheckboxes('cb_fat', data.quick_references.fatigue);
    setCheckboxes('cb_act', data.quick_references.action_used);
  }

  // --- 3. Ability ---
  if (data.ability) {
    setValue('ability', data.ability.search_input);
    if (data.ability.details) {
      setText('h4-abil-name', data.ability.details.name);
      setText('p-abil-eff', data.ability.details.effect);
      setText('p-abil-text', data.ability.details.text);
    }
  }

  // --- 4. Nature ---
  if (data.nature) {
    setValue('nature', data.nature.search_input);
    if (data.nature.details) {
      setText('h4-nat-name', data.nature.details.name);
      setText('h4-nat-conf', data.nature.details.configuration);
      setText('h4-nat-key', data.nature.details.key);
      setText('p-nat-text', data.nature.details.text);
    }
  }

  // --- 5. Pokémon Team ---
  if (Array.isArray(data.pokemon_team)) {
    data.pokemon_team.forEach(item => {
      if (item.id) setValue(item.id, item.value);
    });
  }

  // --- 6. Attributes ---
  if (data.attributes) {
    setCheckboxes('cb_str', data.attributes.strength);
    setCheckboxes('cb_dex', data.attributes.dexterity);
    setCheckboxes('cb_vit', data.attributes.vitality);
    setCheckboxes('cb_spe', data.attributes.special);
    setCheckboxes('cb_ins', data.attributes.insight);
  }

  // --- 7. Social Attributes ---
  if (data.social_attributes) {
    setCheckboxes('cb_tou', data.social_attributes.tough);
    setCheckboxes('cb_coo', data.social_attributes.cool);
    setCheckboxes('cb_bea', data.social_attributes.beauty);
    setCheckboxes('cb_cut', data.social_attributes.cute);
    setCheckboxes('cb_cle', data.social_attributes.clever);
  }

  // --- 8. Skills ---
  if (data.skills) {
    if (data.skills.fight) {
      setCheckboxes('cb_bra', data.skills.fight.brawl);
      setCheckboxes('cb_chan', data.skills.fight.channel);
      setCheckboxes('cb_cla', data.skills.fight.clash);
      setCheckboxes('cb_eva', data.skills.fight.evasion);
      setCheckboxes('cb_thr', data.skills.fight.throw);
      setCheckboxes('cb_wea', data.skills.fight.weapons);
    }
    if (data.skills.survival) {
      setCheckboxes('cb_ale', data.skills.survival.alert);
      setCheckboxes('cb_ath', data.skills.survival.athletic);
      setCheckboxes('cb_nat', data.skills.survival.nature);
      setCheckboxes('cb_ste', data.skills.survival.stealth);
    }
    if (data.skills.social) {
      setCheckboxes('cb_chm', data.skills.social.charm);
      setCheckboxes('cb_emp', data.skills.social.empathy);
      setCheckboxes('cb_eti', data.skills.social.etiquette);
      setCheckboxes('cb_int', data.skills.social.intimidate);
      setCheckboxes('cb_per', data.skills.social.perform);
    }
    if (data.skills.knowledge) {
      setCheckboxes('cb_cra', data.skills.knowledge.crafts);
      setCheckboxes('cb_lor', data.skills.knowledge.lore);
      setCheckboxes('cb_med', data.skills.knowledge.medicine);
      setCheckboxes('cb_sci', data.skills.knowledge.science);
    }
  }

  // --- 9. Bag ---
  if (data.bag) {
    setValue('pot', data.bag.potion);
    setValue('spo', data.bag.super_potion);
    setValue('hpo', data.bag.hyper_potion);

    if (Array.isArray(data.bag.items_left)) {
      data.bag.items_left.forEach((val, i) => setValue(`bag-l${i + 1}`, val));
    }
    if (Array.isArray(data.bag.items_right)) {
      data.bag.items_right.forEach((val, i) => setValue(`bag-r${i + 1}`, val));
    }
  }

  // --- 10. Moves ---
  if (Array.isArray(data.moves)) {
    data.moves.forEach(m => {
      let input = document.getElementById(m.id);
      // Se la mossa salvata non esiste nell'HTML (es. oltre move2), crea la mossa dinamicamente
      if (!input && typeof addMove === 'function') {
        addMove('move');
        input = document.getElementById(m.id);
      }
      if (input) {
        input.value = m.value;
        // Se la mossa ha un valore, esegui la ricerca automatica per popolare la scheda
        const prog = m.id.replace('move', '');
        if (m.value && typeof search === 'function') {
          search('move', prog);
        }
      }
    });
  }

  // --- 11. Textareas ---
  const bgTextarea = document.querySelector('#div-background textarea');
  if (bgTextarea) bgTextarea.value = data.background || '';

  const conTextarea = document.querySelector('#div-conoscenze textarea');
  if (conTextarea) conTextarea.value = data.conoscenze_personali || '';
}

async function loadSheetData2(sheetType) {
  const SUPABASE_URL = 'https://lzppgwqfahqrcgsjyybl.supabase.co';
  const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx6cHBnd3FmYWhxcmNnc2p5eWJsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU3NjgxMzMsImV4cCI6MjEwMTM0NDEzM30.eTTTCnQ0usjEHnILcc9yboMMI_uC8nS8WdpcA1N_-5k';

  const supabaseclient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

  const trainer = document.getElementById('trainer')?.innerText.trim();
  const name = document.getElementById('name')?.innerText.trim();
  
  let filename = sheetType === 'trainer' 
    ? (trainer || 'trainer') 
    : ([trainer, name].filter(Boolean).join('_') || 'poke');

  try {
    const { data, error } = await supabaseclient
      .from('Characters')
      .select('value')
      .eq('name', filename)
      .single();

    if (error) throw error;

    if (data && data.value) {
      const parsedData = typeof data.value === 'string' ? JSON.parse(data.value) : data.value;
      populateSheet(parsedData);
    }
  } catch (err) {
    console.error("Failed to load sheet data:", err);
  }
}

// Run load on page startup
//window.onload = loadData;

// Call autoSave() whenever an input changes
//document.addEventListener('input', autoSave);

// Change input
// Change 'form' to '.fullpage' or 'document.body'
const container = document.querySelector('.fullpage');
const autosaveCheckbox = document.getElementById('autosave');
let save = autosaveCheckbox ? autosaveCheckbox.checked : false;

autosaveCheckbox.addEventListener('change', (e) => {
  save = e.target.checked; // Updates save whenever the user clicks the box
});

// 1. Tracks typing in text inputs, textareas, etc.
container.addEventListener('input', (event) => {
  const target = event.target;
  if (target.type !== 'checkbox' && target.type !== 'radio') {
    //console.log(`Input Modified [${target.name || target.id}]:`, target.value);
    if (save) {
      saveSheetData2('Trainer');
    }
  }
});

// 2. Tracks checkboxes and radio buttons on toggle
container.addEventListener('change', (event) => {
  const target = event.target;
  if (target.type === 'checkbox' || target.type === 'radio') {
    //console.log(`Checkbox Toggle [${target.name || target.id}]:`, target.checked);
    if (save) {
      saveSheetData2('Trainer');
    }
  }
});

// Start the app
init();
