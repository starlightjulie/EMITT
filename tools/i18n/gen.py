"""Builds res/values*/strings.xml from en.txt and <lang>.txt (key|text per line).

Translations may leave keys out (English is used for those). Placeholders like %1$s must match English.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")

# Strings that predate this file and live in the base strings.xml too
BASE = [
    ("app_name", "EMITT"),
    ("widget_label", "EMITT code"),
    ("widget_description", "Shows one of your saved codes on the home screen"),
    ("tile_label", "EMITT code"),
    ("channel_updates", "App updates"),
    ("channel_emulation", "Phone as NFC tag"),
    ("emulation_description", "EMITT NFC tag"),
]
UNTRANSLATABLE = {"app_name", "n_hours", "n_days", "hex", "pat_sos", "kind_sms", "wifi_wpa", "wifi_wep", "kind_wifi", "tab_flipper"}

LANGS = {
    "zh-CN": "zh-rCN", "hi": "hi", "es": "es", "fr": "fr", "ar": "ar", "bn": "bn", "pt-BR": "pt-rBR",
    "ru": "ru", "ur": "ur", "id": "in", "de": "de", "ja": "ja", "pcm": "b+pcm", "ar-EG": "ar-rEG",
    "mr": "mr", "te": "te", "tr": "tr", "ta": "ta", "zh-HK": "zh-rHK", "it": "it", "vi": "vi", "ko": "ko",
}


def load(path):
    d = {}
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        if "|" not in line:
            raise SystemExit(f"{path}:{n}: no '|'")
        k, v = line.split("|", 1)
        d[k.strip()] = v
    return d


def esc(s):
    s = s.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s[:1] in ("@", "?"):
        s = "\\" + s
    return s


def placeholders(s):
    return sorted(re.findall(r"%\d\$[.\d]*[sdf]", s))


def write(folder, entries, base=False):
    os.makedirs(os.path.join(RES, folder), exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for k, v in entries:
        attr = ' translatable="false"' if base and k in UNTRANSLATABLE else ""
        out.append(f'    <string name="{k}"{attr}>{esc(v)}</string>')
    out.append("</resources>\n")
    open(os.path.join(RES, folder, "strings.xml"), "w", encoding="utf-8", newline="\n").write("\n".join(out))


en = dict(BASE)
en.update(load(os.path.join(HERE, "en.txt")))
write("values", list(en.items()), base=True)
print(f"values: {len(en)} strings")

problems = 0
for tag, q in LANGS.items():
    p = os.path.join(HERE, f"{tag}.txt")
    if not os.path.exists(p):
        continue
    tr = load(p)
    entries = []
    for k, v in tr.items():
        if k not in en:
            print(f"  {tag}: unknown key {k}"); problems += 1; continue
        if k in UNTRANSLATABLE:
            continue
        if placeholders(v) != placeholders(en[k]):
            print(f"  {tag}: placeholder mismatch in {k}: {v!r}"); problems += 1; continue
        entries.append((k, v))
    missing = [k for k in en if k not in tr and k not in UNTRANSLATABLE]
    write(f"values-{q}", entries)
    print(f"values-{q}: {len(entries)} strings" + (f", {len(missing)} missing" if missing else ""))
sys.exit(1 if problems else 0)
