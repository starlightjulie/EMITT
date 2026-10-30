# Translations

Every `app/src/main/res/values*/strings.xml` is generated from the text files in this folder. Edit these files, not the XML.

- `en.txt` is the English source. One string per line: `key|text`. Lines starting with `#` are comments.
- `<tag>.txt` holds one language, using the same keys. The tags match `res/xml/locales_config.xml` (`zh-CN`, `pt-BR`, `ar-EG`, `pcm` and so on).
- A few strings that must look the same everywhere (the app name, "Wi-Fi", "Flipper", SMS, WPA/WEP, SOS, hex and the short hour/day units) are listed in `UNTRANSLATABLE` in `gen.py` and only appear in English.

After editing, run:

```bash
python tools/i18n/gen.py
```

It rewrites the `strings.xml` files and prints how many strings each language has. It stops with an error if a translation's placeholders (`%1$s`, `%2$d`, `%1$.1f`) don't match the English ones, or if a file uses a key English doesn't have.

## Adding a string

1. Add `key|English text` to `en.txt` and use `R.string.key` in the code.
2. Add the same key to each language file. A missing key falls back to English, so a new feature can ship before every translation is in. The script prints how many strings each language is missing.
3. Run the script and commit both the `.txt` files and the generated XML.

## Adding a language

Add the tag to `LANGS` in `gen.py` (with its Android resource folder name, for example `"id": "in"` or `"pcm": "b+pcm"`), to `res/xml/locales_config.xml`, and to `data/Languages.kt`. Then create `<tag>.txt`.

Keep Flipper menu paths (like `NFC → Saved → Emulate`) in English, since that's what the Flipper shows.
