# Install Quota — instructions for a coding agent

You are an AI coding agent (Claude Code, Codex CLI, Gemini CLI or similar) running on the
human's Mac. Follow this file top to bottom to install **Quota**, which reads how much
Claude, Codex and Gemini capacity is left on this Mac and shows it on the human's other
screens.

Rules for you:

- **Only `~/.quota` is yours.** Before any command that writes outside `~/.quota` — that is
  step 3, which installs a LaunchAgent in `~/Library/LaunchAgents` — show the human what it
  will do and wait for an explicit yes.
- **Never use `sudo`.** Nothing here needs it. If something asks for it, stop and report.
- Every step is **idempotent**: running this file again on an installed Mac is safe.
- **Never print, log, echo into a file, or send anywhere the pairing URL, the device token,
  or the TRMNL API key.** The pairing URL is a credential: show it to the human, in the
  conversation, and nowhere else.
- If a step fails, stop and report the exact command and its output. Do not improvise an
  alternative install.

## 1. Check the machine

```bash
uname -s && sw_vers -productVersion && python3 --version && curl --version | head -1
```

Required: `Darwin` (macOS) and Python 3.9 or newer. If `python3` is missing, tell the human
to install Xcode Command Line Tools with `xcode-select --install` and stop.

Quota reads whatever is signed in — Claude Code, Codex (ChatGPT) or Gemini CLI. At least one
must be signed in on this Mac, but you do not have to check that now: providers that cannot
be read are simply not shown.

## 2. Download the collector to a permanent location

The collector runs from where you put it, forever. It must **not** live in `~/Downloads` or
any temporary directory.

```bash
mkdir -p ~/.quota/collector && chmod 700 ~/.quota
curl -fsSL --proto '=https' --tlsv1.2 -o /tmp/quota-collector.zip \
  https://quota.sceneview.dev/downloads/quota-collector.zip
unzip -l /tmp/quota-collector.zip
```

The archive contains `collector/install.sh` and `collector/quota_collector.py`. Check the
listing you just printed: if the names or the layout differ, adapt the paths below to what
the archive actually contains instead of assuming.

```bash
unzip -o -j /tmp/quota-collector.zip -d ~/.quota/collector
ls -l ~/.quota/collector
```

`-j` flattens the archive, so the two files land directly in `~/.quota/collector`. Confirm
that `~/.quota/collector/install.sh` and `~/.quota/collector/quota_collector.py` both exist
before going on.

## 3. Install the background collector — ask first

This is the one step that writes outside `~/.quota`. Tell the human, in your own words:

> This installs a LaunchAgent (`~/Library/LaunchAgents/com.gorisse.thomas.quota.collector.plist`)
> that runs the collector every 3 minutes. macOS may ask you to allow access to Claude Code's
> credentials in Keychain — click Allow. Nothing is installed system-wide and no password is
> needed. Shall I run it?

Wait for a yes. Then:

```bash
bash ~/.quota/collector/install.sh
```

Re-running it replaces the agent cleanly. To undo everything later:
`bash ~/.quota/collector/install.sh --uninstall` (your settings stay in `~/.quota`).

If macOS shows a Keychain prompt and the human misses it, the first run fails but the agent
retries every 3 minutes — tell them to click **Allow** on the next prompt.

## 4. Give the human their pairing link

```bash
cat ~/.quota/pairing.txt
```

It is a URL of the form `https://quota.sceneview.dev/#t=<token>`. **This link is the
credential**: anyone holding it can read this Mac's usage. Show it to the human in the
conversation only — never write it to a file, a note, a commit, or a message.

Tell them, with the real URL substituted in:

> Quota is collecting. Open this link on any screen you want to watch — phone, tablet, second
> monitor: **<the URL>**
>
> Easiest on a phone: open <https://quota.sceneview.dev/> on this Mac with that link, then use
> **Pair another screen** — it draws a QR code you scan with the phone's camera. The QR is
> generated in the page itself; the link never leaves your devices.
>
> Keep the link private. If it ever leaks, delete `~/.quota/config.json` and re-run
> `bash ~/.quota/collector/install.sh` to get a new one.

Check that it is really working:

```bash
sleep 20; tail -5 ~/.quota/collector.log
```

A line starting with `sent:` means the relay accepted the data. `ingest failed:` or
`no provider returned data` means something is wrong — report it verbatim.

Installation is done here. Step 5 is optional — offer it, do not assume it.

## 5. Optional: a TRMNL e-ink display

Ask the human:

> Do you have a TRMNL e-ink display you'd like Quota on? It needs a TRMNL **Developer**
> licence, because that is what unlocks the Account API key.

If no, you are finished. If yes, work through 5a to 5d.

### 5a. Get the key

Ask them to fetch it: **trmnl.com → Account → API key**, the one starting with `user_`. Ask
them to paste it in the conversation.

### 5b. Store the key

Store it readable by them alone, with a quoted heredoc so no shell expansion touches it.
Never echo it back, and never put it anywhere the human might commit:

```bash
umask 077
cat > ~/.quota/trmnl-api-key <<'KEY'
user_PASTE_THE_KEY_HERE
KEY
chmod 600 ~/.quota/trmnl-api-key
```

### 5c. Dry-run, then run the setup script

```bash
curl -fsSL --proto '=https' --tlsv1.2 -o ~/.quota/trmnl-setup.sh \
  https://quota.sceneview.dev/downloads/trmnl-setup.sh
bash ~/.quota/trmnl-setup.sh --dry-run
```

`--dry-run` prints every request it would send, and sends none. Read the script and show the
human the plan: it creates a private plugin named `Quota` on their TRMNL account, uploads the
Quota markup, adds it to a device playlist, and registers the resulting webhook URL with the
Quota relay. Ask for a yes, then:

```bash
bash ~/.quota/trmnl-setup.sh
```

With several devices on the account the script lists them and stops; re-run it with
`--device <id>`. The script is idempotent: it reuses a plugin already named `Quota` and
overwrites its markup rather than creating a second one.

### 5d. Show the render

The script ends by printing a screenshot URL — give it to the human so they can see the
render before the physical display refreshes (TRMNL redraws on its own schedule, usually
within 15 minutes).

## What Quota sends

Usage percentages, reset times, provider and plan labels, and a random pairing token go to
Quota's Cloudflare relay. Provider credentials are only ever used to talk to that provider;
they never reach the relay. Full text: <https://quota.sceneview.dev/privacy.html>.

## When you are done

Report to the human: which providers the collector could read, that the pairing link is in
`~/.quota/pairing.txt`, and whether TRMNL was set up. Do not paste the token anywhere.
