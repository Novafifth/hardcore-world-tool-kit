# Hardcore GETracker

Grand Exchange prices, trade history and offer alerts for Old School RuneScape's **Hardcore Worlds**.

Hardcore Worlds have their own economy, so the main game's GE price guides don't apply there. Hardcore GETracker builds
a shared Hardcore price index from the trades of the players who use it, and keeps track of your own offers and
trades. It stays idle on every other world.

## Features

### Hardcore market prices

Search any item to see what it actually trades for on the Hardcore Worlds: the last bought and sold prices, 24-hour
averages and volume, open bids and asks, recent trades and a price chart from 24 hours to 1 year.

The home view lists your open offers and a feed of recent Hardcore trades. Each open offer is flagged when someone
sells below it or bids above it, or when it hasn't filled for a while. You can also get a notification.

### Prices right in the Grand Exchange

When you set up an offer, the item's Hardcore prices appear under RuneLite's own GE text, and the item opens in the
side panel.

### Trade history and profit

- Your offers and fills are recorded as they happen, and past trades are picked up from the game's GE History tab. They
  are kept in a monthly log on your computer.
- Profit (turn on **Show profit**): realized profit today, over 7 days and all time, per item. It's worked out on your
  computer from your own trades, and it doesn't subtract any Grand Exchange tax.

## Getting started

1. Install **Hardcore GETracker** from the Plugin Hub and log in to a Hardcore World.
2. Open the panel (the coin icon in the sidebar) and follow the *Get started* card.
3. Press **Connect** to see the shared market data. Your own trades work without it.
4. Open your **GE History** tab once, so past trades are picked up.

## Settings

Under **Configuration > Hardcore GETracker**, or the gear button in the panel.

| Setting | Default | What it does |
|---|---|---|
| Connect to Hardcore GETracker server | Off | Shows the shared market data and shares your trades |
| Share my trades | On | What is shared while connected |
| Open item when setting up a GE offer | On | Shows the item in the side panel |
| Switch to this panel for GE offers | On | Off: the item still opens here, but whatever panel is open stays on top (handy with another price plugin) |
| Right-click "HC prices" | On | The menu option on tradeable items that opens them in the panel |
| Show profit | Off | Your profit (today, 7 days, all time) at the top of the panel |
| Flag offers with no fill for (hours) | 6 | When an open offer counts as stale |
| Notify when undercut or outbid | Off | A RuneLite notification for your open offers |
| Save local trade log | On | A monthly file of your trades on your computer |

## Privacy

- Nothing is sent anywhere until you press **Connect**. It is off by default, and RuneLite shows its standard
  third-party warning first.
- Once connected, the plugin shares your own Hardcore World Grand Exchange trades and open offers (item, price,
  quantity, time). They are sent with a random install id and a hashed account id. Your name is never sent, and the
  server never stores IP addresses.
- **Delete my shared data** in the panel removes everything your computer has shared. You can turn sharing off at any
  time.
- The full privacy notice:
  [hardcore-ge-tracker-107023686748.europe-north1.run.app/privacy/hardcore](https://hardcore-ge-tracker-107023686748.europe-north1.run.app/privacy/hardcore)

## FAQ

**Why do some items have no price yet?**
The prices come only from trades by players who use the plugin and have connected. The more people connect, the more
items get covered.

**Does it work on other worlds?**
No. It only does anything on the Hardcore Worlds.

## Feedback

Found a bug or have an idea? [Open an issue](https://github.com/Novafifth/hardcore-ge-tracker/issues), or message
**@Novith** on Discord.

How it all works in detail: [docs/TECHNICAL.md](docs/TECHNICAL.md).
