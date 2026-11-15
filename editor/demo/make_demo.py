"""Erzeugt die Demo des Rechte-Editors und die deutschen Texte aus den echten Befehlen.

Eingaben:  ../../src/main/resources/crownscore/commands.tsv  (Befehle der Mod)
           modpack-befehle.txt  (Wurzelbefehle des Modpacks, aus /help eines Testservers)
           de-luckperms.json    (deutsche Texte von LuckPermsWeb, einmalig geladen)
Ausgaben:  editor-demo.json, ../meta/translation/web/de, ../meta/data/translations, ../meta/data/all

Aufruf im Ordner editor/demo:  python make_demo.py
"""
import io
import json
import os
import re
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
META = os.path.join(os.path.dirname(HERE), "meta")


def read_catalog():
    nodes = []
    with io.open(os.path.join(ROOT, "src", "main", "resources", "crownscore", "commands.tsv"), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#") or line.startswith("@"):
                continue
            syntax = line.split("\t")[0]
            lits = []
            for t in syntax.lstrip("/").split():
                if t.startswith("<") or t.startswith("["):
                    break
                lits.append(t.lower())
            nodes.append("crownscore." + ".".join(lits))
    return sorted(set(nodes))


def node(key, value=True, expiry=None, world=None):
    t = ("inheritance" if key.startswith("group.") else "prefix" if key.startswith("prefix.") else "suffix" if key.startswith("suffix.")
         else "weight" if key.startswith("weight.") else "display_name" if key.startswith("displayname.") else "permission")
    n = {"type": t, "key": key, "value": value}
    if expiry:
        n["expiry"] = expiry
    if world:
        n["context"] = {"world": world}
    return n


def demo():
    commands = [c.strip() for c in io.open(os.path.join(HERE, "modpack-befehle.txt"), encoding="utf-8") if c.strip()]
    catalog = read_catalog()
    known = sorted(set(catalog) | {"command." + c for c in commands} | {"crownscore.admin", "crownscore.*", "*"})

    def has(cmd):
        return cmd in commands

    groups = [
        {
            "type": "group", "id": "admin", "displayName": "Admin",
            "nodes": [node("displayname.Admin"), node("weight.100"), node("prefix.100.&c[Admin]"), node("group.gamemaster"),
                      node("crownscore.*"), node("crownscore.admin"), node("command.op"), node("command.stop"),
                      node("command.whitelist"), node("command.ban"), node("command.pardon")],
        },
        {
            "type": "group", "id": "gamemaster", "displayName": "Gamemaster",
            "nodes": [node("displayname.Gamemaster"), node("weight.80"), node("prefix.80.&6[Gamemaster]"), node("group.moderator"),
                      node("crownscore.nation.anlegen"), node("crownscore.nation.staatschef"), node("crownscore.nation.setzen"),
                      node("crownscore.krieg.beenden"), node("crownscore.kolonie.genehmigen"), node("crownscore.kolonie.ablehnen"),
                      node("crownscore.staatskasse.setzen"), node("crownscore.staatskasse.geldgeben"),
                      node("crownscore.cccore.admin.*"), node("crownscore.cccore.perms.*"),
                      node("command.gamemode"), node("command.time"), node("command.weather"), node("command.give")]
                     + [node("command." + c) for c in ("season", "resetmoney", "recruits", "smallships", "waystones") if has(c)],
        },
        {
            "type": "group", "id": "moderator", "displayName": "Moderator",
            "nodes": [node("displayname.Moderator"), node("weight.50"), node("prefix.50.&9[Mod]"), node("group.default"),
                      node("crownscore.cccore.admin.lookup"), node("crownscore.cccore.admin.inspect"),
                      node("crownscore.cccore.admin.near"), node("crownscore.cccore.admin.seite"),
                      node("crownscore.akte.spieler"), node("crownscore.akte.gebiet"), node("crownscore.akte.neueste"),
                      node("crownscore.aktivitaet.bericht"), node("command.kick"), node("command.tp"),
                      node("command.spectate", True, None, "minecraft:overworld")]
                     + [node("command." + c) for c in ("restoreinventory", "view") if has(c)],
        },
        {
            "type": "group", "id": "default", "displayName": "default",
            "nodes": [node("crownscore.modus"), node("crownscore.nation.beitreten"), node("crownscore.krieg.liste"),
                      node("crownscore.post.senden"), node("crownscore.vertrag.hinterlegen"),
                      node("command.msg"), node("command.tell"), node("command.me"), node("command.list"),
                      node("command.seed", False)],
        },
    ]

    def fake_uuid(name):
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, "crownscore-demo-" + name))

    users = [
        ("Koenig_Arthur", [node("group.admin")]),
        ("Spielleiterin_Mia", [node("group.gamemaster"), node("prefix.200.&5[Spielleitung]")]),
        ("Wache_Konrad", [node("group.moderator"), node("crownscore.cccore.admin.rollback", True, 1924988400)]),
        ("Haendlerin_Lene", [node("group.default"), node("command.atm")] if has("atm") else [node("group.default")]),
        ("Seefahrer_Jonas", [node("group.default"), node("crownscore.krieg.erklaeren", False)]),
    ]
    holders = groups + [{"type": "user", "id": fake_uuid(n), "displayName": n, "nodes": ns} for n, ns in users]

    return {
        "metadata": {
            "commandAlias": "cccore perms",
            "uploader": {"name": "Konsole", "uuid": "00000000-0000-0000-0000-000000000000"},
            "time": 1763200000000,
            "pluginVersion": "5.5.0",
            "platform": "Crowns Core (Forge)",
        },
        "permissionHolders": holders,
        "tracks": [{"type": "track", "id": "team", "groups": ["default", "moderator", "gamemaster", "admin"]}],
        "knownPermissions": known,
        "potentialContexts": {"world": ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"]},
    }


def german():
    d = json.load(io.open(os.path.join(HERE, "de-luckperms.json"), encoding="utf-8"))

    def walk(o):
        if isinstance(o, dict):
            return {k: walk(v) for k, v in o.items()}
        if isinstance(o, list):
            return [walk(v) for v in o]
        if isinstance(o, str):
            return o.replace("/lp ", "/cccore perms ").replace("LuckPerms", "Crowns Core")
        return o

    d = walk(d)
    d["description"] = "Rechte-Editor für den Crowns-and-Cannons-Server."
    d.setdefault("editor", {}).setdefault("unsupported", {})["info"] = "Bitte aktualisiere Crowns Core auf dem Server, um den Rechte-Editor zu nutzen. ({download})"
    return d


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    write(os.path.join(HERE, "editor-demo.json"), demo())
    write(os.path.join(META, "translation", "web", "de"), german())
    write(os.path.join(META, "data", "translations"), {"languages": {
        "de": {"name": "Deutsch", "localeTag": "de_DE", "progress": 100, "progressWeb": 100, "contributors": []}}})
    write(os.path.join(META, "data", "all"), {"version": "5.5.0", "versionTimestamp": 0, "changeLog": [], "downloads": {},
                                              "extensions": {}, "additionalPlugins": {}, "placeholderExpansions": {}, "discordUserCount": 0})
    print("Demo und Texte geschrieben")
