import json
import os
from datetime import datetime, timezone
from urllib.parse import urlparse

import yt_dlp


MAX_FILE_SIZE = 512 * 1024 * 1024
# YouTube puede devolver formatos DRM para el cliente predeterminado. Estos
# clientes públicos no requieren cookies y cubren el caso de recuperación.
# Keep five total public client attempts: visionos first, then the clients
# that previously recovered the largest number of previews and downloads.
YOUTUBE_FALLBACK_CLIENTS = ("android", "android_vr", "web_embedded", "tv")
PREVIEW_HEADER_NAMES = {
    "user-agent": "User-Agent",
    "referer": "Referer",
    "origin": "Origin",
    "accept": "Accept",
    "accept-language": "Accept-Language",
}


class _QuietLogger:
    def debug(self, _message):
        pass

    def info(self, _message):
        pass

    def warning(self, _message):
        pass

    def error(self, _message):
        pass


def _base_options():
    return {
        "quiet": True,
        "no_warnings": True,
        "logger": _QuietLogger(),
        "noplaylist": True,
        "playlist_items": "1",
        "socket_timeout": 30,
        "retries": 3,
        "fragment_retries": 3,
        "max_filesize": MAX_FILE_SIZE,
        "restrictfilenames": True,
        "windowsfilenames": False,
        "cachedir": False,
        "ignoreconfig": True,
        "no_color": True,
        # YouTube signs googlevideo URLs with the source IP. Android may
        # resolve the extraction over IPv6 and Media3 over IPv4, which turns
        # an otherwise valid URL into a 403. Keep both requests on IPv4.
        "force_ipv4": True,
        "extractor_args": {
            "youtube": {
                # Start with the runtime-free client that was most reliable
                # before the aggressive retry optimization was introduced.
                "player_client": ["visionos"],
            },
        },
        # yt-dlp enables Deno by default when this option is absent. Chaquopy
        # does not ship an external JavaScript runtime, and attempting to
        # probe one through subprocess is not safe on Android. An explicit
        # empty map selects yt-dlp's JS-less YouTube client instead.
        "js_runtimes": {},
    }


def _is_youtube_url(url):
    try:
        host = (urlparse(str(url or "")).hostname or "").lower()
    except ValueError:
        return False
    return (
        host == "youtu.be"
        or host == "youtube.com"
        or host.endswith(".youtube.com")
        or host == "youtube-nocookie.com"
        or host.endswith(".youtube-nocookie.com")
    )


def _is_youtube_search(url):
    return str(url or "").lower().startswith("ytsearch")


def _should_retry_youtube(url, error):
    if not (_is_youtube_url(url) or _is_youtube_search(url)):
        return False
    message = str(error or "").lower()
    return any(
        marker in message
        for marker in (
            "sign in to confirm",
            "not a bot",
            "login_required",
            "po token",
            "requested format is not available",
            "http error 403",
            "http error 400",
            "http error 408",
            "http error 429",
            "http error 500",
            "http error 502",
            "http error 503",
            "bad request",
            "too many requests",
            "unable to download",
            "timed out",
            "connection reset",
            "connection aborted",
            "temporary failure",
            "forbidden",
            "failed to extract any player response",
            "all player responses are invalid",
            "your ip is likely being blocked",
            "the page needs to be reloaded",
            "video unavailable",
            "this video is restricted",
            "workspace administrator",
            "network administrator",
            "this video is drm protected",
            "drm protected",
            "drm-only",
        )
    )


def _youtube_fallback_options(options, player_client):
    fallback = dict(options)
    extractor_args = {
        key: dict(value)
        for key, value in (options.get("extractor_args") or {}).items()
    }
    youtube_args = dict(extractor_args.get("youtube") or {})
    youtube_args["player_client"] = [player_client]
    extractor_args["youtube"] = youtube_args
    fallback["extractor_args"] = extractor_args
    return fallback


def _extract_with_youtube_fallback(url, options, download, accept_info=None):
    attempts = [options]
    if _is_youtube_url(url) or _is_youtube_search(url):
        configured_clients = set(
            ((options.get("extractor_args") or {}).get("youtube") or {})
            .get("player_client")
            or []
        )
        attempts.extend(
            _youtube_fallback_options(options, player_client)
            for player_client in YOUTUBE_FALLBACK_CLIENTS
            if player_client not in configured_clients
        )

    last_error = None
    for attempt in attempts:
        try:
            with yt_dlp.YoutubeDL(attempt) as ydl:
                info = ydl.extract_info(url, download=download)
                if accept_info is not None and not accept_info(info):
                    last_error = RuntimeError("El cliente de YouTube no devolvió una pista reproducible")
                    continue
                return info, ydl
        except Exception as error:
            if not _should_retry_youtube(url, error):
                raise
            last_error = error

    if last_error is not None:
        raise last_error
    raise RuntimeError("No se pudo preparar la extracción")


def _media_json(info, path=None):
    payload = {
        "id": str(info.get("id") or ""),
        "title": str(info.get("title") or "Audio"),
        "artist": str(
            info.get("artist")
            or info.get("uploader")
            or info.get("channel")
            or ""
        ),
        "album": str(info.get("album") or ""),
        "durationMs": int(float(info.get("duration") or 0) * 1000),
        "thumbnail": _best_thumbnail(info),
        "webpageUrl": str(info.get("webpage_url") or info.get("original_url") or ""),
        "extractor": str(info.get("extractor_key") or info.get("extractor") or ""),
        "extension": str(info.get("ext") or ""),
        "sizeBytes": int(info.get("filesize") or info.get("filesize_approx") or -1),
        "path": str(path or ""),
    }
    return json.dumps(payload, ensure_ascii=False)


def _best_thumbnail(info):
    thumbnails = [item for item in (info.get("thumbnails") or []) if item.get("url")]
    if thumbnails:
        best = max(
            thumbnails,
            key=lambda item: (
                int(item.get("preference") or -1),
                int(item.get("width") or 0) * int(item.get("height") or 0),
            ),
        )
        return str(best.get("url") or "")
    return str(info.get("thumbnail") or "")


def _upload_date(info):
    raw = str(info.get("upload_date") or "")
    if len(raw) == 8 and raw.isdigit():
        return raw
    timestamp = info.get("timestamp") or info.get("release_timestamp")
    if timestamp:
        return datetime.fromtimestamp(float(timestamp), tz=timezone.utc).strftime("%Y%m%d")
    return ""


def inspect_media(url):
    options = _base_options()
    options.update(
        {
            "format": (
                "bestaudio[ext=m4a]/bestaudio[ext=webm]/"
                "bestaudio/best[acodec!=none]"
            ),
            "skip_download": True,
        }
    )
    info, _ydl = _extract_with_youtube_fallback(
        url,
        options,
        download=False,
        accept_info=lambda value: bool(_preview_stream_format(
            next(
                (entry for entry in (value or {}).get("entries", []) if entry),
                value or {},
            ),
        )),
    )
    if info and info.get("entries"):
        info = next((entry for entry in info["entries"] if entry), None)
    if not info:
        raise RuntimeError("El proveedor no devolvió información del audio")
    return _media_json(info)


def inspect_playlist(url):
    """Read public playlist metadata without resolving or downloading audio."""
    options = _base_options()
    options.pop("playlist_items", None)
    options.update(
        {
            "skip_download": True,
            "extract_flat": "in_playlist",
            "noplaylist": False,
        }
    )
    info, _ydl = _extract_with_youtube_fallback(url, options, download=False)
    if not info:
        raise RuntimeError("YouTube no devolvió información de la playlist")

    entries = []
    for entry in info.get("entries") or []:
        if not entry:
            continue
        video_id = str(entry.get("id") or "")
        raw_url = str(
            entry.get("webpage_url")
            or entry.get("original_url")
            or entry.get("url")
            or ""
        )
        if not video_id and raw_url.startswith("https://"):
            video_id = str(urlparse(raw_url).query.split("v=", 1)[-1].split("&", 1)[0])
        webpage_url = raw_url
        if not webpage_url.startswith("https://") and video_id:
            webpage_url = f"https://www.youtube.com/watch?v={video_id}"
        if not webpage_url.startswith("https://"):
            continue
        entries.append(
            {
                "id": video_id or webpage_url,
                "title": str(entry.get("title") or ""),
                "artist": str(
                    entry.get("artist")
                    or entry.get("artists")
                    or entry.get("channel")
                    or entry.get("uploader")
                    or ""
                ),
                "album": str(entry.get("album") or ""),
                "durationMs": int(float(entry.get("duration") or 0) * 1000),
                "thumbnail": _best_thumbnail(entry),
                "webpageUrl": webpage_url,
            }
        )
    return json.dumps(
        {
            "id": str(info.get("id") or ""),
            "title": str(info.get("title") or ""),
            "description": str(info.get("description") or ""),
            "thumbnail": _best_thumbnail(info),
            "webpageUrl": str(info.get("webpage_url") or info.get("original_url") or url),
            "entries": entries,
            "totalTracks": int(info.get("playlist_count") or len(entries)),
        },
        ensure_ascii=False,
    )


def _preview_stream_url(info):
    selected = _preview_stream_format(info)
    return str(selected.get("url") or "") if selected else ""


def _preview_stream_format(info):
    requested = info.get("requested_formats") or []
    audio_formats = [
        item
        for item in requested
        if item.get("url") and item.get("acodec") not in (None, "none")
    ]
    if audio_formats:
        return audio_formats[0]
    if info.get("url") and info.get("acodec") not in (None, "none"):
        return info
    for item in info.get("formats") or []:
        if item.get("url") and item.get("acodec") not in (None, "none"):
            return item
    return None


def _preview_mime_type(format_info):
    if not format_info:
        return ""
    explicit = str(
        format_info.get("mime_type")
        or format_info.get("mime")
        or ""
    ).split(";", 1)[0].strip().lower()
    if explicit.startswith("audio/"):
        return explicit
    if explicit.startswith("video/") and "mp4" in explicit:
        return "video/mp4"
    return {
        "aac": "audio/aac",
        "flac": "audio/flac",
        "m4a": "audio/mp4",
        "mp4": "video/mp4",
        "mp3": "audio/mpeg",
        "oga": "audio/ogg",
        "ogg": "audio/ogg",
        "opus": "audio/opus",
        "wav": "audio/wav",
        "webm": "audio/webm",
    }.get(str(format_info.get("ext") or "").lower(), "")


def _preview_http_headers(*format_infos):
    headers = {}
    for format_info in format_infos:
        if not isinstance(format_info, dict):
            continue
        raw_headers = format_info.get("http_headers") or {}
        if not isinstance(raw_headers, dict):
            continue
        for raw_name, raw_value in raw_headers.items():
            name = PREVIEW_HEADER_NAMES.get(str(raw_name).lower())
            value = str(raw_value or "").strip()
            if name and value and len(value) <= 512:
                headers[name] = value
    return headers


def preview_audio(url):
    options = _base_options()
    options.update(
        {
            "format": (
                "bestaudio[ext=m4a]/bestaudio[ext=webm]/"
                "bestaudio/best[acodec!=none]"
            ),
            "skip_download": True,
        }
    )
    info, _ydl = _extract_with_youtube_fallback(
        url,
        options,
        download=False,
        accept_info=lambda value: bool(_preview_stream_format(
            next(
                (entry for entry in (value or {}).get("entries", []) if entry),
                value or {},
            ),
        )),
    )
    if info and info.get("entries"):
        info = next((entry for entry in info["entries"] if entry), None)
    if not info:
        raise RuntimeError("No se pudo resolver un adelanto de audio")
    stream_format = _preview_stream_format(info)
    stream_url = str(stream_format.get("url") or "") if stream_format else ""
    if not stream_url or urlparse(stream_url).scheme.lower() != "https":
        raise RuntimeError("El proveedor no devolvió una pista HTTPS para el adelanto")
    payload = json.loads(_media_json(info))
    payload["streamUrl"] = stream_url
    payload["mimeType"] = _preview_mime_type(stream_format)
    payload["httpHeaders"] = _preview_http_headers(stream_format, info)
    return json.dumps(payload, ensure_ascii=False)


def search_youtube(query, page=0, page_size=10):
    clean_query = str(query or "").strip()
    if len(clean_query) < 3:
        raise ValueError("La búsqueda requiere al menos 3 caracteres")
    page = max(0, int(page))
    page_size = min(20, max(1, int(page_size)))
    start = page * page_size
    end = start + page_size

    options = _base_options()
    options.pop("playlist_items", None)
    options.update(
        {
            "skip_download": True,
            "extract_flat": "in_playlist",
            "noplaylist": False,
        }
    )
    info, _ydl = _extract_with_youtube_fallback(
        f"ytsearch{end + 1}:{clean_query}",
        options,
        download=False,
    )
    entries = [entry for entry in (info or {}).get("entries", []) if entry]
    selected = entries[start:end]
    items = []
    seen_ids = set()
    for entry in selected:
        video_id = str(entry.get("id") or "")
        webpage_url = str(entry.get("webpage_url") or entry.get("original_url") or "")
        if not webpage_url.startswith("https://") and video_id:
            webpage_url = f"https://www.youtube.com/watch?v={video_id}"
        stable_id = video_id or webpage_url
        if not stable_id or stable_id in seen_ids:
            continue
        seen_ids.add(stable_id)
        items.append(
            {
                "id": stable_id,
                "title": str(entry.get("title") or "Sin título"),
                "channel": str(
                    entry.get("channel")
                    or entry.get("uploader")
                    or entry.get("channel_id")
                    or "Canal desconocido"
                ),
                "durationMs": int(float(entry.get("duration") or 0) * 1000),
                "thumbnail": _best_thumbnail(entry),
                "webpageUrl": webpage_url,
                "uploadDate": _upload_date(entry),
            }
        )
    return json.dumps(
        {
            "items": items,
            "page": page,
            "hasMore": len(entries) > end,
        },
        ensure_ascii=False,
    )


def download_audio(url, output_dir, callback):
    os.makedirs(output_dir, exist_ok=True)

    def progress_hook(data):
        if callback is not None and callback.isCancelled():
            raise RuntimeError("__POLENTITA_CANCELLED__")
        if callback is None:
            return
        status = str(data.get("status") or "")
        downloaded = int(data.get("downloaded_bytes") or 0)
        total = int(data.get("total_bytes") or data.get("total_bytes_estimate") or -1)
        speed = int(data.get("speed") or 0)
        callback.onProgress(status, downloaded, total, speed)

    options = _base_options()
    options.update(
        {
            "format": (
                "bestaudio[ext=m4a]/bestaudio[ext=webm]/"
                "bestaudio/best[acodec!=none]"
            ),
            "outtmpl": os.path.join(output_dir, "%(id).80s.%(ext)s"),
            "continuedl": True,
            "nopart": False,
            "overwrites": False,
            "progress_hooks": [progress_hook],
        }
    )

    info, ydl = _extract_with_youtube_fallback(url, options, download=True)
    if info and info.get("entries"):
        info = next((entry for entry in info["entries"] if entry), None)
    if not info:
        raise RuntimeError("No se pudo resolver un audio descargable")
    requested = info.get("requested_downloads") or []
    path = requested[0].get("filepath") if requested else None
    if not path:
        path = info.get("_filename") or ydl.prepare_filename(info)
    return _media_json(info, path)
