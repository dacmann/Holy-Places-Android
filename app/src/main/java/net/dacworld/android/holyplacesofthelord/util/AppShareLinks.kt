package net.dacworld.android.holyplacesofthelord.util

import android.net.Uri

object AppShareLinks {

    const val SHARE_TEXT = "Holy Places of the Lord - Temples and Historic Sites by Derek Cordon"

    val GOOGLE_PLAY_URL: Uri = Uri.parse(
        "https://play.google.com/store/apps/details?id=net.dacworld.android.holyplacesofthelord"
    )

    val APP_STORE_URL: Uri = Uri.parse(
        "https://apps.apple.com/us/app/holy-places-of-the-lord/id1200184537"
    )

    const val PROMO_PDF_ASSET = "HolyPlacesPromo.pdf"
    const val PROMO_PDF_CACHE_NAME = "HolyPlacesPromo.pdf"
}
