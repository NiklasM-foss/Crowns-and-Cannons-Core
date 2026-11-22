(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var API = "../api/";
  var passwort = "";
  var eintraege = [];      // Ergebnis der Pruefung
  var dateienNachPfad = {}; // Pfad -> File (zum Hochladen)
  var KAT = { mods: "mod", resourcepacks: "rp", datapacks: "dp", shaderpacks: "shader", kubejs: "kubejs" };
  var KAT_NAME = { mod: "Mod", rp: "Ressourcenpaket", dp: "Datenpaket", shader: "Shader", kubejs: "KubeJS" };
  var ENDUNG = { mod: /\.jar$/i, rp: /\.zip$/i, dp: /\.zip$/i, shader: /\.zip$/i, kubejs: /./ };

  function api(pfad, opt) {
    opt = opt || {};
    opt.headers = Object.assign({ "X-Pack-Passwort": passwort }, opt.headers || {});
    return fetch(API + pfad, opt).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) { j._code = r.status; return j; });
    });
  }

  function hex(buf) {
    return Array.prototype.map.call(new Uint8Array(buf), function (b) { return ("0" + b.toString(16)).slice(-2); }).join("");
  }

  $("anmelden").onclick = function () {
    passwort = $("passwort").value;
    api("anmelden", { method: "POST", body: "{}", headers: { "Content-Type": "application/json" } }).then(function (j) {
      if (j._code !== 200) { $("anmeldeStatus").textContent = j.fehler || "Fehler"; $("anmeldeStatus").className = "fehler"; return; }
      $("anmeldeStatus").textContent = "angemeldet" + (j.curseforge ? "" : " (ohne CurseForge-Schlüssel: nur Modrinth und Server)");
      $("anmeldeStatus").className = "leise";
      $("auswahl").hidden = false;
      fetch("../crowns.json").then(function (r) { return r.json(); }).then(function (l) {
        if (l && l.jvm && l.jvm.length) $("jvm").value = l.jvm.join(" ");
      }).catch(function () {});
    });
  };
  $("passwort").addEventListener("keydown", function (e) { if (e.key === "Enter") $("anmelden").click(); });

  $("ordner").onchange = function () {
    var files = Array.prototype.slice.call(this.files);
    var instanz = null, liste = [];
    dateienNachPfad = {};
    files.forEach(function (f) {
      var teile = f.webkitRelativePath.split("/");
      teile.shift(); // Name des gewaehlten Ordners
      var rel = teile.join("/");
      if (rel === "minecraftinstance.json") instanz = f;
      var kat = KAT[teile[0]];
      if (!kat || teile.length < 2) return;
      if (kat !== "kubejs" && teile.length !== 2) return; // nur direkt im Ordner
      if (!ENDUNG[kat].test(rel)) return;
      if (kat === "kubejs" && /(^|\/)(probe|exported|cache)\//.test(rel)) return;
      liste.push({ pfad: rel, kategorie: kat, datei: f });
      dateienNachPfad[rel] = f;
    });
    if (!liste.length) { $("leseStatus").textContent = "Keine passenden Dateien gefunden. Ist das der Instanz-Ordner?"; return; }
    var cf = {};
    var weiter = instanz ? instanz.text().then(function (t) {
      try {
        var j = JSON.parse(t.replace(/^﻿/, ""));
        (j.installedAddons || []).forEach(function (a) {
          if (a.installedFile) cf[a.installedFile.fileName] = { projekt: a.addonID, datei: a.installedFile.id };
        });
        if (j.javaArgsOverride && !$("jvm").value) $("jvm").value = j.javaArgsOverride;
      } catch (e) {}
    }) : Promise.resolve();
    weiter.then(function () { return hashen(liste, cf); }).then(pruefen);
  };

  function hashen(liste, cf) {
    var fertig = 0, out = [];
    var ketten = Promise.resolve();
    liste.forEach(function (x) {
      ketten = ketten.then(function () {
        return x.datei.arrayBuffer().then(function (buf) {
          return Promise.all([crypto.subtle.digest("SHA-1", buf), crypto.subtle.digest("SHA-512", buf)]);
        }).then(function (h) {
          var name = x.pfad.split("/").pop();
          var c = cf[name] || {};
          out.push({ pfad: x.pfad, kategorie: x.kategorie, name: name, groesse: x.datei.size, sha1: hex(h[0]), sha512: hex(h[1]),
                     cfProjekt: c.projekt || null, cfDatei: c.datei || null });
          fertig++;
          $("leseStatus").textContent = "Prüfsummen: " + fertig + " von " + liste.length;
        });
      });
    });
    return ketten.then(function () { return out; });
  }

  function pruefen(liste) {
    $("leseStatus").textContent = "Quellen werden gesucht …";
    return api("pruefen", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ eintraege: liste }) }).then(function (j) {
      if (j._code !== 200) { $("leseStatus").textContent = j.fehler || "Fehler"; return; }
      eintraege = j.eintraege.map(function (e) { e.drin = true; return e; });
      $("leseStatus").textContent = eintraege.length + " Dateien gelesen.";
      $("ergebnis").hidden = false;
      $("veroeff").hidden = false;
      zeigen();
    });
  }

  function zeigen() {
    var f = $("filter").value;
    var tb = $("tabelle");
    tb.textContent = "";
    var z = { modrinth: 0, curseforge: 0, pi: 0, fehlt: 0 };
    // Reihenfolge: ohne Quelle zuerst, dann Modrinth, CurseForge, Server; innerhalb nach Pfad
    var RANG = { fehlt: 0, modrinth: 1, curseforge: 2, pi: 3 };
    var sortiert = eintraege.slice().sort(function (a, b) {
      return (RANG[a.quelle] - RANG[b.quelle]) || a.pfad.localeCompare(b.pfad, "de", { sensitivity: "base" });
    });
    sortiert.forEach(function (e, i) {
      if (e.drin) z[e.quelle]++;
      if (f === "fehlt" && e.quelle !== "fehlt") return;
      if (f && f !== "fehlt" && e.kategorie !== f) return;
      var tr = document.createElement("tr");
      var td0 = document.createElement("td");
      var cb = document.createElement("input");
      cb.type = "checkbox"; cb.checked = e.drin;
      cb.onchange = function () { e.drin = cb.checked; zeigen(); };
      td0.appendChild(cb);
      var td1 = document.createElement("td"); td1.className = "datei"; td1.textContent = e.pfad;
      var td2 = document.createElement("td"); td2.textContent = KAT_NAME[e.kategorie] || e.kategorie;
      var td3 = document.createElement("td");
      var q = document.createElement("span"); q.className = "q " + e.quelle;
      q.textContent = { modrinth: "Modrinth", curseforge: "CurseForge", pi: "Server", fehlt: "keine" }[e.quelle];
      td3.appendChild(q);
      var td4 = document.createElement("td");
      if (e.quelle === "pi" && e.erlaubnis) td4.textContent = e.erlaubnis;
      if (e.quelle === "fehlt") td4.appendChild(hochForm(e, i));
      [td0, td1, td2, td3, td4].forEach(function (t) { tr.appendChild(t); });
      tb.appendChild(tr);
    });
    $("zahlen").innerHTML = "";
    [["Modrinth", z.modrinth], ["CurseForge", z.curseforge], ["Server", z.pi], ["ohne Quelle", z.fehlt]].forEach(function (p) {
      var s = document.createElement("span"); s.textContent = p[0] + ": " + p[1]; $("zahlen").appendChild(s);
    });
  }
  $("filter").onchange = zeigen;

  function hochForm(e) {
    var d = document.createElement("div");
    var hinweis = document.createElement("div");
    hinweis.className = "leise";
    hinweis.textContent = e.seite ? "Nicht automatisch ladbar. Spieler bekommen den Link zur CurseForge-Seite." : "Weder auf Modrinth noch auf CurseForge gefunden.";
    d.appendChild(hinweis);
    var h = document.createElement("div"); h.className = "hoch";
    var sel = document.createElement("select");
    [["", "Auf den Server legen, weil …"], ["eigen", "eigene Datei"], ["lizenz", "Lizenz erlaubt Weitergabe"], ["autor", "Erlaubnis vom Autor"]].forEach(function (o) {
      var op = document.createElement("option"); op.value = o[0]; op.textContent = o[1]; sel.appendChild(op);
    });
    if (e.kategorie === "kubejs") sel.value = "eigen";
    var notiz = document.createElement("input"); notiz.type = "text"; notiz.placeholder = "Notiz, z.B. Lizenz MIT oder Erlaubnis von … am …";
    var btn = document.createElement("button"); btn.className = "zweit"; btn.textContent = "Hochladen";
    btn.onclick = function () { hochladen(e, sel.value, notiz.value, btn); };
    h.appendChild(sel); h.appendChild(notiz); h.appendChild(btn);
    d.appendChild(h);
    return d;
  }

  function hochladen(e, erlaubnis, notiz, btn) {
    if (!erlaubnis) { alert("Bitte auswählen, warum die Datei weitergegeben werden darf."); return Promise.resolve(false); }
    if (erlaubnis === "autor" && notiz.trim().length < 5) { alert("Bitte notieren, wer wann die Erlaubnis gegeben hat."); return Promise.resolve(false); }
    var f = dateienNachPfad[e.pfad];
    if (btn) { btn.disabled = true; btn.textContent = "lädt …"; }
    var q = "hochladen?sha512=" + e.sha512 + "&name=" + encodeURIComponent(e.name) + "&erlaubnis=" + erlaubnis + "&notiz=" + encodeURIComponent(notiz || (erlaubnis === "eigen" ? "eigene Datei" : ""));
    return api(q, { method: "POST", body: f, headers: { "Content-Type": "application/octet-stream" } }).then(function (j) {
      if (j._code !== 200) { alert(j.fehler || "Hochladen fehlgeschlagen"); if (btn) { btn.disabled = false; btn.textContent = "Hochladen"; } return false; }
      e.quelle = "pi"; e.quellen = (e.quellen || []).concat(["pi:" + e.sha512]); e.erlaubnis = notiz || erlaubnis;
      if (btn) zeigen();
      return true;
    });
  }

  $("kubejsAlle").onclick = function () {
    var offen = eintraege.filter(function (e) { return e.kategorie === "kubejs" && e.quelle === "fehlt" && e.drin; });
    if (!offen.length) { alert("Keine KubeJS-Dateien ohne Quelle."); return; }
    if (!confirm(offen.length + " KubeJS-Dateien als eigene Dateien auf den Server legen?")) return;
    var b = this; b.disabled = true;
    var k = Promise.resolve();
    offen.forEach(function (e) { k = k.then(function () { return hochladen(e, "eigen", "eigene KubeJS-Datei"); }); });
    k.then(function () { b.disabled = false; zeigen(); });
  };

  $("veroeffentlichen").onclick = function () {
    var drin = eintraege.filter(function (e) { return e.drin; });
    var ohne = drin.filter(function (e) { return e.quelle === "fehlt"; }).length;
    if (!confirm(drin.length + " Dateien veröffentlichen" + (ohne ? ", davon " + ohne + " ohne automatische Quelle" : "") + "?")) return;
    var b = this; b.disabled = true;
    var jvm = $("jvm").value.trim().split(/\s+/).filter(Boolean);
    api("veroeffentlichen", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ eintraege: drin, jvm: jvm }) }).then(function (j) {
      b.disabled = false;
      $("veroeffStatus").textContent = j._code === 200 ? "Veröffentlicht: Pack-Version " + j.version + " mit " + j.dateien + " Dateien. Die Spieler bekommen sie beim nächsten Lauf des Helfers." : (j.fehler || "Fehler");
      $("veroeffStatus").className = j._code === 200 ? "leise" : "fehler";
    });
  };
})();
