# Hardcore GETracker: technical details

Everything the plugin does, in detail: what is recorded, what is shared and how, and how to build and test it. For the
short overview, see the [README](../README.md).

## What it does

- Records your GE offers as they are placed, partial and complete fills at the actual price paid or received, and
  cancellations.
- Imports trades from the in-game GE History tab. These have no timestamp and are shown as "history".
- Adds a sidebar panel with:
  - item search, a settings (gear) button and the connection status;
  - a "Get started" card until you have connected and imported your GE History;
  - "Your offers": each of your open Hardcore World offers, flagged *undercut* (someone sells below your sell price),
    *outbid* (someone bids above your buy price) or *stale* (no fill for a while) while connected, otherwise how
    much has filled. Offers are compared with the open order book and, in the item view, with live fills made since
    you placed the offer (never GE History rows, whose time is only when they were imported);
  - "Profit" (only with *Show profit* on): your realized profit today, over the last 7 days and all time, and your
    best items (see [Profit](#profit));
  - a market feed (each item's latest live trade, one row per item; both sides of one trade between two plugin
    users show as one "Traded" row) and a "My trades" list (50 rows at a time, with "Show more"), each with a refresh
    button and when it was last updated (or, after a failed refresh, why, e.g. "Update failed: server unreachable";
    the old data stays);
  - an item view with your open offers for the item, stats, a price chart (24h / 7d / 30d / 90d / 1y), the
    open-offer book, recent trades from all players, your own trades and your profit on the item. Your own
    offers and fills show up there immediately; new trades are briefly highlighted.
- Opens the item in the panel when you set up a GE offer for it (can be turned off).
- While connected, adds a line to the GE offer setup's item text with the Hardcore prices: last bought / sold, best
  bid / ask and units traded in the last 24 hours, e.g. `HC last 1.25M/1.24M · bid 1.23M ask 1.26M · 523/24h` (the
  bid / ask part is left out when the line would get too long). RuneLite's own GE text stays as it is.
- Optionally notifies you when one of your offers gets undercut or outbid.
- Adds a "HC prices" right-click option on tradeable items (*Right-click "HC prices"* under Panel turns it off).
- On your first login on a Hardcore World it shows one chat message (only in your own chatbox) pointing at the panel.

## Settings

All settings are on RuneLite's configuration page (**Configuration > Hardcore GETracker**, or the gear button in
the panel):

- **Data sharing**: *Connect to Hardcore GETracker server* (off by default; shows the warning below when turned on)
  and *Share my trades* (on once connected).
- **Panel**: *Open item when setting up a GE offer*, *Switch to this panel for GE offers* (off: the item still opens
  in this panel, ready when you switch to it, but whatever panel is open stays on top) and *Right-click "HC prices"*
  (all on by default), and *Show profit* (off by default).
- **Offer alerts**: *Flag offers with no fill for (hours)* (6 by default) and *Notify when undercut or outbid* (off by
  default; uses RuneLite's notification settings). The market checks need the connection.
- **Local**: *Save local trade log* (on by default).

## Profit

Profit is worked out locally from your own Hardcore World fills and GE History rows, per account, and never leaves your
computer. It is shown only with *Show profit* on, but the totals are kept up to date either way.

- For each item it keeps the units you hold (bought minus sold, within what the plugin has seen), their average cost
  (a moving average) and the profit realized when you sell. The figures are gross: no Grand Exchange tax is
  subtracted.
- Selling more units than the plugin saw you buy (bought before you installed it, or while it was off) has no known
  cost. Those proceeds are shown as "sold without known cost" and are not counted as profit.
- GE History rows have no real time, so they count on the day you import them; the panel says how much of the total
  came from GE History.
- "Today" and "7 days" use your computer's time zone.

The running totals are saved to `profit-<id>.json` in the plugin folder (next to the trade log), where `<id>` is the
first 16 characters of the pseudonymous account id described below, never your name. Start-up reads only that small
file. **Rebuild** (in the Profit section) recounts everything from the local trade log in the background; it needs
*Save local trade log*.

## Hardcore Worlds only

The plugin only tracks the Hardcore Worlds: the worlds the game's world list marks with the `HARDCORE_MAIN` world type
(`WorldType.HARDCORE_MAIN` in RuneLite's API). It uses no world numbers, so nothing needs updating when worlds are
added or renumbered. All Hardcore Worlds share one economy, so trades are sent with the world field set to one fixed
economy id (`HardcoreGeTrackerPlugin.HARDCORE_ECONOMY`, 9000) instead of the number of the world you were on, and the
server keeps one price index for them. Every other world is ignored.

## Opt-in data sharing

Nothing contacts a server until you press **Connect** in the panel or turn on *Connect to Hardcore GETracker server*
in the settings. Both ask you to confirm this warning first:

> This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers.

While connected, the panel reads market data from the server (`v1/recent` with
`kinds=fill`, `v1/item/{id}`, `v1/item/{id}/series`, all with `world=9000`, the Hardcore economy id; the feed lists
live fills only, while GE History rows show in the item views), the GE offer
setup line reads `v1/item/{id}` for the item you are setting up (cached for 30 seconds), and while you have open
offers on a Hardcore World their item ids are checked with
`v1/items?ids=...` at most once a minute while the side panel is open or offer notifications are on (and when you
press refresh or open the panel, at most every 10 seconds). Like any
connection, these requests reach the server from your network address; see *IP addresses* below. Right after one of your
offers fills, the upload is sent within a couple of seconds (instead of at the next 30-second upload) so the item
view can show the new market data.

**Share my trades** (on once connected) uploads:

- a random install id (a UUID; the server stores it together with the hashed account id below);
- a pseudonymous account id (hashed, not your name or RSN): the SHA-256 hash of `hardcore-ge-tracker:` followed by
  RuneLite's account hash, so the server can keep one account's trades together without learning who you are. It is
  pseudonymous, not anonymous: it is the same for every upload of that account, and the server links it to the install
  id. The server's privacy notice (`<server>/privacy/hardcore`) says how long each is kept and how to delete them;
- the economy id (9000) in the world field, not the world you are on;
- your trade events: an event id (derived from the random offer key, or random for GE History rows), kind, buy/sell,
  item id and name, quantity, price, total, GE slot, a random offer key, the time it was observed, a late flag, the
  filled count and the last time the offer was seen;
- a snapshot of your open offers: slot, offer key, side, item id, price, total quantity, filled quantity and when it
  was placed;
- item names and icons, and the item's GE buy limit when RuneLite knows it (from RuneLite's own item stats; the
  server uses it to flag buys over the 4-hour limit for review, never to reject them, and only accepts it from
  established sources);
- the time the upload was sent, so the server can correct for your PC's clock.

Uploads never include your IP address, your RSN, the account hash itself or anything about other players.

**IP addresses.** Nothing the plugin sends (no request body, header or URL) contains your IP address, and the server
never stores, hashes or logs it. Like any connection, the server sees the address you connect from while it answers
the request, and uses it only for short-lived in-memory flood protection (for example, how many requests one address
sent in the last minute); those counters are never written to a database, disk or log and expire on their own. The
only identifiers the server keeps are the random install id and the hashed account id. The RuneLite warning above
is the standard text RuneLite requires for every plugin that contacts a 3rd-party server. Turning **Connect** off in the settings stops all contact with the server;
uploads that were not sent yet are discarded. While connected, uploads that weren't sent yet (the server was down, or
RuneLite closed first) wait in `pending-uploads.json` in the plugin folder and go out after the next start; the server
ignores anything it already has. The file is removed when nothing is waiting.

**Install token.** Before the first upload the plugin registers its install id with the server (`POST v1/register`,
which only sends the install id) and receives a random install token. Every upload carries it
(`Authorization: Bearer ...`), so nobody else can upload, or delete data, under your install id. Registration is part
of uploading: it only happens while connected with *Share my trades* on. The install id and the token are kept in
`server-identity.json` in the plugin folder (`~/.runelite/plugin-data/hardcore-ge-tracker/`), not in the RuneLite
config: RuneLite logs config values it saves at debug level, passes them to every plugin, and syncs the config to its
servers for players with a RuneLite account. So the token is never written to a log or synced, and each computer has
its own install id. A developer-mode client talking to another server (the local test server) keeps a separate file
for it, `server-identity-<host>-<port>.json`, so testing never touches the real install id and token. The token only authorises this install's uploads and the deletion of its data. If the server refuses the token, the
plugin registers once more; if the install id is taken it switches to a new random one, and remembers the old one
(*Delete my shared data* lists it, since data shared under it can't be deleted from here any more). If the file can't
be read when RuneLite starts (another program has it locked), the plugin never writes over it.

**Confirmed prices.** Market requests ask for `include=trust,fill`. Newer servers then also send confirmed figures
(trades from established players at a normal price, or matched by an independent counterparty). The item view shows
"Typical (confirmed)", the median of recent confirmed trades, when there is one, the confirmed / unconfirmed split in
the 24h volume tooltip, and unconfirmed trades in a muted colour. Undercut / outbid checks prefer the best bid / ask
from trusted offers when the server sends them. Older servers ignore the parameter and nothing changes.

**Privacy menu.** The panel's **Privacy** link opens a small menu:

- *Privacy notice* opens the server's privacy notice (`<server>/privacy/hardcore`).
- *Delete my shared data...* asks for confirmation, then deletes everything this computer has shared (your trades,
  offers and ids; `DELETE v1/me` with the install token) and recalculates the market totals without them. It then
  disconnects the plugin, and if you connect again later you start over as a new, unlinked install id. Your local trade
  log and profit stay on your PC. This is the one request the plugin may send while disconnected, because you asked for
  it; it is only offered when this computer has a token or is connected. Data uploaded from another computer has to be
  deleted from that computer. If this computer's key was lost (the server holds the install id under a token the
  plugin no longer has), nothing can be deleted from here: the plugin says so, disconnects and shows the install id,
  which you can send to the contact on the privacy notice.

## Local log

**Save local trade log** is on by default and works without connecting. It appends one JSON object per line to a
file per month (by the trade's time in UTC): `~/.runelite/plugin-data/hardcore-ge-tracker/trades-YYYY-MM.jsonl`
(`%USERPROFILE%\.runelite\plugin-data\hardcore-ge-tracker\trades-YYYY-MM.jsonl` on Windows). Each line also carries
`acct`, the same pseudonymous account id as above, so per-account numbers can be rebuilt from the log.

The profit totals are kept in `profit-<id>.json` files in the same folder (see [Profit](#profit)); they are updated
even when the trade log is off.

When the plugin starts it reads only the end of the newest files, until it has the newest 300 entries for
"My trades", so start-up stays fast however long the log gets.

Per-account offer state is kept in your RuneLite profile config, so fills that happen while you are logged out are
recorded the next time you log in and are marked late.

## Building and running

Requires JDK 11.

- `./gradlew test` runs the unit tests. `ServerContractTest` is skipped unless the environment variable
  `HGT_CONTRACT_SERVER` holds the base URL of a running collection server; it then registers, uploads, reads and
  deletes against it with the plugin's own classes.
- `./gradlew run` starts a developer-mode RuneLite client with the plugin loaded. To log in with a Jagex account, follow
  [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
- `./gradlew shadowJar` builds a standalone jar.

The server address is fixed in code (`HardcoreGeTrackerPlugin.PRODUCTION_SERVER_URL`); players can't change it. Only a
developer-mode client (`./gradlew run`) uses a local server, or the one given with `-Dhardcoregetracker.server=<url>`.

**Developer mode only:**

- `::hgt fresh` shows the panel as a brand-new install sees it (UI only; nothing real changes), `::hgt fresh off` ends
  it, and `::hgt hints` re-arms the one-time chat hint. The command is registered only in developer mode.
- `-Dhardcoregetracker.anyWorld=true` treats every world as a Hardcore World, so the plugin can be tried on a normal
  world. It only takes effect with a server other than the production one (for example a local server from `server/`
  with `npm run dev`), so trades from a normal world can never reach the real price index.
