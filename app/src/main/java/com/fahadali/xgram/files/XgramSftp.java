/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fahadali.xgram.files;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

/**
 * XGram: SFTP servers in the attach menu's File tab, browsed like the phone's folders and attached from. A server is
 * kept with its password or private key encrypted ({@link XgramSecrets}) and its host key, trusted on the first
 * connection after the user saw its fingerprint; a server whose key changes is refused. A picked file is sent as a
 * file TDLib asks XGram to make (an inputFileGenerated): XGram downloads it then, with progress.
 */
public final class XgramSftp {
  private XgramSftp () { }

  private static final String TAG = "XgramSftp";
  private static final String KEY_SERVERS = "xgram_sftp_servers";
  public static final String PATH_PREFIX = "xgramsftp://";
  public static final String CONVERSION = "xgram_sftp";
  private static final int TIMEOUT_MS = 15_000;

  /** A saved server. The secrets stay encrypted; {@link #hostKey} is the trusted host key (base64), or null. */
  public static final class Server {
    public final String id;
    public String name, host, user, folder;
    public int port = 22;
    @Nullable String password, privateKey, hostKey;

    Server (String id) {
      this.id = id;
    }

    public boolean hasPassword () {
      return password != null;
    }

    public boolean hasKey () {
      return privateKey != null;
    }

    public String label () {
      return name != null && !name.trim().isEmpty() ? name.trim() : user + "@" + host;
    }

    public String address () {
      return user + "@" + host + (port != 22 ? ":" + port : "");
    }
  }

  /** One entry of a server folder. */
  public static final class Entry {
    public final String name, path;
    public final boolean folder;
    public final long size, modified;

    Entry (String name, String path, boolean folder, long size, long modified) {
      this.name = name;
      this.path = path;
      this.folder = folder;
      this.size = size;
      this.modified = modified;
    }
  }

  /** The server offered a host key XGram has not trusted yet: show {@link #fingerprint} and ask. */
  public static final class UnknownHostKeyException extends Exception {
    public final String type, fingerprint;
    final byte[] key;
    final String host;
    final int port;

    UnknownHostKeyException (String type, byte[] key, String host, int port) {
      super("Unknown host key");
      this.type = type;
      this.key = key;
      this.host = host;
      this.port = port;
      this.fingerprint = fingerprint(key);
    }
  }

  /** The server's host key is not the trusted one (a different server, or someone in between): refused. */
  public static final class ChangedHostKeyException extends Exception {
    public final String fingerprint;

    ChangedHostKeyException (byte[] key) {
      super("Host key changed");
      this.fingerprint = fingerprint(key);
    }
  }

  public static String fingerprint (byte[] key) {
    try {
      final byte[] hash = MessageDigest.getInstance("SHA-256").digest(key);
      return "SHA256:" + Base64.encodeToString(hash, Base64.NO_WRAP | Base64.NO_PADDING);
    } catch (Throwable t) {
      return "?";
    }
  }

  // Saved servers

  public static synchronized List<Server> servers () {
    final String saved = Settings.instance().pmc().getString(KEY_SERVERS, null);
    if (saved == null || saved.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Server> list = new ArrayList<>();
    try {
      final JSONArray array = new JSONArray(saved);
      for (int i = 0; i < array.length(); i++) {
        final JSONObject o = array.getJSONObject(i);
        final Server server = new Server(o.getString("id"));
        server.name = o.optString("name", null);
        server.host = o.getString("host");
        server.port = o.optInt("port", 22);
        server.user = o.getString("user");
        server.folder = o.optString("folder", null);
        server.password = o.optString("password", null);
        server.privateKey = o.optString("key", null);
        server.hostKey = o.optString("hostKey", null);
        list.add(server);
      }
    } catch (Throwable t) {
      Log.w(TAG, "servers", t);
    }
    return list;
  }

  public static synchronized @Nullable Server server (String id) {
    for (Server server : servers()) {
      if (server.id.equals(id)) {
        return server;
      }
    }
    return null;
  }

  private static void store (List<Server> list) {
    final JSONArray array = new JSONArray();
    try {
      for (Server s : list) {
        final JSONObject o = new JSONObject();
        o.put("id", s.id);
        if (s.name != null) o.put("name", s.name);
        o.put("host", s.host);
        o.put("port", s.port);
        o.put("user", s.user);
        if (s.folder != null) o.put("folder", s.folder);
        if (s.password != null) o.put("password", s.password);
        if (s.privateKey != null) o.put("key", s.privateKey);
        if (s.hostKey != null) o.put("hostKey", s.hostKey);
        array.put(o);
      }
    } catch (Throwable t) {
      Log.w(TAG, "store", t);
    }
    Settings.instance().pmc().putString(KEY_SERVERS, array.toString());
  }

  /**
   * Adds a server, or changes {@code existingId}'s: null password or key keep the saved ones, an empty one removes
   * it. A changed host or port forgets the trusted host key. Returns the saved server.
   */
  public static synchronized Server save (@Nullable String existingId, String name, String host, int port, String user, @Nullable String folder, @Nullable String password, @Nullable String privateKey) {
    final List<Server> list = new ArrayList<>(servers());
    Server server = null;
    for (Server s : list) {
      if (s.id.equals(existingId)) {
        server = s;
      }
    }
    if (server == null) {
      server = new Server(UUID.randomUUID().toString().substring(0, 8));
      list.add(server);
    } else if (!host.equals(server.host) || port != server.port) {
      server.hostKey = null;
    }
    server.name = name == null || name.trim().isEmpty() ? null : name.trim();
    server.host = host.trim();
    server.port = port > 0 && port < 65536 ? port : 22;
    server.user = user.trim();
    server.folder = folder == null || folder.trim().isEmpty() ? null : folder.trim();
    if (password != null) {
      final String encrypted = password.isEmpty() ? null : XgramSecrets.encrypt(password);
      if (!password.isEmpty() && encrypted == null) {
        throw new IllegalStateException("The password couldn't be stored securely");
      }
      server.password = encrypted;
    }
    if (privateKey != null) {
      final String encrypted = privateKey.trim().isEmpty() ? null : XgramSecrets.encrypt(privateKey);
      if (!privateKey.trim().isEmpty() && encrypted == null) {
        throw new IllegalStateException("The key couldn't be stored securely");
      }
      server.privateKey = encrypted;
    }
    store(list);
    disconnect(server.id);
    return server;
  }

  /** Removes a server and closes its connection (network: not on the main thread). */
  @WorkerThread
  public static void remove (String id) {
    synchronized (XgramSftp.class) {
      final List<Server> list = new ArrayList<>(servers());
      for (int i = list.size() - 1; i >= 0; i--) {
        if (list.get(i).id.equals(id)) {
          list.remove(i);
        }
      }
      store(list);
    }
    disconnect(id);
  }

  /** Trusts the host key the user was shown for {@code id}. */
  public static synchronized void trust (String id, UnknownHostKeyException key) {
    final List<Server> list = new ArrayList<>(servers());
    for (Server s : list) {
      // Only the server it was shown for, as it still is, and only a first key: never over a trusted one
      if (s.id.equals(id) && s.hostKey == null && s.host.equals(key.host) && s.port == key.port) {
        s.hostKey = Base64.encodeToString(key.key, Base64.NO_WRAP);
      }
    }
    store(list);
  }

  // Connections: one session per server, kept while the app runs; one shared channel for listing

  private static final Map<String, Session> sessions = new HashMap<>();
  private static final Map<String, ChannelSftp> channels = new HashMap<>();

  /** Closes a server's connection; the sockets are closed outside the class lock. */
  private static void disconnect (String id) {
    final ChannelSftp channel;
    final Session session;
    synchronized (XgramSftp.class) {
      channel = channels.remove(id);
      session = sessions.remove(id);
    }
    if (channel != null) {
      try {
        channel.disconnect();
      } catch (Throwable ignored) { }
    }
    if (session != null) {
      try {
        session.disconnect();
      } catch (Throwable ignored) { }
    }
  }

  private static final class Repository implements HostKeyRepository {
    private final Server server;
    byte[] seen;

    Repository (Server server) {
      this.server = server;
    }

    @Override
    public int check (String host, byte[] key) {
      if (server.hostKey != null && Arrays.equals(Base64.decode(server.hostKey, Base64.NO_WRAP), key)) {
        return OK;
      }
      seen = key;
      return server.hostKey == null ? NOT_INCLUDED : CHANGED;
    }

    @Override public void add (HostKey hostkey, UserInfo ui) { }
    @Override public void remove (String host, String type) { }
    @Override public void remove (String host, String type, byte[] key) { }
    @Override public String getKnownHostsRepositoryID () { return "xgram"; }
    @Override public HostKey[] getHostKey () { return new HostKey[0]; }
    @Override public HostKey[] getHostKey (String host, String type) { return new HostKey[0]; }
  }

  private static final class Answers implements UserInfo, UIKeyboardInteractive {
    private final @Nullable String password;

    Answers (@Nullable String password) {
      this.password = password;
    }

    @Override public String getPassphrase () { return null; }
    @Override public String getPassword () { return password; }
    @Override public boolean promptPassword (String message) { return password != null; }
    @Override public boolean promptPassphrase (String message) { return false; }
    @Override public boolean promptYesNo (String message) { return false; }
    @Override public void showMessage (String message) { }

    @Override
    public String[] promptKeyboardInteractive (String destination, String name, String instruction, String[] prompt, boolean[] echo) {
      if (password == null || prompt == null) {
        return null;
      }
      final String[] answers = new String[prompt.length];
      Arrays.fill(answers, password);
      return answers;
    }
  }

  private static final Map<String, Object> connectLocks = new HashMap<>();

  private static Object connectLock (String id) {
    synchronized (XgramSftp.class) {
      Object lock = connectLocks.get(id);
      if (lock == null) {
        connectLocks.put(id, lock = new Object());
      }
      return lock;
    }
  }

  @WorkerThread
  private static Session session (Server server) throws Exception {
    // One connection per server: the check, the connect and the store happen under the server's own lock
    synchronized (connectLock(server.id)) {
      return sessionLocked(server);
    }
  }

  private static Session sessionLocked (Server server) throws Exception {
    synchronized (XgramSftp.class) {
      final Session cached = sessions.get(server.id);
      if (cached != null && cached.isConnected()) {
        return cached;
      }
    }
    disconnect(server.id);
    final JSch jsch = new JSch();
    final Repository repository = new Repository(server);
    jsch.setHostKeyRepository(repository);
    final String key = XgramSecrets.decrypt(server.privateKey);
    if (key != null) {
      jsch.addIdentity("xgram-" + server.id, key.getBytes(StandardCharsets.UTF_8), null, null);
    }
    final String password = XgramSecrets.decrypt(server.password);
    final Session session = jsch.getSession(server.user, server.host, server.port);
    if (password != null) {
      session.setPassword(password);
    }
    session.setUserInfo(new Answers(password));
    session.setConfig("StrictHostKeyChecking", "yes");
    session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
    session.setServerAliveInterval(30_000);
    try {
      session.connect(TIMEOUT_MS);
    } catch (JSchException e) {
      if (repository.seen != null) {
        if (server.hostKey == null) {
          throw new UnknownHostKeyException(hostKeyType(server.host, repository.seen), repository.seen, server.host, server.port);
        }
        throw new ChangedHostKeyException(repository.seen);
      }
      throw e;
    }
    synchronized (XgramSftp.class) {
      sessions.put(server.id, session);
    }
    if (server(server.id) == null) {
      disconnect(server.id); // removed while it connected
      throw new JSchException("The server was removed");
    }
    return session;
  }

  private static String hostKeyType (String host, byte[] key) {
    try {
      return new HostKey(host, key).getType();
    } catch (Throwable t) {
      return "";
    }
  }

  /** Guards the shared listing channel (never the channel object: JSch's session thread locks that one). */
  private static final Object LIST_LOCK = new Object();

  @WorkerThread
  private static ChannelSftp channel (Server server) throws Exception {
    synchronized (XgramSftp.class) {
      final ChannelSftp cached = channels.get(server.id);
      if (cached != null && cached.isConnected()) {
        return cached;
      }
    }
    final ChannelSftp channel = (ChannelSftp) session(server).openChannel("sftp");
    channel.connect(TIMEOUT_MS);
    synchronized (XgramSftp.class) {
      channels.put(server.id, channel);
    }
    return channel;
  }

  /** The folder a server opens at: its saved folder, or the user's home. */
  @WorkerThread
  public static String startFolder (Server server) throws Exception {
    synchronized (LIST_LOCK) {
      return channel(server).realpath(server.folder != null ? server.folder : ".");
    }
  }

  /** JSch reads *, ? and \\ in paths as a pattern: a name with them is meant as it is written. */
  private static String quote (String path) {
    final StringBuilder b = new StringBuilder(path.length() + 4);
    for (int i = 0; i < path.length(); i++) {
      final char c = path.charAt(i);
      if (c == '\\' || c == '*' || c == '?') {
        b.append('\\');
      }
      b.append(c);
    }
    return b.toString();
  }

  /** A folder's entries, folders first, then by name; hidden ones (".name") left out. */
  @WorkerThread
  public static List<Entry> list (Server server, String folder) throws Exception {
    final List<Entry> entries = new ArrayList<>();
    synchronized (LIST_LOCK) {
      final ChannelSftp channel = channel(server);
      final Vector<ChannelSftp.LsEntry> ls = channel.ls(quote(folder));
      for (ChannelSftp.LsEntry entry : ls) {
        final String name = entry.getFilename();
        if (name == null || name.startsWith(".")) {
          continue;
        }
        final String path = (folder.endsWith("/") ? folder : folder + "/") + name;
        SftpATTRS attrs = entry.getAttrs();
        if (attrs.isLink()) {
          try {
            attrs = channel.stat(quote(path)); // a link: what it points to
          } catch (Throwable ignored) { }
        }
        entries.add(new Entry(name, path, attrs.isDir(), attrs.getSize(), attrs.getMTime() * 1000L));
      }
    }
    Collections.sort(entries, (a, b) -> {
      if (a.folder != b.folder) {
        return a.folder ? -1 : 1;
      }
      return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
    });
    return entries;
  }

  // Sending: "xgramsftp://<server>/<size>/<modified><path>" is made into a file when TDLib asks (inputFileGenerated)

  public static boolean isPath (@Nullable String path) {
    return path != null && path.startsWith(PATH_PREFIX);
  }

  public static boolean isConversion (@Nullable String conversion) {
    return CONVERSION.equals(conversion);
  }

  public static String path (Server server, Entry entry) {
    return PATH_PREFIX + server.id + "/" + Math.max(0, entry.size) + "/" + Math.max(0, entry.modified) + "/" + entry.path;
  }

  private static final class Parsed {
    final String serverId, remotePath, name;
    final long size;

    Parsed (String serverId, long size, String remotePath) {
      this.serverId = serverId;
      this.size = size;
      this.remotePath = remotePath;
      final int slash = remotePath.lastIndexOf('/');
      this.name = slash != -1 ? remotePath.substring(slash + 1) : remotePath;
    }
  }

  private static @Nullable Parsed parse (String path) {
    if (!isPath(path)) {
      return null;
    }
    final String rest = path.substring(PATH_PREFIX.length());
    final int a = rest.indexOf('/');
    final int b = a != -1 ? rest.indexOf('/', a + 1) : -1;
    final int c = b != -1 ? rest.indexOf('/', b + 1) : -1;
    if (a <= 0 || b == -1 || c == -1) {
      return null;
    }
    try {
      return new Parsed(rest.substring(0, a), Long.parseLong(rest.substring(a + 1, b)), rest.substring(c + 1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** TDLib's file for a server file (made by {@link #generate} when it uploads), its name and size in {@code out}. */
  public static TdApi.InputFile inputFile (String path, @Nullable TD.FileInfo out) {
    final Parsed parsed = parse(path);
    if (out != null && parsed != null) {
      out.title = parsed.name;
      out.knownSize = parsed.size;
      out.mimeType = null; // sent as a file, as a file manager sends it
    }
    return new TdApi.InputFileGenerated(path, CONVERSION, parsed != null ? parsed.size : 0);
  }

  public interface GenerationCallback {
    void onDone (@Nullable String error);
  }

  private static final Set<Long> active = Collections.synchronizedSet(new HashSet<>());
  /** At most 4 downloads at once per server (OpenSSH allows 10 channels per connection; listing uses one). */
  private static final Map<String, java.util.concurrent.Semaphore> downloadSlots = new HashMap<>();

  private static java.util.concurrent.Semaphore downloadSlots (String id) {
    synchronized (XgramSftp.class) {
      java.util.concurrent.Semaphore slots = downloadSlots.get(id);
      if (slots == null) {
        downloadSlots.put(id, slots = new java.util.concurrent.Semaphore(4));
      }
      return slots;
    }
  }
  private static final Set<Long> cancelled = Collections.synchronizedSet(new HashSet<>());

  /** TDLib no longer needs the file (the message was cancelled): a running download stops. */
  public static void cancel (long generationId) {
    if (active.contains(generationId)) {
      cancelled.add(generationId);
    }
  }

  /** Downloads the file TDLib asked for into {@code destinationPath}, reporting progress; on its own thread. */
  public static void generate (Tdlib tdlib, String originalPath, long generationId, String destinationPath, GenerationCallback callback) {
    active.add(generationId);
    new Thread(() -> {
      try {
        generateImpl(tdlib, originalPath, generationId, destinationPath, callback);
      } finally {
        active.remove(generationId);
        cancelled.remove(generationId);
      }
    }, "XgramSftp").start();
  }

  private static void generateImpl (Tdlib tdlib, String originalPath, long generationId, String destinationPath, GenerationCallback callback) {
    {
      final Parsed parsed = parse(originalPath);
      final Server server = parsed != null ? server(parsed.serverId) : null;
      if (server == null) {
        callback.onDone("The SFTP server was removed");
        return;
      }
      final java.util.concurrent.Semaphore slots = downloadSlots(server.id);
      try {
        slots.acquire(); // an album of many files waits its turn instead of failing past the server's channel limit
      } catch (InterruptedException e) {
        callback.onDone("Interrupted");
        return;
      }
      ChannelSftp channel = null;
      try (OutputStream out = new FileOutputStream(new File(destinationPath))) {
        if (cancelled.contains(generationId)) {
          callback.onDone("Cancelled");
          return;
        }
        channel = (ChannelSftp) session(server).openChannel("sftp"); // its own channel: listing goes on meanwhile
        channel.connect(TIMEOUT_MS);
        final long[] done = {0};
        channel.get(quote(parsed.remotePath), out, new SftpProgressMonitor() {
          @Override public void init (int op, String src, String dest, long max) { }

          @Override
          public boolean count (long count) {
            done[0] += count;
            tdlib.client().send(new TdApi.SetFileGenerationProgress(generationId, parsed.size, done[0]), tdlib.silentHandler());
            return !cancelled.contains(generationId);
          }

          @Override public void end () { }
        });
        callback.onDone(cancelled.contains(generationId) ? "Cancelled" : null);
      } catch (Throwable t) {
        Log.w(TAG, "generate", t);
        callback.onDone(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
      } finally {
        if (channel != null) {
          try {
            channel.disconnect();
          } catch (Throwable ignored) { }
        }
        slots.release();
      }
    }
  }
}
