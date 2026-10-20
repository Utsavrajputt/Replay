# Replay Lua and JavaScript Command Guide

This file documents the public scripting surface that Replay exposes to mpv Lua
and JavaScript scripts.

Scripts talk to Replay by writing string values to properties under:

```text
user-data/Replay/*
```

Replay observes those properties and performs the corresponding player action.
Writable command properties are cleared after dispatch, except `curl_request`,
which remains available while its asynchronous request is handled.
`curl_response` is a read-only output property and is not cleared automatically.

The examples below use public no-auth API endpoints from:

- JSONPlaceholder: https://jsonplaceholder.typicode.com/
- httpbin: https://httpbin.org/

## Contents

- [Quick Start](#quick-start)
- [Command Contract](#command-contract)
- [Command Index](#command-index)
- [Full Working Lua Example](#full-working-lua-example)
- [Full Working JavaScript Example](#full-working-javascript-example)
- [Command Reference](#command-reference)
- [Curl Bridge](#curl-bridge)
- [Custom Buttons](#custom-buttons)
- [Android Telemetry Properties](#android-telemetry-properties)
- [Troubleshooting](#troubleshooting)

## Quick Start

1. Enable Lua/JS scripting in Replay advanced settings.
2. Put script files in the selected mpv config folder.
3. Prefer the `scripts/` subfolder. Replay also falls back to the config root.
4. Use `.lua` for Lua scripts and `.js` for JavaScript scripts.
5. Select the scripts in the Replay scripts panel.
6. Reopen the video if a script was added before playback started.

Replay also syncs `script-opts/` from the selected mpv config folder.

## Command Contract

- Every writable command value must be a non-empty string.
- Command names and enumerated values are case-sensitive and use lowercase.
- Seek values must be base-10 integer seconds. Decimals and missing values are
    invalid.
- `seek_to_with_text` and `seek_by_with_text` require `seconds|message`. The
    message may contain additional `|` characters.
- `curl_request` is asynchronous and `curl_response` is shared by all scripts.
    Give every request a unique `id` and ignore responses with a different `id`.
- Unknown enum values do nothing. Reserved observer properties are cleared but
    have no public behavior.
- JavaScript runs through mpv's JavaScript runtime. Use ES5-compatible syntax:
  `var` and `function` are safest.

## Command Index

### Writable Commands

| Property | Value | What it does |
| --- | --- | --- |
| `user-data/Replay/show_text` | Any string | Shows a native Replay text overlay. |
| `user-data/Replay/toggle_ui` | `show`, `hide`, `toggle` | Shows, hides, or toggles player controls. |
| `user-data/Replay/show_panel` | Panel id | Opens a native Replay sheet or panel. |
| `user-data/Replay/seek_to` | Integer seconds | Seeks to an absolute timestamp. |
| `user-data/Replay/seek_by` | Integer seconds | Seeks relative to the current timestamp. |
| `user-data/Replay/seek_to_with_text` | `seconds|message` | Absolute seek with overlay text. |
| `user-data/Replay/seek_by_with_text` | `seconds|message` | Relative seek with overlay text. |
| `user-data/Replay/software_keyboard` | `show`, `hide`, `toggle` | Controls the Android software keyboard. |
| `user-data/Replay/curl_request` | JSON object encoded as a string | Runs an async HTTP request through the Replay HTTP bridge. |

### Read-only Output

| Property | Value | What it does |
| --- | --- | --- |
| `user-data/Replay/curl_response` | JSON string | Receives the latest completed curl response. Observe it; do not write to it. |
| `user-data/Replay/original-path` | String | The original network path (e.g. `smb://host/share/path/video.mp4`) for the current playback item. Built from the stored connection details; falls back to the raw URI when the connection is unavailable. |

### Supported Panel IDs

| Panel id | Result |
| --- | --- |
| `frame_navigation` | Opens the frame navigation sheet. |
| `subtitle_settings` | Opens subtitle style settings. |
| `subtitle_delay` | Opens subtitle delay controls. |
| `audio_delay` | Opens audio delay controls. |
| `video_filters` | Opens video filter controls. |
| `lua_scripts` | Opens the scripts panel. |
| `hdr_screen_output` | Opens HDR screen output controls. |

### Reserved Observer Properties

`set_button_title`, `reset_button_title`, and `toggle_button` are currently
observed, but the command dispatcher does not implement behavior for them. Do
not use them as commands. Properties named `custombuttons_*_loaded` and
`custombuttons_*_version` are internal coordination state for generated custom
button scripts, not public commands.

## Full Working Lua Example

Save this as `Replay_demo.lua` in your mpv `scripts/` folder.

It fetches a public JSONPlaceholder post, shows the post title, and includes
key bindings for common Replay commands.

```lua
-- Replay_demo.lua

local utils = require("mp.utils")

local request_sequence = 0
local pending_request_id = nil

local function Replay(command, value)
    mp.set_property("user-data/Replay/" .. command, tostring(value))
end

local function show(message)
    Replay("show_text", message)
end

local function next_request_id()
    request_sequence = request_sequence + 1
    return "Replay-demo-lua-post-" .. tostring(os.time()) .. "-" .. tostring(request_sequence)
end

local function fetch_post()
    pending_request_id = next_request_id()
    show("Fetching JSONPlaceholder post...")

    Replay("curl_request", utils.format_json({
        id = pending_request_id,
        url = "https://jsonplaceholder.typicode.com/posts/1",
        method = "GET",
        headers = {
            Accept = "application/json",
        },
        timeout = 15,
    }))
end

mp.observe_property("user-data/Replay/curl_response", "string", function(_, value)
    if value == nil or value == "" then return end

    local res = utils.parse_json(value)
    if res == nil or res.id ~= pending_request_id then return end
    pending_request_id = nil

    if res.error ~= nil then
        show("Curl failed: " .. tostring(res.error))
        return
    end

    if tonumber(res.status) ~= 200 then
        show("HTTP " .. tostring(res.status))
        return
    end

    local body = utils.parse_json(res.body)
    if body == nil then
        show("Could not parse response body")
        return
    end

    show("Post #" .. tostring(body.id) .. "\n" .. tostring(body.title))
end)

mp.register_event("file-loaded", fetch_post)

mp.add_key_binding("J", "Replay-fetch-jsonplaceholder-post", fetch_post)
mp.add_key_binding("U", "Replay-toggle-ui", function()
    Replay("toggle_ui", "toggle")
end)
mp.add_key_binding("V", "Replay-open-video-filters", function()
    Replay("show_panel", "video_filters")
end)
mp.add_key_binding("RIGHT", "Replay-seek-forward", function()
    Replay("seek_by_with_text", "30|Forward 30 seconds")
end)
mp.add_key_binding("LEFT", "Replay-seek-back", function()
    Replay("seek_by_with_text", "-10|Back 10 seconds")
end)
```

## Full Working JavaScript Example

Save this as `Replay_demo.js` in your mpv `scripts/` folder.

This example uses ES5-style JavaScript for mpv compatibility. It sends a POST
request to JSONPlaceholder and shows the fake created post id returned by the
public test API.

```javascript
// Replay_demo.js

var requestSequence = 0;
var pendingRequestId = null;

function Replay(command, value) {
    mp.set_property("user-data/Replay/" + command, String(value));
}

function show(message) {
    Replay("show_text", message);
}

function nextRequestId() {
    requestSequence += 1;
    return "Replay-demo-js-create-post-" + String(new Date().getTime()) + "-" + String(requestSequence);
}

function createPost() {
    var payload = {
        title: "Replay JavaScript curl test",
        body: "Posted from an mpv JavaScript script through Replay curl.",
        userId: 1
    };

    pendingRequestId = nextRequestId();
    show("Posting to JSONPlaceholder...");

    Replay("curl_request", JSON.stringify({
        id: pendingRequestId,
        url: "https://jsonplaceholder.typicode.com/posts",
        method: "POST",
        headers: {
            "Accept": "application/json",
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload),
        content_type: "application/json",
        timeout: 15
    }));
}

mp.observe_property("user-data/Replay/curl_response", "string", function(name, value) {
    if (!value) return;

    var res;
    try {
        res = JSON.parse(value);
    } catch (e) {
        return;
    }

    if (!res || res.id !== pendingRequestId) return;
    pendingRequestId = null;

    if (res.error) {
        show("Curl failed: " + res.error);
        return;
    }

    if (res.status < 200 || res.status >= 300) {
        show("HTTP " + res.status);
        return;
    }

    var body;
    try {
        body = JSON.parse(res.body);
    } catch (e2) {
        show("Could not parse response body");
        return;
    }

    show("Created fake post #" + body.id + "\nHTTP " + res.status);
});

mp.add_key_binding("P", "Replay-create-jsonplaceholder-post", createPost);
mp.add_key_binding("U", "Replay-toggle-ui-js", function() {
    Replay("toggle_ui", "toggle");
});
mp.add_key_binding("S", "Replay-open-subtitle-settings-js", function() {
    Replay("show_panel", "subtitle_settings");
});
mp.add_key_binding("K", "Replay-show-keyboard-js", function() {
    Replay("software_keyboard", "show");
});
```

## Command Reference

### `show_text`

Shows a short native Replay overlay.

Lua:

```lua
mp.set_property("user-data/Replay/show_text", "Shaders enabled")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/show_text", "Shaders enabled");
```

### `toggle_ui`

Controls the player controls overlay.

Accepted values:

- `show`
- `hide`
- `toggle`

Other values have no effect.

Lua:

```lua
mp.set_property("user-data/Replay/toggle_ui", "hide")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/toggle_ui", "toggle");
```

### `show_panel`

Opens a native Replay sheet or panel.

Use one of the IDs in [Supported Panel IDs](#supported-panel-ids). An unknown
ID resolves to no panel and may close the currently displayed panel.

Lua:

```lua
mp.set_property("user-data/Replay/show_panel", "frame_navigation")
mp.set_property("user-data/Replay/show_panel", "video_filters")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/show_panel", "lua_scripts");
mp.set_property("user-data/Replay/show_panel", "hdr_screen_output");
```

### `seek_to`

Seeks to an absolute timestamp in integer seconds. The value must parse as an
integer; do not append units or use a decimal.

```lua
mp.set_property("user-data/Replay/seek_to", "600")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/seek_to", "600");
```

### `seek_by`

Seeks relative to the current timestamp in integer seconds. Positive values
seek forward and negative values seek backward.

```lua
mp.set_property("user-data/Replay/seek_by", "30")
mp.set_property("user-data/Replay/seek_by", "-10")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/seek_by", "30");
mp.set_property("user-data/Replay/seek_by", "-10");
```

### `seek_to_with_text`

Seeks to an absolute timestamp and shows custom overlay text.

Value format:

```text
seconds|message
```

The first `|` separates the integer timestamp from the message. Additional
`|` characters remain part of the message.

Lua:

```lua
mp.set_property("user-data/Replay/seek_to_with_text", "90|Jumping to intro")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/seek_to_with_text", "3600|Jumping to final act");
```

### `seek_by_with_text`

Seeks relative to the current timestamp and shows custom overlay text.

Lua:

```lua
mp.set_property("user-data/Replay/seek_by_with_text", "85|Skipping opening")
mp.set_property("user-data/Replay/seek_by_with_text", "-15|Back 15 seconds")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/seek_by_with_text", "30|Forward 30 seconds");
```

### `software_keyboard`

Controls the Android software keyboard.

Accepted values:

- `show`
- `hide`
- `toggle`

Other values have no effect.

Lua:

```lua
mp.set_property("user-data/Replay/software_keyboard", "show")
```

JavaScript:

```javascript
mp.set_property("user-data/Replay/software_keyboard", "hide");
```

## Curl Bridge

The curl bridge lets Lua and JavaScript scripts make HTTP requests through
Replay's Android HTTP client. Scripts write a JSON request to:

```text
user-data/Replay/curl_request
```

Replay writes the response to:

```text
user-data/Replay/curl_response
```

Requests are async. Playback continues while the request runs.

### Curl Request JSON

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | No | UUID generated by Replay | Use your own id so scripts can match responses. |
| `url` | string | Yes | none | Must not be blank. Use `http://` or `https://`. |
| `method` | string | No | `GET` | Case-insensitive. Supported: `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`. |
| `headers` | object | No | `{}` | String key/value request headers. Maximum 64 headers. |
| `body` | string | No | null | Used only for `POST`, `PUT`, and `PATCH`. Other methods send no body. |
| `content_type` | string | No | `text/plain; charset=utf-8` | Body media type for `POST`, `PUT`, and `PATCH`; blank omits it. |
| `timeout` | integer | No | `30` | Clamped to 1 through 120 seconds. |

Lua request:

```lua
local utils = require("mp.utils")

mp.set_property("user-data/Replay/curl_request", utils.format_json({
    id = "lua-httpbin-get",
    url = "https://httpbin.org/get",
    method = "GET",
    headers = {
        Accept = "application/json",
    },
    timeout = 10,
}))
```

JavaScript request:

```javascript
mp.set_property("user-data/Replay/curl_request", JSON.stringify({
    id: "js-httpbin-get",
    url: "https://httpbin.org/get",
    method: "GET",
    headers: {
        "Accept": "application/json"
    },
    timeout: 10
}));
```

### Curl Response JSON

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Echoes the request id, or generated id if omitted. |
| `status` | integer | HTTP status code. `0` means bridge/network/native error. |
| `body` | string | UTF-8 response body. Capped at 8 MiB. |
| `headers` | object | Response headers as string key/value pairs. Repeated values are comma-separated. |
| `error` | string or null | Omitted on success. String message on failure. |

Lua observer:

```lua
local utils = require("mp.utils")

mp.observe_property("user-data/Replay/curl_response", "string", function(_, value)
    if value == nil or value == "" then return end

    local res = utils.parse_json(value)
    if res == nil or res.id ~= "lua-httpbin-get" then return end

    if res.error ~= nil then
        mp.set_property("user-data/Replay/show_text", "Curl error: " .. res.error)
        return
    end

    mp.set_property("user-data/Replay/show_text", "HTTP " .. tostring(res.status))
end)
```

JavaScript observer:

```javascript
mp.observe_property("user-data/Replay/curl_response", "string", function(name, value) {
    if (!value) return;

    var res;
    try {
        res = JSON.parse(value);
    } catch (e) {
        return;
    }

    if (!res || res.id !== "js-httpbin-get") return;

    if (res.error) {
        mp.set_property("user-data/Replay/show_text", "Curl error: " + res.error);
        return;
    }

    mp.set_property("user-data/Replay/show_text", "HTTP " + res.status);
});
```

### Curl Limits and Behavior

- At most four requests execute concurrently and at most 32 may be pending.
- Response body capture is capped at 8 MiB. There is no separate truncation
    flag; `body` contains at most the first 8 MiB.
- Request headers are capped at 64 entries.
- Only HTTP and HTTPS URLs are allowed.
- Timeout is clamped to 1 through 120 seconds and applies to connection, read,
  and total call time.
- Only `POST`, `PUT`, and `PATCH` send request bodies. `GET`, `HEAD`, and
  `DELETE` do not.
- Invalid request JSON produces `id: "unknown"`, `status: 0`, an empty body and
  headers, and a descriptive `error`.
- Network errors and rejected requests use `status: 0` with a descriptive
  `error`.
- Unknown JSON fields are ignored.
- `curl_request` and `curl_response` are not cleared automatically. Since
    `curl_response` is shared, observers must filter by their current request ID.

## Custom Buttons

Replay custom buttons support Lua and JavaScript actions.

In the Custom Button editor:

- `Button title` is the text shown in the player UI.
- `Tap action` is required.
- `Long press action` is optional.
- `On startup` is optional.
- `Script language` can be Lua or JavaScript.

Paste only the action body into the editor. Replay wraps it in a generated
script and registers the correct script message internally.

Lua tap action example:

```lua
mp.set_property("deband", "yes")
mp.set_property("user-data/Replay/show_text", "Deband enabled")
```

Lua long press action example:

```lua
mp.set_property("deband", "no")
mp.set_property("user-data/Replay/show_text", "Deband disabled")
```

JavaScript tap action example:

```javascript
mp.set_property("video-zoom", "0.25");
mp.set_property("user-data/Replay/show_text", "Zoom 25%");
```

JavaScript long press action example:

```javascript
mp.set_property("video-zoom", "0");
mp.set_property("user-data/Replay/show_text", "Zoom reset");
```

Generated custom button internals:

- Replay writes generated scripts to the app internal `scripts/` directory.
- Lua buttons are generated into `custombuttons.lua`.
- JavaScript buttons are generated into `custombuttons.js`.
- Tapping a button sends `script-message call_button_<safe_id>`.
- Long pressing a button sends `script-message call_button_long_<safe_id>`.
- `<safe_id>` is the internal button id with `-` replaced by `_`.
- Generated Lua actions are guarded by `is_active_instance()`.
- Generated JavaScript actions are guarded by `isActiveInstance()`.

Do not call `is_active_instance()` or `isActiveInstance()` from normal script
files. They exist only inside generated custom button scripts.

## Android Telemetry Properties

Replay writes Android device state into mpv `user-data/android/*` properties at
playback startup and refreshes them every 30 seconds. Scripts can read or
observe these values.

| Property | Type | Meaning |
| --- | --- | --- |
| `user-data/android/battery-level` | integer | Battery level from 0 to 100, or `-1` when unavailable. |
| `user-data/android/battery-charging` | boolean | `true` when charging. |
| `user-data/android/battery-plugged` | boolean | `true` when plugged into power. |

Lua telemetry example:

```lua
mp.observe_property("user-data/android/battery-level", "native", function(_, level)
    if level == nil then return end

    local charging = mp.get_property_native("user-data/android/battery-charging")
    if tonumber(level) < 15 and not charging then
        mp.set_property("deband", "no")
        mp.set_property("user-data/Replay/show_text", "Low battery: deband disabled")
    end
end)
```

JavaScript telemetry example:

```javascript
mp.observe_property("user-data/android/battery-level", "native", function(name, level) {
    if (level === null || level === undefined) return;

    var charging = mp.get_property_native("user-data/android/battery-charging");
    if (Number(level) < 15 && !charging) {
        mp.set_property("deband", "no");
        mp.set_property("user-data/Replay/show_text", "Low battery: deband disabled");
    }
});
```

## Troubleshooting

If a command does nothing:

- Confirm scripting is enabled in Replay settings.
- Confirm the script is selected in the scripts panel.
- Confirm the script file extension is `.lua` or `.js`.
- Use integer seconds for seek commands.
- Use the exact command property path.
- For curl, check `res.id` before handling the response.
- For curl, check `res.error` and `res.status`.
- Reopen the video after adding a new script.

Minimal smoke test:

Lua:

```lua
mp.register_event("file-loaded", function()
    mp.set_property("user-data/Replay/show_text", "Lua script loaded")
end)
```

JavaScript:

```javascript
mp.register_event("file-loaded", function() {
    mp.set_property("user-data/Replay/show_text", "JavaScript script loaded");
});
```
