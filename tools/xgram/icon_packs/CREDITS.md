# XGram icon packs: credits and licences

XGram's icon packs (Settings → Themes → Icon Set, or Settings → XGram → Appearance → Icons) are converted to Android
vector drawables by `tools/xgram/icon_packs/build.py` from these open-source sets (Iconify JSON packages, fetched from
cdn.jsdelivr.net into `tools/xgram/icon_packs/vendor/`, which is not committed):

| Pack | Source | Author | Licence |
|---|---|---|---|
| Solar | [Solar Icon Set](https://www.figma.com/community/file/1166831539721848736) (`@iconify-json/solar` 1.2.13), Bold Duotone style | 480 Design | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| Phosphor | [Phosphor Icons](https://phosphoricons.com) (`@iconify-json/ph` 1.2.2), Bold weight | Phosphor Icons | MIT |
| MingCute | [MingCute](https://www.mingcute.com) (`@iconify-json/mingcute` 1.2.8), Line style | MingCute Design | Apache 2.0 |
| Pixel | [Pixelarticons](https://pixelarticons.com) (`@iconify-json/pixelarticons` 1.2.10) | Gerrit Halfmann | MIT |
| XGram Line | XGram's own drawings (`tools/xgram/icons_line.py`, `tools/xgram/icon_packs/batches/`) | XGram | GPLv3 (as XGram) |

The Solar pack is changed from the original: icons are recoloured to follow the app theme and converted to Android
vector drawables. The picker shows each pack's author and licence.

Which library icon replaces which Telegram X icon is in `tools/xgram/icon_packs/maps/<pack>.json`
(`{concept: library base name}`), with the concepts and their Telegram X drawables in
`tools/xgram/icon_packs/concepts.json`. Brand logos (GitHub, YouTube, browsers, Telegram), icons that contain text and
parts of animated icons always keep Telegram X's drawing.
