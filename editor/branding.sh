#!/bin/sh
# Tauscht Logo, Name und Farbe von LuckPermsWeb gegen Crowns Core. Laeuft im Docker-Build.
set -e
cd /app

cp /branding/logo.svg src/assets/logo.svg
cp /branding/logo.svg public/logo.svg
cp /branding/logo.png public/logo.png
cp /branding/apple-touch-icon.png public/apple-touch-icon.png
cp /branding/favicon.ico public/favicon.ico

# Schriften lokal statt von Google Fonts
npm install --no-save @fontsource/source-sans-pro@5 @fontsource/source-code-pro@5

# Texte, Menue, Befehle, Uebersetzungen, Schriften und Demo
node /branding/branding.js

# Uebersetzungs- und Versionsdaten liegen als Dateien beim Editor
mkdir -p public/meta
cp -r /branding/meta/. public/meta/

# Akzentfarbe: Gold wie im Logo statt LuckPerms-Gruen
sed -i 's|\$brand-color: #94df03;|$brand-color: #e0a83a;|' src/scss/variables.scss

# Seitentitel, Farbe, kein Statistik-Skript von luckperms.net
sed -i 's#<title>LuckPerms</title>#<title>Crowns Core Rechte</title>#' public/index.html
sed -i 's|#94df03|#e0a83a|' public/index.html
sed -i '/pl\.luckperms\.net/d; /window\.plausible/d; /fonts\.googleapis\.com/d' public/index.html

# Pruefen, dass alles gegriffen hat, sonst lieber abbrechen als halb umgebaut ausliefern
grep -q '<span>Crowns Core</span>' src/App.vue || { echo "Branding: App.vue nicht angepasst"; exit 1; }
grep -q 'e0a83a' src/scss/variables.scss || { echo "Branding: Farbe nicht angepasst"; exit 1; }
grep -q 'Crowns Core Rechte' public/index.html || { echo "Branding: index.html nicht angepasst"; exit 1; }
if grep -q 'luckperms.net/js' public/index.html; then echo "Branding: Statistik-Skript noch drin"; exit 1; fi
if grep -q 'fonts.googleapis' public/index.html; then echo "Branding: Google Fonts noch drin"; exit 1; fi
echo "Branding fertig"
