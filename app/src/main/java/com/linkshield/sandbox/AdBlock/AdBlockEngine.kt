package com.linkshield.sandbox.adblock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AdBlockEngine"

class AdBlockEngine private constructor() {

    private val blockedDomains   = ConcurrentHashMap.newKeySet<String>()
    private val whitelistDomains = ConcurrentHashMap.newKeySet<String>()
    private val isInitialized    = AtomicBoolean(false)

    companion object {
        @Volatile private var instance: AdBlockEngine? = null

        fun getInstance(): AdBlockEngine =
            instance ?: synchronized(this) {
                instance ?: AdBlockEngine().also { instance = it }
            }
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized.getAndSet(true)) return@withContext

        blockedDomains.addAll(BUILTIN_AD_DOMAINS)
        whitelistDomains.addAll(BUILTIN_WHITELIST)

        runCatching {
            context.assets.open("adblock_rules.txt").bufferedReader().useLines { lines ->
                var count = 0
                lines.forEach { line ->
                    val rule = parseEasyListLine(line)
                    if (rule != null) { blockedDomains.add(rule); count++ }
                }
                Log.d(TAG, "Loaded $count custom rules from assets")
            }
        }.onFailure {
            Log.d(TAG, "No custom adblock_rules.txt in assets — using built-in rules only")
        }

        Log.d(TAG, "AdBlockEngine ready: ${blockedDomains.size} domains blocked")
    }

    fun shouldBlock(url: String): Boolean {
        if (!isInitialized.get()) return false

        val lower   = url.lowercase()
        val host    = extractHost(lower) ?: return false

        if (whitelistDomains.contains(host)) return false
        if (blockedDomains.contains(host)) return true

        val parts = host.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.drop(i).joinToString(".")
            if (whitelistDomains.contains(parent)) return false
            if (blockedDomains.contains(parent)) return true
        }

        if (URL_TRACKER_PATTERNS.any { lower.contains(it) }) return true

        return false
    }

    fun addBlockRule(domain: String)     { blockedDomains.add(domain.lowercase().trim()) }
    fun removeBlockRule(domain: String)  { blockedDomains.remove(domain.lowercase().trim()) }
    fun addWhitelistRule(domain: String) { whitelistDomains.add(domain.lowercase().trim()) }
    fun getBlockedCount(): Int           = blockedDomains.size

    private fun extractHost(url: String): String? {
        return try {
            val withScheme = if (url.startsWith("http")) url else "https://$url"
            val uri        = java.net.URI(withScheme)
            uri.host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) { null }
    }

    private fun parseEasyListLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty())               return null
        if (trimmed.startsWith("!"))         return null
        if (trimmed.startsWith("#"))         return null
        if (trimmed.startsWith("@@"))        return null
        if (trimmed.contains("##"))          return null
        if (trimmed.contains("#?#"))         return null
        if (!trimmed.startsWith("||"))       return null

        return trimmed
            .removePrefix("||")
            .substringBefore("^")
            .substringBefore("/")
            .substringBefore("$")
            .lowercase()
            .trim()
            .takeIf { it.isNotEmpty() && it.contains(".") }
    }
}
private val URL_TRACKER_PATTERNS = listOf(
    "/pixel.gif", "/pixel.png", "/beacon.",
    "tracking/pixel", "/analytics/", "/collect?",
    "utm_source=", "utm_campaign=", "utm_medium=",
    "/pagead/", "/doubleclick/", "/adserving/",
    "googletagmanager.com/gtm.js", "/gtag/js",
    "bat.bing.com", "click.simpleads",
    "youtube.com/api/stats/ads",
    "youtube.com/pagead",
    "youtube.com/ptracking",
    "youtube.com/youtubei/v1/log_event",
    "youtube.com/youtubei/v1/feedback",
    "googlevideo.com/videoplayback?*adformat=",
    "googlevideo.com/videoplayback?*oad=",
    "googlevideo.com/videoplayback?*ctier=",
    "googlevideo.com/videoplayback?*afs=",
    "&adformat=", "&oad=", "&ctier=L", "&afs="
)

private val BUILTIN_WHITELIST = setOf(
    "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
    "accounts.google.com", "fonts.googleapis.com", "fonts.gstatic.com",
    "cdnjs.cloudflare.com", "cdn.jsdelivr.net", "unpkg.com",
    "ajax.googleapis.com", "code.jquery.com",
    "youtube.com", "youtu.be", "ytimg.com", "yt3.ggpht.com",
    "googlevideo.com", "youtubei.googleapis.com",
    "play.google.com", "apple.com",
    "instagram.com", "cdninstagram.com",
    "facebook.com", "fbcdn.net",
    "twitter.com", "twimg.com",
    "tiktok.com", "tiktokcdn.com",
    "github.com", "githubusercontent.com",
    "cloudflare.com", "cloudflareinsights.com"
)

private val BUILTIN_AD_DOMAINS = setOf(
    "googleadservices.com", "googlesyndication.com", "doubleclick.net",
    "adservice.google.com", "pagead2.googlesyndication.com",
    "adwords.google.com", "google-analytics.com", "googletagmanager.com",
    "googletagservices.com", "googleoptimize.com", "googletag.com",
    "adsense.google.com", "adservice.google.co.uk", "g.doubleclick.net",
    "stats.g.doubleclick.net", "cm.g.doubleclick.net",
    "tpc.googlesyndication.com", "pagead.google.com",
    "afs.googlesyndication.com", "ad.doubleclick.net",
    "youtube.com/api/stats/ads",
    "youtube.com/pagead",
    "youtube.com/ptracking",
    "youtube.com/youtubei/v1/log_event",
    "youtube.com/youtubei/v1/feedback",
    "s.youtube.com",
    "www.youtube.com/api/stats",
    "youtube.com/get_video_info",
    "connect.facebook.net", "graph.facebook.com", "an.facebook.com",
    "staticxx.facebook.com", "pixel.facebook.com",
    "analytics.facebook.com", "advertise.facebook.com",
    "amazon-adsystem.com", "aax.amazon-adsystem.com",
    "fls-na.amazon.com", "ir-na.amazon-adsystem.com",
    "s.amazon-adsystem.com", "z-na.amazon-adsystem.com",
    "advertising.amazon.com", "adsystem.amazon.com",
    "bat.bing.com", "ads.microsoft.com", "msads.net",
    "bingads.microsoft.com", "adnxs.com", "appnexus.com",
    "ads.twitter.com", "analytics.twitter.com", "ads-api.twitter.com",
    "platform.twitter.com", "syndication.twitter.com",
    "analytics.tiktok.com", "ads.tiktok.com", "business.tiktok.com",
    "log.bytefcdn.com", "bytefcdn.com",
    "taboola.com", "trc.taboola.com", "cdn.taboola.com",
    "outbrain.com", "widgets.outbrain.com", "outbrain.org",
    "criteo.com", "static.criteo.net", "dis.criteo.com",
    "pubmatic.com", "ads.pubmatic.com",
    "rubiconproject.com", "fastlane.rubiconproject.com",
    "openx.net", "delivery.openx.net",
    "adform.net", "track.adform.net",
    "casalemedia.com", "sas.casalemedia.com",
    "media.net", "contextual.media.net",
    "adtech.de", "adtechus.com",
    "spotxchange.com", "spotx.tv",
    "smartadserver.com", "prg.smartadserver.com",
    "mathtag.com", "sync.mathtag.com",
    "advertising.com", "o.advertising.com",
    "yieldmanager.com", "yieldmanager.net",
    "adnxs.com", "ib.adnxs.com",
    "lijit.com", "rp.lijit.com",
    "sharethrough.com", "native.sharethrough.com",
    "tribalfusion.com", "ads.tribalfusion.com",
    "trafficjunky.net", "trafficjunky.com",
    "exelator.com", "loadm.exelator.com",
    "yad.yandex.ru", "an.yandex.ru", "mc.yandex.ru",
    "adriver.ru", "doubleclick.ru",
    "mixpanel.com", "api.mixpanel.com",
    "segment.com", "cdn.segment.com", "api.segment.io",
    "amplitude.com", "api.amplitude.com", "cdn.amplitude.com",
    "hotjar.com", "script.hotjar.com", "static.hotjar.com",
    "clarity.ms", "c.clarity.ms",
    "fullstory.com", "edge.fullstory.com",
    "heap.io", "cdn.heapanalytics.com",
    "mouseflow.com", "cdn.mouseflow.com",
    "clicktale.net", "cdn.clicktale.net",
    "logrocket.com", "cdn.logrocket.io",
    "newrelic.com", "js-agent.newrelic.com", "bam.nr-data.net",
    "bugsnag.com", "notify.bugsnag.com",
    "sentry.io", "browser.sentry-cdn.com",
    "crazyegg.com", "script.crazyegg.com",
    "optimizely.com", "cdn.optimizely.com",
    "vwo.com", "cdn.vwo.com",
    "quantserve.com", "edge.quantserve.com",
    "comscore.com", "sb.scorecardresearch.com", "scorecardresearch.com",
    "chartbeat.com", "static.chartbeat.com", "ping.chartbeat.net",
    "parsely.com", "srv.pixel.parsely.com",
    "nielsen.com", "cdn-gl.imrworldwide.com",
    "statcounter.com", "c.statcounter.com",
    "pagefair.com", "api.pagefair.com",
    "blockthrough.com", "btloader.com",
    "admiral.com", "getadmiral.com",
    "fuckadblock.js", "adblockanalytics.com",
    "shareasale.com", "www.shareasale.com",
    "linksynergy.com", "click.linksynergy.com",
    "commission-junction.com", "cj.com",
    "zanox.com", "partner.zanox.com",
    "tradedoubler.com", "impactradius.com", "impact.com",
    "awin.com", "awin1.com",
    "dpbolvw.net", "tkqlhce.com",
    "addthis.com", "s7.addthis.com",
    "sharethis.com", "platform.sharethis.com",
    "moatads.com", "z.moatads.com",
    "adsafeprotected.com", "pixel.adsafeprotected.com",
    "rlcdn.com", "sync.rlcdn.com",
    "bluekai.com", "tags.bluekai.com",
    "agkn.com", "idio.co",
    "everesttech.net", "tealiumiq.com", "tags.tiqcdn.com",
    "bounceexchange.com", "bounceexchange.net",
    "parsely.com", "pixel.parsely.com",
    "krxd.net", "beacon.krxd.net",
    "turn.com", "r.turn.com",
    "demdex.net", "cm.everesttech.net",
    "liveramp.com", "idsync.rlcdn.com",
    "id5-sync.com", "id5.io",
    "springserve.com", "cdn.springserve.com",
    "freewheel.tv", "1point0.freewheel.tv",
    "brightroll.com", "ads.brightroll.com",
    "teads.tv", "cdn.teads.tv",
    "unrulymedia.com", "targeting.unrulymedia.com",
    "jwpltx.com", "cdn.jwplayer.com/",
    "onesignal.com", "cdn.onesignal.com",
    "pushwoosh.com", "cp.pushwoosh.com",
    "pushengage.com", "cdn.pushengage.com",
    "izooto.com", "cdn.izooto.com",
    "pushcrew.com", "cdn.vizury.com",
    "firstload.com", "instantads.com",
    "youradexchange.com", "adblade.com",
    "undertone.com", "ads.undertone.com",
    "revsci.net", "secure-ads.imrworldwide.com",
    "marketo.com", "munchkin.marketo.com",
    "hubspot.com", "js.hs-scripts.com", "js.hs-analytics.net",
    "eloqua.com", "img.en25.com",
    "actionsend.com", "servedby.flashtalking.com",
    "netseer.com", "ads.netseer.com",
    "adpicker.com", "ads.adpicker.com",
    "propellerads.com", "syn.propellerads.com",
    "popads.net", "popcash.net",
    "evadav.com", "hilltopads.net",
    "richpush.co", "propeller-ads.com",
    "daum.net", "ad.daum.net",
    "naver.com", "cr.shopping.naver.com",
    "yotpo.com", "api.yotpo.com",
    "sailthru.com", "ak.sail-horizon.com",
    "dotmailer.com", "r.dotmailer-surveys.com",
    "responsys.net", "links.email.responsys.net"
)
