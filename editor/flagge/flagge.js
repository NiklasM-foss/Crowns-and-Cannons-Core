// Upload-Seite fuer Flaggen (Crowns Core). Rechnet den Umhang genauso wie der Server
// (FlagImages.java): Flagge um 90 Grad drehen, Flaeche 10x16 fuellen, mittig zuschneiden.
(function () {
  "use strict";

  var S = 8;                 // Aufloesung wie auf dem Server: 512 x 256
  var FW = 10 * S, FH = 16 * S;
  var STORE_SIDE = 1024;     // laengste Seite beim Hochladen
  var MAX_BYTES = 8 * 1024 * 1024;
  var rechts = !/[?&]drehung=links/i.test(location.search);

  var $ = function (id) { return document.getElementById(id); };
  var datei = $("datei"), drop = $("drop"), fehler = $("fehler");
  var upload = null;         // PNG-Blob, der hochgeladen wird
  var viewer = null;

  if (!rechts) $("drehText").textContent = "die Mastseite kommt nach unten";

  function zeigeFehler(text) {
    fehler.textContent = text;
    fehler.hidden = !text;
  }

  function canvas(w, h) {
    var c = document.createElement("canvas");
    c.width = w;
    c.height = h;
    return c;
  }

  function ctx2d(c) {
    var g = c.getContext("2d");
    g.imageSmoothingEnabled = true;
    g.imageSmoothingQuality = "high";
    return g;
  }

  // Auf hoechstens STORE_SIDE verkleinern, als PNG neu kodieren (entfernt auch Metadaten)
  function normalisieren(img) {
    var w = img.naturalWidth, h = img.naturalHeight;
    var f = Math.min(1, STORE_SIDE / Math.max(w, h));
    var c = canvas(Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)));
    ctx2d(c).drawImage(img, 0, 0, c.width, c.height);
    return c;
  }

  function drehen(src) {
    var c = canvas(src.height, src.width), g = ctx2d(c);
    if (rechts) {
      g.translate(c.width, 0);
      g.rotate(Math.PI / 2);
    } else {
      g.translate(0, c.height);
      g.rotate(-Math.PI / 2);
    }
    g.drawImage(src, 0, 0);
    return c;
  }

  function flaeche(flag) {
    var r = drehen(flag);
    var f = Math.max(FW / r.width, FH / r.height);
    var sw = Math.max(FW, Math.ceil(r.width * f)), sh = Math.max(FH, Math.ceil(r.height * f));
    var c = canvas(FW, FH), g = ctx2d(c);
    g.fillStyle = "#fff";
    g.fillRect(0, 0, FW, FH);
    g.drawImage(r, -Math.floor((sw - FW) / 2), -Math.floor((sh - FH) / 2), sw, sh);
    return c;
  }

  function umhang(face) {
    var tex = canvas(64 * S, 32 * S), g = tex.getContext("2d");
    g.imageSmoothingEnabled = false;
    g.drawImage(face, S, S);
    // Innenseite gespiegelt und dunkler
    g.save();
    g.translate(12 * S + FW, S);
    g.scale(-1, 1);
    g.drawImage(face, 0, 0);
    g.restore();
    g.fillStyle = "rgba(0,0,0,0.3)";
    g.fillRect(12 * S, S, FW, FH);
    // Kanten aus den Randpixeln
    g.drawImage(face, 0, 0, FW, 1, S, 0, FW, S);
    g.drawImage(face, 0, FH - 1, FW, 1, 11 * S, 0, FW, S);
    g.drawImage(face, FW - 1, 0, 1, FH, 0, S, S, FH);
    g.drawImage(face, 0, 0, 1, FH, 11 * S, S, S, FH);
    g.fillStyle = "rgba(0,0,0,0.2)";
    g.fillRect(S, 0, 20 * S, S);
    g.fillRect(0, S, S, FH);
    g.fillRect(11 * S, S, S, FH);
    return tex;
  }

  // Schlichter Skin ohne fremde Texturen, damit der Umhang im Mittelpunkt steht
  function skin() {
    var c = canvas(64, 64), g = c.getContext("2d");
    var teile = [
      ["#c69c6d", 0, 0, 32, 16],   // Kopf
      ["#3d5a80", 16, 16, 24, 16], // Koerper
      ["#3d5a80", 40, 16, 16, 16], // rechter Arm
      ["#2b2d42", 0, 16, 16, 16],  // rechtes Bein
      ["#2b2d42", 16, 48, 16, 16], // linkes Bein
      ["#3d5a80", 32, 48, 16, 16]  // linker Arm
    ];
    teile.forEach(function (t) {
      g.fillStyle = t[0];
      g.fillRect(t[1], t[2], t[3], t[4]);
    });
    g.fillStyle = "#4a3426";      // Haare oben auf dem Kopf
    g.fillRect(8, 0, 8, 8);
    g.fillRect(24, 8, 8, 8);
    return c;
  }

  function vonHinten() {
    if (!viewer) return;
    viewer.playerObject.rotation.y = Math.PI;
    viewer.controls.reset && viewer.controls.reset();
  }

  function zeige3d(tex) {
    var el = $("skin3d");
    if (!window.skinview3d) return;
    if (!viewer) {
      viewer = new skinview3d.SkinViewer({ canvas: el, width: el.clientWidth || 340, height: 380, skin: skin() });
      viewer.zoom = 0.85;
      viewer.autoRotate = false;
      viewer.animation = new skinview3d.WalkingAnimation();
      viewer.animation.speed = 0.6;
      viewer.playerObject.rotation.y = Math.PI;
      window.addEventListener("resize", function () {
        viewer.setSize(el.clientWidth || 340, 380);
      });
    }
    viewer.loadCape(tex);
  }

  function lade(file) {
    zeigeFehler("");
    if (!file) return;
    if (!/^image\//.test(file.type)) return zeigeFehler("Das ist kein Bild. Erlaubt sind PNG und JPG.");
    if (file.size > MAX_BYTES) return zeigeFehler("Das Bild ist zu groß (höchstens 8 MB).");
    var url = URL.createObjectURL(file);
    var img = new Image();
    img.onload = function () {
      URL.revokeObjectURL(url);
      if (img.naturalWidth < 8 || img.naturalHeight < 8) return zeigeFehler("Das Bild ist zu klein.");
      var flag = normalisieren(img);
      var face = flaeche(flag);
      var f = $("flaeche");
      var g = f.getContext("2d");
      g.clearRect(0, 0, f.width, f.height);
      g.drawImage(face, 0, 0);
      zeige3d(umhang(face));
      flag.toBlob(function (b) {
        upload = b;
        $("dropText").innerHTML = "<b>" + escape(file.name) + "</b><br><small>anderes Bild: hier klicken oder hineinziehen</small>";
        $("schritt2").hidden = false;
        $("schritt3").hidden = true;
        $("hochladen").disabled = false;
      }, "image/png");
    };
    img.onerror = function () {
      URL.revokeObjectURL(url);
      zeigeFehler("Das Bild konnte nicht gelesen werden.");
    };
    img.src = url;
  }

  function escape(s) {
    return s.replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; });
  }

  function hochladen() {
    if (!upload) return;
    var btn = $("hochladen");
    btn.disabled = true;
    btn.textContent = "Lädt hoch ...";
    zeigeFehler("");
    fetch(new URL("../data/post", location.href), {
      method: "POST",
      headers: { "Content-Type": "image/png" },
      body: upload
    }).then(function (res) {
      if (!res.ok) throw new Error("Server antwortet mit HTTP " + res.status);
      var loc = res.headers.get("Location");
      return res.json().catch(function () { return {}; }).then(function (j) {
        var key = j.key || (loc ? loc.split("/").pop() : "");
        if (!key) throw new Error("Kein Code erhalten");
        return key;
      });
    }).then(function (key) {
      $("befehl").textContent = "/nation flagge anwenden " + key;
      $("schritt3").hidden = false;
      $("schritt3").scrollIntoView({ behavior: "smooth", block: "start" });
    }).catch(function (e) {
      zeigeFehler("Hochladen fehlgeschlagen: " + e.message);
    }).then(function () {
      btn.disabled = false;
      btn.textContent = "Hochladen";
    });
  }

  datei.addEventListener("change", function () { lade(datei.files[0]); });
  ["dragenter", "dragover"].forEach(function (t) {
    drop.addEventListener(t, function (e) { e.preventDefault(); drop.classList.add("ueber"); });
  });
  ["dragleave", "drop"].forEach(function (t) {
    drop.addEventListener(t, function (e) { e.preventDefault(); drop.classList.remove("ueber"); });
  });
  drop.addEventListener("drop", function (e) { lade(e.dataTransfer.files[0]); });
  $("hochladen").addEventListener("click", hochladen);
  $("dreh").addEventListener("click", function () { if (viewer) viewer.autoRotate = !viewer.autoRotate; });
  $("hinten").addEventListener("click", function () { if (viewer) { viewer.autoRotate = false; vonHinten(); } });
  $("kopieren").addEventListener("click", function () {
    var t = $("befehl").textContent;
    (navigator.clipboard ? navigator.clipboard.writeText(t) : Promise.reject()).then(function () {
      $("kopieren").textContent = "Kopiert";
      setTimeout(function () { $("kopieren").textContent = "Kopieren"; }, 1500);
    }).catch(function () {
      var r = document.createRange();
      r.selectNodeContents($("befehl"));
      getSelection().removeAllRanges();
      getSelection().addRange(r);
    });
  });
  $("nochmal").addEventListener("click", function () { datei.value = ""; datei.click(); });
})();
