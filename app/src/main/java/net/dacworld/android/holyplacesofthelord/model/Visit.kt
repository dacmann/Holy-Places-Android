package net.dacworld.android.holyplacesofthelord.model // Or your chosen package

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date // For the dateVisited field - will need a TypeConverter

// It's good practice to define constants for table and column names
object VisitContract {
    const val TABLE_NAME = "visits"
    const val COLUMN_ID = "visit_id" // Primary key for Visit
    const val COLUMN_PLACE_ID = "place_id" // Foreign key to Temple
    const val COLUMN_BAPTISMS = "baptisms"
    const val COLUMN_COMMENTS = "comments"
    const val COLUMN_CONFIRMATIONS = "confirmations"
    const val COLUMN_DATE_VISITED = "date_visited"
    const val COLUMN_ENDOWMENTS = "endowments"
    const val COLUMN_HOLY_PLACE_NAME = "holy_place_name" // Storing the name for convenience
    const val COLUMN_INITIATORIES = "initiatories"
    const val COLUMN_IS_FAVORITE = "is_favorite"
    const val COLUMN_PICTURE_DATA = "visit_picture_data"
    const val COLUMN_SEALINGS = "sealings"
    const val COLUMN_SHIFT_HRS = "shift_hrs"
    const val COLUMN_VISIT_TYPE = "visit_type" // To avoid conflict with Temple's 'type'
    const val COLUMN_YEAR = "year"
}

@Entity(
    tableName = VisitContract.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = Temple::class, // Assuming your Temple entity is named Temple
            parentColumns = [TempleContract.COLUMN_ID], // Assuming Temple has a TempleContract.COLUMN_ID for its PrimaryKey
            childColumns = [VisitContract.COLUMN_PLACE_ID],
            onDelete = ForeignKey.CASCADE // Or SET_NULL, RESTRICT, etc. depending on desired behavior
        )
    ],
    indices = [Index(value = [VisitContract.COLUMN_PLACE_ID])] // Index for faster lookups by placeID
)
// If you use Date type, you'll need a TypeConverter for it at the Database level
// or registered here with @TypeConverters(YourDateConverters::class)
data class Visit(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = VisitContract.COLUMN_ID)
    val id: Long = 0, // Auto-generating Long primary key for visits

    @ColumnInfo(name = VisitContract.COLUMN_PLACE_ID)
    val placeID: String, // Foreign key linking to Temple's ID

    @ColumnInfo(name = VisitContract.COLUMN_BAPTISMS)
    val baptisms: Short?, // Integer 16 is Kotlin Short, nullable if not always provided

    @ColumnInfo(name = VisitContract.COLUMN_COMMENTS)
    val comments: String?,

    @ColumnInfo(name = VisitContract.COLUMN_CONFIRMATIONS)
    val confirmations: Short?,

    @ColumnInfo(name = VisitContract.COLUMN_DATE_VISITED)
    val dateVisited: Date?, // java.util.Date - will require a TypeConverter

    @ColumnInfo(name = VisitContract.COLUMN_ENDOWMENTS)
    val endowments: Short?,

    @ColumnInfo(name = VisitContract.COLUMN_HOLY_PLACE_NAME)
    val holyPlaceName: String?, // Storing the name of the place for easy display with the visit

    @ColumnInfo(name = VisitContract.COLUMN_INITIATORIES)
    val initiatories: Short?,

    @ColumnInfo(name = VisitContract.COLUMN_IS_FAVORITE)
    val isFavorite: Boolean = false,

    @ColumnInfo(name = VisitContract.COLUMN_PICTURE_DATA, typeAffinity = ColumnInfo.BLOB)
    val picture: ByteArray?, // Binary Data

    @ColumnInfo(name = VisitContract.COLUMN_SEALINGS)
    val sealings: Short?,

    @ColumnInfo(name = VisitContract.COLUMN_SHIFT_HRS)
    val shiftHrs: Double?,

    @ColumnInfo(name = VisitContract.COLUMN_VISIT_TYPE)
    val type: String?, // e.g., T, H, A, C, V, etc."

    @ColumnInfo(name = VisitContract.COLUMN_YEAR)
    val year: String? // Or Int? if it's always a numerical year
) {
    // Override equals and hashCode if you include ByteArray and need content equality
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Visit

        if (id != other.id) return false
        if (placeID != other.placeID) return false
        if (baptisms != other.baptisms) return false
        if (comments != other.comments) return false
        if (confirmations != other.confirmations) return false
        if (dateVisited != other.dateVisited) return false // Date comparison works
        if (endowments != other.endowments) return false
        if (holyPlaceName != other.holyPlaceName) return false
        if (initiatories != other.initiatories) return false
        if (isFavorite != other.isFavorite) return false
        if (picture != null) {
            if (other.picture == null) return false
            if (!picture.contentEquals(other.picture)) return false
        } else if (other.picture != null) return false
        if (sealings != other.sealings) return false
        if (shiftHrs != other.shiftHrs) return false
        if (type != other.type) return false
        if (year != other.year) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + placeID.hashCode()
        result = 31 * result + (baptisms ?: 0)
        result = 31 * result + (comments?.hashCode() ?: 0)
        result = 31 * result + (confirmations ?: 0)
        result = 31 * result + (dateVisited?.hashCode() ?: 0)
        result = 31 * result + (endowments ?: 0)
        result = 31 * result + (holyPlaceName?.hashCode() ?: 0)
        result = 31 * result + (initiatories ?: 0)
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + (picture?.contentHashCode() ?: 0)
        result = 31 * result + (sealings ?: 0)
        result = 31 * result + (shiftHrs?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        return result
    }
}

// TempleContract object
 object TempleContract {
     const val TABLE_NAME = "temples"
     const val COLUMN_ID = "temple_id" // Ensure this matches the PrimaryKey column name in Temple
     // ... other temple column names
 }