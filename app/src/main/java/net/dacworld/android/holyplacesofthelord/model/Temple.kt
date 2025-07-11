package net.dacworld.android.holyplacesofthelord.model

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore
import java.time.LocalDate
import java.util.Objects

@Entity(tableName = "temples")
data class Temple(
    @PrimaryKey
    @ColumnInfo(name = "temple_id")
    var id: String = "", // Changed to var, added default

    var name: String = "", // Changed to var, added default
    var address: String? = "", // Changed to var, added default
    var snippet: String = "", // Changed to var, added default

    @ColumnInfo(name = "city_state")
    var cityState: String = "", // Changed to var, added default

    var country: String = "", // Changed to var, added default
    var phone: String? = "", // Changed to var, added default (or consider making it String? = null if often empty)
    var latitude: Double = 0.0, // Added default
    var longitude: Double = 0.0, // Added default
    var order: Short = 0, // Added default

    @ColumnInfo(name = "announced_date")
    var announcedDate: LocalDate? = null,

    @ColumnInfo(name = "picture_url")
    var pictureUrl: String = "", // Changed to var, added default
    @ColumnInfo(name = "site_url")
    var siteUrl: String? = "", // Changed to var, added default

    var type: String = "", // Changed to var, added default

    @ColumnInfo(name = "info_url")
    var infoUrl: String? = null,

    @ColumnInfo(name = "sq_ft")
    var sqFt: Int? = null,

    @ColumnInfo(name = "fh_code")
    var fhCode: String? = null,

    @ColumnInfo(name = "picture_data", typeAffinity = ColumnInfo.BLOB)
    var pictureData: ByteArray? = null,

    @Ignore
    var hasLocalPictureData: Boolean = false,

    @Ignore
    var distance: Double? = null
) {
    // The calculateDistance methods remain the same

    fun calculateDistance(fromLat: Double, fromLon: Double, inKilometers: Boolean = true) {
        val templeLocation = Location("TempleLocationProvider").apply {
            latitude = this@Temple.latitude // Assumes latitude will be set
            longitude = this@Temple.longitude // Assumes longitude will be set
        }
        val fromPointLocation = Location("FromPointLocationProvider").apply {
            latitude = fromLat
            longitude = fromLon
        }
        var calculatedDistance = templeLocation.distanceTo(fromPointLocation).toDouble()
        if (inKilometers) {
            calculatedDistance /= 1000.0
        }
        this.distance = calculatedDistance
    }

    fun calculateDistance(fromLocation: Location?, inKilometers: Boolean = true) {
        fromLocation?.let {
            val templeLocation = Location("TempleLocationProvider").apply {
                latitude = this@Temple.latitude // Assumes latitude will be set
                longitude = this@Temple.longitude // Assumes longitude will be set
            }
            var calculatedDistance = templeLocation.distanceTo(it).toDouble()
            if (inKilometers) {
                calculatedDistance /= 1000.0
            }
            this.distance = calculatedDistance
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Temple

        if (id != other.id) return false
        if (name != other.name) return false
        if (address != other.address) return false
        if (snippet != other.snippet) return false
        if (cityState != other.cityState) return false
        if (country != other.country) return false
        if (phone != other.phone) return false
        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (order != other.order) return false
        if (announcedDate != other.announcedDate) return false
        if (pictureUrl != other.pictureUrl) return false
        if (siteUrl != other.siteUrl) return false
        if (type != other.type) return false
        if (infoUrl != other.infoUrl) return false
        if (sqFt != other.sqFt) return false
        if (fhCode != other.fhCode) return false
        // DO NOT compare pictureData
        // distance is @Ignored, so typically not part of equals/hashCode for persisted entities

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id,
            name,
            address,
            snippet,
            cityState,
            country,
            phone,
            latitude,
            longitude,
            order,
            announcedDate,
            pictureUrl, // Keep pictureUrl
            siteUrl,
            type,
            infoUrl,
            sqFt,
            fhCode
            // DO NOT include pictureData.contentHashCode() or pictureData itself
        )
    }
}