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

// Generic adapter that can take a list of any objects and a way to display them
class CustomSpinnerAdapter<T>(
    context: Context,
    private val resource: Int, // layout for the item itself (e.g., spinner_item_custom)
    private val dropdownResource: Int, // layout for dropdown items (e.g., spinner_dropdown_item_custom)
    private val items: List<T>,
    private val displayMapper: (T) -> String, // Function to get display string from T
    private val colorMapper: ((T) -> Int?)? = null // Optional function to get color resource ID for T
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
            view.text = displayMapper(item)
            colorMapper?.let { mapper ->
                mapper(item)?.let { colorRes ->
                    view.setTextColor(ContextCompat.getColor(context, colorRes))
                } ?: view.setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.design_default_color_primary)) // Default if no specific color
            }
        }
        return view
    }
}
