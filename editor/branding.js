// Passt den Quelltext von LuckPermsWeb an Crowns Core an. Laeuft im Docker-Build (Node).
// Jede Ersetzung muss greifen, sonst bricht der Build ab, damit eine neue Editor-Version nicht
// unbemerkt halb umgebaut ausgeliefert wird.
const fs = require('fs');

function patch(file, pairs) {
  let s = fs.readFileSync(file, 'utf8');
  for (const [from, to, all] of pairs) {
    const hit = typeof from === 'string' ? s.includes(from) : from.test(s);
    if (!hit) {
      console.error(`Branding: "${from}" nicht gefunden in ${file}`);
      process.exit(1);
    }
    if (typeof from === 'string') s = all ? s.split(from).join(to) : s.replace(from, to);
    else s = s.replace(from, to);
  }
  fs.writeFileSync(file, s);
}

const toolLink = (route) => new RegExp(`\\s*<li>\\s*<router-link to="/${route}">[\\s\\S]*?</router-link>\\s*</li>`);
const homeLink = (route) => new RegExp(`\\s*<router-link to="/${route}"[^>]*>[\\s\\S]*?</router-link>`, 'g');

// Kopfzeile: Name, Titel, Menue nur mit dem Editor
patch('src/App.vue', [
  ['<span>LuckPerms</span>', '<span>Crowns Core</span>'],
  ['alt="LuckPerms logo"', 'alt="Crowns Core"'],
  ["'%s | LuckPerms'", "'%s | Crowns Core'"],
  [toolLink('verbose'), ''],
  [toolLink('treeview'), ''],
]);

// Startseite
patch('src/views/Home.vue', [
  ['<h1>LuckPerms</h1>', '<h1>Crowns Core</h1>'],
  ['alt="LuckPerms logo"', 'alt="Crowns Core"'],
  [homeLink('verbose'), ''],
  [homeLink('treeview'), ''],
]);

// Editor-Startseite: unsere Befehle statt /lp
patch('src/views/Editor.vue', [
  ['<h1>LuckPerms</h1>', '<h1>Crowns Core</h1>', true],
  ['<code>/lp editor</code>', '<code>/cccore perms editor</code>', true],
  [/\s*<li><code>\/lp user &lt;user&gt; editor<\/code><\/li>/, ''],
  [/\s*<li><code>\/lp group &lt;group&gt; editor<\/code><\/li>/, ''],
]);

// Uebersetzungen vom eigenen Server statt von metadata.luckperms.net
patch('src/store/language.js', [
  ["'https://metadata.luckperms.net/data/translations'", "'/meta/data/translations'"],
  ['`https://metadata.luckperms.net/translation/web/${locale}`', '`/meta/translation/web/${locale}`'],
]);

// Englische Texte (fest eingebaut)
const en = JSON.parse(fs.readFileSync('src/messages/en.json', 'utf8'));
const walk = (o) => {
  if (typeof o === 'string') return o.split('/lp ').join('/cccore perms ').split('LuckPerms').join('Crowns Core');
  if (Array.isArray(o)) return o.map(walk);
  if (o && typeof o === 'object') return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, walk(v)]));
  return o;
};
const enNew = walk(en);
enNew.description = 'Permission editor for the Crowns and Cannons server.';
fs.writeFileSync('src/messages/en.json', JSON.stringify(enNew, null, 2));

// Schriften aus dem eigenen Build statt von Google
const main = fs.readFileSync('src/main.js', 'utf8');
fs.writeFileSync('src/main.js', [
  "import '@fontsource/source-sans-pro/400.css';",
  "import '@fontsource/source-sans-pro/700.css';",
  "import '@fontsource/source-code-pro/400.css';",
  "import '@fontsource/source-code-pro/700.css';",
  main,
].join('\n'));

// Demo mit den Befehlen von Crowns Core und dem Modpack
fs.copyFileSync('/branding/editor-demo.json', 'src/data/editor-demo.json');

console.log('Branding (Quelltext) fertig');
