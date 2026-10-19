#!/usr/bin/env python3
"""
XGram: audit of the universal settings search's Telegram X index (app/src/main/java/com/fahadali/xgram/search/
TgxSearchIndex.kt). Run it before a release, from anywhere:

    python3 scripts/audit_settings_search.py

It prints, per indexed Telegram X page:
  - rows of the page's controller that a user can look for (TYPE_SETTING, TYPE_VALUED_SETTING*, TYPE_RADIO_SETTING
    with an id) that are neither in the index nor in TgxSearchIndex.IGNORED: new rows to index (or to ignore);
  - ids the index points at that the page's controller no longer has: removed or renamed rows.
It changes nothing. Exit code 1 when it found something.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app", "src", "main", "java")
UI = os.path.join(JAVA, "org", "thunderdog", "challegram", "ui")
INDEX = os.path.join(JAVA, "com", "fahadali", "xgram", "search", "TgxSearchIndex.kt")

# The first part of a result key ("t:<page>/...") and the page variable names of TgxSearchIndex: the controller.
PAGE_FILES = {
    "settings": "SettingsController",
    "devices": "SettingsSessionsController",
    "websites": "SettingsWebsitesController",
    "notif": "SettingsNotificationController",
    "notifications": "SettingsNotificationController",
    "archive": "SettingsArchiveChatListController",
    "data": "SettingsDataController",
    "net": "SettingsDataController",
    "networkUsage": "SettingsDataController",
    "storage": "SettingsCacheController",
    "proxy": "SettingsProxyController",
    "privacy": "SettingsPrivacyController",
    "rule": "SettingsPrivacyKeyController",
    "blocked": "SettingsBlockedController",
    "passcode": "PasscodeSetupController",
    "themes": "SettingsThemeController",
    "ui": "SettingsThemeController",
    "tweaks": "SettingsThemeController",
    "stickers": "SettingsStickersAndEmojiController",
    "language": "SettingsLanguageController",
    "translate": "SettingsLanguageTranslateController",
    "translation": "SettingsLanguageTranslateController",
    "folders": "SettingsFoldersController",
    "logout": "SettingsLogOutController",
    "logs": "SettingsBugController",
    "appLogs": "SettingsBugController",
}

# Debug sections and one chat's notification page: left out as a whole (see TgxSearchIndex.IGNORED).
IGNORED_PREFIXES = ("btn_secret_", "btn_test_", "btn_tdlib_", "btn_customChat_")

ROW_TYPES = re.compile(
    r"new\s+ListItem\s*\(\s*(?:ListItem\.)?TYPE_(SETTING|VALUED_SETTING\w*|RADIO_SETTING)\s*,\s*R\.id\.(\w+)")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def main():
    index = read(INDEX)
    sources = {}
    for name in set(PAGE_FILES.values()):
        path = os.path.join(UI, name + ".java")
        if os.path.exists(path):
            sources[name] = read(path)
        else:
            print("missing controller file:", path)

    problems = 0
    indexed_ids = set(re.findall(r"R\.id\.(\w+)", index))

    # Ids the index points at, by the page they are on
    for m in re.finditer(r'\b(row|entry)\(\s*\w+\s*,\s*"([^"]+)"[^\n]*', index):
        key = m.group(2)
        page = key.split("/")[0]
        name = PAGE_FILES.get(page)
        line = m.group(0)
        for rid in re.findall(r"RowRef\(\s*R\.id\.(\w+)", line):
            if name and name in sources and ("R.id." + rid) not in sources[name]:
                problems += 1
                print("[gone]  t:%s points at R.id.%s, which %s no longer has" % (key, rid, name))
    # Rows that open a page: on the parent page
    for m in re.finditer(r'page\(\s*"([^"]+)"\s*,[^\n]*?,\s*(\w+)\s*,\s*RowRef\(\s*R\.id\.(\w+)', index, re.S):
        parent, rid = m.group(2), m.group(3)
        name = PAGE_FILES.get(parent)
        if name and name in sources and ("R.id." + rid) not in sources[name]:
            problems += 1
            print("[gone]  page %s opens from R.id.%s, which %s no longer has" % (m.group(1), rid, name))

    # Rows of the indexed pages that are not in the index or IGNORED
    for name in sorted(set(PAGE_FILES.values())):
        text = sources.get(name)
        if text is None:
            continue
        missing = []
        for m in ROW_TYPES.finditer(text):
            rid = m.group(2)
            if rid not in indexed_ids and rid not in missing and not rid.startswith(IGNORED_PREFIXES):
                missing.append(rid)
        if missing:
            problems += len(missing)
            print("[new]   %s: %s" % (name, ", ".join("R.id." + r for r in missing)))

    if problems == 0:
        print("settings search index: no differences")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
