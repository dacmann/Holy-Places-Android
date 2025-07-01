package net.dacworld.android.holyplacesofthelord.model

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore
import java.time.LocalDate

@Entity(tableName = "temples")
data class Temple(
    @PrimaryKey
    @ColumnInfo(name = "temple_id")
    var id: String = "", // Changed to var, added default

    var name: String = "", // Changed to var, added default
    var address: String = "", // Changed to var, added default
    var snippet: String = "", // Changed to var, added default

    @ColumnInfo(name = "city_state")
    var cityState: String = "", // Changed to var, added default

    var country: String = "", // Changed to var, added default
    var phone: String = "", // Changed to var, added default (or consider making it String? = null if often empty)
    var latitude: Double = 0.0, // Added default
    var longitude: Double = 0.0, // Added default
    var order: Short = 0, // Added default

    @ColumnInfo(name = "announced_date")
    var announcedDate: LocalDate? = null,

    @ColumnInfo(name = "picture_url")
    var pictureUrl: String = "", // Changed to var, added default
    @ColumnInfo(name = "site_url")
    var siteUrl: String = "", // Changed to var, added default

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

    // equals() and hashCode() should be fine as they are, but since many fields became 'var',
    // data class generated ones would reflect that.
    // If you keep your custom equals/hashCode, ensure they correctly handle the defaults
    // or how you define equality for your Temples.
    // For Room, primary key `id` is the main concern for updates/diffing.

    // Your existing equals and hashCode methods:
    // It's generally recommended to let data classes auto-generate equals() and hashCode()
    // unless you have very specific needs. If you change properties from val to var,
    // the auto-generated ones would be more appropriate.
    // However, if your custom ones are deliberately checking all these fields for equality
    // even with default values, you can keep them.
    // For now, I'll leave your custom implementations.

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
        if (pictureData != null) {
            if (other.pictureData == null) return false
            if (!pictureData.contentEquals(other.pictureData)) return false
        } else if (other.pictureData != null) return false
        // distance is @Ignored, so typically not part of equals/hashCode for persisted entities

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + snippet.hashCode()
        result = 31 * result + cityState.hashCode()
        result = 31 * result + country.hashCode()
        result = 31 * result + phone.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + order.hashCode()
        result = 31 * result + (announcedDate?.hashCode() ?: 0)
        result = 31 * result + pictureUrl.hashCode()
        result = 31 * result + siteUrl.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (infoUrl?.hashCode() ?: 0)
        result = 31 * result + (sqFt ?: 0)
        result = 31 * result + (fhCode?.hashCode() ?: 0)
        result = 31 * result + (pictureData?.contentHashCode() ?: 0)
        return result
    }
}