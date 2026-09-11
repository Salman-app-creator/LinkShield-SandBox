package com.linkshield.sandbox.ui.browser

import android.webkit.WebView

/**
 * ReaderModeManager
 * 
 * Website ka main text nikal kar clean reading experience deta hai.
 * Ads, sidebar, navigation sab hata deta hai.
 */
object ReaderModeManager {

    private val READER_MODE_JS = """
        (function() {
            // ── Remove clutter elements ──
            var removeSelectors = [
                'script', 'style', 'nav', 'header', 'footer', 'aside',
                'iframe', 'noscript', 'form', 'button',
                '[role="navigation"]', '[role="banner"]', '[role="complementary"]',
                '[class*="ad-"]', '[class*="ads-"]', '[class*="advert"]',
                '[class*="sidebar"]', '[class*="menu"]', '[class*="nav-"]',
                '[class*="header"]', '[class*="footer"]', '[class*="popup"]',
                '[class*="modal"]', '[class*="cookie"]', '[class*="banner"]',
                '[id*="ad-"]', '[id*="ads-"]', '[id*="sidebar"]',
                '[id*="menu"]', '[id*="nav-"]', '[id*="header"]', '[id*="footer"]'
            ];
            
            removeSelectors.forEach(function(sel) {
                try {
                    document.querySelectorAll(sel).forEach(function(el) {
                        el.remove();
                    });
                } catch(e) {}
            });
            
            // ── Try to find main content ──
            var mainContent = null;
            var candidates = [
                'article', 'main', '[role="main"]',
                '.post-content', '.article-content', '.entry-content',
                '.story-body', '.article-body', '.post-body',
                '#content', '#main-content', '#article'
            ];
            
            for (var i = 0; i < candidates.length; i++) {
                var el = document.querySelector(candidates[i]);
                if (el && el.innerText && el.innerText.length > 500) {
                    mainContent = el;
                    break;
                }
            }
            
            // ── Fallback: find largest text block ──
            if (!mainContent) {
                var allDivs = document.querySelectorAll('div, section, article');
                var maxLength = 0;
                allDivs.forEach(function(div) {
                    var len = div.innerText ? div.innerText.length : 0;
                    if (len > maxLength && len > 500) {
                        maxLength = len;
                        mainContent = div;
                    }
                });
            }
            
            if (!mainContent) return 'NO_CONTENT';
            
            // ── Get title ──
            var title = document.title || '';
            var h1 = document.querySelector('h1');
            if (h1 && h1.innerText) title = h1.innerText;
            
            // ── Build clean HTML ──
            var cleanHtml = '<!DOCTYPE html><html><head>' +
                '<meta name="viewport" content="width=device-width, initial-scale=1">' +
                '<style>' +
                'body {' +
                '  font-family: Georgia, serif;' +
                '  font-size: 18px;' +
                '  line-height: 1.7;' +
                '  max-width: 720px;' +
                '  margin: 0 auto;' +
                '  padding: 20px;' +
                '  color: #1a1a1a;' +
                '  background: #ffffff;' +
                '}' +
                'h1 { font-size: 28px; line-height: 1.3; margin-bottom: 24px; color: #000; }' +
                'h2 { font-size: 22px; margin-top: 32px; margin-bottom: 16px; color: #000; }' +
                'h3 { font-size: 19px; margin-top: 24px; margin-bottom: 12px; color: #000; }' +
                'p { margin-bottom: 18px; }' +
                'img { max-width: 100%; height: auto; display: block; margin: 20px auto; border-radius: 8px; }' +
                'a { color: #0066cc; text-decoration: none; }' +
                'blockquote { border-left: 4px solid #ddd; padding-left: 16px; margin: 20px 0; color: #555; font-style: italic; }' +
                'pre, code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; font-family: monospace; font-size: 14px; }' +
                'pre { padding: 16px; overflow-x: auto; }' +
                '</style></head><body>' +
                '<h1>' + title + '</h1>' +
                mainContent.innerHTML +
                '</body></html>';
            
            // ── Replace page content ──
            document.open();
            document.write(cleanHtml);
            document.close();
            
            return 'SUCCESS';
        })();
    """.trimIndent()

    /**
     * Reader mode enable karein.
     */
    fun enable(webView: WebView) {
        webView.evaluateJavascript(READER_MODE_JS) { result ->
            // Result: "SUCCESS" ya "NO_CONTENT"
        }
    }

    /**
     * Reader mode disable karein (page reload karein).
     */
    fun disable(webView: WebView) {
        webView.reload()
    }
}
