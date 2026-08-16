package com.linkshield.sandbox.ui.grabber

import android.webkit.WebView

class WebSnifferManager {

    private val mediaExtensions = setOf(
        ".mp4",
        ".m3u8",
        ".mp3",
        ".webm",
        ".mkv",
        ".mov",
        ".m4a",
        ".ogg",
        ".aac",
        ".wav",
        ".mpd",
        ".ts"
    )

    private val mediaKeywords = setOf(
        "videoplayback",
        "manifest",
        "playlist",
        "stream",
        "media",
        "audio",
        "video"
    )

    fun isMediaUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()

        if (url.isBlank()) {
            return false
        }

        if (url.startsWith("blob:", true)) {
            return true
        }

        val cleanUrl = url
            .substringBefore("?")
            .substringBefore("#")
            .lowercase()

        if (mediaExtensions.any { cleanUrl.endsWith(it) }) {
            return true
        }

        val lowerUrl = url.lowercase()

        return mediaKeywords.any {
            lowerUrl.contains(it)
        }
    }

    fun inject(
        webView: WebView?,
        title: String,
        pageUrl: String,
        callback: (
            url: String,
            title: String,
            pageUrl: String
        ) -> Unit
    ) {
        webView ?: return

        webView.evaluateJavascript(
            buildInjectionScript(),
            null
        )
    }

    private fun buildInjectionScript(): String {
        return """
            (function() {
                if (window.__linkShieldSniffer) {
                    return;
                }

                window.__linkShieldSniffer = true;

                function absoluteUrl(value) {
                    try {
                        return new URL(
                            value,
                            location.href
                        ).href;
                    } catch (e) {
                        return value;
                    }
                }

                function isMedia(value) {
                    if (!value ||
                        typeof value !== 'string') {
                        return false;
                    }

                    var url =
                        value.toLowerCase();

                    return (
                        url.indexOf('.mp4') >= 0 ||
                        url.indexOf('.m3u8') >= 0 ||
                        url.indexOf('.mp3') >= 0 ||
                        url.indexOf('.webm') >= 0 ||
                        url.indexOf('.mkv') >= 0 ||
                        url.indexOf('.mov') >= 0 ||
                        url.indexOf('.m4a') >= 0 ||
                        url.indexOf('.mpd') >= 0 ||
                        url.indexOf('.ts') >= 0 ||
                        url.indexOf('videoplayback') >= 0 ||
                        url.indexOf('manifest') >= 0 ||
                        url.indexOf('playlist') >= 0 ||
                        url.indexOf('blob:') === 0
                    );
                }

                function report(value) {
                    try {
                        if (!value) {
                            return;
                        }

                        var url =
                            absoluteUrl(value);

                        if (!isMedia(url)) {
                            return;
                        }

                        if (
                            window.LinkShieldBridge &&
                            window.LinkShieldBridge
                                .onVideoFound
                        ) {
                            window.LinkShieldBridge
                                .onVideoFound(
                                    url,
                                    document.title || '',
                                    location.href
                                );
                        }
                    } catch (e) {
                    }
                }

                var originalOpen =
                    XMLHttpRequest.prototype.open;

                XMLHttpRequest.prototype.open =
                    function(method, url) {
                        report(url);

                        return originalOpen.apply(
                            this,
                            arguments
                        );
                    };

                if (window.fetch) {
                    var originalFetch =
                        window.fetch;

                    window.fetch =
                        function(input, init) {
                            try {
                                var url =
                                    typeof input === 'string'
                                        ? input
                                        : (
                                            input &&
                                            input.url
                                                ? input.url
                                                : ''
                                        );

                                report(url);
                            } catch (e) {
                            }

                            return originalFetch.apply(
                                this,
                                arguments
                            );
                        };
                }

                function scanElement(element) {
                    if (!element) {
                        return;
                    }

                    try {
                        report(
                            element.currentSrc
                        );

                        report(
                            element.src
                        );

                        if (
                            element.querySelectorAll
                        ) {
                            var sources =
                                element.querySelectorAll(
                                    'source'
                                );

                            for (
                                var i = 0;
                                i < sources.length;
                                i++
                            ) {
                                report(
                                    sources[i].src
                                );
                            }
                        }
                    } catch (e) {
                    }
                }
                                function scanPage() {
                    try {
                        var elements =
                            document.querySelectorAll(
                                'video,audio'
                            );

                        for (
                            var i = 0;
                            i < elements.length;
                            i++
                        ) {
                            scanElement(
                                elements[i]
                            );
                        }
                    } catch (e) {
                    }
                }

                function startObserver() {
                    try {
                        if (!document.body) {
                            return;
                        }

                        var observer =
                            new MutationObserver(
                                function(mutations) {
                                    for (
                                        var i = 0;
                                        i < mutations.length;
                                        i++
                                    ) {
                                        var nodes =
                                            mutations[i]
                                                .addedNodes;

                                        for (
                                            var j = 0;
                                            j < nodes.length;
                                            j++
                                        ) {
                                            var node =
                                                nodes[j];

                                            scanElement(node);

                                            if (
                                                node &&
                                                node.querySelectorAll
                                            ) {
                                                var media =
                                                    node.querySelectorAll(
                                                        'video,audio'
                                                    );

                                                for (
                                                    var k = 0;
                                                    k < media.length;
                                                    k++
                                                ) {
                                                    scanElement(
                                                        media[k]
                                                    );
                                                }
                                            }
                                        }
                                    }
                                }
                            );

                        observer.observe(
                            document.body,
                            {
                                childList: true,
                                subtree: true
                            }
                        );
                    } catch (e) {
                    }

                    scanPage();
                }

                if (
                    document.readyState ===
                    'loading'
                ) {
                    document.addEventListener(
                        'DOMContentLoaded',
                        startObserver
                    );
                } else {
                    startObserver();
                }

                window.addEventListener(
                    'load',
                    scanPage
                );

                setInterval(
                    scanPage,
                    3000
                );
            })();
        """.trimIndent()
    }
}
