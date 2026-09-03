// CustomSpinnerAdapter.kt (in your ui.options or ui.adapters package)
package net.dacworld.android.holyplacesofthelord.ui.places // Or wherever you place it

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.color
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.model.PlaceFilter // For custom filter colors
import net.dacworld.android.holyplacesofthelord.util.placeTypeSymbolTitle

// Generic adapter that can take a list of any objects and a way to display them
class CustomSpinnerAdapter<T>(
    context: Context,
    private val resource: Int, // layout for the item itself (e.g., spinner_item_custom)
    private val dropdownResource: Int, // layout for dropdown items (e.g., spinner_dropdown_item_custom)
    private val items: List<T>,
    private val displayMapper: (T) -> String, // Function to get display string from T
    private val colorMapper: ((T) -> Int?)? = null, // Optional function to get color resource ID for T
    // Optional function to get the place type code for T, used to show a type symbol
    private val placeTypeMapper: ((T) -> String?)? = null
) : ArrayAdapter<T>(context, resource, items) {

    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(layoutInflater, position, convertView, parent, resource)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(layoutInflater, position, convertView, parent, dropdownResource)
    }

    private fun createViewFromResource(
        inflater: LayoutInflater,
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        resourceId: Int
    ): View {
        val view: TextView = convertView as? TextView
            ?: inflater.inflate(resourceId, parent, false) as TextView

        val item = getItem(position)
        if (item != null) {
            var textColor = view.currentTextColor
            colorMapper?.let { mapper ->
                textColor = mapper(item)?.let { colorRes ->
                    ContextCompat.getColor(context, colorRes)
                } ?: ContextCompat.getColor(context, com.google.android.material.R.color.design_default_color_primary) // Default if no specific color
                view.setTextColor(textColor)
            }
            // The rows are center-aligned, so the symbol is prefixed inline rather than
            // set as a compound drawable (which would sit at the view's edge).
            val title = displayMapper(item)
            view.text = placeTypeMapper?.let { mapper ->
                placeTypeSymbolTitle(context, title, mapper(item), textColor)
            } ?: title
        }
        return view
    }
}
