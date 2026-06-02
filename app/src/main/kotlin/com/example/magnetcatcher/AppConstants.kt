package com.example.magnetcatcher

object AppConstants {
    const val TAG = "MagnetCatcher"
    const val XTUNNEL_PACKAGE = "com.jxiouytlzb.pikjfb"
    const val BASE_URL = "https://sehuatang.org"
    const val DIRECT_URL = "https://plwt.kpqq4.com"
    const val FORUM_INDEX_API = "$BASE_URL/api/mobile/index.php?module=forumindex&version=4"
    const val FORUM_OPTIONS_PREF = "forum_options_json"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36"

    const val CONNECT_TIMEOUT_MS = 25_000
    const val READ_TIMEOUT_MS = 45_000
    const val PROXY_CONNECT_TIMEOUT_MS = 12_000
    const val PROXY_READ_TIMEOUT_MS = 30_000
    const val IMAGE_READ_TIMEOUT_MS = 25_000
    const val IMAGE_PROXY_READ_TIMEOUT_MS = 18_000
    const val XTUNNEL_PROXY_HEALTH_TIMEOUT_MS = 700
    const val XTUNNEL_FAILURE_COOLDOWN_MS = 2L * 60L * 1000L
    const val DEFAULT_PROXY_PORT = 40635
    const val EXPORT_BATCH_SIZE = 50
    const val MAX_THREADS_PER_PAGE = 40
    const val XTUNNEL_READY_ATTEMPTS = 60
    const val XTUNNEL_RESTART_READY_ATTEMPTS = 30
    const val THUMBNAIL_WIDTH_DP = 112
    const val THUMBNAIL_HEIGHT_DP = 84
}
