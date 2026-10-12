#!/usr/bin/env python3
"""XGram Build Bot: posts to the XGram group (forum topics) and the XGram CI channel.

Every command is a DRY RUN unless --send is given: it prints the requests it would make.
Config: environment variables first, then ~/.xgram/telegram.env (KEY=VALUE). See README.md.
Python 3.9+, stdlib only. Telethon is imported only for uploads over the Bot API limit.
"""
import argparse, glob, hashlib, html, importlib.util, json, mimetypes, os, re, sys, tempfile, time, uuid
import http.client, urllib.error, urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser

HERE = os.path.dirname(os.path.abspath(__file__))
XGRAM_HOME = os.path.join(os.path.expanduser("~"), ".xgram")
CLOUD_API = "https://api.telegram.org"
TEXT_LIMIT, CAPTION_LIMIT, MIB, MB = 4096, 1024, 1024 * 1024, 1000 * 1000
LOCAL_UPLOAD_LIMIT = 2000 * MIB   # self-hosted telegram-bot-api started with --local
CLOUD_UPLOAD_MB = 49              # MB = 1,000,000 bytes; scripts/ci/publish-telegram.sh uses the same default
GROUP_GAP = 3.5                   # seconds between posts into one group (Bot API FAQ: ~20 per minute)
COLORS = {"blue": 7322096, "yellow": 16766590, "violet": 13338331, "green": 9367192, "pink": 16749490, "red": 16478047}
GROUP_RIGHTS, GROUP_OPTIONAL = ("can_manage_topics", "can_pin_messages"), ("can_delete_messages",)
CHANNEL_RIGHTS, CHANNEL_OPTIONAL = ("can_post_messages", "can_edit_messages"), ("can_delete_messages",)
KEYS = ("XGRAM_BOT_TOKEN", "XGRAM_GROUP_ID", "XGRAM_CHANNEL_ID", "XGRAM_TOPIC_UPDATES", "XGRAM_TOPIC_SUPPORT",
        "TELEGRAM_API_ID", "TELEGRAM_API_HASH", "XGRAM_BOT_API_URL", "XGRAM_SOURCE_URL", "XGRAM_CLOUD_UPLOAD_MB")
SECRET_RE = re.compile(r"TOKEN|HASH|PASS|SECRET|KEY|AUTH|SESSION", re.I)   # never print such values
TARGET_HELP = "general, updates, support, channel, or any topic set as XGRAM_TOPIC_<NAME>"
DEFAULT_HELP = ("<b>XGram Build Bot</b>\nI post XGram builds to the XGram CI channel.\n\n"
                "/latest – the newest build, with file size and SHA-256\n/help – this message\n\n"
                "To install: download the APK from the build post, check that its SHA-256 matches the one in "
                "the post, then open it.")
mimetypes.add_type("application/vnd.android.package-archive", ".apk")

# ------------------------------------------------------------------ errors, masking, small helpers

_SECRETS = []
_TOKEN_RE = re.compile(r"\d{6,}:[A-Za-z0-9_-]{30,}")

def mask(text):
    """Hide the bot token (and the API hash) in anything we print."""
    text = str(text)
    for secret in _SECRETS:
        text = text.replace(secret, "<secret>")
    return _TOKEN_RE.sub("<bot-token>", text)

def log(msg):
    print(mask(msg), file=sys.stderr, flush=True)

class XgError(Exception):
    """A config, input or Telegram problem: printed without a traceback."""

class TgError(XgError):
    HINTS = (("message thread not found", "check XGRAM_TOPIC_*; General must not have a topic id"),
             ("TOPIC_CLOSED", "the topic is closed: give the bot 'Manage topics', or reopen the topic"),
             ("not enough rights", "the bot lacks an admin right: run 'check --send'"),
             ("CHAT_ADMIN_REQUIRED", "the bot lacks an admin right: run 'check --send'"),
             ("chat not found", "wrong chat id, or the bot is not a member of that chat"),
             ("Unauthorized", "XGRAM_BOT_TOKEN is wrong or was revoked"),
             ("webhook is active", "this bot has a webhook; discover/serve need it removed (deleteWebhook)"),
             ("Conflict", "another getUpdates loop (serve) is running for this bot"),
             ("can't parse entities", "the HTML is not valid Telegram HTML"),
             ("Too Large", "file over the Bot API upload limit"))

    def __init__(self, method, code, description, parameters=None):
        self.code, self.parameters = code, parameters or {}
        msg = "%s failed: %s %s" % (method, code or "network error:", description or "")
        if self.parameters.get("migrate_to_chat_id"):
            msg += "\n  hint: the group was upgraded; its new id is %s" % self.parameters["migrate_to_chat_id"]
        hint = next((h for needle, h in self.HINTS if needle.lower() in msg.lower()), None)
        msg += "\n  hint: " + hint if hint else ""
        super().__init__(msg)

def esc(text):
    """Plain text -> safe inside parse_mode=HTML."""
    return html.escape(str(text), quote=True)

def human_size(n):
    """1024-based, the way Telegram apps show file sizes."""
    for unit in ("bytes", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return "%d bytes" % n if unit == "bytes" else "%.1f %s" % (n, unit)
        n /= 1024.0

def read_file(path):
    with open(path, encoding="utf-8-sig") as f:   # -sig drops a BOM; text mode turns CRLF into LF
        return f.read()

def quoted(text):
    return "\n".join("    | " + line for line in text.split("\n"))

# ------------------------------------------------------------------ Telegram HTML

ALLOWED_TAGS = {"b", "strong", "i", "em", "u", "ins", "s", "strike", "del", "span", "tg-spoiler",
                "a", "tg-emoji", "tg-time", "code", "pre", "blockquote"}
TELETHON_TAGS = {"b", "strong", "i", "em", "u", "s", "del", "a", "code", "pre", "blockquote", "tg-emoji"}
ENTITIES = {"lt": "<", "gt": ">", "amp": "&", "quot": '"'}
_BARE_AMP = re.compile(r"&(?!(?:[a-zA-Z][a-zA-Z0-9]*|#\d+|#[xX][0-9a-fA-F]+);)")

class TgHtml(HTMLParser):
    """Parses Telegram HTML (core.telegram.org/bots/api#html-style): collects the visible text and
    lists what Telegram would reject. Newlines are literal; there is no <br> or <p>."""

    def __init__(self, source):
        super().__init__(convert_charrefs=False)
        self.parts, self.stack, self.tags, self.problems = [], [], set(), []
        if _BARE_AMP.search(source):   # HTMLParser silently drops some bare '&', so check the source
            self.problems.append("bare '&' (write &amp;)")
        self.feed(source)
        self.close()
        self.problems += ["unclosed <%s>" % tag for tag in reversed(self.stack)]
        self.problems = list(dict.fromkeys(self.problems))
        self.text = "".join(self.parts)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        self.tags.add(tag)
        if tag not in ALLOWED_TAGS:
            self.problems.append("unsupported tag <%s>" % tag)
        elif tag == "span" and a.get("class") != "tg-spoiler":
            self.problems.append('<span> needs class="tg-spoiler"')
        elif (tag == "a" and not a.get("href")) or (tag == "tg-emoji" and not a.get("emoji-id")):
            self.problems.append("<%s> without its required attribute" % tag)
        if ("pre" in self.stack or "code" in self.stack) and not (tag == "code" and self.stack[-1:] == ["pre"]):
            self.problems.append("<%s> inside <pre>/<code>" % tag)
        if tag == "blockquote" and "blockquote" in self.stack:
            self.problems.append("nested <blockquote>")
        self.stack.append(tag)

    def handle_endtag(self, tag):
        if tag not in self.stack:
            return self.problems.append("stray </%s>" % tag)
        while self.stack[-1] != tag:
            self.problems.append("<%s> closed by </%s> (tags must nest)" % (self.stack.pop(), tag))
        self.stack.pop()

    def handle_data(self, data):
        if any(c in data for c in "<>&"):
            self.problems.append("raw '<', '>' or '&' near %r (write &lt; &gt; &amp;)" % data.strip()[:20])
        self.parts.append(data)

    def handle_entityref(self, name):
        if name not in ENTITIES:
            self.problems.append("entity &%s; is not supported (only &lt; &gt; &amp; &quot; or numeric)" % name)
        self.parts.append(ENTITIES.get(name, "?"))

    def handle_charref(self, name):
        try:
            self.parts.append(chr(int(name[1:], 16) if name[:1] in "xX" else int(name)))
        except (ValueError, OverflowError):
            self.problems.append("bad character reference &#%s;" % name)

    def handle_comment(self, data):
        self.problems.append("comments and <!...> / <?...> are not supported")

    handle_decl = handle_pi = unknown_decl = handle_comment

def utf16_len(s):
    return len(s.encode("utf-16-le")) // 2

def visible_len(source):
    """Length after entity parsing, in UTF-16 code units (an emoji counts 2). Telegram does not
    document its exact unit; this count is never lower than a count in code points."""
    return utf16_len(TgHtml(source).text.strip())

def check_html(source, limit, what, telethon=False):
    """Raise XgError if Telegram would reject the text; return its visible length."""
    parsed = TgHtml(source)
    problems, n = list(parsed.problems), utf16_len(parsed.text.strip())
    if n > limit:
        problems.append("%d visible characters, the limit is %d (split it: name.1.html, name.2.html)" % (n, limit))
    if n == 0 and limit == TEXT_LIMIT:
        problems.append("no visible text")
    if problems:
        raise XgError("%s cannot be sent:\n  - %s" % (what, "\n  - ".join(problems)))
    if telethon and parsed.tags - TELETHON_TAGS:
        log("warning: %s: Telethon's HTML parser drops <%s>" % (what, ">, <".join(sorted(parsed.tags - TELETHON_TAGS))))
    return n

# ------------------------------------------------------------------ config, ids, targets

def load_config(path):
    cfg = {}
    if path and os.path.isfile(path):
        for n, line in enumerate(read_file(path).splitlines(), 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key, sep, value = (line[7:] if line.startswith("export ") else line).partition("=")
            if not sep:
                raise XgError("%s line %d: expected KEY=VALUE" % (path, n))   # never echo the line (secrets)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            elif " #" in value:
                value = value.split(" #", 1)[0].rstrip()
            cfg[key.strip()] = value
    for key, value in os.environ.items():   # XGRAM_TOPIC_<NAME>: a target; XGRAM_<NAME>: fills {{NAME}}
        if value.strip() and (key in KEYS or key.startswith("XGRAM_")):
            cfg[key] = value.strip()
    cfg = {k: v for k, v in cfg.items() if v and not v.startswith("<")}   # "<placeholder>" = unset
    _SECRETS.extend(v for k, v in cfg.items() if SECRET_RE.search(k) and len(v) >= 6)
    return cfg

_LINK_RE = re.compile(r"^(?:https?://)?(?:www\.)?(?:t|telegram)\.me/(?:c/(\d+)|([A-Za-z][A-Za-z0-9_]{3,}))"
                      r"(?:/(\d+))?(?:/(\d+))?/?(?:\?(.*))?$")

def parse_tme_link(link):
    """t.me link -> (chat_id or '@name', topic_id or None, message_id or None).
    t.me/c/<N>/... is the private form; its Bot API chat id is -100<N>. In a forum, one number is a
    topic (a topic's id is the id of the message that created it), two numbers are topic/message,
    and t.me/c/<N>/<msg>?thread=<topic> is the older form."""
    m = _LINK_RE.match(link.strip())
    if not m:
        raise XgError("not a t.me chat/topic link: %s" % link)
    chat = -(10 ** 12 + int(m.group(1))) if m.group(1) else "@" + m.group(2)
    first, second = (int(x) if x else None for x in m.group(3, 4))
    thread = re.search(r"(?:^|&)thread=(\d+)", m.group(5) or "")
    if second is not None:
        return chat, first, second
    return (chat, int(thread.group(1)), first) if thread else (chat, first, None)

def parse_chat(value):
    if re.match(r"^-?\d+$", value):
        return int(value)
    return value if re.match(r"^@[A-Za-z][A-Za-z0-9_]{3,}$", value) else parse_tme_link(value)[0]

def parse_topic(value):
    topic = int(value) if re.match(r"^\d+$", value) else parse_tme_link(value)[1]
    if topic is None:
        raise XgError("no topic id in %s" % value)
    return topic

class Target:
    def __init__(self, name, chat_id, thread_id=None):
        self.name, self.chat_id = name, chat_id
        # General is topic 1, but the Bot API rejects message_thread_id=1: General = no thread id.
        self.thread_id = None if thread_id in (0, 1) else thread_id

    def kw(self):
        return {"chat_id": self.chat_id, **({"message_thread_id": self.thread_id} if self.thread_id else {})}

    def __str__(self):
        return "%s (chat %s%s)" % (self.name, self.chat_id, ", topic %s" % self.thread_id if self.thread_id else "")

def setting(cfg, key, send, parse):
    if key in cfg:
        try:
            return parse(cfg[key])
        except XgError as e:
            raise XgError("%s: %s" % (key, e))
    if send:
        raise XgError("%s is not set (environment or ~/.xgram/telegram.env); see README.md" % key)
    log("warning: %s is not set; the dry run uses a placeholder" % key)
    return "<%s>" % key

def resolve_target(cfg, name, send):
    if name == "channel":
        return Target(name, setting(cfg, "XGRAM_CHANNEL_ID", send, parse_chat))
    chat = setting(cfg, "XGRAM_GROUP_ID", send, parse_chat)
    if name == "general":
        return Target(name, chat)
    if re.match(r"^\d+$", name) or "t.me/" in name:   # a topic id or a topic link also works
        return Target("topic", chat, parse_topic(name))
    if not re.match(r"^[A-Za-z0-9_]+$", name):
        raise XgError("unknown target %r: use %s" % (name, TARGET_HELP))
    return Target(name.lower(), chat, setting(cfg, "XGRAM_TOPIC_" + name.upper(), send, parse_topic))

def chat_base(chat):
    if isinstance(chat, int) and chat < -10 ** 12:
        return "t.me/c/%d" % (-chat - 10 ** 12)   # private link: opens only for members
    return "t.me/" + chat[1:] if isinstance(chat, str) and chat.startswith("@") else None

def message_link(target, message_id):
    base = chat_base(target.chat_id)
    return base and "https://%s/%s%s" % (base, "%s/" % target.thread_id if target.thread_id else "", message_id)

_PLACEHOLDER = re.compile(r"\{\{([A-Z0-9_]+)\}\}")

def fill(text, cfg, send):
    """Replace {{NAME}} in a post with XGRAM_<NAME> from the config, or with a computed link:
    {{<TOPIC>_TOPIC_LINK}} -> t.me/c/<N>/<topic id>, {{CI_CHANNEL_LINK}} -> t.me/<name> for a public
    channel. Links carry no scheme because the posts write href="https://{{...}}"."""
    missing = []

    def value(m):
        name = m.group(1)
        if SECRET_RE.search(name):
            raise XgError("{{%s}} looks like a secret; it is never put into a post" % name)
        if "XGRAM_" + name in cfg:
            return esc(re.sub(r"^https?://", "", cfg["XGRAM_" + name]))
        topic = re.match(r"^(\w+)_TOPIC_LINK$", name)
        key = topic and "XGRAM_TOPIC_" + topic.group(1)
        group = chat_base(parse_chat(cfg["XGRAM_GROUP_ID"])) if "XGRAM_GROUP_ID" in cfg else None
        if topic and group and (key in cfg or topic.group(1) == "GENERAL"):
            return "%s/%s" % (group, parse_topic(cfg[key]) if key in cfg else 1)
        channel = chat_base(parse_chat(cfg["XGRAM_CHANNEL_ID"])) if "XGRAM_CHANNEL_ID" in cfg else None
        if name == "CI_CHANNEL_LINK" and channel and "/c/" not in channel:
            return channel   # a private channel needs XGRAM_CI_CHANNEL_LINK (an invite link)
        missing.append("{{%s}}" % name)
        return m.group(0)
    text = _PLACEHOLDER.sub(value, text)
    if missing:
        msg = "unfilled %s: set XGRAM_<NAME> in the config (see README.md)" % ", ".join(dict.fromkeys(missing))
        if send:
            raise XgError(msg)
        log("warning: " + msg)
    return text

# ------------------------------------------------------------------ Bot API client

def form_value(v):
    if isinstance(v, (dict, list)):
        return json.dumps(v, ensure_ascii=False)   # objects travel as JSON strings in multipart
    return ("true" if v else "false") if isinstance(v, bool) else str(v)

class Multipart:
    """multipart/form-data body. Files are streamed from disk in 1 MiB chunks, the length is known
    up front (so no chunked encoding), and the body can be iterated again for a retry."""

    def __init__(self, fields, files):
        self.boundary = "xgram-" + uuid.uuid4().hex
        self.parts = [(self._head(k), form_value(v).encode("utf-8")) for k, v in fields.items()]
        for field, path in files.items():
            name = re.sub(r'["\\\r\n]', "_", os.path.basename(path))
            ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
            self.parts.append((self._head(field, name, ctype), path))
        self.tail = ("--%s--\r\n" % self.boundary).encode()
        self.content_type = "multipart/form-data; boundary=" + self.boundary
        self.length = len(self.tail) + sum(len(head) + 2 + (len(v) if isinstance(v, bytes) else os.path.getsize(v))
                                           for head, v in self.parts)

    def _head(self, name, filename=None, ctype=None):
        head = '--%s\r\nContent-Disposition: form-data; name="%s"' % (self.boundary, name)
        if filename:
            head += '; filename="%s"\r\nContent-Type: %s' % (filename, ctype)
        return (head + "\r\n\r\n").encode("utf-8")

    def __iter__(self):
        for head, value in self.parts:
            yield head
            if isinstance(value, bytes):
                yield value
            else:
                with open(value, "rb") as f:
                    yield from iter(lambda: f.read(MIB), b"")
            yield b"\r\n"
        yield self.tail

def show_request(method, params, files=None):
    rest = {k: v for k, v in params.items() if k not in ("text", "caption")}
    print("[dry run] %s %s" % (method, json.dumps(rest, ensure_ascii=False)))
    for key, limit in (("text", TEXT_LIMIT), ("caption", CAPTION_LIMIT)):
        if key in params:
            print("  %s, %d/%d visible chars:\n%s" % (key, visible_len(params[key]), limit, quoted(params[key])))
    for field, path in (files or {}).items():
        print("  %s: %s (%s, multipart upload)" % (field, path, human_size(os.path.getsize(path))))

class BotApi:
    def __init__(self, cfg, send):
        self.token, self.send = cfg.get("XGRAM_BOT_TOKEN", ""), send
        self.local = bool(cfg.get("XGRAM_BOT_API_URL"))   # a self-hosted server is used for every call
        self.base = (cfg.get("XGRAM_BOT_API_URL") or CLOUD_API).rstrip("/")
        # The cloud limit is "50 MB", decimal or binary (not documented). 49,000,000 bytes stays below both, with
        # room for the multipart overhead. A file over the limit gets 413, which is not retried.
        mb = float(cfg.get("XGRAM_CLOUD_UPLOAD_MB", CLOUD_UPLOAD_MB))
        self.upload_limit = LOCAL_UPLOAD_LIMIT if self.local else int(mb * MB)

    def check_token(self):
        if not re.match(r"^\d+:[A-Za-z0-9_-]{30,}$", self.token):
            raise XgError("XGRAM_BOT_TOKEN is missing or does not look like a bot token")

    def call(self, method, params=None, files=None, timeout=120, tries=5):
        params = {k: v for k, v in (params or {}).items() if v is not None}
        if not self.send:
            show_request(method, params, files)
            return {"message_id": 0, "message_thread_id": 0, "dry_run": True}
        self.check_token()
        url = "%s/bot%s/%s" % (self.base, self.token, method)   # holds the token: never print it
        if files:
            body = Multipart(params, files)
            headers = {"Content-Type": body.content_type, "Content-Length": str(body.length)}
        else:
            body, headers = json.dumps(params, ensure_ascii=False).encode("utf-8"), {"Content-Type": "application/json"}
        for attempt in range(1, tries + 1):
            res = self._post(url, body, headers, timeout)
            if res.get("ok"):
                return res.get("result")
            code, desc = res.get("error_code"), res.get("description", "")
            retry_after = (res.get("parameters") or {}).get("retry_after") or 0
            # 429 = flood wait; 5xx and network errors are usually transient. A retry after a lost
            # response can post twice; that is rare and better than losing a release post.
            transient = code in (None, 429) or (isinstance(code, int) and code >= 500)
            if not transient or attempt == tries or retry_after > 600:
                raise TgError(method, code, desc, res.get("parameters"))
            delay = retry_after + 1 if retry_after else min(60, 2 ** attempt)
            log("%s: %s %s; retry %d/%d in %ds" % (method, code or "network error:", desc, attempt, tries - 1, delay))
            time.sleep(delay)

    @staticmethod
    def _post(url, body, headers, timeout):
        req = urllib.request.Request(url, data=body, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            try:
                return json.loads(e.read().decode("utf-8", "replace"))
            except ValueError:   # not JSON, e.g. an HTML 413 page from a proxy
                return {"ok": False, "error_code": e.code, "description": str(e.reason)}
        except (urllib.error.URLError, http.client.HTTPException, OSError, ValueError) as e:
            return {"ok": False, "error_code": None, "description": mask(e)}

def send_text(api, target, text, silent=False, preview=False, reply_to=None):
    params = {**target.kw(), "text": text, "parse_mode": "HTML", "disable_notification": silent or None,
              "link_preview_options": None if preview else {"is_disabled": True}}
    if reply_to is not None:
        params["reply_parameters"] = {"message_id": reply_to, "allow_sending_without_reply": True}
    return api.call("sendMessage", params)

def telethon_status(cfg):
    """(Telethon installed, TELEGRAM_API_ID and TELEGRAM_API_HASH set)."""
    return importlib.util.find_spec("telethon") is not None, "TELEGRAM_API_ID" in cfg and "TELEGRAM_API_HASH" in cfg

def telethon_problem(cfg):
    """Why big files can't be uploaded through Telethon, or None."""
    installed, have_keys = telethon_status(cfg)
    if not installed:
        return ("Install Telethon (pip install telethon; written against 1.45), or set XGRAM_BOT_API_URL to a "
                "self-hosted telegram-bot-api --local.")
    if not have_keys:
        return "Set TELEGRAM_API_ID and TELEGRAM_API_HASH (from my.telegram.org)."
    return None

def upload_telethon(cfg, api, target, path, caption, silent):
    """Upload a file over the Bot API limit through MTProto (Telethon logged in as the bot, up to
    ~2000 MiB). Returns the message id, which is the same id the Bot API uses."""
    installed, have_keys = telethon_status(cfg)
    if not api.send:
        print("[dry run] Telethon upload (over the Bot API limit of %s bytes) -> %s\n  file: %s (%s); telethon "
              "installed: %s; TELEGRAM_API_ID/HASH set: %s\n%s" % (format(api.upload_limit, ","), target, path,
                                                                   human_size(os.path.getsize(path)),
                                                                   "yes" if installed else "NO",
                                                                   "yes" if have_keys else "NO", quoted(caption)))
        return 0
    problem = telethon_problem(cfg)
    if problem:
        raise XgError("%s is over the Bot API upload limit. %s" % (path, problem))
    api.check_token()
    import asyncio
    from telethon import TelegramClient
    from telethon.sessions import StringSession

    async def run():
        # In-memory session: a fresh bot login each run, logged out at the end, nothing saved.
        client = TelegramClient(StringSession(), int(cfg["TELEGRAM_API_ID"]), cfg["TELEGRAM_API_HASH"])
        await client.start(bot_token=api.token)
        try:
            entity = await client.get_input_entity(target.chat_id)   # -100... id or @name; bot must be a member
            next_pct = [0]

            def progress(done, total):
                if total and done * 100 // total >= next_pct[0]:
                    log("upload %s: %d%%" % (os.path.basename(path), done * 100 // total))
                    next_pct[0] = done * 100 // total + 10
            # MTProto: posting into a topic is a reply to the topic id; General and channels: no reply.
            msg = await client.send_file(entity, path, caption=caption, parse_mode="html", force_document=True,
                                         reply_to=target.thread_id, silent=silent, progress_callback=progress)
            return msg.id
        finally:
            await client.log_out()
    return asyncio.run(run())

# ------------------------------------------------------------------ files, posts, state

def file_info(path):
    if not os.path.isfile(path):
        raise XgError("no such file: %s" % path)
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(MIB), b""):
            digest.update(chunk)
    name = os.path.basename(path)
    abi = re.search(r"arm64-v8a|armeabi-v7a|x86_64|x86|universal", name)
    return {"path": path, "name": name, "size": os.path.getsize(path), "sha256": digest.hexdigest(),
            "abi": abi.group(0) if abi else None}

def post_files(name, posts_dir):
    """posts/<name>(.html), or its parts name.1.html, name.2.html, ... in order."""
    for cand in (name, os.path.join(posts_dir, name), os.path.join(posts_dir, name + ".html")):
        if os.path.isfile(cand):
            return [cand]
    base = name[:-5] if name.endswith(".html") else name
    for stem in (base, os.path.join(posts_dir, base)):
        parts = [p for p in glob.glob(glob.escape(stem) + ".*.html") if re.search(r"\.\d+\.html$", p)]
        if parts:
            return sorted(parts, key=lambda p: int(re.search(r"\.(\d+)\.html$", p).group(1)))
    raise XgError("post not found: %s (also looked in %s)" % (name, posts_dir))

def read_notes(path, cfg, send):
    if not path:
        return ""
    raw = (sys.stdin.read() if path == "-" else read_file(path)).strip()
    return fill(raw, cfg, send) if path.lower().endswith((".html", ".htm")) else esc(raw)   # else plain text

def load_state(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}

def save_state(path, latest):
    state = load_state(path)
    state["latest"], state["history"] = latest, ([latest] + state.get("history", []))[:20]
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path + ".tmp", "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)
    os.replace(path + ".tmp", path)

def bot_file(bot_dir, *stems):
    for path in (os.path.join(bot_dir, stem + ext) for stem in stems for ext in (".txt", ".md", "")):
        if os.path.isfile(path):
            return path
    return None

def read_commands(path):
    """BotFather /setcommands format: one 'command - description' per line; # comments."""
    cmds = []
    for line in read_file(path).splitlines():
        if line.strip() and not line.strip().startswith("#"):
            name, sep, desc = line.strip().partition(" - ")
            cmds.append({"command": name.strip().lstrip("/"), "description": desc.strip()})
            if not sep or not re.match(r"^[a-z0-9_]{1,32}$", cmds[-1]["command"]) or len(desc.strip()) > 256:
                raise XgError("%s: bad line %r (want 'command - description', [a-z0-9_]{1,32}, <= 256 chars)"
                              % (path, line))
    if not 1 <= len(cmds) <= 100:
        raise XgError("%s: 1 to 100 commands" % path)
    return cmds

# ------------------------------------------------------------------ commands

def cmd_check(args, cfg, api):
    print("config: %s (%s)" % (args.env_file, "found" if os.path.isfile(args.env_file) else "not found"))
    for key in KEYS + tuple(sorted(k for k in cfg if k.startswith("XGRAM_") and k not in KEYS)):
        print("  %-22s %s" % (key, ("set (hidden)" if SECRET_RE.search(key) else cfg[key]) if key in cfg else "-"))
    if not api.send:
        print("[dry run] would call getMe, getWebhookInfo, then getChat + getChatMember for the group and the "
              "channel (all read-only). Add --send to run them.")
        return 0
    me = api.call("getMe")
    print("bot: %s (@%s, id %s); can join groups: %s; privacy off: %s" % (me.get("first_name"), me.get("username"),
          me.get("id"), me.get("can_join_groups"), me.get("can_read_all_group_messages")))
    if api.call("getWebhookInfo").get("url"):
        print("  warning: a webhook is set, so discover/serve (getUpdates) will fail")
    ok = True
    for key, kind, need, optional in (("XGRAM_GROUP_ID", "group", GROUP_RIGHTS, GROUP_OPTIONAL),
                                      ("XGRAM_CHANNEL_ID", "channel", CHANNEL_RIGHTS, CHANNEL_OPTIONAL)):
        try:
            chat_id = setting(cfg, key, True, parse_chat)
            chat = api.call("getChat", {"chat_id": chat_id})
            member = api.call("getChatMember", {"chat_id": chat_id, "user_id": me["id"]})
        except XgError as e:
            print("%s: %s" % (kind, e))
            ok = False
            continue
        print('%s: "%s" (%s, id %s%s); the bot is %s' % (kind, chat.get("title"), chat.get("type"), chat.get("id"),
              ", forum" if chat.get("is_forum") else "", member.get("status")))
        if kind == "group" and not chat.get("is_forum"):
            print("  warning: topics are not enabled in this group")
        for right in need + optional:
            have = member.get(right) is True
            ok = ok and (have or right in optional)
            print("  [%s] %s%s" % ("x" if have else " ", right, "" if right in need else " (optional)"))
    return 0 if ok else 1

def cmd_discover(args, cfg, api):
    if not api.send:
        print("[dry run] would call getUpdates (read-only: it does not confirm or delete updates) and list the\n"
              "chats and topic ids it sees. First add the bot as admin, then post one message in each topic and\n"
              "in the channel (updates are kept 24 h; by default a bot doesn't see other bots' messages, e.g.\n"
              "Rose's). Add --send.")
        return 0
    kinds = ["message", "edited_message", "channel_post", "my_chat_member"]
    chats, topics = {}, {}
    for update in api.call("getUpdates", {"limit": 100, "timeout": 0, "allowed_updates": kinds}):
        for kind, m in ((k, update[k]) for k in kinds if k in update):
            chat = chats.setdefault(m["chat"]["id"], m["chat"])
            if kind == "my_chat_member":
                print("the bot became %s in %s" % (m.get("new_chat_member", {}).get("status"), chat["id"]))
            elif chat.get("is_forum"):
                tid = m.get("message_thread_id") if m.get("is_topic_message") else 1   # General has no thread id
                info = (m.get("forum_topic_created") or m.get("forum_topic_edited")
                        or (m.get("reply_to_message") or {}).get("forum_topic_created") or {})
                seen = topics.setdefault(chat["id"], {})
                seen[tid] = "General" if tid == 1 else info.get("name") or seen.get(tid)
    if not chats:
        print("no updates. Post a message in each topic and in the channel, then run this again.")
        return 1
    print("chats:")
    lines = []
    for chat in chats.values():
        print('  %s  %s%s  "%s"%s' % (chat["id"], chat.get("type"), " forum" if chat.get("is_forum") else "",
              chat.get("title") or chat.get("first_name"), "  @" + chat["username"] if chat.get("username") else ""))
        if chat.get("type") in ("channel", "supergroup"):
            lines.append("XGRAM_%s_ID=%s" % ("CHANNEL" if chat["type"] == "channel" else "GROUP", chat["id"]))
    for chat_id, seen in topics.items():
        print("topics in %s:" % chat_id)
        for tid, name in sorted(seen.items()):
            print("  %-8s %s" % ("(none)" if tid == 1 else tid, name or "? (name unknown: copy the topic link)"))
            lines += ["XGRAM_TOPIC_%s=%s" % (w.upper(), tid) for w in ("updates", "support")
                      if tid != 1 and w in (name or "").lower()]
    print("suggested lines for ~/.xgram/telegram.env (check them):\n  " + "\n  ".join(lines))
    return 0

def cmd_setup_bot(args, cfg, api):
    bot_dir, did = os.path.join(args.posts_dir, "bot"), 0
    path = bot_file(bot_dir, "commands")
    if path:
        api.call("setMyCommands", {"commands": read_commands(path)})
        did += 1
    for stems, method, field, limit in ((("description",), "setMyDescription", "description", 512),
                                        (("short_description", "about"), "setMyShortDescription",
                                         "short_description", 120)):
        path = bot_file(bot_dir, *stems)
        if path:
            text = read_file(path).strip()   # plain text, not HTML
            if len(text) > limit:
                raise XgError("%s: %d chars, %s allows %d" % (path, len(text), method, limit))
            api.call(method, {field: text})
            did += 1
    if args.rights:   # pre-ticks these rights when someone adds the bot as an admin
        for for_channels, rights in ((False, GROUP_RIGHTS + GROUP_OPTIONAL), (True, CHANNEL_RIGHTS + CHANNEL_OPTIONAL)):
            rights = dict({r: True for r in rights}, can_manage_chat=True)
            api.call("setMyDefaultAdministratorRights", {"for_channels": for_channels, "rights": rights})
    elif not did:
        raise XgError("nothing to set: %s has no commands.txt, description.txt or short_description.txt" % bot_dir)
    return 0

def cmd_post(args, cfg, api):
    target = resolve_target(cfg, args.to, api.send)
    texts = [(path, fill(read_file(path).strip(), cfg, api.send)) for path in post_files(args.file, args.posts_dir)]
    for path, text in texts:   # check every part before sending any
        check_html(text, TEXT_LIMIT, path)
    first = None
    for i, (path, text) in enumerate(texts):
        if i and api.send:
            time.sleep(GROUP_GAP if target.name != "channel" else 1)
        res = send_text(api, target, text, args.silent, args.preview)
        first = first or res
        if api.send:
            link = message_link(target, res["message_id"]) or res["message_id"]
            print("sent %s -> %s" % (os.path.basename(path), link))
    if args.pin:   # pinChatMessage has no thread id; the message's own topic should apply (not verified)
        api.call("pinChatMessage", {"chat_id": target.chat_id, "message_id": first["message_id"],
                                    "disable_notification": args.silent or None})
    return 0

def cmd_topic_create(args, cfg, api):
    if not 1 <= len(args.name) <= 128:
        raise XgError("topic name must be 1-128 characters")
    params = {"chat_id": resolve_target(cfg, "general", api.send).chat_id, "name": args.name}
    if args.color:
        number = re.match(r"^(0x[0-9a-fA-F]+|\d+)$", args.color)
        color = COLORS.get(args.color.lower()) or (int(args.color, 0) if number else 0)
        if color not in COLORS.values():
            raise XgError("--color must be one of: %s (the color is fixed after creation)" % ", ".join(COLORS))
        params["icon_color"] = color
    if args.emoji and not api.send:
        print("[dry run] getForumTopicIconStickers {} -> custom_emoji_id of the sticker for %s" % args.emoji)
        params["icon_custom_emoji_id"] = "<custom_emoji_id for %s>" % args.emoji
    elif args.emoji:   # only Telegram's topic icon set is allowed for bots, not our own emoji pack
        strip = lambda e: (e or "").replace(chr(0xFE0F), "")   # ignore the emoji variation selector
        icons = api.call("getForumTopicIconStickers")
        match = [s["custom_emoji_id"] for s in icons if strip(s.get("emoji")) == strip(args.emoji)]
        if not match:
            allowed = " ".join(s.get("emoji", "?") for s in icons)
            raise XgError("%s is not a topic icon; allowed: %s" % (args.emoji, allowed))
        params["icon_custom_emoji_id"] = match[0]
    params["icon_custom_emoji_id"] = args.emoji_id or params.get("icon_custom_emoji_id")
    topic = api.call("createForumTopic", params)
    print("new topic id (message_thread_id): %s" % topic.get("message_thread_id"))
    return 0

# Stable releases are tested builds posted in Updates; CI builds are every new build in the CI channel.
KINDS = {"stable": ("📦", "Stable"), "ci": ("⚙️", "CI build")}

def build_heading(version, kind):
    emoji, label = KINDS[kind]
    return "%s <b>XGram %s</b> · %s" % (emoji, esc(version), label)

def apk_caption(info, version, commit, source_url, kind="stable"):
    lines = [build_heading(version, kind) + (" · " + esc(info["abi"]) if info["abi"] else ""),
             "<code>%s</code>" % esc(info["name"]), "",
             "Size: " + human_size(info["size"]) + (" (%s bytes)" % format(info["size"], ",")
                                                    if info["size"] >= 1024 else ""),
             "Commit: <code>%s</code>" % esc(commit[:12]), "SHA-256: <code>%s</code>" % info["sha256"]]
    if source_url:
        lines.append('Source: <a href="%s">%s</a>' % (esc(source_url), esc(source_url)))
    return "\n".join(lines)

def cmd_publish(args, cfg, api):
    target = resolve_target(cfg, args.to, api.send)
    announce = resolve_target(cfg, args.announce, api.send) if args.announce else None
    kind = getattr(args, "kind", None) or ("ci" if target.name == "channel" else "stable")
    notes, heading = read_notes(args.notes, cfg, api.send), build_heading(args.version, kind)
    if notes:
        check_html(heading + ": what's new\n\n" + notes, TEXT_LIMIT, "--notes " + args.notes)
    infos, notes_in_caption, sent = [file_info(p) for p in args.apk], False, []
    big_files = [info["name"] for info in infos if info["size"] > api.upload_limit]
    if big_files and api.local:
        raise XgError("over 2000 MB, the self-hosted server's limit: %s" % ", ".join(big_files))
    problem = telethon_problem(cfg) if big_files and api.send else None
    if problem:   # decided for every file before any is posted, so a release is never half-posted
        raise XgError("nothing was sent: %s %s over the Bot API upload limit (%s bytes). %s"
                      % (", ".join(big_files), "is" if len(big_files) == 1 else "are",
                         format(api.upload_limit, ","), problem))
    plan = []   # build and check every caption before sending any
    for info in infos:
        caption = apk_caption(info, args.version, args.commit, cfg.get("XGRAM_SOURCE_URL"), kind)
        big = info["size"] > api.upload_limit
        # Notes go into the caption only for one file sent via the Bot API, if it fits in 1024.
        if len(infos) == 1 and notes and not big and visible_len(caption + "\n\n" + notes) <= CAPTION_LIMIT:
            caption, notes_in_caption = caption + "\n\n" + notes, True
        check_html(caption, CAPTION_LIMIT, info["name"] + " caption", telethon=big)
        plan.append((info, caption, big))
    for i, (info, caption, big) in enumerate(plan):
        if i and api.send:
            time.sleep(GROUP_GAP if target.name != "channel" else 1)
        if big:
            message_id = upload_telethon(cfg, api, target, info["path"], caption, args.silent)
        else:
            message_id = api.call("sendDocument", {**target.kw(), "caption": caption, "parse_mode": "HTML",
                                                   "disable_notification": args.silent or None,
                                                   "disable_content_type_detection": True},
                                  files={"document": info["path"]}, timeout=600)["message_id"]
        sent.append(dict(info, message_id=message_id, link=message_link(target, message_id)))
    main_id = sent[0]["message_id"]
    if notes and not notes_in_caption:   # the details stay in the captions; the notes follow as a reply
        main_id = send_text(api, target, heading + ": what's new\n\n" + notes, args.silent,
                            reply_to=sent[0]["message_id"])["message_id"]
    if args.pin:
        api.call("pinChatMessage", {"chat_id": target.chat_id, "message_id": main_id, "disable_notification": True})
    link, announce_link = message_link(target, main_id), None
    if announce:
        tail = '\n\n<a href="%s">Download</a>' % esc(link) if link else ""
        text = heading + " is out." + ("\n\n" + notes if notes else "") + tail
        if visible_len(text) > TEXT_LIMIT:
            log("warning: the notes are too long for the announcement; announcing without them")
            text = heading + " is out." + tail
        announce_link = message_link(announce, send_text(api, announce, text, args.silent)["message_id"])
    latest = {"version": args.version, "kind": kind, "commit": args.commit, "chat_id": target.chat_id,
              "date": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "message_id": main_id,
              "link": link, "announce_link": announce_link,
              "files": [{k: f[k] for k in ("name", "size", "sha256", "abi", "message_id", "link")} for f in sent]}
    if not api.send:
        print("[dry run] would update %s with:" % args.state_file)
        print(json.dumps(latest, indent=2, ensure_ascii=False))
        return 0
    save_state(args.state_file, latest)
    print("published XGram %s: %s" % (args.version, link or main_id))
    return 0

def parse_command(text, username):
    """'/latest@XGramBuildBot args' -> ('latest', True); None if it is meant for another bot."""
    m = re.match(r"^/([A-Za-z0-9_]{1,32})(?:@([A-Za-z0-9_]+))?(?:\s|$)", text or "")
    if not m or (m.group(2) and m.group(2).lower() != (username or "").lower()):
        return None
    return m.group(1).lower(), bool(m.group(2))

def reply_text(cmd, args, cfg, send):
    if cmd in ("start", "help"):   # posts/bot/start.html, else posts/bot/help.html, else the built-in text
        for name in dict.fromkeys((cmd, "help")):
            custom = os.path.join(args.posts_dir, "bot", name + ".html")
            if os.path.isfile(custom):
                return fill(read_file(custom).strip(), cfg, send)
        return DEFAULT_HELP
    state = load_state(args.state_file)
    latest = state.get("latest")
    if not latest:
        return "No XGram build has been published yet."
    lines = [build_heading(latest["version"], latest.get("kind") if latest.get("kind") in KINDS else "stable"),
             "Commit <code>%s</code>, %s" % (esc(latest["commit"][:12]), esc(latest["date"][:10]))]
    if latest.get("link"):
        lines.append('<a href="%s">Release post</a>' % esc(latest["link"]))
    for f in latest.get("files", []):
        name = '<a href="%s">%s</a>' % (esc(f["link"]), esc(f["name"])) if f.get("link") else esc(f["name"])
        lines += ["", "%s · %s" % (name, human_size(f["size"])), "SHA-256: <code>%s</code>" % f["sha256"]]
    return "\n".join(lines)

def cmd_serve(args, cfg, api):
    replies = ("start", "help", "latest")
    if not api.send:
        print("[dry run] would long-poll getUpdates and answer /%s. The replies right now:" % ", /".join(replies))
        for cmd in replies:
            text = reply_text(cmd, args, cfg, False)
            n = check_html(text, TEXT_LIMIT, "/" + cmd)
            print("  /%s (%d/%d visible chars):\n%s" % (cmd, n, TEXT_LIMIT, quoted(text)))
        return 0
    me, started, offset = api.call("getMe"), time.time(), None
    log("serving as @%s; Ctrl+C stops" % me.get("username"))
    while True:
        try:
            updates = api.call("getUpdates", {"offset": offset, "timeout": 50, "allowed_updates": ["message"]},
                               timeout=70)   # the HTTP timeout must outlast the long poll
        except TgError as e:
            if e.code in (401, 409):
                raise
            log("%s; waiting 10 s" % e)
            time.sleep(10)
            continue
        for update in updates:
            offset = update["update_id"] + 1   # the next getUpdates confirms this one
            m = update.get("message") or {}
            parsed = parse_command(m.get("text"), me.get("username"))
            if not parsed or parsed[0] not in replies or m.get("date", 0) < started - 120:
                continue
            if m["chat"].get("type") != "private" and parsed[0] != "latest" and not parsed[1]:
                continue   # a bare /start or /help in the group is for Rose
            target = Target("reply", m["chat"]["id"], m.get("message_thread_id") if m.get("is_topic_message") else None)
            try:
                send_text(api, target, reply_text(parsed[0], args, cfg, True), reply_to=m["message_id"])
            except XgError as e:
                log(e)
        if args.once:
            return 0

def cmd_posts(args, cfg, api):
    """Offline check of every post: HTML problems and visible length."""
    paths, bad = sorted(glob.glob(os.path.join(args.posts_dir, "**", "*.html"), recursive=True)), 0
    for path in paths:
        rel = os.path.relpath(path, args.posts_dir)
        text = read_file(path).strip()
        fills = ", ".join(sorted(set(_PLACEHOLDER.findall(text))))
        try:
            n = check_html(text, TEXT_LIMIT, rel)
            print("  ok   %4d/%d  %s%s" % (n, TEXT_LIMIT, rel, "  needs: " + fills if fills else ""))
        except XgError as e:
            bad += 1
            print("  BAD  %s" % e)
    print("%d post(s), %d with problems, in %s" % (len(paths), bad, args.posts_dir))
    return 1 if bad or not paths else 0

# ------------------------------------------------------------------ offline self-test

def selftest(args=None, cfg=None, api=None):
    checks = []

    def ok(cond, what):
        if not cond:
            raise AssertionError("selftest failed: " + what)
        checks.append(what)

    with tempfile.TemporaryDirectory() as d:   # multipart: framing, field values, file bytes, length
        path = os.path.join(d, "XGram-test.apk")
        data = bytes(range(256)) * 4100 + b"\r\n--not-a-boundary--\r\n"
        with open(path, "wb") as f:
            f.write(data)
        mp = Multipart({"chat_id": -1001234567890, "caption": "<b>Größe</b> 📦", "disable_notification": True,
                        "reply_parameters": {"message_id": 7}}, {"document": path})
        body = b"".join(mp)
        ok(len(body) == mp.length and b"".join(mp) == body, "Content-Length matches; body re-iterable for retries")
        segments = body.split(b"--" + mp.boundary.encode())
        ok(segments[0] == b"" and segments[-1] == b"--\r\n", "multipart framing")
        parts = {}
        for seg in segments[1:-1]:
            ok(seg.startswith(b"\r\n") and seg.endswith(b"\r\n"), "CRLF around each part")
            head, _, value = seg[2:-2].partition(b"\r\n\r\n")
            parts[re.search(rb'name="([^"]+)"', head).group(1).decode()] = (head, value)
        ok(parts["chat_id"][1] == b"-1001234567890" and parts["disable_notification"][1] == b"true", "int/bool fields")
        ok(parts["caption"][1].decode("utf-8") == "<b>Größe</b> 📦", "UTF-8 field")
        ok(json.loads(parts["reply_parameters"][1]) == {"message_id": 7}, "object field as a JSON string")
        ok(parts["document"][1] == data, "file bytes intact")
        ok(b'filename="XGram-test.apk"\r\nContent-Type: application/vnd.android.package-archive'
           in parts["document"][0], "file name and APK content type")
    ok(TgHtml("<b>Hi</b> &amp; <a href='https://x.y'>you</a> &#60;&#x3E;&quot;").text == 'Hi & you <>"', "visible text")
    ok(visible_len("📦 <i>x</i>") == 4 and visible_len("\n <b> a </b>\n") == 1, "UTF-16 length, outer space trimmed")
    ok(visible_len('<tg-emoji emoji-id="5368324170671202286">✖️</tg-emoji> XGram') == 8, "custom emoji")
    for bad in ("<br>", "a < b", "a -> b", "AT&T", "<b>x", "<b><i>x</b></i>", "<p>x</p>", "&nbsp;", "<span>x</span>",
                "<pre><b>x</b></pre>", "<!-- c -->x", "<a>x</a>"):
        ok(TgHtml(bad).problems, "rejects %r" % bad)
    ok(not TgHtml('<pre><code class="language-sh">ls</code></pre><blockquote expandable>q</blockquote>').problems,
       "accepts pre/code and blockquote")
    def raises(fn, *a):
        try:
            fn(*a)
        except XgError:
            return True
        return False
    ok(raises(check_html, "x" * 4097, TEXT_LIMIT, "long"), "length limit enforced")
    cfg = {"XGRAM_GROUP_ID": "-1000000000123", "XGRAM_TOPIC_UPDATES": "https://t.me/c/123/4",
           "XGRAM_CHANNEL_ID": "@xgram_ci"}
    ok(fill('<a href="https://{{UPDATES_TOPIC_LINK}}">u</a> {{CI_CHANNEL_LINK}} {{GENERAL_TOPIC_LINK}}', cfg, True)
       == '<a href="https://t.me/c/123/4">u</a> t.me/xgram_ci t.me/c/123/1', "placeholders filled")
    ok(raises(fill, "{{SUPPORT_TOPIC_LINK}}", cfg, True), "an unfilled placeholder blocks --send")
    ok(parse_tme_link("https://t.me/c/123/4/5") == (-1000000000123, 4, 5), "t.me/c/123/4/5")
    ok(parse_tme_link("t.me/name/4") == ("@name", 4, None), "t.me/name/4")
    ok(parse_tme_link("https://t.me/c/123/9?thread=4") == (-1000000000123, 4, 9), "?thread= form")
    ok(parse_chat("https://t.me/c/123/4") == -1000000000123 and parse_topic("t.me/c/123/4/5") == 4, "links in config")
    ok(Target("general", -1001, 1).kw() == {"chat_id": -1001}, "General has no message_thread_id")
    apk = {"name": "XGram-1.2.3.4-arm64-v8a.apk", "size": 2048, "sha256": "ab" * 32, "abi": "arm64-v8a"}
    ok(apk_caption(apk, "1.2.3.4", "0123456789abcdef", None, "stable").startswith(
        "📦 <b>XGram 1.2.3.4</b> · Stable · arm64-v8a\n"), "stable caption label")
    ok(apk_caption(apk, "1.2.3.4", "0123456789abcdef", None, "ci").startswith(
        "⚙️ <b>XGram 1.2.3.4</b> · CI build · arm64-v8a\n"), "CI caption label")
    ok(Target("updates", -1001, 12).kw() == {"chat_id": -1001, "message_thread_id": 12}, "topic thread id")
    ok(message_link(Target("u", -1000000000123, 4), 99) == "https://t.me/c/123/4/99", "message link")
    token = "1234567890:" + "fake" * 9   # token-shaped, obviously not real
    ok(token not in mask("POST https://api.telegram.org/bot%s/getMe" % token), "token masked by its pattern")
    _SECRETS.append("s3cr3t-hash")
    ok(mask("hash=s3cr3t-hash") == "hash=<secret>", "configured secret masked")
    _SECRETS.remove("s3cr3t-hash")
    ok(raises(fill, "{{BOT_TOKEN}}", {"XGRAM_BOT_TOKEN": token}, False), "a secret never goes into a post")
    ok(str(TgError("getMe", 401, "Unauthorized")).count("hint") == 1, "error hint")
    ok(fill("&#123;&#123;BOT_TOKEN}} &#123;&#123;SUPPORT_TOPIC_LINK}}", {}, True)
       == "&#123;&#123;BOT_TOKEN}} &#123;&#123;SUPPORT_TOPIC_LINK}}"
       and TgHtml("&#123;&#123;BOT_TOKEN}}").text == "{{BOT_TOKEN}}", "escaped braces (changelog.sh) are no placeholder")
    ok(human_size(52267500) == "49.8 MB" and human_size(512) == "512 bytes", "human size")
    ok(BotApi({}, False).upload_limit == 49 * MB < 50 * MB, "cloud upload cutoff below 50 MB in either unit")
    with tempfile.TemporaryDirectory() as d:   # a big file without a Telethon route stops publish before any call
        apk = os.path.join(d, "XGram-1-arm64-v8a.apk")
        with open(apk, "wb") as f:
            f.write(b"\0" * 64)
        api, calls = BotApi({}, True), []
        api.upload_limit, api.call = 10, lambda *a, **k: calls.append(a)
        ns = argparse.Namespace(apk=[apk, apk], version="1", commit="0" * 40, notes=None, to="channel", announce=None,
                                pin=False, silent=False, state_file=os.path.join(d, "state.json"))
        ok(raises(cmd_publish, ns, {"XGRAM_CHANNEL_ID": "@xgram_ci"}, api) and not calls
           and not os.path.exists(ns.state_file), "no Telethon route: nothing is sent")
    ok(parse_command("/latest@XGramBuildBot now", "xgrambuildbot") == ("latest", True), "addressed command")
    ok(parse_command("/help@MissRose_bot", "XGramBuildBot") is None, "command for another bot is ignored")
    print("selftest: %d checks passed" % len(checks))
    return 0

# ------------------------------------------------------------------ CLI

def build_parser():
    common = argparse.ArgumentParser(add_help=False)
    mode = common.add_mutually_exclusive_group()
    mode.add_argument("--send", action="store_true", help="really call Telegram (default: dry run)")
    mode.add_argument("--dry-run", action="store_true", help="only print what would be sent (the default)")
    common.add_argument("--env-file", default=os.path.join(XGRAM_HOME, "telegram.env"), help="KEY=VALUE config file")
    common.add_argument("--posts-dir", default=os.path.join(HERE, "posts"))
    common.add_argument("--state-file", default=os.path.join(XGRAM_HOME, "telegram_state.json"))
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", metavar="command")
    sub.required = True

    def add(name, fn, text, *arguments):
        p = sub.add_parser(name, parents=[common], help=text, description=text)
        for flags, kw in arguments:
            p.add_argument(*flags.split(), **kw)
        p.set_defaults(fn=fn)

    flag = {"action": "store_true"}
    add("check", cmd_check, "show the bot and its admin rights in the group and the channel")
    add("discover", cmd_discover, "list chat ids and topic ids from recent updates")
    add("setup-bot", cmd_setup_bot, "set the bot's commands and descriptions from posts/bot/",
        ("--rights", dict(flag, help="also pre-set the admin rights the bot asks for when added")))
    add("post", cmd_post, "send a Telegram-HTML post (or its .1/.2/... parts)",
        ("--file", {"required": True, "help": "a path, or a name inside --posts-dir"}),
        ("--to", {"required": True, "help": TARGET_HELP}),
        ("--pin", dict(flag, help="pin the (first) message")), ("--silent", dict(flag, help="no notification")),
        ("--preview", dict(flag, help="allow a link preview (off by default)")))
    add("topic-create", cmd_topic_create, "create a forum topic in the group and print its id",
        ("--name", {"required": True}),
        ("--color", {"help": "blue, yellow, violet, green, pink or red (fixed after creation)"}),
        ("--emoji", {"help": "a topic icon from Telegram's set, looked up by emoji, e.g. 📣"}),
        ("--emoji-id", {"help": "a custom_emoji_id from getForumTopicIconStickers"}))
    add("publish", cmd_publish, "post APKs with size and SHA-256, plus release notes",
        ("--apk", {"action": "append", "required": True, "help": "repeat for several APKs"}),
        ("--version", {"required": True}), ("--commit", {"required": True}),
        ("--notes", {"help": "release notes: .html = Telegram HTML, other files = plain text, - = stdin"}),
        ("--to", {"default": "channel", "help": TARGET_HELP + " (default: channel)"}),
        ("--announce", {"metavar": "TOPIC", "help": "also announce in this topic, e.g. updates"}),
        ("--kind", {"choices": sorted(KINDS), "help": "label the build Stable or CI build "
                                                     "(default: ci when posting to the channel, stable elsewhere)"}),
        ("--pin", dict(flag, help="pin the release post (the notes, or the first APK)")), ("--silent", flag))
    add("serve", cmd_serve, "answer /start /help /latest (long polling)",
        ("--once", dict(flag, help="handle one batch of updates, then exit")))
    add("posts", cmd_posts, "check every post in --posts-dir (offline)")
    add("selftest", selftest, "run the offline self-test")
    return parser

def main(argv=None):
    for stream in (sys.stdout, sys.stderr):   # emoji on Windows consoles and pipes
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    args = build_parser().parse_args(argv)
    try:
        cfg = {} if args.fn is selftest else load_config(args.env_file)
        return args.fn(args, cfg, BotApi(cfg, args.send))
    except XgError as e:
        log("error: %s" % e)
        return 1
    except KeyboardInterrupt:
        return 130
    except Exception as e:   # unexpected: still never print the token
        log("unexpected error: %s: %s" % (type(e).__name__, e))
        if os.environ.get("XGRAM_DEBUG"):
            import traceback
            log(traceback.format_exc())
        return 2

if __name__ == "__main__":
    sys.exit(main())
