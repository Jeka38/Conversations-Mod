package eu.siacs.conversations.medialib.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.EditorFilterItemBinding
import eu.siacs.conversations.medialib.models.FilterItem

class FiltersAdapter(val context: Context, val filterItems: ArrayList<FilterItem>, val itemClick: (Int) -> Unit) :
    RecyclerView.Adapter<FiltersAdapter.ViewHolder>() {

    private var currentSelection = filterItems.first()
    private var strokeBackground = context.resources.getDrawable(R.drawable.stroke_background)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(filterItems[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.editor_filter_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filterItems.size

    fun getCurrentFilter() = currentSelection

    private fun setCurrentFilter(position: Int) {
        val filterItem = filterItems.getOrNull(position) ?: return
        if (currentSelection != filterItem) {
            currentSelection = filterItem
            notifyDataSetChanged()
            itemClick.invoke(position)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = EditorFilterItemBinding.bind(view)

        fun bindView(filterItem: FilterItem): View {
            itemView.apply {
                binding.editorFilterItemLabel.text = filterItem.filter.name
                binding.editorFilterItemThumbnail.setImageBitmap(filterItem.bitmap)
                binding.editorFilterItemThumbnail.background = if (getCurrentFilter() == filterItem) {
                    strokeBackground
                } else {
                    null
                }

                setOnClickListener {
                    setCurrentFilter(adapterPosition)
                }
            }
            return itemView
        }
    }
}
